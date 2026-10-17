package com.spaceconquest.control;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.macrostructure.*;
import com.spaceconquest.engine.technology.*;
import com.spaceconquest.engine.ship.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AIShipyardCapabilityTest {
    @TempDir Path directory;
    private ShipDesign advanced(ShipDesign source, int complexity, List<String> modules) {
        return new ShipDesign(source.id(), source.name(), source.ownerEntityId(), source.role(), source.hullMaterialId(), modules,
                source.armorMaterialId(), source.armorThicknessCm(), source.totalDryMassKg(), source.maxCargoMassKg(), source.fuelCapacityKg(),
                source.powerBalanceKw(), source.calculatedStructuralIntegrity(), source.minLaunchThrustRequiredN(), source.totalThrustN(),
                source.isValidForLaunch(), source.isProprietaryCorporateDesign(), new ShipManufacturingProfile(1, complexity), source.powerProfile(), source.sensorProfile(), source.combatModules());
    }
    private GameState world(String provider, boolean pending) throws Exception {
        var state = new AIShipyardInvestmentTest().world(provider); String designId = provider.equals("owner") ? "design" : "corporate-design";
        state = state.withShipDesigns(state.shipDesigns().stream().map(design -> design.id().equals(designId) ? advanced(design, 8, design.equippedModuleIds()) : design).toList());
        return pending ? state : state.withShipConstructionOrders(List.of());
    }
    private RequestAIShipyardCapabilityCommand command(String provider) {
        return new RequestAIShipyardCapabilityCommand(provider, provider.equals("owner") ? "design" : "corporate-design", "a", "port-a");
    }
    @Test void publicUpgradePersistsItsWaitingShipAndActuallyUnlocksPaidConstruction() throws Exception {
        var state = world("owner", false); var command = command("owner"); assertFalse(new QueueShipBuildCommand("owner", "design", "a").validate(state));
        assertTrue(command.validate(state)); assertEquals(List.of(command), new ShipyardInvestmentPlanner().plan(state, state.empires().getFirst()));
        var queue = new CommandQueue(); queue.submit(command); queue.submit(command); var ordered = queue.drainAndExecute(state);
        assertEquals(1, ordered.constructionProjects().size()); assertEquals(1, ordered.shipConstructionOrders().size());
        assertEquals(state.shipDesigns(), ordered.shipDesigns()); assertEquals(state.orbitalStations(), ordered.orbitalStations());
        assertEquals(state.empires(), ordered.empires()); assertEquals(state.commercialHubs(), ordered.commercialHubs());
        assertTrue(ShipyardDiagnostics.orders(ordered, "owner").getFirst().status().contains("needs 8, yard supports 7"));
        var blocked = new ShipConstructionProcessor().process(ordered); assertEquals(0, blocked.shipConstructionOrders().getFirst().accumulatedWorkHours());
        assertEquals(7, ShipManufacturingCapacity.forYard(blocked, "owner", "a", "port-a")); assertTrue(blocked.fleets().isEmpty());
        var engine = new SpaceConquestEngine(42); engine.applyGameState(ordered); engine.stepTurn();
        var partlyBuilt = engine.getGameState(); assertFalse(partlyBuilt.constructionProjects().isEmpty()); assertTrue(partlyBuilt.fleets().isEmpty());
        var file = directory.resolve("capability.scsave").toFile(); var saves = new SaveGameManager(directory);
        saves.save(file, partlyBuilt, 1, GameClock.START_TIME.plusDays(partlyBuilt.turn()).toString()); var restored = saves.load(file).toGameState(partlyBuilt.turn(), partlyBuilt.status());
        assertSame(restored, command.apply(restored)); assertTrue(new ShipyardInvestmentPlanner().plan(restored, restored.empires().getFirst()).isEmpty());
        engine.applyGameState(restored);
        for (int day = 0; day < 12 && !engine.getGameState().shipConstructionOrders().isEmpty(); day++) engine.stepTurn();
        var built = engine.getGameState(); assertTrue(built.constructionProjects().isEmpty()); assertTrue(built.shipConstructionOrders().isEmpty());
        assertEquals(10, ShipManufacturingCapacity.forYard(built, "owner", "a", "port-a")); assertEquals(1, built.fleets().size());
        assertEquals("owner", built.fleets().getFirst().ownerEntityId()); assertTrue(EconomicOwnership.cash(built, "owner") < EconomicOwnership.cash(state, "owner"));
        assertEquals(22500, state.commercialHubs().getFirst().activeOrders().get("refined_iron").supplyKg()
                - built.commercialHubs().getFirst().activeOrders().get("refined_iron").supplyKg(), .001);
    }
    @Test void profitableCorporateContractKeepsItsCustomerAndRateThroughPaidUpgrade() throws Exception {
        var state = world("yard-corp", true); var command = command("yard-corp"); assertTrue(command.validate(state));
        var order = state.shipConstructionOrders().getFirst(); var staged = command.apply(state);
        assertEquals(state.shipConstructionOrders(), staged.shipConstructionOrders()); assertEquals(order.yardContract(), staged.shipConstructionOrders().getFirst().yardContract());
        var engine = new SpaceConquestEngine(42); engine.applyGameState(staged);
        for (int day = 0; day < 25 && !engine.getGameState().shipConstructionOrders().isEmpty(); day++) engine.stepTurn();
        var built = engine.getGameState(); assertTrue(built.shipConstructionOrders().isEmpty()); assertEquals("corp", built.fleets().getFirst().ownerEntityId());
        assertEquals(800000, built.shipyardLedger().history().stream().filter(row -> row.orderId().equals(order.id())).mapToDouble(ShipyardReceipt::feeCredits).sum(), .001);
        assertTrue(EconomicOwnership.cash(built, "yard-corp") > EconomicOwnership.cash(state, "yard-corp"));
        assertEquals(10, ShipManufacturingCapacity.forYard(built, "yard-corp", "a", "port-a"));
    }
    @Test void missingResearchResourcesAndDemandDoNotBecomeCapabilityInvestments() throws Exception {
        var state = world("owner", false); var command = command("owner"); assertTrue(command.validate(state));
        var home = state.empires().getFirst();
        var noResearch = state.withEmpires(List.of(new Empire(home.id(), home.name(), home.raceId(), home.societyStructure(), home.treasuryCredits(), home.corporateTaxRate(),
                home.controlledSystemIds(), home.ministries(), home.systemGovernorAssignments(), List.of("space_stations"), home.activeShipDesignIds())));
        assertFalse(command.validate(noResearch)); assertSame(noResearch, command.apply(noResearch));
        assertFalse(command.validate(state.withCommercialHubs(List.of()))); assertFalse(command.validate(state.toBuilder().householdAccounts(List.of()).build()));
        var noDemand = state.toBuilder().industrialFacilities(List.of()).fleets(List.of(new Fleet("existing", "Existing carrier", "owner", "a", "", 0, 0, 0, false, "PASSIVE",
                List.of(new ShipInstance("existing-ship", "design", "owner", 100, 0, 0, Map.of()))))).build();
        assertFalse(command.validate(noDemand));
        var tooComplex = state.withShipDesigns(state.shipDesigns().stream().map(design -> design.id().equals("design") ? advanced(design, 11, design.equippedModuleIds()) : design).toList());
        assertFalse(command.validate(tooComplex)); assertFalse(new RequestAIShipyardCapabilityCommand("corp", "design", "a", "port-a").validate(state));
        var corporate = world("yard-corp", true); var order = corporate.shipConstructionOrders().getFirst();
        var cheap = new ShipConstructionOrder(order.id(), order.ownerEntityId(), order.designId(), order.systemId(), order.yardBodyId(), 0, order.requiredWorkHours(),
                order.requiredMaterialsKg(), Map.of(), new ShipyardContract("yard-corp", 2, 0));
        assertFalse(command("yard-corp").validate(corporate.withShipConstructionOrders(List.of(cheap))));
    }
    @Test void surfaceCapabilityUpgradeUsesApplicationTierLimitsAndPaidExpansion() throws Exception {
        var state = world("owner", true); var facility = new IndustrialFacility("surface-capability", "planet-a", "surface_shipyard", "owner", "PUBLIC_STATE", 1, 100, "industrial_worker", false, 0);
        var facilities = new ArrayList<>(state.industrialFacilities()); facilities.add(facility);
        var hub = state.commercialHubs().getFirst(); var surfaceHub = new CommercialHub("surface-stock", "planet-a", hub.transactionTariffRate(), hub.storageCapacityKg(),
                hub.currentStoredWeightKg(), hub.logisticsRangeUnits(), hub.activeOrders()); var hubs = new ArrayList<>(state.commercialHubs()); hubs.add(surfaceHub);
        var account = new IndustryAccount(facility.id(), Map.of(), Map.of(), Map.of(), 0, 2500, 0, 0, 0, 0, 100000, false, 0, 0, 100);
        var order = state.shipConstructionOrders().getFirst();
        state = state.toBuilder().industrialFacilities(facilities).industryAccounts(List.of(account)).commercialHubs(hubs)
                .shipConstructionOrders(List.of(new ShipConstructionOrder(order.id(), order.ownerEntityId(), order.designId(), "a", "planet-a", 0, 4000, order.requiredMaterialsKg(), Map.of()))).build();
        var command = new RequestAIShipyardCapabilityCommand("owner", "design", "a", "planet-a"); assertTrue(command.validate(state));
        var staged = command.apply(state); assertEquals(4, staged.expansionProjects().getFirst().targetTier()); assertSame(staged, command.apply(staged));
        var processor = new IndustryProcessor();
        for (int day = 0; day < 6; day++) staged = processor.processIndustrialProduction(staged, Map.of(facility.id(), 100));
        assertTrue(staged.expansionProjects().isEmpty()); assertEquals(8, ShipManufacturingCapacity.forYard(staged, "owner", "a", "planet-a"));
        assertTrue(staged.shipConstructionOrders().getFirst().accumulatedWorkHours() > 0);
        assertTrue(EconomicOwnership.cash(staged, "owner") < EconomicOwnership.cash(state, "owner"));
    }
    @Test void aResearchedComplexityIncreaseStagesARevisionWithoutChangingItsSource() throws Exception {
        var state = new AIShipyardInvestmentTest().world("owner").withShipConstructionOrders(List.of()); var home = state.empires().getFirst();
        var research = new ArrayList<>(home.unlockedTechIds()); research.add("rocket_engines");
        state = state.withEmpires(List.of(new Empire(home.id(), home.name(), home.raceId(), home.societyStructure(), home.treasuryCredits(), home.corporateTaxRate(),
                home.controlledSystemIds(), home.ministries(), home.systemGovernorAssignments(), research, home.activeShipDesignIds())));
        var evaluation = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification("research-source", "Researched combat ship", "owner", ShipRole.COMBAT_SHIP,
                "steel", ShipComponentCatalog.workbenchModules("mod_chemical_rocket", false), "steel", .5));
        assertTrue(evaluation.valid(), evaluation.errors().toString()); var original = evaluation.design();
        state = state.toBuilder().shipDesigns(List.of(original)).diplomaticRelations(List.of(new DiplomaticRelation("owner", "enemy", "TOTAL_WAR", 0)))
                .applicationOptimizations(List.of(new ApplicationOptimization("owner", "rocket_engines", "TEST",
                        new ResearchVarianceResult("TEST", 1, 1, 6)))).build();
        var command = new RequestAIShipyardCapabilityCommand("owner", original.id(), "a", "port-a"); assertTrue(command.validate(state));
        var staged = command.apply(state); assertEquals(original, staged.shipDesigns().getFirst());
        var order = staged.shipConstructionOrders().getFirst(); assertNotEquals(original.id(), order.designId());
        var revision = staged.shipDesigns().stream().filter(design -> order.designId().equals(design.id())).findFirst().orElseThrow();
        assertEquals(8, ShipManufacturingCapacity.requiredComplexity(revision)); assertFalse(ShipApplicationProduction.developmentChanged(staged, revision));
        assertEquals(ShipConstructionRequirements.estimate(revision).materialsKg(), order.requiredMaterialsKg());
    }
    @Test void existingCapableYardsAndInsufficientPowerPreventRedundantInvestment() throws Exception {
        var state = world("owner", false); var command = command("owner"); var yard = state.orbitalStations().getFirst();
        var powerless = new OrbitalStation(yard.id(), yard.name(), yard.systemId(), yard.planetOrbitId(), yard.ownerEntityId(), yard.ownershipType(), yard.totalSlots(),
                yard.modules(), yard.storedCargoKg(), 0, yard.currentPowerDemandKw(), yard.currentShieldHealth(), yard.maxShieldHealth(), yard.currentHullHealth(),
                yard.maxHullHealth(), yard.armorMaterialId(), yard.armorThicknessCm(), true, yard.populations());
        assertFalse(command.validate(state.toBuilder().orbitalStations(List.of(powerless, state.orbitalStations().getLast())).build()));
        var alternate = state.orbitalStations().getLast(); var modules = new ArrayList<>(alternate.modules());
        modules.add(ShipyardTechnology.yard(StationModule.TYPE_CAPITAL_SLIPWAY).module(StationModule.TYPE_CAPITAL_SLIPWAY).withPayroll(300, 15000));
        alternate = new OrbitalStation(alternate.id(), alternate.name(), alternate.systemId(), alternate.planetOrbitId(), "owner", OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                300, modules, alternate.storedCargoKg(), 1000, 300, 0, 0, 100, 100, "steel", 0, true, alternate.populations());
        assertFalse(command.validate(state.toBuilder().orbitalStations(List.of(yard, alternate)).build()));
    }
}
