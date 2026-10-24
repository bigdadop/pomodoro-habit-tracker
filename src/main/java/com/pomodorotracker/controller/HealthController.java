package com.pomodorotracker.controller;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.List;

/** Handles {@code GET /api/health}. Docker uses it to check that the app is up. */
public class HealthController extends BaseController {

    private record Health(String status) {
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        List<String> parts = pathParts(exchange);
        if (parts.size() != 2 || !parts.get(1).equals("health")) {
            sendNotFound(exchange);
        } else if (!exchange.getRequestMethod().equals("GET")) {
            sendMethodNotAllowed(exchange, "GET");
        } else {
            sendJson(exchange, 200, new Health("ok"));
        }
    }
}
