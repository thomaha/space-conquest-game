package com.spaceconquest.engine.industry;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves turn-based planetary electrical grid balance sheets, battery buffering and brownout load shedding.
 */
public class PowerProcessor {

    /**
     * Balances the planetary power grid for one turn cycle.
     */
    public PowerGridState balanceGrid(
            String entityId,
            double totalGenerationKw,
            double totalDemandKw,
            double currentBatteryKwh,
            double maxBatteryCapacityKwh
    ) {
        double gen = Math.max(0.0, totalGenerationKw);
        double dem = Math.max(0.0, totalDemandKw);
        double netBalance = gen - dem;
        double maxCap = Math.max(0.0, maxBatteryCapacityKwh);
        double stored = Math.max(0.0, currentBatteryKwh);
        boolean brownout = false;

        if (netBalance >= 0.0) {
            stored = Math.min(maxCap, stored + netBalance);
            brownout = false;
        } else {
            double deficit = -netBalance;
            if (stored >= deficit) {
                stored -= deficit;
                brownout = false;
            } else {
                stored = 0.0;
                brownout = true;
            }
        }

        return new PowerGridState(
                entityId,
                gen,
                dem,
                netBalance,
                maxCap,
                stored,
                brownout
        );
    }

    /**
     * Executes emergency load-shedding during power deficit brownouts.
     * Heavy metallurgy and manufacturing facilities are throttled to 0 output while protecting life support and farming.
     */
    public List<IndustrialFacility> applyEmergencyLoadShedding(
            List<IndustrialFacility> facilities,
            boolean isBrownoutActive
    ) {
        if (facilities == null || !isBrownoutActive) {
            return facilities != null ? facilities : List.of();
        }

        List<IndustrialFacility> processed = new ArrayList<>();
        for (IndustrialFacility facility : facilities) {
            String appId = facility.applicationId().toLowerCase();
            boolean isHeavyIndustry = appId.contains("refin") || appId.contains("foundry")
                    || appId.contains("metallurgy") || appId.contains("nanotube") || appId.contains("heavy");

            if (isHeavyIndustry) {
                // Shed load by idling workers / pausing throughput
                processed.add(new IndustrialFacility(
                        facility.id(),
                        facility.planetId(),
                        facility.applicationId(),
                        facility.ownerEntityId(),
                        facility.ownershipType(),
                        facility.tier(),
                        0, // Idled during brownout
                        facility.workerProfessionId(),
                        facility.isUndergoingExpansion(),
                        facility.expansionProgress()
                ));
            } else {
                processed.add(facility);
            }
        }
        return processed;
    }
}
