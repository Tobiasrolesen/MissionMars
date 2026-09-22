package org.example.mars.service;

import org.example.mars.model.SensorReading;
import org.example.mars.model.SensorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the log file.
 *
 * The logger writes to a real file, so every test logs to a temporary file
 * created by JUnit instead of the real mars.log.
 */
class MarsLoggerTest {

    // Matches "[2026-09-22 14:32:01] " at the start of a line
    private static final String TIMESTAMP_PATTERN = "^\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}] .*";

    @TempDir
    Path tempDir;

    @Test
    void logReading_normalReading_writesTimestampedLine(@TempDir Path dir) throws IOException {
        // Arrange
        Path logFile = dir.resolve("mars-test.log");
        MarsLogger logger = new MarsLogger(logFile.toString());
        SensorReading reading = new SensorReading("SENSOR-02", SensorType.O2, 22.5);

        // Act
        logger.logReading(reading);
        logger.close();

        // Assert
        List<String> lines = Files.readAllLines(logFile);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).matches(TIMESTAMP_PATTERN));
        assertTrue(lines.get(0).endsWith("SENSOR-02 O2: 22.5 %"));
    }

    @Test
    void logAlarm_criticalReading_marksTheLineWithAlarm() throws IOException {
        // Arrange
        Path logFile = tempDir.resolve("alarm.log");
        MarsLogger logger = new MarsLogger(logFile.toString());
        SensorReading reading = new SensorReading("SENSOR-04", SensorType.CO2, 2100.0);

        // Act
        logger.logAlarm(reading);
        logger.close();

        // Assert
        List<String> lines = Files.readAllLines(logFile);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).endsWith("SENSOR-04 CO2: 2100.0 ppm -> ALARM!"));
    }

    @Test
    void logEvent_anyMessage_writesTimestampedLine() throws IOException {
        // Arrange
        Path logFile = tempDir.resolve("event.log");
        MarsLogger logger = new MarsLogger(logFile.toString());

        // Act
        logger.logEvent("Mars HQ started on port 5000");
        logger.close();

        // Assert
        List<String> lines = Files.readAllLines(logFile);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).matches(TIMESTAMP_PATTERN));
        assertTrue(lines.get(0).endsWith("Mars HQ started on port 5000"));
    }

    @Test
    void newLogger_existingFile_appendsInsteadOfOverwriting() throws IOException {
        // Arrange
        Path logFile = tempDir.resolve("append.log");
        MarsLogger first = new MarsLogger(logFile.toString());
        first.logEvent("first mission");
        first.close();

        // Act
        MarsLogger second = new MarsLogger(logFile.toString());
        second.logEvent("second mission");
        second.close();

        // Assert
        List<String> lines = Files.readAllLines(logFile);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).endsWith("first mission"));
        assertTrue(lines.get(1).endsWith("second mission"));
    }

    @Test
    void logReading_manyThreadsAtOnce_writesEveryLineWithoutMixingThem() throws Exception {
        // Arrange - 5 threads, like the server pool, each logging 50 readings
        Path logFile = tempDir.resolve("threads.log");
        MarsLogger logger = new MarsLogger(logFile.toString());
        int threadCount = 5;
        int linesPerThread = 50;
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startSignal.await();
                    for (int line = 0; line < linesPerThread; line++) {
                        logger.logReading(new SensorReading("SENSOR-01", SensorType.TEMP, 21.0));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            }).start();
        }

        // Act
        startSignal.countDown();
        assertTrue(finished.await(10, TimeUnit.SECONDS));
        logger.close();

        // Assert - nothing lost, and every single line is intact
        List<String> lines = Files.readAllLines(logFile);
        assertEquals(threadCount * linesPerThread, lines.size());
        for (String line : lines) {
            assertTrue(line.matches(TIMESTAMP_PATTERN), "Mixed up line: " + line);
            assertTrue(line.endsWith("SENSOR-01 TEMP: 21.0 C"), "Mixed up line: " + line);
        }
    }
}
