package com.spaceconquest.control;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.MarketAccount;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.logistics.*;
import com.spaceconquest.engine.macrostructure.MacroStructureProcessor;
import com.spaceconquest.engine.technology.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class InfrastructureApplicationLifecycleTest {
    @TempDir Path directory;

    @Test void terminalRequiresPaidResearchAndConstructionThenConsumesQuotedFuelThroughReload() throws Exception {
        var state = world("cargo_terminal");
        var capability = InfrastructureService.find("cargo_terminal").capability();
        var build = new BuildFacilityCommand("research-world", capability.facilityType(), "owner",
                IndustrialFacility.PUBLIC_STATE, capability.workers(), capability.professionId());
        assertFalse(build.validate(state));
        state = research(state, "cargo_terminal");
        assertTrue(build.validate(state));
        state = build.apply(state);
        var project = state.expansionProjects().getFirst();
        assertEquals(ConstructionMaterialCatalog.facility(state, "owner", "cargo_terminal", 1), project.requiredMaterialsKg());
        state = reload(state);
        double before = stock(state, "refined_iron");
        for (int day = 0; day < 30 && !state.expansionProjects().isEmpty(); day++)
            state = new IndustryProcessor().processIndustrialProduction(state);
        assertTrue(state.expansionProjects().isEmpty());
        assertTrue(stock(state, "refined_iron") < before);
        state = payroll(state);
        var plan = LaunchService.choose(state, "research-world", "owner", 100, false, false, 0);
        assertNotNull(plan);
        assertEquals(LaunchService.Mode.ROCKET, plan.mode());
        assertEquals(plan.fuelKg(), plan.fuelMaterialsKg().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-6);
        var source = state;
        state = LaunchService.settle(state, "owner", plan);
        for (var fuel : plan.fuelMaterialsKg().entrySet())
            assertEquals(stock(source, fuel.getKey()) - fuel.getValue(), stock(state, fuel.getKey()), 1e-6);
        var loaded = reload(state);
        assertEquals(state.launchUsageKg(), loaded.launchUsageKg());
        assertEquals(state.launchActivities(), loaded.launchActivities());
        assertEquals(state.commercialHubs(), loaded.commercialHubs());
    }

    @Test void driverUsesItsApplicationGateAndRejectsReusedCapacityAndPower() throws Exception {
        var state = world("mass_driver_launch_tracks");
        var capability = InfrastructureService.find("mass_driver").capability();
        var build = new BuildFacilityCommand("research-world", "mass_driver", "owner", IndustrialFacility.PUBLIC_STATE,
                capability.workers(), capability.professionId());
        assertFalse(build.validate(state));
        state = research(state, "mass_driver_launch_tracks");
        assertTrue(build.validate(state));
        state = build.apply(state);
        for (int day = 0; day < 10 && !state.expansionProjects().isEmpty(); day++)
            state = new IndustryProcessor().processIndustrialProduction(state);
        state = state.withPowerGrids(List.of(new PowerGridState("research-world", 1000, 0, 1000, 0, 0, false)));
        state = payroll(state);
        var plan = LaunchService.choose(state, "research-world", "owner", capability.capacityKgPerTier(), false, false, 0);
        assertNotNull(plan);
        assertEquals(capability.powerKwPerTier(), plan.powerKw());
        assertNull(LaunchService.choose(state, "research-world", "owner", 1, true, false, 0));
        var emptyGrid = state.withPowerGrids(List.of());
        assertSame(emptyGrid, LaunchService.settle(emptyGrid, "owner", plan));
        state = reload(LaunchService.settle(state, "owner", plan));
        assertSame(state, LaunchService.settle(state, "owner", plan));
        assertNull(LaunchService.choose(state, "research-world", "owner", 1, false, false, 0));
    }

    @Test void elevatorRequiresResearchAndHonorsApplicationConstructionAndCapacity() throws Exception {
        var state = world("space_elevators");
        var service = InfrastructureService.find("SPACE_ELEVATOR").capability();
        var build = new BuildSpaceElevatorCommand("research-world", "owner", service.capacityKgPerTier());
        assertFalse(build.validate(state));
        state = research(state, "space_elevators");
        assertTrue(build.validate(state));
        assertFalse(new BuildSpaceElevatorCommand("research-world", "owner", service.capacityKgPerTier() + 1).validate(state));
        state = reload(build.apply(state));
        for (int day = 0; day < service.infrastructure().constructionWorkPerTier(); day++)
            state = new MacroStructureProcessor().advanceConstructionProjects(state);
        assertEquals(service.liftDiscount(), state.spaceElevators().getFirst().surfaceToOrbitCostDiscount());
        assertEquals(service.capacityKgPerTier(), state.spaceElevators().getFirst().transitThroughputCapacityKgPerTurn());
        assertEquals(state.spaceElevators(), reload(state).spaceElevators());
    }

    @Test void yardConstructionRequiresOwningApplicationAndUnknownFacilitiesHaveNoInventedBill() throws Exception {
        var state = world("space_stations");
        var build = new BuildFacilityCommand("owner", "research-world", "surface_shipyard", "industrial_worker", 1);
        assertFalse(build.validate(state));
        state = research(state, "space_stations");
        assertTrue(build.validate(state));
        var capability = com.spaceconquest.engine.ship.ShipyardTechnology.yard("surface_shipyard");
        assertEquals(ApplicationProduction.constructionWorkHours(state, "owner", "surface_shipyard", capability.constructionWork()),
                build.apply(state).expansionProjects().getFirst().requiredWorkHours());
        assertThrows(IllegalArgumentException.class, () -> ConstructionMaterialCatalog.facility("undefined_service", 1));
    }

    private GameState world(String goal) throws Exception {
        var known = new ArrayList<String>();
        DataModelLoader.loadTechnologies().forEach(t -> { known.add(t.id());
            t.applications().stream().filter(a -> !goal.equals(a.id())).forEach(a -> known.add(a.id())); });
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e9, 0, List.of("sol"), List.of(), Map.of(), known, List.of());
        var state = ResearchTestWorld.staffed(GameState.builder().empires(List.of(owner)).build());
        var orders = new HashMap<String, MarketOrder>();
        for (var material : List.of("refined_iron", "refined_aluminum", "refined_copper", "silicon", "rp1_kerosene", "liquid_oxygen"))
            orders.put(material, new MarketOrder(material, 1e6, 1e6, 1, 0));
        return state.toBuilder().commercialHubs(List.of(new CommercialHub("hub", "research-world", 0, 1e9, 6e6, 10, orders)))
                .marketAccounts(List.of(new MarketAccount("hub", 1e9))).build();
    }
    private GameState research(GameState state, String goal) {
        var start = new StartResearchCommand("owner", goal, true, 30);
        assertTrue(start.validate(state));
        state = start.apply(state);
        var processor = new ResearchProcessor(new Random() { @Override public double nextDouble() { return .01; } });
        for (int day = 0; day < 200 && !state.researchProjects().isEmpty(); day++) {
            var result = new ResearchTickProcessor().process(state, List.of(), processor);
            state = state.toBuilder().empires(result.empires()).researchProjects(result.projects()).applicationOptimizations(result.optimizations()).build();
        }
        assertTrue(state.empires().getFirst().unlockedTechIds().contains(goal));
        return new ResolveApplicationResearchCommand("owner", goal, true).apply(state);
    }
    private GameState reload(GameState state) throws Exception {
        var saves = new SaveGameManager(directory);
        return saves.load(saves.save("infrastructure", state, 1, GameClock.START_TIME.toString()).toFile()).toGameState(state.turn(), state.status());
    }
    private GameState payroll(GameState state) throws Exception {
        var paid = new com.spaceconquest.engine.economy.HouseholdEconomyProcessor().process(state, DataModelLoader.loadRaces());
        state = state.toBuilder().empires(paid.empires()).corporations(paid.corporations()).householdAccounts(paid.householdAccounts())
                .commercialHubs(paid.commercialHubs()).marketAccounts(paid.marketAccounts()).industryAccounts(paid.industryAccounts()).build();
        var day = new IndustryMarketProcessor().process(state, paid.paidWorkersByFacility(), paid.wagesByFacility());
        return state.toBuilder().industryAccounts(day.industryAccounts()).corporations(day.corporations()).empires(day.empires()).build();
    }
    private double stock(GameState state, String material) { return state.commercialHubs().getFirst().activeOrders().get(material).supplyKg(); }
}
