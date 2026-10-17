package com.spaceconquest.control;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.market.CorporateInvestmentProcessor;
import com.spaceconquest.engine.technology.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WaterProcessingLifecycleTest {
    @TempDir Path directory;

    @Test void fundedDiscoveryBuildsARealWaterPlantAndProducesMarketStockAfterCurrentFormatReload() throws Exception {
        var state = ResearchTestWorld.staffed(GameState.builder().empires(List.of(owner(List.of("industrial_production", "electricity")))).build());
        var build = new BuildFacilityCommand("research-world", "surface_water_treatment", "owner", IndustrialFacility.PUBLIC_STATE, 10, "technician");
        assertFalse(build.validate(state));
        var queue = new CommandQueue(); queue.submit(new StartResearchCommand("owner", "surface_water_treatment", true, 30));
        state = queue.drainAndExecute(state);
        var research = new ResearchProcessor(new Random() { @Override public double nextDouble() { return .01; } });
        for (int tick = 0; tick < 100 && !state.researchProjects().isEmpty(); tick++) {
            var result = new ResearchTickProcessor().process(state, List.of(), research);
            state = state.toBuilder().empires(result.empires()).researchProjects(result.projects()).applicationOptimizations(result.optimizations()).build();
        }
        state = new ResolveApplicationResearchCommand("owner", "surface_water_treatment", true).apply(state);
        assertTrue(build.validate(state)); queue.submit(build); state = queue.drainAndExecute(state);
        var facility = state.industrialFacilities().getFirst();
        var bill = state.expansionProjects().getFirst().requiredMaterialsKg();
        var orders = new HashMap<String, MarketOrder>(); bill.forEach((id, kg) -> orders.put(id, new MarketOrder(id, kg, 0, 1, 0)));
        orders.put("purified_water", new MarketOrder("purified_water", 0, 10000, 1, 0));
        state = state.toBuilder().commercialHubs(List.of(new CommercialHub("hub", "research-world", 0, 1e6,
                        bill.values().stream().mapToDouble(Double::doubleValue).sum(), 10, orders)))
                .marketAccounts(List.of(new MarketAccount("hub", 1e6))).build();
        for (int day = 0; day < 6 && !state.expansionProjects().isEmpty(); day++) state = new IndustryProcessor().processIndustrialProduction(state);
        assertTrue(state.expansionProjects().isEmpty());
        var saves = new SaveGameManager(directory); var file = saves.save("water", state, 1, GameClock.START_TIME.toString());
        state = saves.load(file.toFile()).toGameState(state.turn(), state.status());
        var result = new IndustryMarketProcessor().process(state, Map.of(facility.id(), 10), Map.of(), Map.of(facility.id(), 10),
                Map.of(facility.id(), new ProductionWork.EnergyBudget(.4, 0)));
        assertEquals(1000, result.industryAccounts().stream().filter(a -> a.facilityId().equals(facility.id())).findFirst().orElseThrow().producedKg().get("purified_water"), 1e-8);
        assertEquals(1000, result.hubs().getFirst().activeOrders().get("purified_water").supplyKg(), 1e-8);
    }

    @Test void corporateSelectionUsesDeclaredSourcesAndResearchRatherThanAFactoryNameFallback() {
        var processor = new CorporateInvestmentProcessor();
        var wet = processor.invest(world(true, List.of("surface_water_treatment", "ice_water_treatment")), "corp", "body", "INFRASTRUCTURE", 8000);
        assertEquals("surface_water_treatment", wet.industrialFacilities().getFirst().applicationId());
        var dry = processor.invest(world(false, List.of("surface_water_treatment", "ice_water_treatment")), "corp", "body", "INFRASTRUCTURE", 8000);
        assertEquals("ice_water_treatment", dry.industrialFacilities().getFirst().applicationId());
        var alternate = processor.invest(world(true, List.of("ice_water_treatment")), "corp", "body", "INFRASTRUCTURE", 8000);
        assertEquals("ice_water_treatment", alternate.industrialFacilities().getFirst().applicationId());
        assertEquals(FactoryDefinition.find("ice_water_treatment").constructionMaterialsKg(), alternate.expansionProjects().getFirst().requiredMaterialsKg());
    }

    @Test void publicAndTileCommandsRejectMissingEnvironmentalInputsAndIncorrectEquipmentProfession() {
        var dry = world(false, List.of("surface_water_treatment", "ice_water_treatment"));
        assertFalse(new BuildFacilityCommand("body", "surface_water_treatment", "owner", IndustrialFacility.PUBLIC_STATE, 10, "technician").validate(dry));
        assertFalse(new PlaceFacilityOnTileCommand("body", 0, "surface_water_treatment", "owner", IndustrialFacility.PUBLIC_STATE, 10, "technician").validate(dry));
        assertTrue(new BuildFacilityCommand("body", "ice_water_treatment", "owner", IndustrialFacility.PUBLIC_STATE, 10, "technician").validate(dry));
        assertFalse(new BuildFacilityCommand("body", "ice_water_treatment", "owner", IndustrialFacility.PUBLIC_STATE, 10, "farmer").validate(dry));
    }

    private Empire owner(List<String> known) {
        return new Empire("owner", "Owner", "human", "Individualist", 1e7, 0, List.of("sol"), List.of(), Map.of(), known, List.of());
    }
    private GameState world(boolean wet, List<String> applications) {
        var known = new ArrayList<>(applications); known.addAll(List.of("industrial_production", "electricity"));
        var planet = new Planet("body", "Body", "", 1e24, 9.81, SolarRadiation.AU_KM, 0, 12742, "terrestrial", "breathable", wet, wet ? 1 : 0, List.of(), List.of(), List.of());
        var system = new SolarSystem("sol", "Sol", "", 0, 0, 0, SolarRadiation.SOLAR_MASS_KG, 1392700, "Yellow", List.of(planet), List.of());
        var orders = Map.of("purified_water", new MarketOrder("purified_water", 0, 10000, 1, 1),
                "water_ice", new MarketOrder("water_ice", 10000, 0, 1, 0));
        return GameState.builder().empires(List.of(owner(known))).solarSystems(List.of(system))
                .corporations(List.of(new Corporation("corp", "Corp", "owner", "body", "AGRICULTURE", 1e6, List.of(), List.of(), List.of())))
                .commercialHubs(List.of(new CommercialHub("hub", "body", 0, 1e9, 10000, 10, orders)))
                .marketAccounts(List.of(new MarketAccount("hub", 1e6))).build();
    }
}
