package com.pomodorotracker.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreakServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);

    private final StreakService streaks = new StreakService();

    /** daysAgo(0, 1) = today and yesterday. */
    private static Set<LocalDate> daysAgo(int... offsets) {
        Set<LocalDate> dates = new HashSet<>();
        for (int offset : offsets) {
            dates.add(TODAY.minusDays(offset));
        }
        return dates;
    }

    @Test
    void countsConsecutiveDaysEndingToday() {
        assertEquals(0, streaks.calculateCurrentStreak(Set.of(), TODAY));
        assertEquals(1, streaks.calculateCurrentStreak(daysAgo(0), TODAY));
        assertEquals(3, streaks.calculateCurrentStreak(daysAgo(0, 1, 2), TODAY));

        // Also across the end of a month (and a leap day).
        LocalDate march2 = LocalDate.of(2028, 3, 2);
        Set<LocalDate> overMonthEnd = Set.of(LocalDate.of(2028, 2, 28), LocalDate.of(2028, 2, 29),
                LocalDate.of(2028, 3, 1), march2);
        assertEquals(4, streaks.calculateCurrentStreak(overMonthEnd, march2));
    }

    @Test
    void streakFromYesterdayIsStillActiveUntilTheDayIsOver() {
        assertEquals(2, streaks.calculateCurrentStreak(daysAgo(1, 2), TODAY));
    }

    @Test
    void aMissedDayResetsTheStreak() {
        assertEquals(2, streaks.calculateCurrentStreak(daysAgo(0, 1, 3, 4, 5), TODAY));   // 2 days ago was missed
        assertEquals(0, streaks.calculateCurrentStreak(daysAgo(2, 3, 4), TODAY));         // not done yesterday or today
    }

    @Test
    void longestStreakFindsTheBestRunEvenIfItIsOver() {
        assertEquals(0, streaks.calculateLongestStreak(Set.of()));
        // Runs: 10-12 days ago (3 days), 5-8 days ago (4 days) and today (1 day).
        assertEquals(4, streaks.calculateLongestStreak(daysAgo(0, 5, 6, 7, 8, 10, 11, 12)));
    }
}
