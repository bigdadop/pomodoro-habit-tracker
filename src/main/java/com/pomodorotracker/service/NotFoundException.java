package com.pomodorotracker.service;

/** Thrown when something with the requested id does not exist. The web layer turns it into "404 Not Found". */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
