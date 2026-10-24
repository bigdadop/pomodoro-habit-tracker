package com.pomodorotracker.service;

import com.pomodorotracker.MutableClock;
import com.pomodorotracker.model.Habit;
import com.pomodorotracker.repository.JsonDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HabitServiceTest {

    @TempDir
    Path tempDir;   // JUnit creates an empty folder for every test, so real data is never touched

    private MutableClock clock;
    private HabitService habitService;

    @BeforeEach
    void setUp() {
        clock = MutableClock.at("2026-10-24T09:00:00Z");
        habitService = new HabitService(new JsonDataRepository(dataFile()), new StreakService(), clock);
    }

    private Path dataFile() {
        return tempDir.resolve("app-data.json");
    }

    @Test
    void createHabitSavesItWithAnIdAndTodaysDate() {
        Habit habit = habitService.createHabit("  Read 10 pages ", "Before bed");

        assertEquals(1, habit.getId());
        assertEquals("Read 10 pages", habit.getName());
        assertEquals(LocalDate.of(2026, 10, 24), habit.getCreatedOn());
        // A new repository reads the same file, so the habit really was saved.
        Habit fromFile = new JsonDataRepository(dataFile()).findHabitById(1).orElseThrow();
        assertEquals("Before bed", fromFile.getDescription());
    }

    @Test
    void createHabitRejectsEmptyTooLongAndDuplicateNames() {
        habitService.createHabit("Read", "");

        IllegalArgumentException empty = assertThrows(IllegalArgumentException.class,
                () -> habitService.createHabit("   ", ""));
        assertEquals("Please enter a habit name.", empty.getMessage());
        assertThrows(IllegalArgumentException.class, () -> habitService.createHabit("x".repeat(51), ""));
        assertThrows(IllegalArgumentException.class, () -> habitService.createHabit("READ", ""));   // same name
        assertEquals(1, habitService.getAllHabits().size());
    }

    @Test
    void updateHabitChangesEverythingOrNothing() {
        Habit habit = habitService.createHabit("Read", "old text");
        habitService.createHabit("Walk", "");

        habitService.updateHabit(habit.getId(), "Read 20 pages", "new text");
        assertEquals("Read 20 pages", habitService.getHabit(habit.getId()).getName());
        assertEquals("new text", habitService.getHabit(habit.getId()).getDescription());

        // A too long description, or a name another habit already has: nothing changes.
        assertThrows(IllegalArgumentException.class,
                () -> habitService.updateHabit(habit.getId(), "Read 30 pages", "d".repeat(201)));
        assertThrows(IllegalArgumentException.class, () -> habitService.updateHabit(habit.getId(), "walk", ""));
        assertEquals("Read 20 pages", habitService.getHabit(habit.getId()).getName());
        assertThrows(NotFoundException.class, () -> habitService.updateHabit(99, "Ghost", ""));
    }

    @Test
    void deleteHabitRemovesItForGood() {
        Habit habit = habitService.createHabit("Read", "");

        habitService.deleteHabit(habit.getId());

        assertTrue(habitService.getAllHabits().isEmpty());
        assertThrows(NotFoundException.class, () -> habitService.getHabit(habit.getId()));
        assertThrows(NotFoundException.class, () -> habitService.deleteHabit(habit.getId()));
        assertTrue(new JsonDataRepository(dataFile()).findAllHabits().isEmpty());
    }

    @Test
    void completingTwiceOnTheSameDayCountsOnceAndCanBeUndone() {
        Habit habit = habitService.createHabit("Read", "");

        assertTrue(habitService.markCompletedToday(habit.getId()));
        assertFalse(habitService.markCompletedToday(habit.getId()));   // second click: no change
        assertEquals(1, habit.getTotalCompletions());
        assertEquals(1, habitService.getCurrentStreak(habit));
        assertEquals(1, habitService.countCompletedToday());

        assertTrue(habitService.undoCompletionToday(habit.getId()));
        assertFalse(habitService.undoCompletionToday(habit.getId()));
        assertEquals(0, habitService.getCurrentStreak(habit));
    }

    @Test
    void streakGrowsOnConsecutiveDaysAndResetsAfterAMissedDay() {
        Habit habit = habitService.createHabit("Read", "");
        habitService.markCompletedToday(habit.getId());   // day 1
        clock.advanceDays(1);
        habitService.markCompletedToday(habit.getId());   // day 2
        assertEquals(2, habitService.getCurrentStreak(habit));

        clock.advanceDays(1);                              // day 3: not done yet, streak still alive
        assertEquals(2, habitService.getCurrentStreak(habit));
        assertEquals(1, habitService.countActiveStreaks());
        assertEquals(2, habitService.getBestCurrentStreak());

        clock.advanceDays(1);                              // day 4: day 3 was missed
        assertEquals(0, habitService.getCurrentStreak(habit));
        assertEquals(0, habitService.countActiveStreaks());
        habitService.markCompletedToday(habit.getId());
        assertEquals(1, habitService.getCurrentStreak(habit));
        assertEquals(2, habitService.getLongestStreak(habit));   // the best run is still remembered
    }

    @Test
    void handlesMoreThanFiftyHabits() {
        for (int i = 1; i <= 60; i++) {
            habitService.createHabit("Habit number " + i, "Description " + i);
        }
        for (int id = 1; id <= 60; id += 2) {
            habitService.markCompletedToday(id);           // every second habit is done today
        }

        // Load everything again from the file, as if the app was restarted.
        HabitService afterRestart = new HabitService(new JsonDataRepository(dataFile()), new StreakService(), clock);
        List<Habit> habits = afterRestart.getAllHabits();
        assertEquals(60, habits.size());
        assertEquals(60, habits.stream().map(Habit::getId).distinct().count());   // all ids are different
        assertEquals("Habit number 60", habits.get(59).getName());
        assertEquals(30, afterRestart.countCompletedToday());
        assertEquals(30, afterRestart.countActiveStreaks());
    }
}
