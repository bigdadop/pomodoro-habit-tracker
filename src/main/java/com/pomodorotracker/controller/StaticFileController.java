package com.pomodorotracker.controller;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Sends the web page files (HTML, CSS, JavaScript) to the browser. The files live in
 * src/main/resources/static and are packed into the JAR. They are the "View" of our
 * MVC design.
 */
public class StaticFileController extends BaseController {

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            ".html", "text/html; charset=utf-8",
            ".css", "text/css; charset=utf-8",
            ".js", "text/javascript; charset=utf-8",
            ".svg", "image/svg+xml",
            ".png", "image/png",
            ".ico", "image/x-icon");

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/api") || path.startsWith("/api/")) {
            sendNotFound(exchange);   // an API URL that no other controller knows
            return;
        }
        if (!exchange.getRequestMethod().equals("GET")) {
            sendMethodNotAllowed(exchange, "GET");
            return;
        }
        if (path.equals("/")) {
            path = "/index.html";
        }

        // Only known file types, and never ".." (which could reach other folders).
        String contentType = contentTypeOf(path);
        if (contentType == null || path.contains("..")) {
            sendError(exchange, 404, "Page not found: " + path);
            return;
        }
        try (InputStream file = StaticFileController.class.getResourceAsStream("/static" + path)) {
            if (file == null) {
                sendError(exchange, 404, "Page not found: " + path);
                return;
            }
            byte[] bytes = file.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            sendBytes(exchange, 200, bytes);
        }
    }

    private static String contentTypeOf(String path) {
        int dot = path.lastIndexOf('.');
        return (dot < 0) ? null : CONTENT_TYPES.get(path.substring(dot).toLowerCase());
    }
}
