package com.spaceconquest.control;

import com.spaceconquest.control.ai.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.economy.*;
import com.spaceconquest.engine.industry.*;
import com.spaceconquest.engine.logistics.*;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;
import com.spaceconquest.engine.ship.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AIFacilityLogisticsTest {
    @TempDir Path directory;
    GameState world() throws Exception {
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1000000, 0, List.of("a", "b"), List.of(), Map.of(),
                List.of("rocketry", "electricity", "industrial_production", "ship_cannons", "cannon_ammunition_mfg", "combustion_power"), List.of());
        var profile = new ShipPowerProfile(0, 100, 0, 0, 0, 0, 5000, 0, 1, 2, 0, .65,
                Map.of("rp1", new ShipPowerProfile.Fuel("rp1_kerosene", "liquid_oxygen", .28, 1.008)));
        var design = new ShipDesign("design", "Freighter", "owner", ShipRole.CARGO_TRANSPORT, "steel", List.of("mod_chemical_rocket"),
                "steel", 0, 1000, 1000, 1000, 100, 1, 0, 2000, false, false, ShipManufacturingProfile.baseline(), profile);
        var power = new ShipPowerState(Map.of("rp1_kerosene", 1120.0, "liquid_oxygen", 2880.0), "rp1", "uranium", 0, true, 1, 1, 0, 0, 0, 0, 0);
        var ship = new ShipInstance("ship", design.id(), "owner", 100, 0, 1000, Map.of()).withPowerState(power);
        var fleet = new Fleet("fleet", "Freighter", "owner", "a", "", 0, 0, 0, false, "PASSIVE", List.of(ship), FleetLocation.at(FleetLocation.Site.docked("port-a")));
        var source = port("source", "port-a", 10000, 20, 2);
        var destination = port("destination", "port-b", 0, 4, 10).withStockTargets(
                Map.of("cannon_round", new com.spaceconquest.engine.market.HubStockTarget(150, 150, true)));
        var state = GameState.builder().empires(List.of(owner)).shipDesigns(List.of(design)).fleets(List.of(fleet))
                .solarSystems(List.of(new SolarSystem("a", "A", "", 0, 0, 0, 1, 1, "Yellow", List.of(planet("planet-a")), List.of()),
                        new SolarSystem("b", "B", "", 1e6 / InterstellarTravel.METERS_PER_LIGHT_YEAR, 0, 0, 1, 1, "Yellow", List.of(planet("planet-b")), List.of())))
                .orbitalStations(List.of(station("port-a", "a"), station("port-b", "b").withPopulations(List.of(new Population("human", Map.of(25, 20000L))))))
                .systemEconomies(List.of(SystemEconomy.createDefault("a", "owner", 1000), SystemEconomy.createDefault("b", "owner", 1000))).commercialHubs(List.of(source, destination)).marketAccounts(List.of(new MarketAccount("source", 20000), new MarketAccount("destination", 20000)))
                .industrialFacilities(List.of(new IndustrialFacility("factory", "port-b", "cannon_ammunition_mfg", "owner", IndustrialFacility.PUBLIC_STATE, 1, 1, "industrial_worker", false, 0),
                        new IndustrialFacility("generator", "port-b", "combustion_power", "owner", IndustrialFacility.PUBLIC_STATE, 1, 100, "technician", false, 0)))
                // The isolated factory pays the local plant's full tariff while waiting for its carrier.
                .industryAccounts(List.of(IndustryAccount.empty("factory").withOperatingCash(100000), IndustryAccount.empty("generator").withOperatingCash(100000))).build();
        var paid = new HouseholdEconomyProcessor().process(state, DataModelLoader.loadRaces());
        return state.toBuilder().empires(paid.empires()).householdAccounts(paid.householdAccounts()).commercialHubs(paid.commercialHubs()).marketAccounts(paid.marketAccounts())
                .industryAccounts(paid.industryAccounts().stream().map(account -> account.withPaidWorkers(paid.paidWorkersByFacility().getOrDefault(account.facilityId(), 0))).toList()).build();
    }
    private Planet planet(String id) {
        return new Planet(id, id, "", 1, 1, 1, 0, 100, "Terrestrial", "Nitrogen", true, .5, List.of(), List.of(), List.of(new Population("human", Map.of(25, 1000L))));
    }
    private OrbitalStation station(String id, String system) {
        var control = new StationModule(id + "-control", "Control", StationModule.TYPE_CONTROL, 1, 100, 0, 0, Map.of(), "technician", 0, true);
        return new OrbitalStation(id, id, system, "", "owner", "PUBLIC_STATE", 10, List.of(control), Map.of(), 0, 0, 0, 0, 100, 100, "steel", 0, true);
    }
    private CommercialHub port(String id, String body, double steel, double demand, double price) {
        var orders = new HashMap<String, MarketOrder>(); orders.put("steel", new MarketOrder("steel", steel, demand, price, 1));
        for (String fuel : List.of("rp1_kerosene", "liquid_oxygen", "hydrocarbons")) orders.put(fuel, new MarketOrder(fuel, 10000, 0, 1, 0));
        orders.put("cannon_round", new MarketOrder("cannon_round", 0, 5, 100, 1));
        orders.put("food_matrix", new MarketOrder("food_matrix", 1000000, 0, .01, 0)); orders.put("purified_water", new MarketOrder("purified_water", 1000000, 0, .01, 0));
        return new CommercialHub(id, body, 0, 10000000, orders.values().stream().mapToDouble(MarketOrder::supplyKg).sum(), 10, orders);
    }
    private RequestAIFacilitySupplyCommand request(GameState state) {
        var planner = new EmpireLogisticsPlanner(); var owner = state.empires().getFirst();
        var first = planner.plan(state, owner); assertTrue(first.commands().isEmpty()); assertEquals(Set.of("fleet"), first.reservedFleetIds());
        assertTrue(planner.plan(state, owner).commands().isEmpty());
        return (RequestAIFacilitySupplyCommand) planner.plan(state.withTurn(state.turn() + 1), owner).commands().getFirst();
    }
    private double steel(GameState state) {
        return state.commercialHubs().stream().mapToDouble(hub -> hub.activeOrders().getOrDefault("steel", new MarketOrder("steel", 0, 0, 0, 0)).supplyKg()).sum()
                + state.fleets().stream().flatMap(fleet -> fleet.ships().stream()).mapToDouble(ship -> ship.storedCargoKg().getOrDefault("steel", 0.0)).sum();
    }
    @Test void paidInterSystemSupplySurvivesReloadReturnsItsShipAndRestoresActualProduction() throws Exception {
        var opening = world(); var command = request(opening); var initial = opening.withTurn(1); var queue = new CommandQueue(); queue.submit(command); queue.submit(command);
        var dispatched = queue.drainAndExecute(initial); assertEquals(1, dispatched.tradeRoutes().size());
        var route = dispatched.tradeRoutes().getFirst(); assertFalse(route.isActive()); assertTrue(route.carrierCommitted()); assertEquals(TradeRoute.DELIVERING, route.phase());
        assertTrue(route.onboardKg() >= 4); assertEquals(steel(initial), steel(dispatched), 1e-6);
        assertTrue(dispatched.empires().getFirst().treasuryCredits() < initial.empires().getFirst().treasuryCredits());
        assertTrue(dispatched.commercialHubs().getFirst().activeOrders().get("steel").supplyKg() >= 60);
        assertTrue(FacilitySupplyNeeds.shortages(dispatched, dispatched.empires().getFirst(), LogisticsPolicy.current()).isEmpty());
        assertFalse(new CreateTradeRouteCommand("owner", "Competing", "source", "destination", "steel", 10, 0, 100, List.of("ship")).validate(dispatched));
        var file = directory.resolve("supply.scsave").toFile(); var saves = new SaveGameManager(directory); saves.save(file, dispatched, 1, GameClock.START_TIME.plusDays(1).toString());
        var engine = new SpaceConquestEngine(42); engine.applyGameState(saves.load(file).toGameState(dispatched.turn(), dispatched.status()));
        double produced = 0;
        var report = new StringBuilder("Day,phase,onboard,moved,system,inTransit,crossing,fuel,steel,produced,paidWorkers,powerCosts,operatingCash,generatedKwh,residents,workingAge\n");
        for (int day = 0; day < 25; day++) {
            engine.stepTurn(); var state = engine.getGameState();
            produced += state.industryAccounts().stream().filter(account -> account.facilityId().equals("factory")).mapToDouble(account -> account.producedKg().getOrDefault("cannon_round", 0.0)).sum();
            var current = state.tradeRoutes().getFirst(); var carrier = state.fleets().getFirst();
            var factory = state.industryAccounts().stream().filter(account -> account.facilityId().equals("factory")).findFirst().orElseThrow();
            var generator = state.industryAccounts().stream().filter(account -> account.facilityId().equals("generator")).findFirst().orElseThrow();
            report.append(day + "," + current.phase() + "," + current.onboardKg() + "," + current.totalVolumeMovedKg() + "," + carrier.currentSystemId()
                    + "," + carrier.location().inTransit() + "," + carrier.hasInterstellarOrder() + "," + carrier.ships().getFirst().currentFuelKg()
                    + "," + state.commercialHubs().stream().filter(hub -> hub.id().equals("destination")).findFirst().orElseThrow().activeOrders().get("steel").supplyKg() + "," + produced
                    + "," + factory.paidWorkers() + "," + factory.powerCostsCredits() + "," + factory.operatingCashCredits() + "," + generator.generatedKwh()
                    + "," + state.orbitalStations().getLast().residentCount() + "," + state.householdAccounts().stream().mapToLong(value -> value.employment().workingAge()).sum() + "\n");
        }
        java.nio.file.Files.writeString(Path.of("target/ai-facility-logistics.csv"), report);
        var end = engine.getGameState(); assertTrue(end.tradeRoutes().getFirst().totalVolumeMovedKg() > 0, end.tradeRoutes().getFirst().status()); assertTrue(produced > 0, "Paid factory output");
        var recipe = IndustryRecipeCatalog.find("cannon_ammunition_mfg");
        assertEquals(steel(initial) - produced * recipe.inputsKg().get("steel") / recipe.outputsKg().get("cannon_round"), steel(end), .001);
        assertEquals(TradeRoute.LOADING, end.tradeRoutes().getFirst().phase()); assertFalse(end.tradeRoutes().getFirst().carrierCommitted());
        assertTrue(FleetPositioning.atHub(end, end.fleets().getFirst(), end.commercialHubs().stream().filter(hub -> hub.id().equals("source")).findFirst().orElseThrow()));
    }
    @Test void staleStockFundingControlAndInboundCommitmentsRejectWithoutPurchases() throws Exception {
        var state = world(); var command = request(state); state = state.withTurn(1); var owner = state.empires().getFirst();
        var noMoney = state.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), 0, 0, owner.controlledSystemIds(), List.of(), Map.of(), owner.unlockedTechIds(), List.of())));
        assertFalse(command.validate(noMoney)); assertSame(noMoney, command.apply(noMoney));
        var noControl = state.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), owner.treasuryCredits(), 0, List.of("a"), List.of(), Map.of(), owner.unlockedTechIds(), List.of())));
        assertSame(noControl, command.apply(noControl));
        var source = state.commercialHubs().getFirst(); var orders = new HashMap<>(source.activeOrders()); orders.put("steel", new MarketOrder("steel", 60, 20, 2, 0));
        var depleted = state.withCommercialHubs(List.of(source.withInventory(1, source.currentStoredWeightKg() - 9940, orders), state.commercialHubs().getLast()));
        assertSame(depleted, command.apply(depleted));
        var funded = command.apply(state); assertSame(funded, command.apply(funded));
        assertTrue(new EmpireLogisticsPlanner().plan(funded, funded.empires().getFirst()).commands().isEmpty());
    }
    @Test void controllerReservesTheIdleTraderWhileObservingAndThenDispatchesSupply() throws Exception {
        var state = world(); var queue = new CommandQueue(); var controller = new EmpireAIController("owner", queue);
        controller.onGameStateUpdate(state); var first = queue.drainAndExecute(state); assertTrue(first.tradeRoutes().isEmpty());
        controller.onGameStateUpdate(first.withTurn(1)); var second = queue.drainAndExecute(first.withTurn(1));
        assertEquals(1, second.tradeRoutes().size()); assertEquals("AI facility supply", second.tradeRoutes().getFirst().name());
    }
    private GameState stock(GameState state, String hubId, String material, double supply) {
        return state.withCommercialHubs(state.commercialHubs().stream().map(hub -> {
            if (!hubId.equals(hub.id())) return hub;
            var orders = new HashMap<>(hub.activeOrders()); var old = orders.get(material);
            orders.put(material, new MarketOrder(material, supply, old.demandKg(), old.pricePerKg(), old.shortcomingScore()));
            return hub.withInventory(state.turn(), hub.currentStoredWeightKg() + supply - old.supplyKg(), orders);
        }).toList());
    }
    @Test void fuelShortagesTakePriorityAndPolicyControlsRequestedStock() throws Exception {
        var state = stock(world(), "destination", "hydrocarbons", 0); var owner = state.empires().getFirst();
        var needs = FacilitySupplyNeeds.shortages(state, owner, LogisticsPolicy.current());
        assertTrue(needs.getFirst().generationFuel()); assertEquals("hydrocarbons", needs.getFirst().materialId());
        var tuned = new LogisticsPolicy(2, 2, 5, 10, 1, 50000, 10000, 8, 30);
        var steel = FacilitySupplyNeeds.shortages(state, owner, tuned).stream().filter(need -> need.materialId().equals("steel")).findFirst().orElseThrow();
        assertEquals(steel.dailyKg() * tuned.targetDays(), steel.targetKg(), .0001);
        var explicit = new SetHubStockTargetCommand("owner", "destination", "steel", 2, 7).apply(state);
        assertEquals(7, FacilitySupplyNeeds.shortages(explicit, owner, tuned).stream().filter(need -> need.materialId().equals("steel")).findFirst().orElseThrow().targetKg());
        var command = request(state); assertEquals("hydrocarbons", command.materialId());
        var supplied = command.apply(state.withTurn(1)); assertEquals(1, supplied.tradeRoutes().size());
        assertTrue(supplied.tradeRoutes().getFirst().onboardKg() > 0);
    }
    @Test void missingTravelFuelAndUnavailableCarrierLeaveStocksAndTargetsUntouched() throws Exception {
        var state = world(); var command = request(state); state = state.withTurn(1);
        var dry = stock(stock(stock(state, "destination", "rp1_kerosene", 0), "destination", "liquid_oxygen", 0), "destination", "hydrocarbons", 0);
        dry = stock(stock(dry, "source", "rp1_kerosene", 0), "source", "liquid_oxygen", 0);
        var emptyPower = new ShipPowerState(Map.of(), "rp1", "uranium", 0, true, 1, 1, 0, 0, 0, 0, 0);
        dry = dry.withFleets(List.of(dry.fleets().getFirst().withShips(List.of(dry.fleets().getFirst().ships().getFirst().withPowerState(emptyPower)))));
        assertSame(dry, command.apply(dry));
        assertEquals(state.commercialHubs().getLast().stockTargets(), dry.commercialHubs().getLast().stockTargets());
        var fleet = state.fleets().getFirst();
        var ship = fleet.ships().getFirst();
        var occupied = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(), ship.currentFuelKg(),
                Map.of("steel", 1.0), ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState(), ship.moduleDamage());
        var loaded = state.withFleets(List.of(fleet.withShips(List.of(occupied))));
        assertSame(loaded, command.apply(loaded));
        var emptyStation = state.toBuilder().orbitalStations(state.orbitalStations().stream().map(station -> station.withPopulations(List.of())).toList()).build();
        assertTrue(FacilitySupplyNeeds.shortages(emptyStation, emptyStation.empires().getFirst(), LogisticsPolicy.current()).isEmpty());
    }
    @Test void gapsAndResolvedShortagesRestartObservation() throws Exception {
        var state = world(); var planner = new EmpireLogisticsPlanner(); var owner = state.empires().getFirst();
        assertTrue(planner.plan(state, owner).commands().isEmpty());
        assertTrue(planner.plan(state.withTurn(5), owner).commands().isEmpty());
        assertEquals(1, planner.plan(state.withTurn(6), owner).commands().size());
        assertTrue(planner.plan(stock(state.withTurn(7), "destination", "steel", 100), owner).commands().isEmpty());
        assertTrue(planner.plan(state.withTurn(8), owner).commands().isEmpty());
        assertEquals(1, planner.plan(state.withTurn(9), owner).commands().size());
    }
}
