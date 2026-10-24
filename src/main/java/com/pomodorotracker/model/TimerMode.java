package com.pomodorotracker.model;

import java.time.Duration;

/** The three kinds of Pomodoro sessions and how long each one lasts. */
public enum TimerMode {

    FOCUS("Focus", 25),
    SHORT_BREAK("Short break", 5),
    LONG_BREAK("Long break", 15);

    private final String label;
    private final int minutes;

    TimerMode(String label, int minutes) {
        this.label = label;
        this.minutes = minutes;
    }

    public String getLabel() {
        return label;
    }

    public int getMinutes() {
        return minutes;
    }

    public Duration getDuration() {
        return Duration.ofMinutes(minutes);
    }

    /** Only focus sessions are saved in the history and can be linked to a habit. */
    public boolean isFocus() {
        return this == FOCUS;
    }

    /** Turns text such as "SHORT_BREAK" into a TimerMode, with a friendly error message. */
    public static TimerMode fromName(String name) {
        for (TimerMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name == null ? "" : name.strip())) {
                return mode;
            }
        }
        throw new IllegalArgumentException(
                "Unknown timer mode '" + name + "'. Use FOCUS, SHORT_BREAK or LONG_BREAK.");
    }
}
