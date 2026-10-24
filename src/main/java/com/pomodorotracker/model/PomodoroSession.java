package com.pomodorotracker.model;

import java.time.Instant;

/**
 * A focus session that was completed and saved.
 *
 * <p>This is a {@code record}: a short way to write a class whose values never change
 * after it is created. That fits a finished session perfectly - it is history.
 *
 * @param id              unique number of the session
 * @param habitId         the habit this session was for, or null if no habit was chosen
 * @param habitName       the habit's name at that time (kept even if the habit is deleted later)
 * @param durationMinutes length of the focus session in minutes
 * @param startedAt       when the session was started
 * @param completedAt     when the timer reached 00:00
 */
public record PomodoroSession(int id, Integer habitId, String habitName, int durationMinutes,
                              Instant startedAt, Instant completedAt) {
}
