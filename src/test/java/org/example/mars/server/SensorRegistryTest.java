package org.example.mars.server;

import org.example.mars.model.SensorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the registry that hands out sensor types.
 *
 * This is the only state shared between the handler threads, so besides the
 * normal cases there is a test that hammers it from several threads at once.
 */
class SensorRegistryTest {

    private SensorRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SensorRegistry();
    }

    @Test
    void claimFreeType_firstSensor_returnsFirstType() {
        // Arrange, Act
        SensorType result = registry.claimFreeType();

        // Assert
        assertEquals(SensorType.TEMP, result);
    }

    @Test
    void claimFreeType_secondSensor_returnsNextType() {
        // Arrange
        registry.claimFreeType();

        // Act
        SensorType result = registry.claimFreeType();

        // Assert
        assertEquals(SensorType.O2, result);
    }

    @Test
    void claimFreeType_allTypesTaken_returnsNull() {
        // Arrange - claim one per type
        for (int i = 0; i < SensorType.values().length; i++) {
            assertNotNull(registry.claimFreeType());
        }

        // Act
        SensorType result = registry.claimFreeType();

        // Assert
        assertNull(result);
    }

    @Test
    void claimFreeType_everyTypeIsHandedOutOnce_returnsAllFourTypes() {
        // Arrange
        List<SensorType> claimed = new ArrayList<>();

        // Act
        for (int i = 0; i < SensorType.values().length; i++) {
            claimed.add(registry.claimFreeType());
        }

        // Assert
        assertEquals(SensorType.values().length, claimed.size());
        assertEquals(claimed.size(), claimed.stream().distinct().count());
    }

    @Test
    void release_typeGivenBack_canBeClaimedAgain() {
        // Arrange
        SensorType first = registry.claimFreeType();
        registry.claimFreeType();
        registry.claimFreeType();
        registry.claimFreeType();
        registry.release(first);

        // Act
        SensorType result = registry.claimFreeType();

        // Assert
        assertEquals(first, result);
    }

    @Test
    void release_null_doesNotChangeAnything() {
        // Arrange
        registry.claimFreeType();

        // Act
        registry.release(null);

        // Assert
        assertEquals(1, registry.countInUse());
    }

    @Test
    void countInUse_noSensors_returnsZero() {
        // Arrange, Act
        int result = registry.countInUse();

        // Assert
        assertEquals(0, result);
    }

    @Test
    void countInUse_twoSensors_returnsTwo() {
        // Arrange
        registry.claimFreeType();
        registry.claimFreeType();

        // Act
        int result = registry.countInUse();

        // Assert
        assertEquals(2, result);
    }

    @Test
    void idFor_temperature_returnsSensor01() {
        // Arrange, Act
        String result = SensorRegistry.idFor(SensorType.TEMP);

        // Assert
        assertEquals("SENSOR-01", result);
    }

    @Test
    void idFor_co2_returnsSensor04() {
        // Arrange, Act
        String result = SensorRegistry.idFor(SensorType.CO2);

        // Assert
        assertEquals("SENSOR-04", result);
    }

    @Test
    void claimFreeType_manyThreadsAtOnce_neverHandsOutTheSameTypeTwice() throws InterruptedException {
        // Arrange - 20 threads try to claim a type at the exact same moment
        int threadCount = 20;
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(threadCount);
        List<SensorType> claimed = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startSignal.await();
                    SensorType type = registry.claimFreeType();
                    if (type != null) {
                        claimed.add(type);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            }).start();
        }

        // Act - release all threads at once
        startSignal.countDown();
        assertTrue(finished.await(5, TimeUnit.SECONDS));

        // Assert - four types handed out, and no type handed out twice
        assertEquals(SensorType.values().length, claimed.size());
        assertEquals(claimed.size(), claimed.stream().distinct().count());
    }
}
