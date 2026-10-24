package com.pomodorotracker;

import com.pomodorotracker.controller.DashboardController;
import com.pomodorotracker.controller.HabitController;
import com.pomodorotracker.controller.HealthController;
import com.pomodorotracker.controller.StaticFileController;
import com.pomodorotracker.controller.TimerController;
import com.pomodorotracker.repository.JsonDataRepository;
import com.pomodorotracker.service.HabitService;
import com.pomodorotracker.service.StreakService;
import com.pomodorotracker.service.TimerService;
import com.pomodorotracker.util.AppConfig;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Creates all parts of the application, connects them, and runs the web server
 * (the built-in {@code com.sun.net.httpserver.HttpServer} that comes with Java).
 */
public class AppServer {

    private final HttpServer httpServer;
    private final ScheduledExecutorService worker;
    private final JsonDataRepository repository;

    public AppServer(AppConfig config, Clock clock) throws IOException {
        // Reserve the port first: if the app is already running, we stop here
        // before touching the data file.
        httpServer = HttpServer.create(new InetSocketAddress(config.host(), config.port()), 0);

        // Repository -> services -> controllers (each layer only talks to the one below it).
        repository = new JsonDataRepository(config.dataFile());
        HabitService habitService = new HabitService(repository, new StreakService(), clock);
        TimerService timerService = new TimerService(repository, habitService, clock);

        httpServer.createContext("/api/habits", new HabitController(habitService, timerService));
        httpServer.createContext("/api/timer", new TimerController(timerService));
        httpServer.createContext("/api/dashboard", new DashboardController(habitService, timerService));
        httpServer.createContext("/api/health", new HealthController());
        httpServer.createContext("/", new StaticFileController());

        // ONE worker thread handles every web request AND the once-a-second timer check.
        // Because only one thread ever touches the data, we never need to worry about two
        // requests changing the same habit at the same time.
        worker = Executors.newSingleThreadScheduledExecutor();
        httpServer.setExecutor(worker);
        worker.scheduleAtFixedRate(() -> {
            try {
                timerService.checkForCompletion();
            } catch (RuntimeException e) {
                // Never let one error stop the timer check for the rest of the run.
                System.err.println("ERROR: timer check failed: " + e.getMessage());
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    public void start() {
        httpServer.start();
    }

    public void stop() {
        httpServer.stop(0);
        worker.shutdownNow();
    }

    /** The real port (useful when port 0 was requested and the system chose a free one). */
    public int getPort() {
        return httpServer.getAddress().getPort();
    }

    public Path getDataFile() {
        return repository.getDataFile();
    }
}
