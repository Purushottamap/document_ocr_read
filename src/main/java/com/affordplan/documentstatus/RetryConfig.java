package com.affordplan.documentstatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Loads worker settings from the same properties file used by the upload Lambda. */
final class RetryConfig {
    private RetryConfig() {}

    static String get(String name, String fallback) {
        String override = System.getProperty(name);
        if (override == null || override.isBlank()) override = System.getenv(name);
        if (override != null && !override.isBlank()) return override;
        String value = FileConfig.VALUES.getProperty(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    static String required(String name) {
        String value = get(name, StringConstant.EMPTY);
        if (value.isBlank()) throw new IllegalStateException(StringConstant.MISSING_CONFIG_PREFIX + name);
        return value;
    }

    private static Properties load() {
        String path = System.getProperty(StringConstant.CONFIG_FILE_PROPERTY);
        if (path == null || path.isBlank()) path = System.getenv(StringConstant.CONFIG_FILE_PROPERTY);
        boolean explicitPath = path != null && !path.isBlank();
        if (!explicitPath) path = StringConstant.DEFAULT_CONFIG_PATH;
        Properties properties = new Properties();
        if (!explicitPath && !Files.exists(Path.of(path))) return properties;
        try (var input = Files.newInputStream(Path.of(path))) {
            properties.load(input);
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException(StringConstant.CONFIG_LOAD_ERROR_PREFIX + path, e);
        }
    }

    private static final class FileConfig {
        private static final Properties VALUES = load();
    }
}

