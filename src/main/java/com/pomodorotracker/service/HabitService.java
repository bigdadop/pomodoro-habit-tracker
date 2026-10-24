package com.pomodorotracker.service;

import com.pomodorotracker.model.Habit;
import com.pomodorotracker.repository.JsonDataRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Business logic for habits: create, edit, delete, mark as done and streak numbers.
 *
 * <p>The service gets a {@link Clock} instead of calling {@code LocalDate.now()} directly.
 * In the real app it is the computer's clock; tests use a fake clock so they can
 * pretend that days have passed.
 */
public class HabitService {

    private final JsonDataRepository repository;
    private final StreakService streakService;
    private final Clock clock;

    public HabitService(JsonDataRepository repository, StreakService streakService, Clock clock) {
        this.repository = repository;
        this.streakService = streakService;
        this.clock = clock;
    }

    /** Today's date in the app's time zone. */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public ZoneId getTimeZone() {
        return clock.getZone();
    }

    public List<Habit> getAllHabits() {
        return repository.findAllHabits();
    }

    /** @throws NotFoundException if there is no habit with this id */
    public Habit getHabit(int id) {
        return repository.findHabitById(id)
                .orElseThrow(() -> new NotFoundException("There is no habit with id " + id + "."));
    }

    /** @throws IllegalArgumentException if the name is empty, too long or already used */
    public Habit createHabit(String name, String description) {
        String cleanName = Habit.cleanName(name);
        String cleanDescription = Habit.cleanDescription(description);
        checkNameIsFree(cleanName, 0);

        Habit habit = new Habit(repository.nextHabitId(), cleanName, cleanDescription, today());
        repository.addHabit(habit);
        repository.save();
        return habit;
    }

    /** Changes name and description. If anything is invalid, nothing is changed. */
    public Habit updateHabit(int id, String name, String description) {
        Habit habit = getHabit(id);
        // Check both values first, so a bad description cannot leave a half-updated habit.
        String cleanName = Habit.cleanName(name);
        String cleanDescription = Habit.cleanDescription(description);
        checkNameIsFree(cleanName, id);

        habit.setName(cleanName);
        habit.setDescription(cleanDescription);
        repository.save();
        return habit;
    }

    public void deleteHabit(int id) {
        if (!repository.deleteHabit(id)) {
            throw new NotFoundException("There is no habit with id " + id + ".");
        }
        repository.save();
    }

    /**
     * Marks the habit as done for today.
     *
     * @return true if it was marked now, false if it was already done today (nothing changes)
     */
    public boolean markCompletedToday(int id) {
        Habit habit = getHabit(id);
        boolean added = habit.markCompleted(today());
        if (added) {
            repository.save();
        }
        return added;
    }

    /**
     * Undoes today's completion (for example after a wrong click).
     *
     * @return true if today's completion was removed, false if it was not done today
     */
    public boolean undoCompletionToday(int id) {
        Habit habit = getHabit(id);
        boolean removed = habit.unmarkCompleted(today());
        if (removed) {
            repository.save();
        }
        return removed;
    }

    public int getCurrentStreak(Habit habit) {
        return streakService.calculateCurrentStreak(habit.getCompletionDates(), today());
    }

    public int getLongestStreak(Habit habit) {
        return streakService.calculateLongestStreak(habit.getCompletionDates());
    }

    // ----- Numbers for the dashboard -----

    public int countCompletedToday() {
        int count = 0;
        for (Habit habit : getAllHabits()) {
            if (habit.isCompletedOn(today())) {
                count++;
            }
        }
        return count;
    }

    /** Number of habits whose current streak is at least one day. */
    public int countActiveStreaks() {
        int count = 0;
        for (Habit habit : getAllHabits()) {
            if (getCurrentStreak(habit) > 0) {
                count++;
            }
        }
        return count;
    }

    public int getBestCurrentStreak() {
        int best = 0;
        for (Habit habit : getAllHabits()) {
            best = Math.max(best, getCurrentStreak(habit));
        }
        return best;
    }

    // Two habits with the same name (ignoring upper/lower case) would be confusing.
    private void checkNameIsFree(String name, int idOfHabitBeingEdited) {
        for (Habit other : getAllHabits()) {
            if (other.getId() != idOfHabitBeingEdited && other.getName().equalsIgnoreCase(name)) {
                throw new IllegalArgumentException("You already have a habit called \"" + other.getName() + "\".");
            }
        }
    }
}
