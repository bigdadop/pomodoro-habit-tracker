package com.pomodorotracker.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppConfigTest {

    @Test
    void readsSettingsFromEnvironmentVariablesWithSafeDefaults() {
        AppConfig defaults = AppConfig.fromEnvironment(Map.of());
        assertEquals("127.0.0.1", defaults.host());   // only reachable from this computer
        assertEquals(8080, defaults.port());
        assertEquals(Path.of("data", "app-data.json"), defaults.dataFile());
        assertEquals("http://127.0.0.1:8080", defaults.browserUrl(8080));

        AppConfig docker = AppConfig.fromEnvironment(
                Map.of("APP_HOST", "0.0.0.0", "APP_PORT", "9090", "APP_DATA_DIR", "/app/data"));
        assertEquals(9090, docker.port());
        assertEquals(Path.of("/app/data"), docker.dataDirectory());
        assertEquals("http://127.0.0.1:9090", docker.browserUrl(9090));   // a browser cannot open 0.0.0.0
    }

    @Test
    void rejectsAnInvalidPort() {
        assertThrows(IllegalArgumentException.class, () -> AppConfig.fromEnvironment(Map.of("APP_PORT", "eighty")));
        assertThrows(IllegalArgumentException.class, () -> AppConfig.fromEnvironment(Map.of("APP_PORT", "70000")));
    }
}
