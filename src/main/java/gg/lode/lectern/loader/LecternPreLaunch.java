package gg.lode.lectern.loader;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;

public final class LecternPreLaunch implements PreLaunchEntrypoint {
    private static Log LOG = new Log("LecternLoader");

    @Override
    public void onPreLaunch() {
        try {
            run();
        } catch (Throwable fatal) {
            LOG.error("Lectern could not be loaded", fatal);
        }
    }

    private void run() throws Exception {
        LoaderConfig container = LoaderConfig.embedded();
        if (container == null) {
            LOG.info("No container configuration; nothing to load");
            return;
        }

        Path root = FabricLoader.getInstance().getGameDir().resolve("lectern");
        Path mods = root.resolve("mods");
        Files.createDirectories(mods);

        LOG = new Log("LecternLoader", root.resolve("loader.log"));
        LoaderConfig user = LoaderConfig.user(root.resolve("loader.properties"));

        Path pinned = unpackPinned(container, root);
        Path chosen = pinned;
        String version = container.pinnedFileVersion();

        Path downloaded = newestIn(mods);
        if (downloaded != null && isNewer(versionOf(downloaded), version)) {
            chosen = downloaded;
            version = versionOf(downloaded);
            LOG.info("Using the downloaded " + downloaded.getFileName() + " over the pinned "
                    + container.pinnedFileVersion());
        }

        if (user.autoUpdate() && container.autoUpdate()) {
            Path fetched = new UpdateFetcher(LOG).fetchNewer(container.manifestUrl(), version, mods);
            if (fetched != null) {
                chosen = fetched;
                version = versionOf(fetched);
            }
        } else {
            LOG.info("Auto-update is off; running " + version);
        }

        if (chosen == null) {
            LOG.warn("Nothing to load: no pinned jar and nothing downloaded");
            return;
        }

        chosen = unwrapContainer(chosen, root);
        prunePinned(root.resolve("pinned"), chosen);

        try {
            new ModInjector(LOG).inject(chosen);
        } catch (Throwable failed) {
            if (chosen != pinned) {
                Files.writeString(root.resolve("pending-update"), version);
            }
            throw failed;
        }

        UpdateFetcher.prune(mods, chosen, LOG);
    }

    private Path unpackPinned(LoaderConfig container, Path root) throws Exception {
        String pinnedFile = container.pinnedFile();
        if (pinnedFile == null) return null;

        Path target = root.resolve("pinned").resolve(
                "lectern-" + container.pinnedFileVersion() + ".jar");
        Files.createDirectories(target.getParent());

        String expected = container.pinnedFileMd5();
        if (Files.isRegularFile(target) && (expected == null || expected.equalsIgnoreCase(md5(target)))) {
            return target;
        }

        try (InputStream in = LecternPreLaunch.class.getResourceAsStream(pinnedFile)) {
            if (in == null) {
                LOG.warn("Container says it carries " + pinnedFile + ", and it does not");
                return null;
            }
            Path temp = target.resolveSibling(target.getFileName() + ".part");
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            if (expected != null && !expected.equalsIgnoreCase(md5(temp))) {
                Files.deleteIfExists(temp);
                throw new IllegalStateException("Pinned jar does not match its own checksum");
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        LOG.info("Unpacked the pinned " + container.pinnedFileVersion());
        return target;
    }

    private Path unwrapContainer(Path jar, Path root) throws Exception {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar.toFile())) {
            var entry = zip.getEntry("lectern-loader.properties");
            if (entry == null) return jar;

            java.util.Properties properties = new java.util.Properties();
            try (InputStream in = zip.getInputStream(entry)) {
                properties.load(in);
            }
            String inner = properties.getProperty("pinnedFile", "").trim();
            String version = properties.getProperty("pinnedFileVersion", "").trim();
            if (inner.isEmpty()) {
                LOG.warn(jar.getFileName() + " is a container carrying nothing; ignoring it");
                return null;
            }

            var innerEntry = zip.getEntry(inner.startsWith("/") ? inner.substring(1) : inner);
            if (innerEntry == null) {
                LOG.warn(jar.getFileName() + " says it carries " + inner + ", and it does not");
                return null;
            }

            Path target = root.resolve("pinned").resolve("lectern-" + version + ".jar");
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".part");
            try (InputStream in = zip.getInputStream(innerEntry)) {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            LOG.info("Took " + version + " out of the downloaded container");
            return target;
        }
    }

    private void prunePinned(Path pinned, Path keep) {
        if (!Files.isDirectory(pinned)) return;
        try (var listing = Files.list(pinned)) {
            for (Path file : listing.toList()) {
                if (!file.getFileName().toString().endsWith(".jar") || file.equals(keep)) continue;
                try {
                    Files.delete(file);
                    LOG.info("Removed the unpacked " + file.getFileName());
                } catch (Exception locked) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private Path newestIn(Path mods) throws Exception {
        Path best = null;
        try (var listing = Files.list(mods)) {
            for (Path candidate : listing.toList()) {
                if (!candidate.getFileName().toString().endsWith(".jar")) continue;
                if (best == null || isNewer(versionOf(candidate), versionOf(best))) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static String versionOf(Path jar) {
        String name = jar.getFileName().toString();
        int dash = name.lastIndexOf('-');
        int dot = name.lastIndexOf('.');
        return dash < 0 || dot < dash ? "0.0.0" : name.substring(dash + 1, dot);
    }

    static boolean isNewer(String candidate, String current) {
        String[] a = candidate.split("[._-]");
        String[] b = current.split("[._-]");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int left = i < a.length ? number(a[i]) : 0;
            int right = i < b.length ? number(b[i]) : 0;
            if (left != right) return left > right;
        }
        return false;
    }

    private static int number(String part) {
        try {
            return Integer.parseInt(part.replaceAll("\\D", ""));
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }

    private static String md5(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
        }
        return String.format("%032x", new BigInteger(1, digest.digest()));
    }
}
