package com.pomodorotracker.controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pomodorotracker.AppServer;
import com.pomodorotracker.util.AppConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts the real web server on a free port (with a temporary data folder) and talks
 * to it over HTTP, exactly like the browser does.
 */
class ApiIntegrationTest {

    @TempDir
    Path tempDir;

    private AppServer server;
    private HttpClient client;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        Clock clock = Clock.fixed(Instant.parse("2026-10-24T09:00:00Z"), ZoneOffset.UTC);
        server = new AppServer(new AppConfig("127.0.0.1", 0, tempDir), clock);   // port 0 = any free port
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getPort();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    private HttpResponse<String> send(String method, String path, String json) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path));
        if (json == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json));
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonObject json(HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private int habitCount() throws Exception {
        return JsonParser.parseString(send("GET", "/api/habits", null).body()).getAsJsonArray().size();
    }

    @Test
    void servesTheWebPageAndTheHealthCheck() throws Exception {
        HttpResponse<String> page = send("GET", "/", null);
        assertEquals(200, page.statusCode());
        assertTrue(page.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(page.body().contains("Pomodoro Habit Tracker"));
        assertEquals(200, send("GET", "/app.js", null).statusCode());
        assertEquals(200, send("GET", "/style.css", null).statusCode());
        assertEquals(404, send("GET", "/missing.html", null).statusCode());
        assertEquals("ok", json(send("GET", "/api/health", null)).get("status").getAsString());
    }

    @Test
    void habitCanBeCreatedEditedCompletedAndDeletedOverHttp() throws Exception {
        HttpResponse<String> created = send("POST", "/api/habits", "{\"name\": \"Read\", \"description\": \"10 pages\"}");
        assertEquals(201, created.statusCode());
        int id = json(created).getAsJsonObject("habit").get("id").getAsInt();
        assertEquals(1, habitCount());

        HttpResponse<String> edited = send("PUT", "/api/habits/" + id, "{\"name\": \"Read daily\", \"description\": \"\"}");
        assertEquals("Read daily", json(edited).getAsJsonObject("habit").get("name").getAsString());

        JsonObject completed = json(send("POST", "/api/habits/" + id + "/complete", null)).getAsJsonObject("habit");
        assertTrue(completed.get("completedToday").getAsBoolean());
        assertEquals(1, completed.get("currentStreak").getAsInt());

        JsonObject dashboard = json(send("GET", "/api/dashboard", null));
        assertEquals("2026-10-24", dashboard.get("today").getAsString());
        assertEquals(1, dashboard.get("totalHabits").getAsInt());
        assertEquals(1, dashboard.get("completedToday").getAsInt());
        assertEquals(1, dashboard.get("activeStreaks").getAsInt());

        assertEquals(200, send("DELETE", "/api/habits/" + id, null).statusCode());
        assertEquals(404, send("GET", "/api/habits/" + id, null).statusCode());
    }

    @Test
    void badRequestsGetClearErrorMessagesAndStatusCodes() throws Exception {
        HttpResponse<String> emptyName = send("POST", "/api/habits", "{\"name\": \"  \"}");
        assertEquals(400, emptyName.statusCode());
        assertEquals("Please enter a habit name.", json(emptyName).get("error").getAsString());

        assertEquals(400, send("POST", "/api/habits", "{not json").statusCode());
        assertEquals(400, send("GET", "/api/habits/abc", null).statusCode());
        assertEquals(404, send("GET", "/api/habits/99", null).statusCode());
        assertEquals(404, send("GET", "/api/unknown", null).statusCode());
        assertEquals(405, send("PATCH", "/api/habits", null).statusCode());
        assertEquals(409, send("POST", "/api/timer/pause", null).statusCode());   // the timer is not running

        // A change sent by another website is refused.
        HttpRequest fromOtherSite = HttpRequest.newBuilder(URI.create(baseUrl + "/api/habits"))
                .header("Origin", "http://evil.example")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\": \"Hacked\"}"))
                .build();
        assertEquals(403, client.send(fromOtherSite, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(0, habitCount());
    }

    @Test
    void timerCanBeStartedPausedResumedAndResetOverHttp() throws Exception {
        int habitId = json(send("POST", "/api/habits", "{\"name\": \"Study\"}"))
                .getAsJsonObject("habit").get("id").getAsInt();

        JsonObject started = json(send("POST", "/api/timer/start", "{\"habitId\": " + habitId + "}"))
                .getAsJsonObject("timer");
        assertEquals("RUNNING", started.get("status").getAsString());
        assertEquals("Study", started.get("habitName").getAsString());
        assertEquals(1500, started.get("remainingSeconds").getAsLong());

        assertEquals("PAUSED", timerStatusAfter("pause"));
        assertEquals("RUNNING", timerStatusAfter("resume"));
        assertEquals("IDLE", timerStatusAfter("reset"));

        JsonObject shortBreak = json(send("POST", "/api/timer/mode", "{\"mode\": \"SHORT_BREAK\"}"))
                .getAsJsonObject("timer");
        assertEquals(300, shortBreak.get("remainingSeconds").getAsLong());
        assertEquals(3, shortBreak.getAsJsonArray("modes").size());
    }

    private String timerStatusAfter(String action) throws Exception {
        HttpResponse<String> response = send("POST", "/api/timer/" + action, null);
        assertEquals(200, response.statusCode());
        return json(response).getAsJsonObject("timer").get("status").getAsString();
    }
}
