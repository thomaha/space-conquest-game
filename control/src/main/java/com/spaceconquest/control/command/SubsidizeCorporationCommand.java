package com.spaceconquest.control.command;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to transfer public state treasury credits to subsidize a private corporation.
 */
public record SubsidizeCorporationCommand(
        String empireId,
        String corporationId,
        double grantCredits
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || empireId == null || corporationId == null) return false;
        if (grantCredits <= 0.0) return false;

        Empire empire = state.empires().stream().filter(e -> e.id().equals(empireId)).findFirst().orElse(null);
        boolean corpExists = state.corporations().stream().anyMatch(c -> c.id().equals(corporationId));

        return empire != null && corpExists && empire.treasuryCredits() >= grantCredits;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;

        List<Empire> updatedEmpires = new ArrayList<>();
        for (Empire e : state.empires()) {
            if (e.id().equals(empireId)) {
                updatedEmpires.add(new Empire(
                        e.id(), e.name(), e.raceId(), e.societyStructure(),
                        e.treasuryCredits() - grantCredits, e.corporateTaxRate(),
                        e.controlledSystemIds(), e.ministries(), e.systemGovernorAssignments(),
                        e.unlockedTechIds(), e.activeShipDesignIds()
                ));
            } else {
                updatedEmpires.add(e);
            }
        }

        List<Corporation> updatedCorps = new ArrayList<>();
        for (Corporation c : state.corporations()) {
            if (c.id().equals(corporationId)) {
                updatedCorps.add(new Corporation(
                        c.id(), c.name(), c.empireId(), c.headquartersEntityId(),
                        c.marketOrientation(), c.liquidCapitalReserves() + grantCredits,
                        c.ownedFacilityIds(), c.ownedShipIds(), c.claimedVeinIds()
                ));
            } else {
                updatedCorps.add(c);
            }
        }

        return state.toBuilder()
                .empires(updatedEmpires)
                .corporations(updatedCorps)
                .build();
    }
}
