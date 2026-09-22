package org.example.mars.protocol;

import org.example.mars.model.SensorType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the message format shared by HQ and the sensors.
 *
 * If these change, both sides must be updated - that is exactly why the
 * format lives in one class.
 */
class SensorProtocolTest {

    @Test
    void buildAssignment_oxygenSensor_returnsAssignedLine() {
        // Arrange, Act
        String result = SensorProtocol.buildAssignment(SensorType.O2, "SENSOR-02");

        // Assert
        assertEquals("ASSIGNED:O2;SENSOR-02", result);
    }

    @Test
    void buildAssignment_anyType_startsWithAssignedPrefix() {
        // Arrange, Act
        String result = SensorProtocol.buildAssignment(SensorType.CO2, "SENSOR-04");

        // Assert
        assertTrue(result.startsWith(SensorProtocol.ASSIGNED_PREFIX));
    }

    @Test
    void buildRejection_noFreeType_returnsRejectedLineWithReason() {
        // Arrange, Act
        String result = SensorProtocol.buildRejection("all sensor types are already in use");

        // Assert
        assertEquals("REJECTED:all sensor types are already in use", result);
    }

    @Test
    void buildMeasurement_temperature_returnsMeasurementLine() {
        // Arrange, Act
        String result = SensorProtocol.buildMeasurement("SENSOR-01", SensorType.TEMP, "27.4");

        // Assert
        assertEquals("SENSOR-01;TEMP:27.4", result);
    }

    @Test
    void buildMeasurement_negativeValue_keepsTheMinusSign() {
        // Arrange, Act
        String result = SensorProtocol.buildMeasurement("SENSOR-01", SensorType.TEMP, "-22.5");

        // Assert
        assertEquals("SENSOR-01;TEMP:-22.5", result);
    }
}
