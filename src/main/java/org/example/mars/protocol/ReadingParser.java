package org.example.mars.protocol;

import org.example.mars.model.SensorReading;
import org.example.mars.model.SensorType;

/**
 * Turns a measurement line into a SensorReading.
 *
 * The expected format is "SENSOR-01;TEMP:27.4" - the same format
 * SensorProtocol.buildMeasurement produces.
 *
 * The parser never throws: an invalid line simply returns null, so one bad
 * message can never take down the sensor's handler thread.
 */
public final class ReadingParser {

    private ReadingParser() {
    }

    /**
     * Parses one line. Returns null if the line is empty, has the wrong shape,
     * names an unknown sensor type, or holds a value that is not a number.
     */
    public static SensorReading parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        // "SENSOR-01;TEMP:27.4" -> "SENSOR-01" + "TEMP:27.4"
        String[] idAndRest = line.trim().split(SensorProtocol.ID_SEPARATOR, 2);
        if (idAndRest.length != 2 || idAndRest[0].isBlank()) {
            return null;
        }

        // "TEMP:27.4" -> "TEMP" + "27.4"
        String[] typeAndValue = idAndRest[1].split(SensorProtocol.VALUE_SEPARATOR, 2);
        if (typeAndValue.length != 2) {
            return null;
        }

        SensorType type = parseType(typeAndValue[0].trim());
        if (type == null) {
            return null;
        }

        try {
            double value = Double.parseDouble(typeAndValue[1].trim());
            return new SensorReading(idAndRest[0].trim(), type, value);
        } catch (NumberFormatException e) {
            // The value was not a number, e.g. "TEMP:warm"
            return null;
        }
    }

    /** Converts the type text into a SensorType, or null if it is unknown. */
    private static SensorType parseType(String typeText) {
        try {
            return SensorType.valueOf(typeText.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
