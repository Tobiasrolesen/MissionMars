package org.example.mars.service;

import org.example.mars.model.SensorReading;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Writes every reading and every event to mars.log.
 *
 * One single instance is shared by all sensor handlers, which means several
 * pool threads write to the same file. Therefore the actual write method is
 * synchronized: without it, two threads could interleave halfway through a
 * line and the log would be unreadable.
 *
 * Every line is flushed immediately. The server runs until it is stopped, so
 * buffered lines would otherwise risk being lost.
 */
public class MarsLogger {

    private static final String LOG_FILE = "mars.log";

    // Local time, e.g. [2026-09-22 14:32:01]
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BufferedWriter writer;
    private final String fileName;

    /**
     * Opens mars.log in append mode, so previous missions are not overwritten.
     * Throws IOException if the file cannot be opened - the server then knows
     * that logging is impossible before it starts accepting sensors.
     */
    public MarsLogger() throws IOException {
        this(LOG_FILE);
    }

    /**
     * Logs to another file than mars.log. Used by the unit tests, so they can
     * write to a temporary file instead of the real mission log.
     */
    public MarsLogger(String fileName) throws IOException {
        this.fileName = fileName;
        this.writer = new BufferedWriter(new FileWriter(fileName, true));
    }

    /** Logs a reading that is within the safe range. */
    public void logReading(SensorReading reading) {
        write(reading.getTimestamp(), reading.toString());
    }

    /** Logs a reading that is outside the safe range. */
    public void logAlarm(SensorReading reading) {
        write(reading.getTimestamp(), reading + " -> ALARM!");
    }

    /**
     * Logs anything that is not a reading, for example a sensor connecting or
     * disconnecting, or invalid data.
     */
    public void logEvent(String message) {
        write(LocalDateTime.now(), message);
    }

    /**
     * Writes one timestamped line to the log file.
     *
     * Synchronized because all sensor handler threads share this instance.
     * A failing write is reported but never thrown on, so a full disk cannot
     * take down the monitoring of the colony.
     */
    private synchronized void write(LocalDateTime timestamp, String message) {
        String line = "[" + timestamp.format(TIMESTAMP_FORMAT) + "] " + message;
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.out.println("[ERROR] Could not write to " + fileName + ": " + e.getMessage());
        }
    }

    /** Closes the log file. Called when the server shuts down. */
    public synchronized void close() {
        try {
            writer.close();
        } catch (IOException e) {
            System.out.println("[ERROR] Could not close " + fileName + ": " + e.getMessage());
        }
    }
}
