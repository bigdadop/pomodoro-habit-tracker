package com.pomodorotracker.view;

import com.pomodorotracker.model.PomodoroSession;
import com.pomodorotracker.model.PomodoroTimer;
import com.pomodorotracker.model.TimerMode;
import com.pomodorotracker.model.TimerStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The timer as the web page sees it: status, mode and the remaining seconds that
 * the Java timer has calculated. The page only counts down between updates.
 */
public record TimerView(TimerStatus status, TimerMode mode, String modeLabel, long durationSeconds,
                        long remainingSeconds, Integer habitId, String habitName, Instant startedAt,
                        PomodoroSession lastSavedSession, List<ModeOption> modes) {

    /** One button in the mode selector, for example "Focus - 25 min". */
    public record ModeOption(TimerMode mode, String label, int minutes) {
    }

    public static TimerView from(PomodoroTimer timer, Instant now, PomodoroSession lastSavedSession) {
        List<ModeOption> modes = new ArrayList<>();
        for (TimerMode mode : TimerMode.values()) {
            modes.add(new ModeOption(mode, mode.getLabel(), mode.getMinutes()));
        }
        TimerMode mode = timer.getMode();
        return new TimerView(timer.getStatus(), mode, mode.getLabel(), mode.getDuration().toSeconds(),
                timer.getRemainingSeconds(now), timer.getHabitId(), timer.getHabitName(),
                timer.getStartedAt(), lastSavedSession, modes);
    }
}
