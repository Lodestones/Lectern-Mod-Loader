package gg.lode.lectern.loader;

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

    Path fetchNewer(String manifestUrl, String current, Path mods) {
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
            String sha256 = jsonString(body, "sha256");
            if (latest == null || url == null) {
                log.warn("Update check skipped: manifest is missing version or url");
                return null;
            }
            if (!LecternPreLaunch.isNewer(latest, current)) {
                log.info("Already on the newest version (" + current + ")");
                return null;
            }

            Path target = mods.resolve("lectern-" + latest + ".jar");
            if (Files.isRegularFile(target)) {
                log.info("Version " + latest + " is already downloaded");
                return target;
            }

            log.info("Downloading Lectern " + latest);
            byte[] jar = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(DOWNLOAD_TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray()).body();

            if (sha256 != null && !sha256.equalsIgnoreCase(sha256Of(jar))) {
                log.warn("Update discarded: the download does not match the checksum in the manifest");
                return null;
            }

            Path part = mods.resolve("lectern-" + latest + ".jar.part");
            Files.write(part, jar);
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("Downloaded Lectern " + latest);
            return target;
        } catch (Throwable anything) {
            log.warn("Update check skipped: " + anything);
            return null;
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

    private static String sha256Of(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return String.format("%064x", new BigInteger(1, digest.digest(bytes)));
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
