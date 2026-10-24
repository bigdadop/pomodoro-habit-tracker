package com.pomodorotracker.model;

import java.time.Duration;
import java.time.Instant;

/**
 * The Pomodoro timer: a small "state machine" with the states IDLE, RUNNING,
 * PAUSED and FINISHED (see {@link TimerStatus}).
 *
 * <p>The timer does not count down second by second. Instead it remembers
 * <em>when</em> the time will run out ({@code endsAt}) and works out the remaining
 * time from the current time whenever someone asks. That is why it stays exact even
 * if the web page is refreshed or closed for a while.
 *
 * <p>Every method that depends on the time takes "now" as a parameter. This keeps the
 * class easy to test: a test can simply pretend that ten minutes have passed.
 */
public class PomodoroTimer {

    private TimerMode mode = TimerMode.FOCUS;
    private TimerStatus status = TimerStatus.IDLE;
    private Integer habitId;           // optional habit for a focus session
    private String habitName;
    private Instant startedAt;         // when the current session was started
    private Instant endsAt;            // only while RUNNING: the moment the time runs out
    private Duration pausedRemaining;  // only while PAUSED: how much time was left
    private Instant finishedAt;        // only when FINISHED: the moment the time ran out

    /** Chooses Focus, Short break or Long break. Not allowed while a session is in progress. */
    public void selectMode(TimerMode newMode) {
        if (isInProgress()) {
            throw new IllegalStateException("Reset the timer before you choose a different mode.");
        }
        mode = newMode;
        clear();
    }

    /** Starts a new session in the current mode. A habit is only remembered for focus sessions. */
    public void start(Integer habitId, String habitName, Instant now) {
        if (isInProgress()) {
            throw new IllegalStateException("The timer is already running. Pause or reset it first.");
        }
        clear();
        if (mode.isFocus()) {
            this.habitId = habitId;
            this.habitName = habitName;
        }
        startedAt = now;
        endsAt = now.plus(mode.getDuration());
        status = TimerStatus.RUNNING;
    }

    public void pause(Instant now) {
        if (status != TimerStatus.RUNNING) {
            throw new IllegalStateException("The timer is not running, so it cannot be paused.");
        }
        pausedRemaining = getRemaining(now);
        endsAt = null;
        status = TimerStatus.PAUSED;
    }

    public void resume(Instant now) {
        if (status != TimerStatus.PAUSED) {
            throw new IllegalStateException("The timer is not paused, so it cannot be resumed.");
        }
        // Continue with exactly the time that was left when we paused.
        endsAt = now.plus(pausedRemaining);
        pausedRemaining = null;
        status = TimerStatus.RUNNING;
    }

    /** Stops the timer and shows the full time again. An unfinished session is thrown away. */
    public void reset() {
        clear();
    }

    /**
     * Moves a running timer to FINISHED once its time is up.
     *
     * @return true only at the moment the timer finishes, so the caller saves the session exactly once
     */
    public boolean finishIfTimeIsUp(Instant now) {
        if (status == TimerStatus.RUNNING && !now.isBefore(endsAt)) {
            finishedAt = endsAt;   // the real end time, even if we notice it a little later
            endsAt = null;
            status = TimerStatus.FINISHED;
            return true;
        }
        return false;
    }

    /** How much time is left at the moment "now". */
    public Duration getRemaining(Instant now) {
        if (status == TimerStatus.RUNNING) {
            Duration left = Duration.between(now, endsAt);
            return left.isNegative() ? Duration.ZERO : left;
        }
        if (status == TimerStatus.PAUSED) {
            return pausedRemaining;
        }
        if (status == TimerStatus.FINISHED) {
            return Duration.ZERO;
        }
        return mode.getDuration();   // IDLE: the full time of the selected mode
    }

    /** Remaining time in whole seconds, rounded up, so the display shows 25:00 right after starting. */
    public long getRemainingSeconds(Instant now) {
        Duration remaining = getRemaining(now);
        long seconds = remaining.getSeconds();
        return (remaining.getNano() > 0) ? seconds + 1 : seconds;
    }

    /** True while a session is running or paused (it has started but not finished). */
    public boolean isInProgress() {
        return status == TimerStatus.RUNNING || status == TimerStatus.PAUSED;
    }

    public TimerMode getMode() {
        return mode;
    }

    public TimerStatus getStatus() {
        return status;
    }

    public Integer getHabitId() {
        return habitId;
    }

    public String getHabitName() {
        return habitName;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    // Back to IDLE in the current mode, forgetting the previous session.
    private void clear() {
        status = TimerStatus.IDLE;
        habitId = null;
        habitName = null;
        startedAt = null;
        endsAt = null;
        pausedRemaining = null;
        finishedAt = null;
    }
}
