package com.pomodorotracker.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HabitTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);

    @Test
    void nameAndDescriptionAreTrimmedAndValidated() {
        Habit habit = new Habit(1, "  Read 10 pages  ", null, DAY);
        assertEquals("Read 10 pages", habit.getName());
        assertEquals("", habit.getDescription());

        assertThrows(IllegalArgumentException.class, () -> new Habit(2, "   ", "", DAY));
        assertThrows(IllegalArgumentException.class, () -> new Habit(3, null, "", DAY));
        assertThrows(IllegalArgumentException.class, () -> new Habit(4, "x".repeat(51), "", DAY));
        assertThrows(IllegalArgumentException.class, () -> habit.setDescription("d".repeat(201)));
        assertEquals("x".repeat(50), new Habit(5, "x".repeat(50), "", DAY).getName());   // exactly the limit is fine
    }

    @Test
    void sameDayIsOnlyCountedOnceAndCanBeUndone() {
        Habit habit = new Habit(1, "Walk", "", DAY);

        assertTrue(habit.markCompleted(DAY));
        assertFalse(habit.markCompleted(DAY));   // second click on the same day
        assertEquals(1, habit.getTotalCompletions());
        assertTrue(habit.isCompletedOn(DAY));

        assertTrue(habit.unmarkCompleted(DAY));
        assertFalse(habit.isCompletedOn(DAY));
        // Encapsulation: other classes cannot change the dates directly.
        assertThrows(UnsupportedOperationException.class, () -> habit.getCompletionDates().add(DAY));
    }
}
