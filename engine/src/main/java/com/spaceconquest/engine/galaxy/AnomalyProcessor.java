package com.spaceconquest.engine.galaxy;

import java.util.Random;

/**
 * Resolves sensor scans and research investigations on deep-space anomalies.
 */
public class AnomalyProcessor {

    private final Random random;

    public AnomalyProcessor() {
        this.random = new Random();
    }

    public AnomalyProcessor(long seed) {
        this.random = new Random(seed);
    }

    public record AnomalyScanResult(
            boolean isSuccessful,
            Anomaly updatedAnomaly,
            String rewardType,
            double rewardAmount,
            String unlockedTechId,
            String logMessage
    ) {}

    /**
     * Executes a scan roll against an anomaly using exploration sensor profiles and scientist skill.
     */
    public AnomalyScanResult scanAnomaly(
            Anomaly anomaly,
            double fleetSensorRange,
            int assignedScientists,
            Double deterministicRoll
    ) {
        if (anomaly == null) {
            return new AnomalyScanResult(false, null, "", 0.0, "", "Null anomaly target");
        }
        if (anomaly.isScanned()) {
            return new AnomalyScanResult(true, anomaly, anomaly.rewardType(), 0.0, anomaly.unlockedTechId(), "Anomaly has already been scanned");
        }

        double roll = deterministicRoll != null ? deterministicRoll : random.nextDouble();
        double scanPower = (fleetSensorRange * 2.5) + (assignedScientists * 8.0);
        double successThreshold = Math.min(0.95, Math.max(0.10, scanPower / Math.max(10.0, anomaly.scanDifficulty())));

        boolean success = roll <= successThreshold;

        if (success) {
            Anomaly resolved = new Anomaly(
                    anomaly.id(),
                    anomaly.systemId(),
                    anomaly.type(),
                    anomaly.title(),
                    anomaly.description(),
                    anomaly.scanDifficulty(),
                    true,
                    anomaly.rewardType(),
                    anomaly.rewardAmount(),
                    anomaly.unlockedTechId()
            );

            return new AnomalyScanResult(
                    true,
                    resolved,
                    anomaly.rewardType(),
                    anomaly.rewardAmount(),
                    anomaly.unlockedTechId(),
                    "Successfully investigated " + anomaly.title() + " yielding " + anomaly.rewardType()
            );
        } else {
            return new AnomalyScanResult(
                    false,
                    anomaly,
                    "",
                    0.0,
                    "",
                    "Scan attempt on " + anomaly.title() + " failed to penetrate sensor interference"
            );
        }
    }
}
