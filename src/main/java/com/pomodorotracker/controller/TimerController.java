package com.pomodorotracker.controller;

import com.pomodorotracker.model.TimerMode;
import com.pomodorotracker.service.TimerService;
import com.pomodorotracker.view.TimerView;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.List;

/**
 * Handles the timer URLs:
 * <pre>
 * GET  /api/timer          current timer state
 * POST /api/timer/mode     choose a mode        {"mode": "FOCUS" | "SHORT_BREAK" | "LONG_BREAK"}
 * POST /api/timer/start    start the timer      {"habitId": 3}   (habitId is optional)
 * POST /api/timer/pause
 * POST /api/timer/resume
 * POST /api/timer/reset
 * </pre>
 */
public class TimerController extends BaseController {

    private final TimerService timerService;

    private record ModeRequest(String mode) {
    }

    private record StartRequest(Integer habitId) {
    }

    /** The JSON sent back after an action: a message for the user and the new timer state. */
    private record TimerResult(String message, TimerView timer) {
    }

    public TimerController(TimerService timerService) {
        this.timerService = timerService;
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        List<String> parts = pathParts(exchange);   // for example ["api", "timer", "start"]

        if (parts.size() < 2 || !parts.get(1).equals("timer") || parts.size() > 3) {
            sendNotFound(exchange);
            return;
        }
        if (parts.size() == 2) {                    // /api/timer
            if (method.equals("GET")) {
                sendJson(exchange, 200, currentState());
            } else {
                sendMethodNotAllowed(exchange, "GET");
            }
            return;
        }
        if (!method.equals("POST")) {
            sendMethodNotAllowed(exchange, "POST");
            return;
        }

        String message;
        switch (parts.get(2)) {
            case "mode" -> message = selectMode(exchange);
            case "start" -> message = start(exchange);
            case "pause" -> {
                timerService.pause();
                message = "Timer paused.";
            }
            case "resume" -> {
                timerService.resume();
                message = "Timer resumed.";
            }
            case "reset" -> {
                timerService.reset();
                message = "Timer reset.";
            }
            default -> {
                sendNotFound(exchange);
                return;
            }
        }
        sendJson(exchange, 200, new TimerResult(message, currentState()));
    }

    private String selectMode(HttpExchange exchange) throws IOException {
        ModeRequest request = readJson(exchange, ModeRequest.class);
        if (request == null) {
            throw new IllegalArgumentException("Send the mode as JSON, for example {\"mode\": \"SHORT_BREAK\"}.");
        }
        TimerMode mode = TimerMode.fromName(request.mode());
        timerService.selectMode(mode);
        return mode.getLabel() + " selected (" + mode.getMinutes() + " minutes).";
    }

    private String start(HttpExchange exchange) throws IOException {
        StartRequest request = readJson(exchange, StartRequest.class);   // the body is optional
        Integer habitId = (request == null) ? null : request.habitId();
        timerService.start(habitId);

        String habitName = timerService.getTimer().getHabitName();
        TimerMode mode = timerService.getTimer().getMode();
        String forHabit = (habitName == null) ? "" : " for \"" + habitName + "\"";
        return mode.getLabel() + " started" + forHabit + " (" + mode.getMinutes() + " minutes).";
    }

    private TimerView currentState() {
        return TimerView.from(timerService.getTimer(), timerService.now(), timerService.getLastSavedSession());
    }
}
