package com.spaceconquest.control.command;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SystemGovernor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command to assign or appoint a governor to a solar system.
 */
public record AssignGovernorCommand(
        String empireId,
        String systemId,
        String professionId
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || empireId == null || systemId == null || professionId == null) return false;

        Empire empire = state.empires().stream().filter(e -> e.id().equals(empireId)).findFirst().orElse(null);
        if (empire == null) return false;

        boolean isHiveMind = "Hive Mind".equalsIgnoreCase(empire.societyStructure())
                || "Hive mind".equalsIgnoreCase(empire.societyStructure());
        return !isHiveMind;
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;

        String governorId = "gov_" + systemId + "_" + professionId;
        SystemGovernor newGov = new SystemGovernor(
                governorId,
                "Governor (" + professionId + ")",
                systemId,
                professionId,
                1.15,
                0.20
        );

        List<SystemGovernor> updatedGovs = new ArrayList<>(state.systemGovernors());
        updatedGovs.removeIf(g -> g.solarSystemId().equals(systemId));
        updatedGovs.add(newGov);

        List<Empire> updatedEmpires = new ArrayList<>();
        for (Empire e : state.empires()) {
            if (e.id().equals(empireId)) {
                Map<String, String> govAssignments = new HashMap<>(e.systemGovernorAssignments());
                govAssignments.put(systemId, governorId);
                updatedEmpires.add(new Empire(
                        e.id(), e.name(), e.raceId(), e.societyStructure(),
                        e.treasuryCredits(), e.corporateTaxRate(),
                        e.controlledSystemIds(), e.ministries(), govAssignments,
                        e.unlockedTechIds(), e.activeShipDesignIds()
                ));
            } else {
                updatedEmpires.add(e);
            }
        }

        return new GameState(
                state.turn(),
                state.status(),
                state.solarSystems(),
                updatedEmpires,
                state.corporations(),
                state.commercialHubs(),
                state.shadowSyndicates(),
                state.diplomaticRelations(),
                updatedGovs,
                state.researchProjects(),
                state.technologyExchangeRoutes(),
                state.shipDesigns(),
                state.fleets(),
                state.geologicalDeposits(),
                state.powerGrids(),
                state.industrialFacilities(),
                state.expansionProjects()
        );
    }
}
