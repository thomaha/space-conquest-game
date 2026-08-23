package com.spaceconquest.control.command;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.galaxy.FogOfWarState;
import com.spaceconquest.engine.galaxy.SensorProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to direct sensor arrays or an explorer fleet to deep-scan an uncharted solar system.
 */
public record ScanSystemCommand(
        String empireId,
        String targetSystemId
) implements GameCommand {

    @Override
    public boolean validate(GameState state) {
        if (empireId == null || targetSystemId == null) return false;
        return state.solarSystems().stream().anyMatch(s -> s.id().equals(targetSystemId));
    }

    @Override
    public GameState apply(GameState state) {
        if (!validate(state)) return state;

        SolarSystem targetSys = state.solarSystems().stream()
                .filter(s -> s.id().equals(targetSystemId))
                .findFirst()
                .orElse(null);

        SensorProcessor sensorProcessor = new SensorProcessor();
        List<FogOfWarState> updated = new ArrayList<>();

        boolean found = false;
        for (FogOfWarState fow : state.fogOfWarStates()) {
            if (fow.empireId().equals(empireId)) {
                updated.add(sensorProcessor.scanSystem(fow, targetSystemId, targetSys, List.of()));
                found = true;
            } else {
                updated.add(fow);
            }
        }

        if (!found) {
            FogOfWarState newState = new FogOfWarState(empireId, List.of(targetSystemId), List.of(), List.of(), List.of(), List.of());
            updated.add(sensorProcessor.scanSystem(newState, targetSystemId, targetSys, List.of()));
        }

        return new GameState(
                state.turn(), state.status(), state.solarSystems(), state.empires(),
                state.corporations(), state.commercialHubs(), state.shadowSyndicates(),
                state.diplomaticRelations(), state.systemGovernors(), state.researchProjects(),
                state.technologyExchangeRoutes(), state.shipDesigns(), state.fleets(),
                state.geologicalDeposits(), state.powerGrids(), state.industrialFacilities(),
                state.expansionProjects(), state.orbitalStations(), state.spaceElevators(),
                state.constructionProjects(), state.sleeperAgents(), state.espionageOperations(),
                state.pirateBases(), state.terraformingProjects(), state.megastructures(),
                state.galacticCommunity(), state.tradeRoutes(), updated
        );
    }
}
