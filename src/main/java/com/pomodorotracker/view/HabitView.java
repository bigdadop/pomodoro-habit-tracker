package com.pomodorotracker.view;

import com.pomodorotracker.model.Habit;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything the web page shows about one habit. It is sent to the browser as JSON.
 *
 * <p>The model class {@link Habit} only stores facts (name, completed days). This view
 * adds the numbers worked out by the services, such as the current streak.
 */
public record HabitView(int id, String name, String description, LocalDate createdOn,
                        boolean completedToday, int currentStreak, int longestStreak,
                        int totalCompletions, List<DayView> lastSevenDays,
                        int focusSessions, int focusMinutes) {

    /** One square in the "last 7 days" row on the page. */
    public record DayView(LocalDate date, boolean completed) {
    }

    public static HabitView from(Habit habit, LocalDate today, int currentStreak, int longestStreak,
                                 int focusSessions, int focusMinutes) {
        List<DayView> lastSevenDays = new ArrayList<>();
        for (int daysAgo = 6; daysAgo >= 0; daysAgo--) {
            LocalDate day = today.minusDays(daysAgo);
            lastSevenDays.add(new DayView(day, habit.isCompletedOn(day)));
        }
        return new HabitView(habit.getId(), habit.getName(), habit.getDescription(), habit.getCreatedOn(),
                habit.isCompletedOn(today), currentStreak, longestStreak, habit.getTotalCompletions(),
                lastSevenDays, focusSessions, focusMinutes);
    }
}
