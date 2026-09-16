package gg.lode.lectern.loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class LoaderConfig {
    private static final String EMBEDDED = "/lectern-loader.properties";

    private final Properties values;

    private LoaderConfig(Properties values) {
        this.values = values;
    }

    public static LoaderConfig embedded() {
        try (InputStream in = LoaderConfig.class.getResourceAsStream(EMBEDDED)) {
            if (in == null) return null;
            Properties properties = new Properties();
            properties.load(in);
            return new LoaderConfig(properties);
        } catch (IOException broken) {
            return null;
        }
    }

    public static LoaderConfig user(Path file) {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (IOException ignored) {
            }
        }
        return new LoaderConfig(properties);
    }

    public String get(String key, String fallback) {
        String value = values.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public boolean flag(String key, boolean fallback) {
        String value = values.getProperty(key);
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value.trim());
    }

    public String displayName() {
        return get("displayName", "Lectern");
    }

    public String pinnedFile() {
        return get("pinnedFile", null);
    }

    public String pinnedFileMd5() {
        return get("pinnedFileMd5", null);
    }

    public String pinnedFileVersion() {
        return get("pinnedFileVersion", "0.0.0");
    }

    public String manifestUrl() {
        return get("manifestUrl", "https://lode.gg/api/lectern/version");
    }

    public boolean autoUpdate() {
        return flag("autoUpdate", true);
    }
}
