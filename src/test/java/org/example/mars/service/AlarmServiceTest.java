package org.example.mars.service;

import org.example.mars.model.SensorReading;
import org.example.mars.model.SensorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the threshold check.
 *
 * The interesting cases are the boundaries themselves: a value exactly on the
 * limit is still safe, because the mission brief says "below -15" and
 * "above 35", not "-15 or below".
 */
class AlarmServiceTest {

    private AlarmService alarmService;

    @BeforeEach
    void setUp() {
        alarmService = new AlarmService();
    }

    // --- Temperature: safe between -15 and 35 ---

    @Test
    void isCritical_temperatureInsideRange_returnsFalse() {
        // Arrange
        SensorReading reading = new SensorReading("SENSOR-01", SensorType.TEMP, 21.0);

        // Act
        boolean result = alarmService.isCritical(reading);

        // Assert
        assertFalse(result);
    }

    @Test
    void isCritical_temperatureExactlyAtLowerLimit_returnsFalse() {
        // Arrange
        SensorReading reading = new SensorReading("SENSOR-01", SensorType.TEMP, -15.0);

        // Act
        boolean result = alarmService.isCritical(reading);

        // Assert
        assertFalse(result);
    }

    @Test
    void isCritical_temperatureJustBelowLowerLimit_returnsTrue() {
        // Arrange
        SensorReading reading = new SensorReading("SENSOR-01", SensorType.TEMP, -15.1);

        // Act
        boolean result = alarmService.isCritical(reading);

        // Assert
        assertTrue(result);
    }

    @Test
    void isCritical_temperatureExactlyAtUpperLimit_returnsFalse() {
        // Arrange
        SensorReading reading = new SensorReading("SENSOR-01", SensorType.TEMP, 35.0);

        // Act
        boolean result = alarmService.isCritical(reading);

        // Assert
        assertFalse(result);
    }

    @Test
    void isCritical_temperatureJustAboveUpperLimit_returnsTrue() {
        // Arrange
        SensorReading reading = new SensorReading("SENSOR-01", SensorType.TEMP, 35.1);

        // Act
        boolean result = alarmService.isCritical(reading);

        // Assert
        assertTrue(result);
    }

    // --- Oxygen: safe between 19 and 23 ---

    @Test
    void isCritical_oxygenTooLow_returnsTrue() {
        // Arrange
        SensorReading reading = new SensorReading("SENSOR-02", SensorType.O2, 18.9);

        // Act
        boolean result = alarmService.isCritical(reading);

        // Assert
        assertTrue(result);
    }

    @Test
    void isCritical_oxygenTooHigh_returnsTrue() {
        // Arrange
        SensorReading reading = new SensorReading("SENSOR-02", SensorType.O2, 23.5);

        // Act
        boolean result = alarmService.isCritical(reading);

        // Assert
        assertTrue(result);
    }

    @Test
    void isCritical_oxygenInsideRange_returnsFalse() {
        // Arrange
        SensorReading reading = new SensorReading("SENSOR-02", SensorType.O2, 21.0);

        // Act
        boolean result = alarmService.isCritical(reading);

        // Assert
        assertFalse(result);
    }

    // --- Pressure: safe between 800 and 1100 ---

    @Test
    void isCritical_pressureTooLow_returnsTrue() {
        // Arrange
        SensorReading reading = new SensorReading("SENSOR-03", SensorType.PRESSURE, 799.9);

        // Act
        boolean result = alarmService.isCritical(reading);

        // Assert
        assertTrue(result);
    }

    @Test
    void isCritical_pressureTooHigh_returnsTrue() {
        // Arrange
        SensorReading reading = new SensorReading("SENSOR-03", SensorType.PRESSURE, 1100.1);

        // Act
        boolean result = alarmService.isCritical(reading);

        // Assert
        assertTrue(result);
    }

    @Test
    void isCritical_pressureInsideRange_returnsFalse() {
        // Arrange
        SensorReading reading = new SensorReading("SENSOR-03", SensorType.PRESSURE, 1013.0);

        // Act
        boolean result = alarmService.isCritical(reading);

        // Assert
        assertFalse(result);
    }

    // --- CO2: only an upper limit of 2000 ---

    @Test
    void isCritical_co2AboveUpperLimit_returnsTrue() {
        // Arrange
        SensorReading reading = new SensorReading("SENSOR-04", SensorType.CO2, 2100.0);

        // Act
        boolean result = alarmService.isCritical(reading);

        // Assert
        assertTrue(result);
    }

    @Test
    void isCritical_co2ExactlyAtUpperLimit_returnsFalse() {
        // Arrange
        SensorReading reading = new SensorReading("SENSOR-04", SensorType.CO2, 2000.0);

        // Act
        boolean result = alarmService.isCritical(reading);

        // Assert
        assertFalse(result);
    }

    @Test
    void isCritical_co2VeryLow_returnsFalse() {
        // Arrange - CO2 has no lower limit, so zero is not an alarm
        SensorReading reading = new SensorReading("SENSOR-04", SensorType.CO2, 0.0);

        // Act
        boolean result = alarmService.isCritical(reading);

        // Assert
        assertFalse(result);
    }

    // --- The alarm message ---

    @Test
    void buildAlarmMessage_criticalTemperature_returnsMessageFromMissionBrief() {
        // Arrange
        SensorReading reading = new SensorReading("SENSOR-01", SensorType.TEMP, -22.5);

        // Act
        String result = alarmService.buildAlarmMessage(reading);

        // Assert
        assertEquals("ALARM: TEMP value out of range! (value = -22.5)", result);
    }

    @Test
    void buildAlarmMessage_criticalCo2_namesTheSensorType() {
        // Arrange
        SensorReading reading = new SensorReading("SENSOR-04", SensorType.CO2, 2350.0);

        // Act
        String result = alarmService.buildAlarmMessage(reading);

        // Assert
        assertEquals("ALARM: CO2 value out of range! (value = 2350.0)", result);
    }
}
