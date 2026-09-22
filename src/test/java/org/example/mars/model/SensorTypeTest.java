package org.example.mars.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the thresholds match the mission brief.
 *
 * These numbers exist in one place only, so if someone changes them by
 * accident, these tests are what catches it.
 */
class SensorTypeTest {

    @Test
    void temperature_thresholds_matchMissionBrief() {
        // Arrange, Act, Assert
        assertEquals(-15.0, SensorType.TEMP.getMinValue());
        assertEquals(35.0, SensorType.TEMP.getMaxValue());
    }

    @Test
    void oxygen_thresholds_matchMissionBrief() {
        // Arrange, Act, Assert
        assertEquals(19.0, SensorType.O2.getMinValue());
        assertEquals(23.0, SensorType.O2.getMaxValue());
    }

    @Test
    void pressure_thresholds_matchMissionBrief() {
        // Arrange, Act, Assert
        assertEquals(800.0, SensorType.PRESSURE.getMinValue());
        assertEquals(1100.0, SensorType.PRESSURE.getMaxValue());
    }

    @Test
    void co2_upperThreshold_matchesMissionBrief() {
        // Arrange, Act, Assert
        assertEquals(2000.0, SensorType.CO2.getMaxValue());
    }

    @Test
    void co2_lowerThreshold_isNegativeInfinity() {
        // Arrange, Act, Assert - CO2 can never be "too low"
        assertEquals(Double.NEGATIVE_INFINITY, SensorType.CO2.getMinValue());
    }

    @Test
    void values_allFourSensorTypesExist() {
        // Arrange, Act
        SensorType[] result = SensorType.values();

        // Assert
        assertEquals(4, result.length);
    }

    @Test
    void formatRange_temperature_showsBothLimitsAndTheUnit() {
        // Act
        String result = SensorType.TEMP.formatRange();

        // Assert - the decimal separator follows the machine's locale
        assertTrue(result.contains("-15"), result);
        assertTrue(result.contains("35"), result);
        assertTrue(result.endsWith("C"), result);
    }

    @Test
    void formatRange_co2_saysThereIsNoLowerLimit() {
        // Act
        String result = SensorType.CO2.formatRange();

        // Assert
        assertTrue(result.startsWith("no lower limit"), result);
        assertTrue(result.endsWith("ppm"), result);
    }
}
