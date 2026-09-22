package org.example.mars.service;

import org.example.mars.model.SensorReading;
import org.example.mars.model.SensorType;

/**
 * Decides whether a reading is dangerous for the colonists.
 *
 * The service holds no thresholds of its own: it asks the SensorType of the
 * reading. Changing a limit is therefore done in one place only.
 */
public class AlarmService {

    /**
     * True if the value is outside the safe range for its sensor type.
     * For CO2 the lower bound is negative infinity, so only the upper limit
     * can ever be exceeded.
     */
    public boolean isCritical(SensorReading reading) {
        SensorType type = reading.getType();
        double value = reading.getValue();
        return value < type.getMinValue() || value > type.getMaxValue();
    }

    /**
     * Builds the alarm message in the format required by the mission brief:
     *     ALARM: TEMP value out of range! (value = -16.4)
     */
    public String buildAlarmMessage(SensorReading reading) {
        return "ALARM: " + reading.getType().name()
                + " value out of range! (value = " + reading.getValue() + ")";
    }
}
