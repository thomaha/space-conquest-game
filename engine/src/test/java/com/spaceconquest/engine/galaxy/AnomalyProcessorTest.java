package com.spaceconquest.engine.galaxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AnomalyProcessorTest {

    private AnomalyProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new AnomalyProcessor(42L);
    }

    @Test
    void testScanAnomalySuccess() {
        Anomaly anomaly = new Anomaly(
                "anom_1", "alpha_centauri", Anomaly.TYPE_DERELICT_STARSHIP,
                "Alien Cruiser", "Derelict hull drifting in orbit", 30.0, false,
                Anomaly.REWARD_CREDITS, 15000.0, ""
        );

        // High sensor range & scientists = high scan power
        AnomalyProcessor.AnomalyScanResult res = processor.scanAnomaly(anomaly, 25.0, 5, 0.10);

        assertTrue(res.isSuccessful());
        assertTrue(res.updatedAnomaly().isScanned());
        assertEquals(Anomaly.REWARD_CREDITS, res.rewardType());
        assertEquals(15000.0, res.rewardAmount(), 0.01);
    }

    @Test
    void testScanAnomalyFailureWithPoorSensors() {
        Anomaly hardAnomaly = new Anomaly(
                "anom_2", "alpha_centauri", Anomaly.TYPE_UNSTABLE_WORMHOLE,
                "Subspace Rift", "Rift fluctuation", 100.0, false,
                Anomaly.REWARD_TECH_UNLOCK, 0.0, "tachyon_sensors"
        );

        // Very poor sensor range & 0 scientists with high roll
        AnomalyProcessor.AnomalyScanResult res = processor.scanAnomaly(hardAnomaly, 2.0, 0, 0.90);

        assertFalse(res.isSuccessful());
        assertFalse(res.updatedAnomaly().isScanned());
    }
}
