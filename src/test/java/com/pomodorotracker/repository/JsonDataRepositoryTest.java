package com.pomodorotracker.repository;

import com.pomodorotracker.model.Habit;
import com.pomodorotracker.model.PomodoroSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonDataRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void createsTheFolderAndAnEmptyFileWhenNothingExistsYet() {
        Path file = tempDir.resolve("data").resolve("app-data.json");

        JsonDataRepository repository = new JsonDataRepository(file);

        assertTrue(Files.exists(file));
        assertTrue(repository.findAllHabits().isEmpty());
        assertTrue(repository.findAllSessions().isEmpty());
    }

    @Test
    void savesAndLoadsHabitsCompletionDatesAndSessions() {
        Path file = tempDir.resolve("app-data.json");
        JsonDataRepository repository = new JsonDataRepository(file);
        Habit habit = new Habit(repository.nextHabitId(), "Read", "Before bed", LocalDate.of(2026, 10, 1));
        habit.markCompleted(LocalDate.of(2026, 10, 24));
        habit.markCompleted(LocalDate.of(2026, 10, 23));
        repository.addHabit(habit);
        repository.addSession(new PomodoroSession(repository.nextSessionId(), habit.getId(), "Read", 25,
                Instant.parse("2026-10-24T09:00:00Z"), Instant.parse("2026-10-24T09:25:00Z")));
        repository.save();

        JsonDataRepository loaded = new JsonDataRepository(file);   // like restarting the app

        Habit loadedHabit = loaded.findHabitById(habit.getId()).orElseThrow();
        assertEquals("Read", loadedHabit.getName());
        assertEquals("Before bed", loadedHabit.getDescription());
        assertEquals(LocalDate.of(2026, 10, 1), loadedHabit.getCreatedOn());
        assertEquals(List.of(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 24)),
                List.copyOf(loadedHabit.getCompletionDates()));   // sorted, oldest first
        assertEquals(repository.findAllSessions(), loaded.findAllSessions());   // records compare every value
    }

    @Test
    void idsAreNeverReusedAfterADeleteAndARestart() {
        Path file = tempDir.resolve("app-data.json");
        JsonDataRepository repository = new JsonDataRepository(file);
        repository.addHabit(new Habit(repository.nextHabitId(), "One", "", LocalDate.of(2026, 10, 24)));
        repository.addHabit(new Habit(repository.nextHabitId(), "Two", "", LocalDate.of(2026, 10, 24)));
        repository.deleteHabit(2);
        repository.save();

        assertEquals(3, new JsonDataRepository(file).nextHabitId());
    }

    @Test
    void brokenFileIsBackedUpAndTheAppStartsWithEmptyData() throws IOException {
        Path file = tempDir.resolve("app-data.json");
        String brokenJson = "{ \"habits\": [ this is not json ";
        Files.writeString(file, brokenJson);

        JsonDataRepository repository = new JsonDataRepository(file);

        assertTrue(repository.findAllHabits().isEmpty());
        try (Stream<Path> files = Files.list(tempDir)) {
            List<Path> backups = files
                    .filter(path -> path.getFileName().toString().startsWith("app-data-broken-"))
                    .toList();
            assertEquals(1, backups.size());
            assertEquals(brokenJson, Files.readString(backups.get(0)));   // nothing was lost
        }
        assertTrue(Files.readString(file).contains("\"habits\": []"));   // a fresh, valid file
    }

    @Test
    void missingFieldsNullEntriesAndEmptyFilesAreHandled() throws IOException {
        Path file = tempDir.resolve("app-data.json");
        Files.writeString(file, """
                {
                  "habits": [
                    { "id": 5, "name": "Walk" },
                    null,
                    { "id": 6, "name": "   " }
                  ],
                  "sessions": null
                }
                """);

        JsonDataRepository repository = new JsonDataRepository(file);

        assertEquals(1, repository.findAllHabits().size());       // the null and the nameless habit are skipped
        Habit walk = repository.findHabitById(5).orElseThrow();
        assertEquals("", walk.getDescription());
        assertEquals(0, walk.getTotalCompletions());
        assertTrue(repository.findAllSessions().isEmpty());
        assertEquals(7, repository.nextHabitId());                 // never clashes with ids in the file

        Files.writeString(file, "   ");
        assertTrue(new JsonDataRepository(file).findAllHabits().isEmpty());
    }
}
