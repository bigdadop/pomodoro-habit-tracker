package com.pomodorotracker;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A clock for tests that only moves forward when the test says so.
 * This lets a test "wait" 25 minutes or three days in a fraction of a second.
 */
public class MutableClock extends Clock {

    private volatile Instant now;
    private final ZoneId zone;

    public MutableClock(Instant start, ZoneId zone) {
        this.now = start;
        this.zone = zone;
    }

    /** A clock in UTC that starts at the given moment, for example "2026-10-24T09:00:00Z". */
    public static MutableClock at(String isoInstant) {
        return new MutableClock(Instant.parse(isoInstant), ZoneOffset.UTC);
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    public void advanceDays(int days) {
        advance(Duration.ofDays(days));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(now, newZone);
    }

    @Override
    public Instant instant() {
        return now;
    }
}
