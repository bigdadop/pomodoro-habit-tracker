package com.pomodorotracker.view;

import com.pomodorotracker.model.PomodoroSession;

import java.time.LocalDate;
import java.util.List;

/** The numbers and recent sessions shown on the dashboard. */
public record DashboardView(LocalDate today, String timeZone,
                            int totalHabits, int completedToday,
                            int activeStreaks, int bestCurrentStreak,
                            int totalFocusSessions, int focusSessionsToday, int focusMinutesToday,
                            List<PomodoroSession> recentSessions) {
}
