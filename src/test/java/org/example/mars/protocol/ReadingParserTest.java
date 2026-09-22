package org.example.mars.protocol;

import org.example.mars.model.SensorReading;
import org.example.mars.model.SensorType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for the parsing of measurement lines.
 *
 * The parser meets everything a sensor might send, so the important cases are
 * the invalid ones: it must return null instead of throwing, because an
 * exception here would kill the handler thread of a connected sensor.
 */
class ReadingParserTest {

    @Test
    void parse_validLine_returnsReadingWithAllFields() {
        // Arrange
        String line = "SENSOR-01;TEMP:27.4";

        // Act
        SensorReading result = ReadingParser.parse(line);

        // Assert
        assertNotNull(result);
        assertEquals("SENSOR-01", result.getSensorId());
        assertEquals(SensorType.TEMP, result.getType());
        assertEquals(27.4, result.getValue());
    }

    @Test
    void parse_negativeValue_returnsReadingWithNegativeValue() {
        // Arrange
        String line = "SENSOR-01;TEMP:-22.5";

        // Act
        SensorReading result = ReadingParser.parse(line);

        // Assert
        assertNotNull(result);
        assertEquals(-22.5, result.getValue());
    }

    @Test
    void parse_wholeNumberValue_returnsReading() {
        // Arrange
        String line = "SENSOR-04;CO2:2350";

        // Act
        SensorReading result = ReadingParser.parse(line);

        // Assert
        assertNotNull(result);
        assertEquals(2350.0, result.getValue());
    }

    @Test
    void parse_lowercaseType_returnsReading() {
        // Arrange
        String line = "SENSOR-02;o2:21.0";

        // Act
        SensorReading result = ReadingParser.parse(line);

        // Assert
        assertNotNull(result);
        assertEquals(SensorType.O2, result.getType());
    }

    @Test
    void parse_lineWithSurroundingSpaces_returnsReading() {
        // Arrange
        String line = "  SENSOR-03 ; PRESSURE : 1013  ";

        // Act
        SensorReading result = ReadingParser.parse(line);

        // Assert
        assertNotNull(result);
        assertEquals("SENSOR-03", result.getSensorId());
        assertEquals(SensorType.PRESSURE, result.getType());
        assertEquals(1013.0, result.getValue());
    }

    @Test
    void parse_valueIsNotANumber_returnsNull() {
        // Arrange
        String line = "SENSOR-01;TEMP:warm";

        // Act
        SensorReading result = ReadingParser.parse(line);

        // Assert
        assertNull(result);
    }

    @Test
    void parse_danishDecimalComma_returnsNull() {
        // Arrange - a Danish locale would produce this, and it must be rejected
        String line = "SENSOR-01;TEMP:27,4";

        // Act
        SensorReading result = ReadingParser.parse(line);

        // Assert
        assertNull(result);
    }

    @Test
    void parse_unknownSensorType_returnsNull() {
        // Arrange
        String line = "SENSOR-01;XRAY:5";

        // Act
        SensorReading result = ReadingParser.parse(line);

        // Assert
        assertNull(result);
    }

    @Test
    void parse_missingSensorId_returnsNull() {
        // Arrange - HQ assigns the id, so a line without one is invalid
        String line = "TEMP:27.4";

        // Act
        SensorReading result = ReadingParser.parse(line);

        // Assert
        assertNull(result);
    }

    @Test
    void parse_emptySensorId_returnsNull() {
        // Arrange
        String line = ";TEMP:27.4";

        // Act
        SensorReading result = ReadingParser.parse(line);

        // Assert
        assertNull(result);
    }

    @Test
    void parse_missingValue_returnsNull() {
        // Arrange
        String line = "SENSOR-01;TEMP";

        // Act
        SensorReading result = ReadingParser.parse(line);

        // Assert
        assertNull(result);
    }

    @Test
    void parse_valueIsEmpty_returnsNull() {
        // Arrange
        String line = "SENSOR-01;TEMP:";

        // Act
        SensorReading result = ReadingParser.parse(line);

        // Assert
        assertNull(result);
    }

    @Test
    void parse_completeGarbage_returnsNull() {
        // Arrange
        String line = "total garbage";

        // Act
        SensorReading result = ReadingParser.parse(line);

        // Assert
        assertNull(result);
    }

    @Test
    void parse_emptyLine_returnsNull() {
        // Arrange
        String line = "   ";

        // Act
        SensorReading result = ReadingParser.parse(line);

        // Assert
        assertNull(result);
    }

    @Test
    void parse_null_returnsNull() {
        // Arrange, Act
        SensorReading result = ReadingParser.parse(null);

        // Assert
        assertNull(result);
    }

    @Test
    void parse_buildMeasurementOutput_returnsSameValues() {
        // Arrange - what the client sends must be what the server can read
        String line = SensorProtocol.buildMeasurement("SENSOR-02", SensorType.O2, "21.4");

        // Act
        SensorReading result = ReadingParser.parse(line);

        // Assert
        assertNotNull(result);
        assertEquals("SENSOR-02", result.getSensorId());
        assertEquals(SensorType.O2, result.getType());
        assertEquals(21.4, result.getValue());
    }
}
