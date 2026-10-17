package com.spaceconquest.control;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.macrostructure.*;
import com.spaceconquest.engine.ship.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AIShipyardInvestmentTest {
    @TempDir Path directory;
    GameState world(String provider) throws Exception {
        var state = new SharedShipyardTest().world(provider); var empire = state.empires().getFirst();
        var research = new ArrayList<>(empire.unlockedTechIds()); research.add("space_stations");
        state = state.withEmpires(List.of(new Empire(empire.id(), empire.name(), empire.raceId(), empire.societyStructure(), 1000000,
                .2, empire.controlledSystemIds(), empire.ministries(), empire.systemGovernorAssignments(), research, empire.activeShipDesignIds())));
        var station = state.orbitalStations().getFirst(); var modules = new ArrayList<>(station.modules());
        modules.add(new StationModule("yard-power", "Power", StationModule.TYPE_POWER, 1, 100, 0, 2000, Map.of(), "technician", 0, true));
        station = new OrbitalStation(station.id(), station.name(), station.systemId(), station.planetOrbitId(), station.ownerEntityId(), station.ownershipType(),
                station.totalSlots(), modules, station.storedCargoKg(), 2000, 0, station.currentShieldHealth(), station.maxShieldHealth(),
                station.currentHullHealth(), station.maxHullHealth(), station.armorMaterialId(), station.armorThicknessCm(), true, station.populations());
        var hub = state.commercialHubs().getFirst(); var stock = new HashMap<>(hub.activeOrders());
        for (String material : List.of("refined_iron", "refined_copper", "refined_aluminum", "silicon", "steel"))
            stock.put(material, new MarketOrder(material, 1000000, 0, 1, 0));
        var accounts = new ArrayList<>(state.householdAccounts());
        accounts.add(new HouseholdAccount("port-a", "a", "owner", "human", "engineer", 1000, 0, 5000, 0, 0, 0,
                Map.of(), 1, 1, 0, 0, HouseholdWellbeing.healthy(), new HouseholdEmployment(1000, 0, 100, 0)));
        state = state.toBuilder().orbitalStations(List.of(station, state.orbitalStations().getLast())).householdAccounts(accounts)
                .commercialHubs(List.of(hub.withInventory(0, stock.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), stock), state.commercialHubs().getLast())).build();
        String customer = provider.equals("owner") ? "owner" : "corp"; String design = provider.equals("owner") ? "design" : "corporate-design";
        var queued = new QueueShipBuildCommand(customer, design, "a").apply(state); var order = queued.shipConstructionOrders().getFirst();
        return queued.withShipConstructionOrders(List.of(new ShipConstructionOrder(order.id(), customer, design, "a", "port-a", 0, 4000,
                Map.of("steel", 10000.0), Map.of(), provider.equals("owner") ? null : new ShipyardContract(provider, 200, 0))));
    }
    private GameState busy(String provider) throws Exception {
        var state = world(provider).withTurn(4); var ledger = state.shipyardLedger();
        for (int day = 1; day <= 4; day++) ledger = ledger.append(new ShipyardReceipt(day, "work", "corp", provider, "a", "port-a", 100, 0, 0, false, "Paid construction"));
        return state.toBuilder().shipyardLedger(ledger).build();
    }
    @Test void normalPaidExpansionIncreasesWorkAndSurvivesSavedPendingProject() throws Exception {
        for (String provider : List.of("owner", "yard-corp")) {
            var engine = new SpaceConquestEngine(42); engine.applyGameState(world(provider));
            for (int day = 0; day < 4; day++) engine.stepTurn();
            var before = engine.getGameState(); var command = new RequestAIShipyardExpansionCommand(provider, "a", "port-a");
            var baseline = new SpaceConquestEngine(42); baseline.applyGameState(before);
            assertTrue(command.validate(before), () -> "Capacity " + ShipyardWorkCapacity.forYard(before, provider, "a", "port-a")
                    + "; households " + before.householdAccounts().stream().filter(a -> a.bodyId().equals("port-a") && a.professionId().equals("engineer")).toList()
                    + "; history " + before.shipyardLedger().history());
            var planned = new ShipyardInvestmentPlanner().plan(before, before.empires().getFirst());
            assertEquals(List.of(command), planned);
            var queue = new CommandQueue(); queue.submit(command); queue.submit(command); var ordered = queue.drainAndExecute(before);
            assertEquals(1, ordered.constructionProjects().size()); assertEquals(before.corporations(), ordered.corporations());
            assertEquals(before.empires(), ordered.empires()); assertEquals(before.commercialHubs(), ordered.commercialHubs());
            var file = directory.resolve(provider + ".scsave").toFile(); var saves = new SaveGameManager(directory);
            saves.save(file, ordered, 1, GameClock.START_TIME.plusDays(ordered.turn()).toString()); var restored = saves.load(file).toGameState(ordered.turn(), ordered.status());
            assertSame(restored, command.apply(restored)); assertTrue(new ShipyardInvestmentPlanner().plan(restored, restored.empires().getFirst()).isEmpty());
            engine.applyGameState(restored);
            for (int day = 0; day < 4; day++) { engine.stepTurn(); baseline.stepTurn(); }
            var expanded = engine.getGameState(); assertTrue(expanded.constructionProjects().isEmpty());
            assertEquals(7500, baseline.getGameState().commercialHubs().getFirst().activeOrders().get("refined_iron").supplyKg()
                    - expanded.commercialHubs().getFirst().activeOrders().get("refined_iron").supplyKg(), .001);
            assertEquals(500, baseline.getGameState().commercialHubs().getFirst().activeOrders().get("silicon").supplyKg()
                    - expanded.commercialHubs().getFirst().activeOrders().get("silicon").supplyKg(), .001);
            assertEquals(200, ShipyardWorkCapacity.forYard(expanded, provider, "a", "port-a").workPerDay(), .001);
            double actualWork = expanded.shipyardLedger().history().stream().filter(row -> row.turn() == expanded.turn() && row.yardId().equals("port-a")).mapToDouble(ShipyardReceipt::workUnits).sum();
            assertEquals(200, actualWork, .001); assertTrue(EconomicOwnership.cash(expanded, provider) < EconomicOwnership.cash(before, provider)
                    || provider.equals("yard-corp") && expanded.shipyardLedger().cumulativeProfit().get(provider) > before.shipyardLedger().cumulativeProfit().get(provider));
            assertTrue(expanded.orbitalStations().getFirst().modules().stream().anyMatch(module -> !module.id().equals("yard-module")
                    && module.type().equals(StationModule.TYPE_SHIPYARD_GRID) && module.paidWorkers() == 100 && module.dailyWageCostsCredits() > 0));
        }
    }
    @Test void realBottlenecksAndUnprofitableContractsPreventExpansion() throws Exception {
        var state = busy("yard-corp"); var command = new RequestAIShipyardExpansionCommand("yard-corp", "a", "port-a"); assertTrue(command.validate(state));
        assertFalse(command.validate(state.withCommercialHubs(List.of())));
        assertFalse(command.validate(state.toBuilder().householdAccounts(List.of()).build()));
        assertFalse(command.validate(state.toBuilder().shipyardLedger(ShipyardLedger.empty()).build()));
        var corp = state.corporations().stream().filter(item -> item.id().equals("yard-corp")).findFirst().orElseThrow();
        assertFalse(command.validate(state.withCorporations(state.corporations().stream().map(item -> item.id().equals(corp.id())
                ? new Corporation(corp.id(), corp.name(), corp.empireId(), corp.headquartersEntityId(), corp.marketOrientation(), 100, corp.ownedFacilityIds(), corp.ownedShipIds(), corp.claimedVeinIds()) : item).toList())));
        var order = state.shipConstructionOrders().getFirst();
        var cheap = new ShipConstructionOrder(order.id(), order.ownerEntityId(), order.designId(), "a", "port-a", 0, 4000, order.requiredMaterialsKg(), Map.of(), new ShipyardContract("yard-corp", 2, 0));
        assertFalse(command.validate(state.withShipConstructionOrders(List.of(cheap))));
        var missingComponent = new ShipConstructionOrder(order.id(), order.ownerEntityId(), order.designId(), "a", "port-a", 0, 4000,
                Map.of("steel", 10000.0, "missing-final-component", 1.0), Map.of(), order.yardContract());
        assertFalse(command.validate(state.withShipConstructionOrders(List.of(missingComponent))));
        // The historical yard can be busy but the current customer's cash or stock may have disappeared.
        assertFalse(command.validate(state.withCorporations(state.corporations().stream().map(item -> item.id().equals("corp")
                ? new Corporation(item.id(), item.name(), item.empireId(), item.headquartersEntityId(), item.marketOrientation(), 0, item.ownedFacilityIds(), item.ownedShipIds(), item.claimedVeinIds()) : item).toList())));
    }
    @Test void powerAccessAndStrategicOwnershipAreRevalidated() throws Exception {
        var state = busy("owner"); var command = new RequestAIShipyardExpansionCommand("owner", "a", "port-a"); assertTrue(command.validate(state));
        var unpowered = new MacroStructureProcessor().processOrbitalStation(new OrbitalStation("port-a", "Unpowered", "a", "planet-a", "owner",
                OrbitalStation.OWNERSHIP_PUBLIC_STATE, 300, List.of(state.orbitalStations().getFirst().modules().stream().filter(module -> module.type().equals(StationModule.TYPE_SHIPYARD_GRID)).findFirst().orElseThrow()),
                Map.of(), 0, 0, 0, 0, 100, 100, "steel", 0, true), 0).updatedStation();
        assertFalse(command.validate(state.toBuilder().orbitalStations(List.of(unpowered)).build()));
        var corporate = new QueueShipBuildCommand("corp", "corporate-design", "a").apply(state.withShipConstructionOrders(List.of()));
        var order = corporate.shipConstructionOrders().getFirst();
        corporate = corporate.withShipConstructionOrders(List.of(new ShipConstructionOrder(order.id(), "corp", "corporate-design", "a", "port-a", 0, 4000,
                Map.of("steel", 10000.0), Map.of(), order.yardContract())));
        assertFalse(command.validate(corporate)); assertFalse(new RequestAIShipyardExpansionCommand("corp", "a", "port-a").validate(state));
    }
    @Test void surfaceExpansionUsesPaidMaterialsAndApplicationTierCapacity() throws Exception {
        var state = busy("owner"); var facility = new IndustrialFacility("surface-yard", "planet-a", "surface_shipyard", "owner", "PUBLIC_STATE", 1, 100, "industrial_worker", false, 0);
        var facilities = new ArrayList<>(state.industrialFacilities()); facilities.add(facility);
        var account = new IndustryAccount(facility.id(), Map.of(), Map.of(), Map.of(), 0, 2500, 0, 0, 0, 0, 100000, false, 0, 0, 100);
        var hub = state.commercialHubs().getFirst();
        // Surface projects and ships use a surface hub rather than borrowing orbital market stock.
        var surfaceHub = new CommercialHub(hub.id() + "-surface", "planet-a", hub.transactionTariffRate(), hub.storageCapacityKg(), hub.currentStoredWeightKg(), hub.logisticsRangeUnits(), hub.activeOrders());
        var hubs = new ArrayList<>(state.commercialHubs()); hubs.add(surfaceHub);
        var order = state.shipConstructionOrders().getFirst(); var ledger = state.shipyardLedger();
        for (int day = 1; day <= 4; day++) ledger = ledger.append(new ShipyardReceipt(day, "surface-work", "owner", "owner", "a", "planet-a", 100, 0, 0, false, "Paid construction"));
        state = state.toBuilder().industrialFacilities(facilities).industryAccounts(List.of(account)).commercialHubs(hubs).shipyardLedger(ledger)
                .shipConstructionOrders(List.of(new ShipConstructionOrder(order.id(), "owner", "design", "a", "planet-a", 0, 4000, Map.of("steel", 10000.0), Map.of()))).build();
        var command = new RequestAIShipyardExpansionCommand("owner", "a", "planet-a"); assertTrue(command.validate(state));
        var ordered = command.apply(state); assertEquals(1, ordered.expansionProjects().size()); assertSame(ordered, command.apply(ordered));
        double cash = EconomicOwnership.cash(ordered, "owner");
        var processor = new IndustryProcessor();
        for (int day = 0; day < 2; day++) ordered = processor.processIndustrialProduction(ordered, Map.of(facility.id(), 100));
        assertTrue(ordered.expansionProjects().isEmpty()); assertTrue(EconomicOwnership.cash(ordered, "owner") < cash);
        assertEquals(150, ShipyardWorkCapacity.forYard(ordered, "owner", "a", "planet-a", Map.of(facility.id(), 100)).workPerDay(), .001);
    }
}
