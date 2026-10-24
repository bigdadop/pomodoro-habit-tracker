package com.pomodorotracker.repository;

import com.pomodorotracker.model.AppData;
import com.pomodorotracker.model.Habit;
import com.pomodorotracker.model.PomodoroSession;
import com.pomodorotracker.util.JsonUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Saves and loads all application data in one JSON file (normally data/app-data.json).
 *
 * <p>This is the only class that reads or writes the file. All data is loaded into
 * memory when the app starts and the whole file is written again after every change.
 * For a personal habit tracker (a few hundred habits and sessions) this is simple
 * and fast enough, and the file stays easy to read in any text editor.
 */
public class JsonDataRepository {

    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final Path dataFile;
    private final AppData data;

    /** Loads the data file, or creates the folder and an empty file if they do not exist yet. */
    public JsonDataRepository(Path dataFile) {
        this.dataFile = dataFile;
        this.data = load();
        writeFile();
    }

    public Path getDataFile() {
        return dataFile;
    }

    // ----- Habits -----

    /** All habits in the order they were created. The list itself is read-only. */
    public List<Habit> findAllHabits() {
        return Collections.unmodifiableList(data.getHabits());
    }

    public Optional<Habit> findHabitById(int id) {
        for (Habit habit : data.getHabits()) {
            if (habit.getId() == id) {
                return Optional.of(habit);
            }
        }
        return Optional.empty();
    }

    /** Hands out the next unused habit id (ids are never reused). */
    public int nextHabitId() {
        return data.takeNextHabitId();
    }

    public void addHabit(Habit habit) {
        data.getHabits().add(habit);
    }

    /** @return true if a habit with this id existed and was removed */
    public boolean deleteHabit(int id) {
        return data.getHabits().removeIf(habit -> habit.getId() == id);
    }

    // ----- Completed focus sessions -----

    /** All completed focus sessions, oldest first. The list itself is read-only. */
    public List<PomodoroSession> findAllSessions() {
        return Collections.unmodifiableList(data.getSessions());
    }

    public int nextSessionId() {
        return data.takeNextSessionId();
    }

    public void addSession(PomodoroSession session) {
        data.getSessions().add(session);
    }

    // ----- Reading and writing the file -----

    /** Writes all data to the JSON file. Services call this after every change. */
    public void save() {
        writeFile();
    }

    private void writeFile() {
        try {
            Path folder = dataFile.toAbsolutePath().getParent();
            Files.createDirectories(folder);
            // Write to a temporary file first and then rename it. If the computer crashes
            // halfway through writing, the previous data file is still complete.
            Path tempFile = folder.resolve(dataFile.getFileName() + ".tmp");
            Files.writeString(tempFile, JsonUtil.toJson(data), StandardCharsets.UTF_8);
            moveIntoPlace(tempFile);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save data to " + dataFile.toAbsolutePath(), e);
        }
    }

    private void moveIntoPlace(Path tempFile) throws IOException {
        try {
            Files.move(tempFile, dataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // Some file systems cannot rename "atomically"; a normal replace is fine there.
            Files.move(tempFile, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private AppData load() {
        if (!Files.exists(dataFile)) {
            return new AppData();   // first start: nothing saved yet
        }
        String json;
        try {
            json = new String(Files.readAllBytes(dataFile), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + dataFile.toAbsolutePath(), e);
        }
        if (json.isBlank()) {
            return new AppData();
        }
        try {
            AppData loaded = JsonUtil.fromJson(json, AppData.class);
            if (loaded == null) {   // the file only contained "null"
                return new AppData();
            }
            loaded.removeInvalidEntries();
            return loaded;
        } catch (RuntimeException e) {
            // The file is not valid JSON (for example it was edited by hand and a comma is
            // missing). Keep a copy so nothing is lost, and start with empty data.
            Path backup = backUpBrokenFile();
            System.err.println("WARNING: " + dataFile + " could not be read: " + e.getMessage());
            System.err.println("WARNING: It was renamed to " + backup.getFileName()
                    + " and the app started with empty data.");
            return new AppData();
        }
    }

    private Path backUpBrokenFile() {
        // app-data.json  ->  app-data-broken-20261024-153000-123.json
        String baseName = dataFile.getFileName().toString().replaceFirst("\\.json$", "");
        String time = LocalDateTime.now().format(BACKUP_TIME);
        Path backup = dataFile.resolveSibling(baseName + "-broken-" + time + ".json");
        try {
            Files.move(dataFile, backup);
            return backup;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not back up the broken data file " + dataFile.toAbsolutePath(), e);
        }
    }
}
