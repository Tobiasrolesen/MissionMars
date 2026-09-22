package org.example.mars.model;

import java.time.LocalDateTime;

/**
 * A single measurement from a single sensor.
 *
 * This is a plain data holder (entity). It knows nothing about thresholds,
 * networking or logging - it only keeps track of who measured what, and when.
 */
public class SensorReading {

    private final String sensorId;          // e.g. "SENSOR-03"
    private final SensorType type;          // e.g. TEMP
    private final double value;             // the measured value
    private final LocalDateTime timestamp;  // when HQ received the reading

    /** Created by HQ when a measurement is received, and timestamped there. */
    public SensorReading(String sensorId, SensorType type, double value) {
        this.sensorId = sensorId;
        this.type = type;
        this.value = value;
        this.timestamp = LocalDateTime.now();
    }

    public String getSensorId() {
        return sensorId;
    }

    public SensorType getType() {
        return type;
    }

    public double getValue() {
        return value;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return sensorId + " " + type.name() + ": " + value + " " + type.getUnit();
    }
}
