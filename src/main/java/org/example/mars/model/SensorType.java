package org.example.mars.model;

/**
 * The sensor types HQ knows about, and their critical thresholds.
 *
 * The thresholds live ONLY here. The rest of the system (alarm checking,
 * logging, clients) asks this enum instead of keeping its own numbers.
 */
public enum SensorType {

    // Temperature: critical below -15 C and above 35 C
    TEMP("Temperature", "C", -15.0, 35.0),

    // Oxygen level: critical below 19 % and above 23 %
    O2("Oxygen", "%", 19.0, 23.0),

    // Air pressure: critical below 800 hPa and above 1100 hPa
    PRESSURE("Pressure", "hPa", 800.0, 1100.0),

    // CO2 has no lower limit, only an upper limit of 2000 ppm.
    // NEGATIVE_INFINITY is used as the lower bound so no reading can ever
    // be considered "too low".
    CO2("CO2 level", "ppm", Double.NEGATIVE_INFINITY, 2000.0);

    private final String description;
    private final String unit;
    private final double minValue;
    private final double maxValue;

    SensorType(String description, String unit, double minValue, double maxValue) {
        this.description = description;
        this.unit = unit;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    public String getDescription() {
        return description;
    }

    public String getUnit() {
        return unit;
    }

    public double getMinValue() {
        return minValue;
    }

    public double getMaxValue() {
        return maxValue;
    }

    /**
     * Returns the thresholds as readable text, e.g. "-15.0 - 35.0 C".
     * Used for console output only, not for the alarm check itself.
     */
    public String formatRange() {
        String lower = minValue == Double.NEGATIVE_INFINITY
                ? "no lower limit"
                : String.format("%.1f", minValue);
        return lower + " - " + String.format("%.1f", maxValue) + " " + unit;
    }
}
