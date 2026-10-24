package com.pomodorotracker.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One shared Gson object that turns Java objects into JSON text and back.
 * It is used both for the data file and for the web API.
 *
 * <p>Gson does not know Java's date classes, so we register two small adapters that
 * write them as plain ISO text, for example {@code "2026-10-24"} for a date and
 * {@code "2026-10-24T09:25:00Z"} for an exact moment in time.
 */
public final class JsonUtil {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter().nullSafe())
            .registerTypeAdapter(Instant.class, new InstantAdapter().nullSafe())
            .serializeNulls()          // write "habitId": null instead of leaving the field out
            .setPrettyPrinting()       // readable JSON with line breaks and indentation
            .disableHtmlEscaping()     // keep characters such as ' and < readable in the file
            .create();

    private JsonUtil() {
        // only static methods - no objects of this class are needed
    }

    public static String toJson(Object value) {
        return GSON.toJson(value);
    }

    /** Converts JSON text into an object. Throws a JsonParseException if the text is not valid. */
    public static <T> T fromJson(String json, Class<T> type) {
        return GSON.fromJson(json, type);
    }

    /** Reads and writes a LocalDate as "yyyy-MM-dd". */
    private static class LocalDateAdapter extends TypeAdapter<LocalDate> {
        @Override
        public void write(JsonWriter out, LocalDate date) throws IOException {
            out.value(date.toString());
        }

        @Override
        public LocalDate read(JsonReader in) throws IOException {
            return LocalDate.parse(in.nextString());
        }
    }

    /** Reads and writes an Instant (an exact moment, in UTC) as ISO-8601 text. */
    private static class InstantAdapter extends TypeAdapter<Instant> {
        @Override
        public void write(JsonWriter out, Instant instant) throws IOException {
            out.value(instant.toString());
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            return Instant.parse(in.nextString());
        }
    }
}
