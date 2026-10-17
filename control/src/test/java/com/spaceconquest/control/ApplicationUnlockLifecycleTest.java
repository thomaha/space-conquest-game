package com.spaceconquest.control;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.technology.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationUnlockLifecycleTest {
    @TempDir Path directory;
    private GameState world() {
        var empire = new Empire("owner", "Owner", "human", "Individualist", 100000, 0,
                List.of("sol"), List.of(), Map.of(), List.of("electricity", "industrial_production"), List.of());
        return ResearchTestWorld.staffed(GameState.builder().empires(List.of(empire)).build());
    }
    @Test void paidResearchUnlocksMaterialFundedConstructionGenerationAndDiskReload() throws Exception {
        var state = world(); var build = new BuildFacilityCommand("owner", "research-world", "solar_power", "technician", 1);
        assertFalse(build.validate(state)); assertSame(state, build.apply(state));
        var queue = new CommandQueue(); queue.submit(new StartResearchCommand("owner", "solar_power", true, 30));
        state = queue.drainAndExecute(state); assertEquals(0, state.researchProjects().getFirst().accumulatedPoints());
        var processor = new ResearchProcessor(new Random() { @Override public double nextDouble() { return .01; } });
        for (int tick = 0; tick < 20 && !state.researchProjects().isEmpty(); tick++) {
            var result = new ResearchTickProcessor().process(state, List.of(), processor);
            state = state.toBuilder().empires(result.empires()).researchProjects(result.projects())
                    .applicationOptimizations(result.optimizations()).build();
        }
        assertTrue(state.researchProjects().isEmpty()); assertTrue(state.empires().getFirst().unlockedTechIds().contains("solar_power"));
        queue.submit(new ResolveApplicationResearchCommand("owner", "solar_power", true)); state = queue.drainAndExecute(state);
        assertTrue(build.validate(state)); queue.submit(build); state = queue.drainAndExecute(state);
        var project = state.expansionProjects().getFirst(); assertFalse(project.requiredMaterialsKg().isEmpty());
        var blocked = new IndustryProcessor().processIndustrialProduction(state);
        assertEquals(0, blocked.expansionProjects().getFirst().accumulatedWorkHours());
        var orders = new HashMap<String, MarketOrder>();
        project.requiredMaterialsKg().forEach((id, kg) -> orders.put(id, new MarketOrder(id, kg, 0, 2, 0)));
        state = state.withCommercialHubs(List.of(new CommercialHub("hub", "research-world", 0, 100000, 1000000, 10, orders)));
        double cash = state.empires().getFirst().treasuryCredits();
        for (int tick = 0; tick < 20 && !state.expansionProjects().isEmpty(); tick++) state = new IndustryProcessor().processIndustrialProduction(state);
        assertTrue(state.expansionProjects().isEmpty()); assertTrue(state.empires().getFirst().treasuryCredits() < cash);
        assertTrue(state.commercialHubs().getFirst().activeOrders().values().stream().allMatch(order -> order.supplyKg() < .000001));
        String facility = state.industrialFacilities().getFirst().id();
        var generation = new PowerGenerationProcessor().process(state, Map.of(facility, 100), Map.of());
        assertTrue(generation.generationKw().get("research-world") > 0);
        var file = directory.resolve("application-unlock.scsave").toFile(); var saves = new SaveGameManager(directory);
        saves.save(file, state, 1, "2026-10-13T00:00:00"); var loaded = saves.load(file).toGameState(state.turn(), "RUNNING");
        assertEquals(state.industrialFacilities(), loaded.industrialFacilities());
        assertEquals(state.applicationOptimizations(), loaded.applicationOptimizations());
        assertEquals(generation.generationKw(), new PowerGenerationProcessor().process(loaded, Map.of(facility, 100), Map.of()).generationKw());
    }
    @Test void recipesAndConstructionCannotSkipApplicationsOrInstallFutureWeaponsAsFactories() {
        var state = world(); var empire = state.empires().getFirst();
        assertTrue(new BuildFacilityCommand("owner", "research-world", com.spaceconquest.engine.ship.ShipyardWorkCapacity.SURFACE_SHIPYARD_APPLICATION_ID,
                "industrial_worker", 1).validate(state));
        assertFalse(IndustryRecipeCatalog.isUnlocked(IndustryRecipeCatalog.find("water_electrolysis"), empire));
        assertFalse(new BuildFacilityCommand("owner", "research-world", "plasma_weapons", "technician", 1).validate(state));
        assertFalse(new BuildFacilityCommand("owner", "research-world", "unknown_application", "technician", 1).validate(state));
        assertFalse(new PlaceFacilityOnTileCommand("research-world", 0, "solar_power", "owner", IndustrialFacility.PUBLIC_STATE, 100, "technician").validate(state));
        var oldPlant = state.withIndustrialFacilities(List.of(new IndustrialFacility("old", "research-world", "solar_power", "owner",
                IndustrialFacility.PUBLIC_STATE, 1, 100, "technician", false, 0)));
        assertEquals(0, new PowerGenerationProcessor().process(oldPlant, Map.of("old", 100), Map.of()).generationKw().get("research-world"));
        state = state.withIndustrialFacilities(List.of(new IndustrialFacility("existing", "research-world", "smelter", "owner",
                IndustrialFacility.PUBLIC_STATE, 1, 100, "technician", false, 0)));
        assertFalse(new SetFacilityRecipeCommand("existing", "water_electrolysis").validate(state));
        assertFalse(new SetFacilityRecipeCommand("existing", "unknown_application").validate(state));
    }
}
