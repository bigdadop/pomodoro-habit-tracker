package com.pomodorotracker;

import com.pomodorotracker.util.AppConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.BindException;
import java.time.Clock;
import java.time.LocalDate;

/** Starts the Pomodoro Habit Tracker. Run it with: java -jar target/pomodoro-habit-tracker.jar */
public class Main {

    public static void main(String[] args) {
        AppConfig config;
        try {
            config = AppConfig.fromEnvironment(System.getenv());
        } catch (IllegalArgumentException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
            return;
        }

        Clock clock = Clock.systemDefaultZone();
        AppServer server;
        try {
            server = new AppServer(config, clock);
        } catch (BindException e) {
            System.err.println("ERROR: Port " + config.port() + " on " + config.host() + " is already in use"
                    + " (is the app already running in another window?).");
            System.err.println("       Stop the other program, or choose another port, for example: APP_PORT=8081");
            System.exit(1);
            return;
        } catch (IOException | UncheckedIOException e) {
            System.err.println("ERROR: The application could not start: " + e.getMessage());
            System.exit(1);
            return;
        }

        server.start();
        // Runs when the user presses Ctrl+C (or Docker stops the container).
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Stopping Pomodoro Habit Tracker... all data is already saved. Bye!");
            server.stop();
        }));

        String line = "=".repeat(64);
        System.out.println(line);
        System.out.println("  Pomodoro Habit Tracker is running!");
        System.out.println();
        System.out.println("  Open this address in your browser:  " + config.browserUrl(server.getPort()));
        System.out.println();
        System.out.println("  Data file : " + server.getDataFile().toAbsolutePath());
        System.out.println("  Today     : " + LocalDate.now(clock) + " (time zone " + clock.getZone().getId() + ")");
        System.out.println("  Stop      : press Ctrl+C");
        System.out.println(line);
    }
}
