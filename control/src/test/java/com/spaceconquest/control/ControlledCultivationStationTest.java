package com.spaceconquest.control;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.macrostructure.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ControlledCultivationStationTest {
    @TempDir Path directory;
    @Test void farmsAndStationModulesShareReservationsAcrossQueuedConstructionAndCurrentReload() throws Exception {
        var knowledge = List.of("industrial_production", "electricity", "hydroponic_growth_arrays", "space_stations");
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e8, 0, List.of("sol"), List.of(), Map.of(), knowledge, List.of());
        var station = new OrbitalStation("station", "Station", "sol", "earth", "owner", "PUBLIC_STATE", 5,
                List.of(), Map.of(), 0, 0, 0, 0, 100, 100, "steel", 1, true);
        var state = GameState.builder().empires(List.of(owner)).orbitalStations(List.of(station)).build();
        var farm = new BuildFacilityCommand("station", "hydroponic_growth_arrays", "owner", "PUBLIC_STATE", 4, "farmer");
        var module = new AddStationModuleCommand("station", "Cargo", StationModule.TYPE_STORAGE, 2, 100, 0, 0, "technician", 1);
        assertTrue(farm.validate(state)); assertTrue(module.validate(state));
        assertFalse(new PlaceFacilityOnTileCommand("station", 0, farm.applicationId(), "owner", "PUBLIC_STATE", 4, "farmer").validate(state));
        var queue = new CommandQueue(); queue.submit(farm); queue.submit(module); state = queue.drainAndExecute(state);
        assertEquals(1, state.industrialFacilities().size()); assertTrue(state.constructionProjects().isEmpty());
        assertEquals(4, CultivationSpace.farmSlots(state, "station", ""));
        assertFalse(farm.validate(state)); assertFalse(module.validate(state));
        var saves = new SaveGameManager(directory); var file = saves.save("reserved", state, 1, GameClock.START_TIME.toString());
        state = saves.load(file.toFile()).toGameState(state.turn(), state.status());
        assertEquals(4, CultivationSpace.farmSlots(state, "station", "")); assertFalse(module.validate(state));
        var planned = new StationModule("pending", "Pending", StationModule.TYPE_STORAGE, 2, 100, 0, 0, Map.of(), "technician", 1, true);
        var project = new ConstructionDeploymentProject("project", null, "sol", "earth", "STATION_MODULE", 0, 2,
                "owner", Map.of(), Map.of(), "Cargo", "PUBLIC_STATE", "station", planned, 0, "steel", 1, 0, false);
        var modulesFirst = state.toBuilder().industrialFacilities(List.of()).expansionProjects(List.of()).constructionProjects(List.of(project)).build();
        assertFalse(farm.validate(modulesFirst), "Pending modules also reserve space before a farm is quoted");
        var foreign = new OrbitalStation("station", "Station", "sol", "earth", "foreign", "PUBLIC_STATE", 5,
                List.of(), Map.of(), 0, 0, 0, 0, 100, 100, "steel", 1, true);
        assertFalse(farm.validate(modulesFirst.toBuilder().constructionProjects(List.of()).orbitalStations(List.of(foreign)).build()));
    }
}
