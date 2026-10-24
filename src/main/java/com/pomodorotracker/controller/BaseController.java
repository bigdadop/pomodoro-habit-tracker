package com.pomodorotracker.controller;

import com.google.gson.JsonParseException;
import com.pomodorotracker.service.NotFoundException;
import com.pomodorotracker.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Code shared by all controllers: reading JSON requests, sending JSON responses and
 * turning exceptions into clear HTTP error messages.
 *
 * <p>Each controller extends this class (inheritance) and only implements
 * {@link #handleRequest}. The Java HTTP server calls {@link #handle} on every controller
 * in exactly the same way, but each one runs its own code - that is polymorphism.
 */
public abstract class BaseController implements HttpHandler {

    private static final int MAX_REQUEST_BYTES = 10_000;

    /** The error JSON sent to the browser, for example {"error": "Please enter a habit name."}. */
    private record ErrorResponse(String error) {
    }

    @Override
    public final void handle(HttpExchange exchange) throws IOException {
        try {
            if (isFromAnotherWebsite(exchange)) {
                sendError(exchange, 403, "Requests from other websites are not allowed.");
            } else {
                handleRequest(exchange);
            }
        } catch (NotFoundException e) {
            sendError(exchange, 404, e.getMessage());
        } catch (JsonParseException e) {
            sendError(exchange, 400, "The request body is not valid JSON.");
        } catch (IllegalArgumentException e) {      // invalid input, e.g. an empty habit name
            sendError(exchange, 400, e.getMessage());
        } catch (IllegalStateException e) {         // e.g. pausing a timer that is not running
            sendError(exchange, 409, e.getMessage());
        } catch (UncheckedIOException e) {          // e.g. the data file could not be written
            System.err.println("ERROR: " + e.getMessage());
            sendError(exchange, 500, e.getMessage());
        } catch (RuntimeException e) {
            System.err.println("ERROR while handling " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
            e.printStackTrace();
            sendError(exchange, 500, "Something went wrong on the server. Check the terminal for details.");
        } finally {
            exchange.close();
        }
    }

    /** Each controller answers its own URLs here. */
    protected abstract void handleRequest(HttpExchange exchange) throws IOException;

    /** Sends any Java object as JSON with the given HTTP status code (200 = OK, 201 = Created ...). */
    protected void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = JsonUtil.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        sendBytes(exchange, status, bytes);
    }

    protected void sendBytes(HttpExchange exchange, int status, byte[] bytes) throws IOException {
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    protected void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, new ErrorResponse(message));
    }

    protected void sendNotFound(HttpExchange exchange) throws IOException {
        sendError(exchange, 404, "There is no API endpoint for "
                + exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + ".");
    }

    protected void sendMethodNotAllowed(HttpExchange exchange, String allowedMethods) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowedMethods);
        sendError(exchange, 405, exchange.getRequestMethod() + " is not allowed here. Allowed: " + allowedMethods + ".");
    }

    /**
     * Reads the JSON request body into an object of the given type.
     *
     * @return the object, or null if the body was empty
     */
    protected <T> T readJson(HttpExchange exchange, Class<T> type) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (bytes.length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("The request body is too large.");
        }
        String body = new String(bytes, StandardCharsets.UTF_8);
        return body.isBlank() ? null : JsonUtil.fromJson(body, type);
    }

    /** Splits "/api/habits/3/complete" into ["api", "habits", "3", "complete"]. */
    protected List<String> pathParts(HttpExchange exchange) {
        List<String> parts = new ArrayList<>();
        for (String part : exchange.getRequestURI().getPath().split("/")) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        return parts;
    }

    protected int parseId(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text + "' is not a valid id. Ids are whole numbers such as 3.");
        }
    }

    /**
     * Browsers add an "Origin" header when a page from another website sends a request
     * to this app. We only accept changes (POST, PUT, DELETE) from our own page, so
     * another website cannot secretly change your habits.
     */
    private boolean isFromAnotherWebsite(HttpExchange exchange) {
        if (exchange.getRequestMethod().equals("GET")) {
            return false;
        }
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        String host = exchange.getRequestHeaders().getFirst("Host");
        return origin != null && !origin.equals("http://" + host);
    }
}
