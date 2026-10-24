package com.pomodorotracker.service;

import com.pomodorotracker.model.PomodoroSession;
import com.pomodorotracker.model.PomodoroTimer;
import com.pomodorotracker.model.TimerMode;
import com.pomodorotracker.repository.JsonDataRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Controls the Pomodoro timer and saves every completed focus session.
 *
 * <p>The timer lives in the memory of the Java application, so refreshing the web page
 * does not change it. The running timer is <b>not</b> written to the data file: if the
 * Java application stops while a session is running or paused, that unfinished session
 * is lost. Completed focus sessions are saved to the file straight away.
 */
public class TimerService {

    private final PomodoroTimer timer = new PomodoroTimer();
    private final JsonDataRepository repository;
    private final HabitService habitService;
    private final Clock clock;
    private PomodoroSession lastSavedSession;   // shown on the page after a session finishes

    public TimerService(JsonDataRepository repository, HabitService habitService, Clock clock) {
        this.repository = repository;
        this.habitService = habitService;
        this.clock = clock;
    }

    /** The current time, rounded to milliseconds (precise enough, and readable in the JSON file). */
    public Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    /** Returns the timer, after checking whether its time is up. */
    public PomodoroTimer getTimer() {
        checkForCompletion();
        return timer;
    }

    public PomodoroSession getLastSavedSession() {
        return lastSavedSession;
    }

    public void selectMode(TimerMode mode) {
        checkForCompletion();
        timer.selectMode(mode);
        lastSavedSession = null;
    }

    /**
     * Starts the timer in the current mode.
     *
     * @param habitId the habit to work on (only used for focus sessions), or null for none
     * @throws NotFoundException if the habit does not exist
     */
    public void start(Integer habitId) {
        checkForCompletion();
        String habitName = null;
        if (habitId != null && timer.getMode().isFocus()) {
            habitName = habitService.getHabit(habitId).getName();
        }
        timer.start(habitId, habitName, now());
        lastSavedSession = null;
    }

    public void pause() {
        checkForCompletion();
        timer.pause(now());
    }

    public void resume() {
        checkForCompletion();
        timer.resume(now());
    }

    /** Goes back to the full time. An unfinished session is not saved. */
    public void reset() {
        checkForCompletion();
        timer.reset();
        lastSavedSession = null;
    }

    /**
     * Saves the session if the timer has just reached 00:00. The app calls this once
     * every second in the background (and before every timer action), so a finished
     * focus session is saved even when no browser window is open.
     *
     * @return true if the timer finished during this call
     */
    public boolean checkForCompletion() {
        if (!timer.finishIfTimeIsUp(now())) {
            return false;
        }
        if (timer.getMode().isFocus()) {   // breaks are not saved in the history
            PomodoroSession session = new PomodoroSession(
                    repository.nextSessionId(),
                    timer.getHabitId(),
                    timer.getHabitName(),
                    timer.getMode().getMinutes(),
                    timer.getStartedAt(),
                    timer.getFinishedAt());
            repository.addSession(session);
            repository.save();
            lastSavedSession = session;
        }
        return true;
    }

    // ----- Completed focus sessions -----

    /** The newest sessions first. */
    public List<PomodoroSession> getRecentSessions(int limit) {
        List<PomodoroSession> sessions = new ArrayList<>(repository.findAllSessions());
        sessions.sort(Comparator.comparing(PomodoroSession::completedAt).reversed());
        return sessions.subList(0, Math.min(limit, sessions.size()));
    }

    public int countAllSessions() {
        return repository.findAllSessions().size();
    }

    public int countSessionsOn(LocalDate day) {
        int count = 0;
        for (PomodoroSession session : repository.findAllSessions()) {
            if (completedOn(session).equals(day)) {
                count++;
            }
        }
        return count;
    }

    public int focusMinutesOn(LocalDate day) {
        int minutes = 0;
        for (PomodoroSession session : repository.findAllSessions()) {
            if (completedOn(session).equals(day)) {
                minutes += session.durationMinutes();
            }
        }
        return minutes;
    }

    public int countSessionsForHabit(int habitId) {
        int count = 0;
        for (PomodoroSession session : repository.findAllSessions()) {
            if (session.habitId() != null && session.habitId() == habitId) {
                count++;
            }
        }
        return count;
    }

    public int focusMinutesForHabit(int habitId) {
        int minutes = 0;
        for (PomodoroSession session : repository.findAllSessions()) {
            if (session.habitId() != null && session.habitId() == habitId) {
                minutes += session.durationMinutes();
            }
        }
        return minutes;
    }

    // The calendar day (in the app's time zone) on which a session was completed.
    private LocalDate completedOn(PomodoroSession session) {
        return LocalDate.ofInstant(session.completedAt(), clock.getZone());
    }
}
