package gg.lode.lectern.loader;

import gg.lode.lectern.loader.ui.LoaderUi;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;

final class UpdateFetcher {
    private static final Duration MANIFEST_TIMEOUT = Duration.ofSeconds(6);

    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(5);

    private final Log log;

    UpdateFetcher(Log log) {
        this.log = log;
    }

    record Available(String version, String url, String sha256) {
    }

    Available check(String manifestUrl, String current) {
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(MANIFEST_TIMEOUT).build();
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(manifestUrl)).timeout(MANIFEST_TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Update check skipped: manifest returned " + response.statusCode());
                return null;
            }

            String body = response.body();
            String latest = jsonString(body, "version");
            String url = jsonString(body, "url");
            if (latest == null || url == null) {
                log.warn("Update check skipped: manifest is missing version or url");
                return null;
            }
            if (!LecternPreLaunch.isNewer(latest, current)) {
                log.info("Already on the newest version (" + current + ")");
                return null;
            }
            return new Available(latest, url, jsonString(body, "sha256"));
        } catch (Throwable anything) {
            log.warn("Update check skipped: " + anything);
            return null;
        }
    }

    Path download(Available update, Path mods, LoaderUi ui, String displayName) {
        Path target = mods.resolve("lectern-" + update.version() + ".jar");
        if (Files.isRegularFile(target)) {
            log.info("Version " + update.version() + " is already downloaded");
            return target;
        }

        Path part = mods.resolve("lectern-" + update.version() + ".jar.part");
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(MANIFEST_TIMEOUT).build();
            HttpResponse<InputStream> response = http.send(
                    HttpRequest.newBuilder(URI.create(update.url())).timeout(DOWNLOAD_TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                log.warn("Download skipped: server returned " + response.statusCode());
                return null;
            }

            long size = response.headers().firstValueAsLong("content-length").orElse(-1);
            ui.downloadStarted(displayName, update.version(), size);
            log.info("Downloading Lectern " + update.version()
                    + (size > 0 ? " (" + size / 1_048_576 + " MB)" : ""));

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long read = 0;
            try (InputStream in = response.body();
                 OutputStream out = Files.newOutputStream(part)) {
                byte[] buffer = new byte[65536];
                int count;
                long lastReport = 0;
                while ((count = in.read(buffer)) > 0) {
                    out.write(buffer, 0, count);
                    digest.update(buffer, 0, count);
                    read += count;
                    if (read - lastReport > 262144) {
                        ui.downloadProgress(read);
                        lastReport = read;
                    }
                }
            }
            ui.downloadProgress(read);

            String actual = String.format("%064x", new BigInteger(1, digest.digest()));
            if (update.sha256() != null && !update.sha256().equalsIgnoreCase(actual)) {
                Files.deleteIfExists(part);
                log.warn("Update discarded: the download does not match the checksum in the manifest");
                return null;
            }

            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("Downloaded Lectern " + update.version());
            return target;
        } catch (Throwable anything) {
            try {
                Files.deleteIfExists(part);
            } catch (Exception ignored) {
            }
            log.warn("Download failed: " + anything);
            return null;
        } finally {
            ui.downloadFinished();
        }
    }

    static String jsonString(String json, String key) {
        String needle = "\"" + key + "\"";
        int at = json.indexOf(needle);
        if (at < 0) return null;
        int colon = json.indexOf(':', at + needle.length());
        if (colon < 0) return null;
        int open = json.indexOf('"', colon + 1);
        if (open < 0) return null;
        int close = json.indexOf('"', open + 1);
        if (close < 0) return null;
        return json.substring(open + 1, close);
    }

    static void prune(Path mods, Path keep, Log log) {
        try (var listing = Files.list(mods)) {
            for (Path file : listing.toList()) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".jar") || file.equals(keep)) continue;
                try {
                    Files.delete(file);
                    log.info("Removed the older " + name);
                } catch (Exception locked) {
                }
            }
        } catch (Exception ignored) {
        }
    }
}
