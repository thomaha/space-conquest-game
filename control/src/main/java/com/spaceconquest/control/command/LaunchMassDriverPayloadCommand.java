package com.spaceconquest.control.command;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.industry.IndustryProcessor;
import com.spaceconquest.engine.industry.SurfaceMassDriver;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to launch a bulk mineral payload from a planetary surface mass driver into orbit.
 */
public record LaunchMassDriverPayloadCommand(
        String massDriverId,
        String ownerEntityId,
        String materialId,
        double payloadTons,
        double availablePowerKw
) implements GameCommand {

    public LaunchMassDriverPayloadCommand(String massDriverId, String materialId, double payloadTons, String targetOrbitId) {
        this(massDriverId, "terran_confederation", materialId, payloadTons, 500.0);
    }

    @Override
    public boolean validate(GameState state) {
        if (state == null || massDriverId == null || ownerEntityId == null || payloadTons <= 0.0) {
            return false;
        }
        boolean empireExists = state.empires().stream().anyMatch(e -> e.id().equals(ownerEntityId));
        boolean corpExists = state.corporations().stream().anyMatch(c -> c.id().equals(ownerEntityId));
        return empireExists || corpExists;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        SurfaceMassDriver driver = new SurfaceMassDriver(
                massDriverId, "planet_loc", ownerEntityId, 5000.0, 250.0, 0.5, true
        );

        double credits = 0.0;
        Empire targetEmpire = state.empires().stream().filter(e -> e.id().equals(ownerEntityId)).findFirst().orElse(null);
        Corporation targetCorp = state.corporations().stream().filter(c -> c.id().equals(ownerEntityId)).findFirst().orElse(null);

        if (targetEmpire != null) credits = targetEmpire.treasuryCredits();
        else if (targetCorp != null) credits = targetCorp.liquidCapitalReserves();

        IndustryProcessor processor = new IndustryProcessor();
        IndustryProcessor.MassDriverLaunchResult res = processor.processMassDriverLaunch(
                driver, payloadTons, availablePowerKw, credits
        );

        if (!res.isSuccessful()) {
            return state;
        }

        double cost = res.operationalCostCredits();

        List<Empire> updatedEmpires = state.empires();
        List<Corporation> updatedCorps = state.corporations();

        if (targetEmpire != null) {
            updatedEmpires = new ArrayList<>();
            for (Empire e : state.empires()) {
                if (e.id().equals(ownerEntityId)) {
                    updatedEmpires.add(new Empire(
                            e.id(), e.name(), e.raceId(), e.societyStructure(),
                            e.treasuryCredits() - cost, e.corporateTaxRate(), e.controlledSystemIds(),
                            e.ministries(), e.systemGovernorAssignments(), e.unlockedTechIds(), e.activeShipDesignIds()
                    ));
                } else {
                    updatedEmpires.add(e);
                }
            }
        } else if (targetCorp != null) {
            updatedCorps = new ArrayList<>();
            for (Corporation c : state.corporations()) {
                if (c.id().equals(ownerEntityId)) {
                    updatedCorps.add(new Corporation(
                            c.id(), c.name(), c.empireId(), c.headquartersEntityId(), c.marketOrientation(),
                            c.liquidCapitalReserves() - cost, c.ownedFacilityIds(), c.ownedShipIds(), c.claimedVeinIds()
                    ));
                } else {
                    updatedCorps.add(c);
                }
            }
        }

        return state.toBuilder()
                .empires(updatedEmpires)
                .corporations(updatedCorps)
                .build();
    }
}
