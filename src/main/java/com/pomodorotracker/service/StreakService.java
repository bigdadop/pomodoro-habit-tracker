package com.pomodorotracker.service;

import java.time.LocalDate;
import java.util.Set;
import java.util.TreeSet;

/**
 * Calculates streaks: how many days in a row a habit was completed.
 *
 * <p>Rules for the <b>current</b> streak:
 * <ul>
 *   <li>Completing a habit several times on one day counts as one day (the dates are a Set).</li>
 *   <li>If the habit is done today, we count today and the days before it.</li>
 *   <li>If it is not done yet today, yesterday's streak is still alive - you have until
 *       midnight - so we count back from yesterday.</li>
 *   <li>As soon as one whole day is missed, the streak starts again from zero.</li>
 * </ul>
 */
public class StreakService {

    public int calculateCurrentStreak(Set<LocalDate> completedDates, LocalDate today) {
        LocalDate day = completedDates.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (completedDates.contains(day)) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }

    /** The longest run of consecutive days ever, for example to show "Best: 12 days". */
    public int calculateLongestStreak(Set<LocalDate> completedDates) {
        int longest = 0;
        int current = 0;
        LocalDate previous = null;
        for (LocalDate date : new TreeSet<>(completedDates)) {   // oldest date first
            if (previous != null && previous.plusDays(1).equals(date)) {
                current++;          // the day after the previous one: the run continues
            } else {
                current = 1;        // a gap: a new run starts
            }
            longest = Math.max(longest, current);
            previous = date;
        }
        return longest;
    }
}
