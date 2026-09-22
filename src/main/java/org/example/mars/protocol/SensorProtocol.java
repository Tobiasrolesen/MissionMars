package org.example.mars.protocol;

import org.example.mars.model.SensorType;

/**
 * The text protocol between the sensors and HQ, kept in one place so the two
 * sides cannot drift apart.
 *
 * When a sensor connects, HQ assigns it a type:
 *     HQ     -> ASSIGNED:O2;SENSOR-02
 *     HQ     -> REJECTED:all sensor types are already in use
 *
 * After that the sensor reports measurements:
 *     Sensor -> SENSOR-02;O2:21.4
 */
public final class SensorProtocol {

    public static final String ASSIGNED_PREFIX = "ASSIGNED:";
    public static final String REJECTED_PREFIX = "REJECTED:";
    public static final String ID_SEPARATOR = ";";
    public static final String VALUE_SEPARATOR = ":";

    // Only constants and static helpers, so the class is never instantiated
    private SensorProtocol() {
    }

    /** Builds HQ's reply to a new sensor, e.g. "ASSIGNED:O2;SENSOR-02". */
    public static String buildAssignment(SensorType type, String sensorId) {
        return ASSIGNED_PREFIX + type.name() + ID_SEPARATOR + sensorId;
    }

    /** Builds HQ's refusal of a new sensor, e.g. "REJECTED:no free type". */
    public static String buildRejection(String reason) {
        return REJECTED_PREFIX + reason;
    }

    /** Builds one measurement line, e.g. "SENSOR-02;O2:21.4". */
    public static String buildMeasurement(String sensorId, SensorType type, String valueText) {
        return sensorId + ID_SEPARATOR + type.name() + VALUE_SEPARATOR + valueText;
    }
}
