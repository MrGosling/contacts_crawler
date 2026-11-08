package com.example.contactscrawler.logging;

import java.time.Instant;

/**
 * Событие лога. {@link #POISON} — маркер остановки потока-писателя (сравнивается по ссылке).
 */
public record LogEvent(Instant timestamp, String level, String thread, String message) {

    public static final LogEvent POISON = new LogEvent(Instant.EPOCH, "POISON", "", "");

    public static LogEvent of(String level, String message) {
        return new LogEvent(Instant.now(), level, Thread.currentThread().getName(), message);
    }

    public String format() {
        return timestamp + " " + level + " [" + thread + "] " + message;
    }
}
