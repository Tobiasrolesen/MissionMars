package org.example.mars.server;

import org.example.mars.model.SensorType;

import java.util.EnumSet;
import java.util.Set;

/**
 * Keeps track of which sensor types are currently covered by a connected
 * sensor, and hands out the next free type to a new sensor.
 *
 * This is the only state shared between the handler threads, so every method
 * that touches the set is synchronized. Without that, two sensors connecting
 * at the same moment could both be handed the same type.
 */
public class SensorRegistry {

    private final Set<SensorType> typesInUse = EnumSet.noneOf(SensorType.class);

    /**
     * Claims the first sensor type that is not in use and returns it.
     * Returns null if all types are already covered, so HQ can turn the new
     * sensor away instead of monitoring the same value twice.
     */
    public synchronized SensorType claimFreeType() {
        for (SensorType type : SensorType.values()) {
            if (!typesInUse.contains(type)) {
                typesInUse.add(type);
                return type;
            }
        }
        return null;
    }

    /**
     * Gives a type back, so the next sensor that connects can take it over.
     * Called when a sensor disconnects or is dropped.
     */
    public synchronized void release(SensorType type) {
        if (type != null) {
            typesInUse.remove(type);
        }
    }

    /** How many sensor types are covered right now. Used for console output. */
    public synchronized int countInUse() {
        return typesInUse.size();
    }

    /**
     * The fixed sensor id for a type: TEMP is SENSOR-01, O2 is SENSOR-02 and
     * so on. A sensor that reconnects therefore gets its own id back.
     */
    public static String idFor(SensorType type) {
        return String.format("SENSOR-%02d", type.ordinal() + 1);
    }
}
