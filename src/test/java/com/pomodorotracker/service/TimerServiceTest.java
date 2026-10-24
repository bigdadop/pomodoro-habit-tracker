package com.pomodorotracker.service;

import com.pomodorotracker.MutableClock;
import com.pomodorotracker.model.Habit;
import com.pomodorotracker.model.PomodoroSession;
import com.pomodorotracker.model.TimerMode;
import com.pomodorotracker.model.TimerStatus;
import com.pomodorotracker.repository.JsonDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimerServiceTest {

    @TempDir
    Path tempDir;

    private MutableClock clock;
    private HabitService habitService;
    private TimerService timerService;

    @BeforeEach
    void setUp() {
        clock = MutableClock.at("2026-10-24T09:00:00Z");
        JsonDataRepository repository = new JsonDataRepository(tempDir.resolve("app-data.json"));
        habitService = new HabitService(repository, new StreakService(), clock);
        timerService = new TimerService(repository, habitService, clock);
    }

    @Test
    void completedFocusSessionIsRecordedAndSaved() {
        Habit habit = habitService.createHabit("Study Java", "");
        timerService.start(habit.getId());
        clock.advance(Duration.ofMinutes(10));
        assertFalse(timerService.checkForCompletion());

        clock.advance(Duration.ofMinutes(15));               // 25 minutes in total
        assertTrue(timerService.checkForCompletion());
        assertEquals(TimerStatus.FINISHED, timerService.getTimer().getStatus());

        PomodoroSession session = timerService.getLastSavedSession();
        assertEquals(habit.getId(), session.habitId());
        assertEquals("Study Java", session.habitName());
        assertEquals(25, session.durationMinutes());
        assertEquals(Instant.parse("2026-10-24T09:00:00Z"), session.startedAt());
        assertEquals(Instant.parse("2026-10-24T09:25:00Z"), session.completedAt());

        // It is in the data file and counted in the statistics - and only once.
        assertEquals(1, new JsonDataRepository(tempDir.resolve("app-data.json")).findAllSessions().size());
        assertFalse(timerService.checkForCompletion());
        assertEquals(1, timerService.countAllSessions());
        assertEquals(1, timerService.countSessionsOn(LocalDate.of(2026, 10, 24)));
        assertEquals(25, timerService.focusMinutesOn(LocalDate.of(2026, 10, 24)));
        assertEquals(1, timerService.countSessionsForHabit(habit.getId()));
        assertEquals(25, timerService.focusMinutesForHabit(habit.getId()));
        assertEquals(session, timerService.getRecentSessions(5).get(0));
    }

    @Test
    void pausedTimeDoesNotCountAsFocusTime() {
        timerService.start(null);
        clock.advance(Duration.ofMinutes(10));
        timerService.pause();
        clock.advance(Duration.ofMinutes(30));                 // a long pause
        assertFalse(timerService.checkForCompletion());
        assertEquals(15 * 60, timerService.getTimer().getRemainingSeconds(timerService.now()));

        timerService.resume();
        clock.advance(Duration.ofMinutes(15));
        assertTrue(timerService.checkForCompletion());
        assertEquals(Instant.parse("2026-10-24T09:55:00Z"), timerService.getLastSavedSession().completedAt());
        assertNull(timerService.getLastSavedSession().habitId());   // no habit was chosen
    }

    @Test
    void resetAndBreaksDoNotCreateFocusSessions() {
        timerService.start(null);
        clock.advance(Duration.ofMinutes(20));
        timerService.reset();                                  // stopped 5 minutes early
        clock.advance(Duration.ofMinutes(30));
        assertFalse(timerService.checkForCompletion());

        timerService.selectMode(TimerMode.SHORT_BREAK);
        timerService.start(null);
        clock.advance(Duration.ofMinutes(5));
        assertTrue(timerService.checkForCompletion());         // the break finished...
        assertEquals(0, timerService.countAllSessions());      // ...but breaks are not saved
    }

    @Test
    void startingWithAnUnknownHabitFailsAndLeavesTheTimerIdle() {
        assertThrows(NotFoundException.class, () -> timerService.start(42));
        assertEquals(TimerStatus.IDLE, timerService.getTimer().getStatus());
    }
}
