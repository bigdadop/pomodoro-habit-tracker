package com.pomodorotracker.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything that is stored in the JSON data file: all habits, all completed
 * focus sessions, and the next free id numbers.
 */
public class AppData {

    // Ids are never reused, even after a habit is deleted, so old sessions can
    // never point to the wrong habit.
    private int nextHabitId = 1;
    private int nextSessionId = 1;
    private List<Habit> habits = new ArrayList<>();
    private List<PomodoroSession> sessions = new ArrayList<>();

    public List<Habit> getHabits() {
        if (habits == null) {   // the file may contain "habits": null
            habits = new ArrayList<>();
        }
        return habits;
    }

    public List<PomodoroSession> getSessions() {
        if (sessions == null) {
            sessions = new ArrayList<>();
        }
        return sessions;
    }

    public int takeNextHabitId() {
        return nextHabitId++;
    }

    public int takeNextSessionId() {
        return nextSessionId++;
    }

    /**
     * Tidies up data that was loaded from a file (which might have been edited by
     * hand): removes empty or broken entries and makes sure new ids cannot clash
     * with ids that are already used.
     */
    public void removeInvalidEntries() {
        // First make sure new ids are higher than every id in the file (even broken entries)...
        for (Habit habit : getHabits()) {
            if (habit != null) {
                nextHabitId = Math.max(nextHabitId, habit.getId() + 1);
            }
        }
        for (PomodoroSession session : getSessions()) {
            if (session != null) {
                nextSessionId = Math.max(nextSessionId, session.id() + 1);
            }
        }
        // ...then drop the entries that cannot be used.
        getHabits().removeIf(habit -> habit == null || habit.getName() == null || habit.getName().isBlank());
        getSessions().removeIf(session -> session == null || session.completedAt() == null);
    }
}
