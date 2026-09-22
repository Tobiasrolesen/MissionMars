package org.example.mars.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the measurement entity.
 *
 * The class holds no logic, so the tests only cover that nothing is lost on
 * the way in, and that the text form used in the log looks right.
 */
class SensorReadingTest {

    @Test
    void constructor_newReading_keepsAllValues() {
        // Arrange, Act
        SensorReading reading = new SensorReading("SENSOR-03", SensorType.PRESSURE, 1013.0);

        // Assert
        assertEquals("SENSOR-03", reading.getSensorId());
        assertEquals(SensorType.PRESSURE, reading.getType());
        assertEquals(1013.0, reading.getValue());
    }

    @Test
    void constructor_newReading_isTimestampedNow() {
        // Arrange
        LocalDateTime before = LocalDateTime.now();

        // Act
        SensorReading reading = new SensorReading("SENSOR-01", SensorType.TEMP, 21.0);

        // Assert
        assertNotNull(reading.getTimestamp());
        Duration age = Duration.between(before, reading.getTimestamp());
        assertTrue(age.toSeconds() < 5, "The reading should be timestamped when it is created");
    }

    @Test
    void toString_oxygenReading_showsIdTypeValueAndUnit() {
        // Arrange
        SensorReading reading = new SensorReading("SENSOR-02", SensorType.O2, 22.5);

        // Act
        String result = reading.toString();

        // Assert
        assertEquals("SENSOR-02 O2: 22.5 %", result);
    }

    @Test
    void toString_temperatureReading_showsTheUnitCelsius() {
        // Arrange
        SensorReading reading = new SensorReading("SENSOR-01", SensorType.TEMP, -12.6);

        // Act
        String result = reading.toString();

        // Assert
        assertEquals("SENSOR-01 TEMP: -12.6 C", result);
    }
}
