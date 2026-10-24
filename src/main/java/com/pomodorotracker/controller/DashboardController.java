package com.pomodorotracker.controller;

import com.pomodorotracker.service.HabitService;
import com.pomodorotracker.service.TimerService;
import com.pomodorotracker.view.DashboardView;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/** Handles {@code GET /api/dashboard}: the summary numbers and the most recent focus sessions. */
public class DashboardController extends BaseController {

    private static final int RECENT_SESSIONS = 10;

    private final HabitService habitService;
    private final TimerService timerService;

    public DashboardController(HabitService habitService, TimerService timerService) {
        this.habitService = habitService;
        this.timerService = timerService;
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        List<String> parts = pathParts(exchange);
        if (parts.size() != 2 || !parts.get(1).equals("dashboard")) {
            sendNotFound(exchange);
            return;
        }
        if (!exchange.getRequestMethod().equals("GET")) {
            sendMethodNotAllowed(exchange, "GET");
            return;
        }

        timerService.checkForCompletion();   // count a session that has only just finished
        LocalDate today = habitService.today();
        DashboardView dashboard = new DashboardView(
                today,
                habitService.getTimeZone().getId(),
                habitService.getAllHabits().size(),
                habitService.countCompletedToday(),
                habitService.countActiveStreaks(),
                habitService.getBestCurrentStreak(),
                timerService.countAllSessions(),
                timerService.countSessionsOn(today),
                timerService.focusMinutesOn(today),
                timerService.getRecentSessions(RECENT_SESSIONS));
        sendJson(exchange, 200, dashboard);
    }
}
