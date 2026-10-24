package com.pomodorotracker.model;

/** The states the Pomodoro timer can be in. */
public enum TimerStatus {
    /** Not started yet - shows the full time of the selected mode. */
    IDLE,
    /** Counting down. */
    RUNNING,
    /** Stopped for a moment; can be resumed. */
    PAUSED,
    /** Reached 00:00. */
    FINISHED
}
