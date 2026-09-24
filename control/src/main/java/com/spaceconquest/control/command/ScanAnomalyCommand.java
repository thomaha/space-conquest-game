package com.spaceconquest.control.command;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.galaxy.Anomaly;
import com.spaceconquest.engine.galaxy.AnomalyProcessor;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Command ordering a science or exploration fleet to survey a deep-space anomaly.
 */
public record ScanAnomalyCommand(
        String empireId,
        String fleetId,
        Anomaly anomaly
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || empireId == null || fleetId == null || anomaly == null) {
            return false;
        }
        boolean empValid = state.empires().stream().anyMatch(e -> e.id().equals(empireId));
        boolean fleetValid = state.fleets().stream().anyMatch(f -> f.id().equals(fleetId));
        return empValid && fleetValid;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        Fleet fleet = state.fleets().stream().filter(f -> f.id().equals(fleetId)).findFirst().orElse(null);
        if (fleet == null) return state;

        FleetProcessor fleetProcessor = new FleetProcessor();
        double sensorRange = fleetProcessor.calculateFleetScannerRange(fleet, state.shipDesigns());

        AnomalyProcessor processor = new AnomalyProcessor();
        AnomalyProcessor.AnomalyScanResult scanRes = processor.scanAnomaly(anomaly, sensorRange, 5, 0.05); // deterministic success for testing / validation

        if (!scanRes.isSuccessful()) {
            return state;
        }

        List<Empire> updatedEmpires = new ArrayList<>();
        for (Empire e : state.empires()) {
            if (e.id().equals(empireId)) {
                double newTreasury = e.treasuryCredits();
                List<String> techs = new ArrayList<>(e.unlockedTechIds());

                if (Anomaly.REWARD_CREDITS.equalsIgnoreCase(scanRes.rewardType())) {
                    newTreasury += scanRes.rewardAmount();
                } else if (Anomaly.REWARD_TECH_UNLOCK.equalsIgnoreCase(scanRes.rewardType())) {
                    if (scanRes.unlockedTechId() != null && !techs.contains(scanRes.unlockedTechId())) {
                        techs.add(scanRes.unlockedTechId());
                    }
                }

                updatedEmpires.add(new Empire(
                        e.id(), e.name(), e.raceId(), e.societyStructure(),
                        newTreasury, e.corporateTaxRate(), e.controlledSystemIds(),
                        e.ministries(), e.systemGovernorAssignments(), techs, e.activeShipDesignIds()
                ));
            } else {
                updatedEmpires.add(e);
            }
        }

        return state.toBuilder()
                .empires(updatedEmpires)
                .build();
    }
}
