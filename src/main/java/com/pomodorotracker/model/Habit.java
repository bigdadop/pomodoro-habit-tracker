package com.pomodorotracker.model;

import java.time.LocalDate;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A habit the user wants to do every day, for example "Read 10 pages".
 *
 * <p>All fields are private (encapsulation). Other classes have to use the methods
 * below, which make sure the name is valid and that a day is only counted once.
 */
public class Habit {

    public static final int MAX_NAME_LENGTH = 50;
    public static final int MAX_DESCRIPTION_LENGTH = 200;

    private int id;
    private String name;
    private String description;
    private LocalDate createdOn;
    // A TreeSet keeps the dates sorted and ignores duplicates, so completing a
    // habit twice on the same day still counts as one day.
    private SortedSet<LocalDate> completionDates = new TreeSet<>();

    /** Empty constructor used by Gson when it loads habits from the JSON file. */
    private Habit() {
    }

    public Habit(int id, String name, String description, LocalDate createdOn) {
        this.id = id;
        this.name = cleanName(name);
        this.description = cleanDescription(description);
        this.createdOn = createdOn;
    }

    /**
     * Checks a habit name and returns it without extra spaces.
     *
     * @throws IllegalArgumentException if the name is empty or too long
     */
    public static String cleanName(String name) {
        String cleaned = (name == null) ? "" : name.strip();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Please enter a habit name.");
        }
        if (cleaned.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "The habit name can be at most " + MAX_NAME_LENGTH + " characters long.");
        }
        return cleaned;
    }

    /**
     * Checks an (optional) description and returns it without extra spaces.
     *
     * @throws IllegalArgumentException if the description is too long
     */
    public static String cleanDescription(String description) {
        String cleaned = (description == null) ? "" : description.strip();
        if (cleaned.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException(
                    "The description can be at most " + MAX_DESCRIPTION_LENGTH + " characters long.");
        }
        return cleaned;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = cleanName(name);
    }

    public String getDescription() {
        return (description == null) ? "" : description;
    }

    public void setDescription(String description) {
        this.description = cleanDescription(description);
    }

    public LocalDate getCreatedOn() {
        return createdOn;
    }

    /**
     * Records that the habit was done on the given day.
     *
     * @return true if the day was added, false if it was already recorded
     */
    public boolean markCompleted(LocalDate date) {
        return dates().add(date);
    }

    /**
     * Removes a completed day again (used by the "Undo" button).
     *
     * @return true if the day was removed, false if it was not recorded
     */
    public boolean unmarkCompleted(LocalDate date) {
        return dates().remove(date);
    }

    public boolean isCompletedOn(LocalDate date) {
        return dates().contains(date);
    }

    /** All completed days, oldest first. The returned set is read-only. */
    public SortedSet<LocalDate> getCompletionDates() {
        return Collections.unmodifiableSortedSet(dates());
    }

    public int getTotalCompletions() {
        return dates().size();
    }

    // The JSON file could contain "completionDates": null, so create an empty set if needed.
    private SortedSet<LocalDate> dates() {
        if (completionDates == null) {
            completionDates = new TreeSet<>();
        }
        return completionDates;
    }
}
