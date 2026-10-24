package com.pomodorotracker.util;

import java.nio.file.Path;
import java.util.Map;

/**
 * Settings that can be changed with environment variables:
 * <ul>
 *   <li>{@code APP_HOST} - network address to listen on (default 127.0.0.1 = only this computer)</li>
 *   <li>{@code APP_PORT} - port number (default 8080)</li>
 *   <li>{@code APP_DATA_DIR} - folder for the JSON data file (default "data")</li>
 * </ul>
 *
 * @param host          address the web server listens on
 * @param port          port the web server listens on (0 = any free port, used by tests)
 * @param dataDirectory folder that contains app-data.json
 */
public record AppConfig(String host, int port, Path dataDirectory) {

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8080;
    public static final String DEFAULT_DATA_DIR = "data";
    public static final String DATA_FILE_NAME = "app-data.json";

    /** Reads the settings from environment variables, usually {@code System.getenv()}. */
    public static AppConfig fromEnvironment(Map<String, String> env) {
        String host = valueOrDefault(env.get("APP_HOST"), DEFAULT_HOST);
        int port = parsePort(valueOrDefault(env.get("APP_PORT"), String.valueOf(DEFAULT_PORT)));
        Path dataDirectory = Path.of(valueOrDefault(env.get("APP_DATA_DIR"), DEFAULT_DATA_DIR));
        return new AppConfig(host, port, dataDirectory);
    }

    /** The JSON file where all habits and focus sessions are saved. */
    public Path dataFile() {
        return dataDirectory.resolve(DATA_FILE_NAME);
    }

    /**
     * The address to open in a browser. "0.0.0.0" means "every network address of this
     * machine" (used inside Docker), which a browser cannot open, so we show 127.0.0.1.
     */
    public String browserUrl(int actualPort) {
        boolean listensEverywhere = host.equals("0.0.0.0") || host.equals("::");
        return "http://" + (listensEverywhere ? "127.0.0.1" : host) + ":" + actualPort;
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value.strip();
    }

    private static int parsePort(String text) {
        try {
            int port = Integer.parseInt(text);
            if (port >= 1 && port <= 65535) {
                return port;
            }
        } catch (NumberFormatException e) {
            // not a number - handled below
        }
        throw new IllegalArgumentException(
                "APP_PORT must be a whole number between 1 and 65535, but it was '" + text + "'.");
    }
}
