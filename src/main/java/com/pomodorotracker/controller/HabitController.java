package com.pomodorotracker.controller;

import com.pomodorotracker.model.Habit;
import com.pomodorotracker.service.HabitService;
import com.pomodorotracker.service.TimerService;
import com.pomodorotracker.view.HabitView;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles the habit URLs:
 * <pre>
 * GET    /api/habits                  list all habits
 * POST   /api/habits                  create a habit      {"name": "...", "description": "..."}
 * GET    /api/habits/{id}             one habit
 * PUT    /api/habits/{id}             edit a habit        {"name": "...", "description": "..."}
 * DELETE /api/habits/{id}             delete a habit
 * POST   /api/habits/{id}/complete    mark as done today
 * POST   /api/habits/{id}/uncomplete  undo today's completion
 * </pre>
 */
public class HabitController extends BaseController {

    private final HabitService habitService;
    private final TimerService timerService;

    /** The JSON the browser sends to create or edit a habit. */
    private record HabitRequest(String name, String description) {
    }

    /** The JSON sent back after a change: a message for the user and the updated habit. */
    private record HabitResult(String message, HabitView habit) {
    }

    private record MessageResult(String message) {
    }

    public HabitController(HabitService habitService, TimerService timerService) {
        this.habitService = habitService;
        this.timerService = timerService;
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        List<String> parts = pathParts(exchange);   // for example ["api", "habits", "3", "complete"]

        if (parts.size() < 2 || !parts.get(1).equals("habits") || parts.size() > 4) {
            sendNotFound(exchange);
        } else if (parts.size() == 2) {                       // /api/habits
            switch (method) {
                case "GET" -> sendJson(exchange, 200, listHabits());
                case "POST" -> createHabit(exchange);
                default -> sendMethodNotAllowed(exchange, "GET, POST");
            }
        } else if (parts.size() == 3) {                       // /api/habits/{id}
            int id = parseId(parts.get(2));
            switch (method) {
                case "GET" -> sendJson(exchange, 200, toView(habitService.getHabit(id)));
                case "PUT" -> updateHabit(exchange, id);
                case "DELETE" -> deleteHabit(exchange, id);
                default -> sendMethodNotAllowed(exchange, "GET, PUT, DELETE");
            }
        } else {                                              // /api/habits/{id}/something
            int id = parseId(parts.get(2));
            String action = parts.get(3);
            if (!action.equals("complete") && !action.equals("uncomplete")) {
                sendNotFound(exchange);
            } else if (!method.equals("POST")) {
                sendMethodNotAllowed(exchange, "POST");
            } else if (action.equals("complete")) {
                markCompleted(exchange, id);
            } else {
                undoCompletion(exchange, id);
            }
        }
    }

    private List<HabitView> listHabits() {
        List<HabitView> views = new ArrayList<>();
        for (Habit habit : habitService.getAllHabits()) {
            views.add(toView(habit));
        }
        return views;
    }

    private void createHabit(HttpExchange exchange) throws IOException {
        HabitRequest request = readHabitRequest(exchange);
        Habit habit = habitService.createHabit(request.name(), request.description());
        sendJson(exchange, 201, new HabitResult("Habit \"" + habit.getName() + "\" was added.", toView(habit)));
    }

    private void updateHabit(HttpExchange exchange, int id) throws IOException {
        HabitRequest request = readHabitRequest(exchange);
        Habit habit = habitService.updateHabit(id, request.name(), request.description());
        sendJson(exchange, 200, new HabitResult("Habit \"" + habit.getName() + "\" was updated.", toView(habit)));
    }

    private void deleteHabit(HttpExchange exchange, int id) throws IOException {
        String name = habitService.getHabit(id).getName();
        habitService.deleteHabit(id);
        sendJson(exchange, 200, new MessageResult("Habit \"" + name + "\" was deleted."));
    }

    private void markCompleted(HttpExchange exchange, int id) throws IOException {
        boolean changed = habitService.markCompletedToday(id);
        Habit habit = habitService.getHabit(id);
        int streak = habitService.getCurrentStreak(habit);
        String message = changed
                ? "Well done! \"" + habit.getName() + "\" is done for today. Streak: " + days(streak) + "."
                : "\"" + habit.getName() + "\" was already marked as done today.";
        sendJson(exchange, 200, new HabitResult(message, toView(habit)));
    }

    private void undoCompletion(HttpExchange exchange, int id) throws IOException {
        boolean changed = habitService.undoCompletionToday(id);
        Habit habit = habitService.getHabit(id);
        String message = changed
                ? "\"" + habit.getName() + "\" is no longer marked as done today."
                : "\"" + habit.getName() + "\" was not marked as done today.";
        sendJson(exchange, 200, new HabitResult(message, toView(habit)));
    }

    private HabitRequest readHabitRequest(HttpExchange exchange) throws IOException {
        HabitRequest request = readJson(exchange, HabitRequest.class);
        if (request == null) {
            throw new IllegalArgumentException(
                    "Send the habit as JSON, for example {\"name\": \"Read 10 pages\", \"description\": \"\"}.");
        }
        return request;
    }

    private HabitView toView(Habit habit) {
        return HabitView.from(habit, habitService.today(),
                habitService.getCurrentStreak(habit),
                habitService.getLongestStreak(habit),
                timerService.countSessionsForHabit(habit.getId()),
                timerService.focusMinutesForHabit(habit.getId()));
    }

    private static String days(int count) {
        return count + (count == 1 ? " day" : " days");
    }
}
