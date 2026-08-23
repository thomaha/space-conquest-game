package com.spaceconquest.control.command;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.megastructure.Megastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Command to commence construction of a grand stellar engineering megastructure or gateway.
 */
public record BuildMegastructureCommand(
        String empireId,
        String megastructureType,
        String systemId,
        String targetCelestialId,
        String customName
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (state == null || empireId == null || megastructureType == null || systemId == null) {
            return false;
        }
        return state.empires().stream()
                .anyMatch(e -> e.id().equalsIgnoreCase(empireId)
                        && e.treasuryCredits() >= 50000.0
                        && (e.unlockedTechIds() == null || e.unlockedTechIds().isEmpty()
                                || e.unlockedTechIds().contains("stellar_megastructures")
                                || e.unlockedTechIds().contains("surface_to_orbit_infrastructure")));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) {
            return state;
        }

        double costCredits = 50000.0;
        List<Empire> updatedEmpires = state.empires().stream().map(e -> {
            if (e.id().equalsIgnoreCase(empireId)) {
                return new Empire(
                        e.id(), e.name(), e.raceId(), e.societyStructure(),
                        e.treasuryCredits() - costCredits,
                        e.corporateTaxRate(), e.controlledSystemIds(),
                        e.ministries(), e.systemGovernorAssignments(),
                        e.unlockedTechIds(), e.activeShipDesignIds()
                );
            }
            return e;
        }).toList();

        int totalStages = switch (megastructureType) {
            case Megastructure.TYPE_DYSON_SPHERE, Megastructure.TYPE_RINGWORLD -> 3;
            case Megastructure.TYPE_DYSON_SWARM, Megastructure.TYPE_STAR_LIFTER -> 2;
            case Megastructure.TYPE_HYPERLANE_GATEWAY -> 1;
            default -> 2;
        };

        String name = (customName != null && !customName.isBlank())
                ? customName
                : "Megastructure " + megastructureType + " (" + systemId + ")";

        Megastructure mega = new Megastructure(
                "mega_" + megastructureType.toLowerCase() + "_" + systemId + "_" + UUID.randomUUID().toString().substring(0, 6),
                name,
                megastructureType,
                systemId,
                targetCelestialId != null ? targetCelestialId : systemId,
                empireId,
                0,
                totalStages,
                0.0,
                15.0,
                false,
                0.0,
                Map.of(),
                0
        );

        List<Megastructure> updatedMegastructures = new ArrayList<>(state.megastructures());
        updatedMegastructures.add(mega);

        return new GameState(
                state.turn(), state.status(), state.solarSystems(), updatedEmpires,
                state.corporations(), state.commercialHubs(), state.shadowSyndicates(),
                state.diplomaticRelations(), state.systemGovernors(), state.researchProjects(),
                state.technologyExchangeRoutes(), state.shipDesigns(), state.fleets(),
                state.geologicalDeposits(), state.powerGrids(), state.industrialFacilities(),
                state.expansionProjects(), state.orbitalStations(), state.spaceElevators(),
                state.constructionProjects(), state.sleeperAgents(), state.espionageOperations(),
                state.pirateBases(), state.terraformingProjects(), updatedMegastructures, state.galacticCommunity(),
                state.tradeRoutes(), state.fogOfWarStates(), state.systemEconomies()
        );
    }
}
