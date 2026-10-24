package com.pomodorotracker.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PomodoroTimerTest {

    private static final Instant START = Instant.parse("2026-10-24T09:00:00Z");

    private final PomodoroTimer timer = new PomodoroTimer();

    private static Instant minutesLater(int minutes) {
        return START.plus(Duration.ofMinutes(minutes));
    }

    @Test
    void startCountsDownFromTwentyFiveMinutes() {
        assertEquals(TimerStatus.IDLE, timer.getStatus());
        assertEquals(TimerMode.FOCUS, timer.getMode());
        assertEquals(25 * 60, timer.getRemainingSeconds(START));

        timer.start(7, "Study Java", START);

        assertEquals(TimerStatus.RUNNING, timer.getStatus());
        assertEquals(25 * 60, timer.getRemainingSeconds(START.plusMillis(500)));   // rounded up: still 25:00
        assertEquals(24 * 60, timer.getRemainingSeconds(START.plusSeconds(60)));
        assertEquals(7, timer.getHabitId());
        assertEquals("Study Java", timer.getHabitName());
    }

    @Test
    void pauseFreezesTheTimeAndResumeContinuesFromThere() {
        timer.start(null, null, START);
        timer.pause(minutesLater(5));                                   // 20 minutes left

        assertEquals(TimerStatus.PAUSED, timer.getStatus());
        assertEquals(Duration.ofMinutes(20), timer.getRemaining(minutesLater(30)));   // time stands still

        timer.resume(minutesLater(30));
        assertEquals(TimerStatus.RUNNING, timer.getStatus());
        assertEquals(Duration.ofMinutes(10), timer.getRemaining(minutesLater(40)));
    }

    @Test
    void resetGoesBackToTheFullTimeAndForgetsTheSession() {
        timer.start(3, "Read", START);
        timer.reset();

        assertEquals(TimerStatus.IDLE, timer.getStatus());
        assertEquals(25 * 60, timer.getRemainingSeconds(minutesLater(10)));
        assertNull(timer.getHabitId());
        assertNull(timer.getStartedAt());
    }

    @Test
    void finishesExactlyOnceAndRemembersTheRealEndTime() {
        timer.start(null, null, START);
        assertFalse(timer.finishIfTimeIsUp(minutesLater(24)));

        // We only notice the end 3 minutes late, but the session really ended after 25 minutes.
        assertTrue(timer.finishIfTimeIsUp(minutesLater(28)));
        assertEquals(TimerStatus.FINISHED, timer.getStatus());
        assertEquals(minutesLater(25), timer.getFinishedAt());
        assertEquals(0, timer.getRemainingSeconds(minutesLater(28)));

        assertFalse(timer.finishIfTimeIsUp(minutesLater(29)));   // never a second time
    }

    @Test
    void actionsThatMakeNoSenseAreRejected() {
        assertThrows(IllegalStateException.class, () -> timer.pause(START));    // not running yet
        assertThrows(IllegalStateException.class, () -> timer.resume(START));   // not paused

        timer.start(null, null, START);
        assertThrows(IllegalStateException.class, () -> timer.start(null, null, START));
        assertThrows(IllegalStateException.class, () -> timer.resume(START));
        assertThrows(IllegalStateException.class, () -> timer.selectMode(TimerMode.LONG_BREAK));
        assertEquals(TimerStatus.RUNNING, timer.getStatus());   // nothing changed
    }

    @Test
    void breakModesLastFiveAndFifteenMinutesAndHaveNoHabit() {
        timer.selectMode(TimerMode.SHORT_BREAK);
        assertEquals(5 * 60, timer.getRemainingSeconds(START));
        timer.start(4, "Read", START);
        assertNull(timer.getHabitId());   // breaks are never linked to a habit

        timer.reset();
        timer.selectMode(TimerMode.LONG_BREAK);
        assertEquals(15 * 60, timer.getRemainingSeconds(START));

        assertEquals(TimerMode.SHORT_BREAK, TimerMode.fromName("short_break"));
        assertThrows(IllegalArgumentException.class, () -> TimerMode.fromName("nap"));
    }
}
