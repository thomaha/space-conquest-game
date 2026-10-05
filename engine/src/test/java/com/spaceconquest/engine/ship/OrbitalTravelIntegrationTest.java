package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.PowerGridState;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class OrbitalTravelIntegrationTest {
    @TempDir Path directory;
    private OrbitalStation port(String id, String planet, double altitude) {
        var slipway = new StationModule("yard-" + id, "Capital yard", StationModule.TYPE_CAPITAL_SLIPWAY,
                4, 10000, 10, 0, Map.of(), "engineer", 0, true);
        return new OrbitalStation(id, id, "sol", planet, "owner", OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                20, List.of(slipway), Map.of(), 100, 10, 0, 0, 100, 100, "steel", 0, true).withParkingAltitudeKm(altitude);
    }
    private GameState funded() throws Exception {
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e6, 0, List.of("sol"), List.of(), Map.of(),
                List.of("rocketry", "hydrolox_propulsion", "nuclear_fission", "electricity", "solar_power", "industrial_production", "space_stations"), List.of());
        Map<String, MarketOrder> stock = new HashMap<>();
        PropulsionCatalog.drive("mod_hydrolox_rocket").propellantMaterials(120000)
                .forEach((id, kg) -> stock.put(id, new MarketOrder(id, kg, 0, 1, 0)));
        var initial = GameState.builder().solarSystems(DataModelLoader.loadSolarSystems()).empires(List.of(owner))
                .orbitalStations(List.of(port("earth-port", "earth", 100000), port("mars-port", "mars", 8000)))
                .commercialHubs(List.of(new CommercialHub("market", "earth-port", 0, 1e6, 120000, 10, stock)))
                .powerGrids(List.of(new PowerGridState("earth-port", 1000, 0, 1000, 2000, 2000, false))).build();
        var result = ShipBlueprintFactory.evaluate(initial, new ShipDesignSpecification("design", "Freighter", "owner",
                ShipRole.CARGO_TRANSPORT, "steel", ChemicalFreighterCatalog.modules("mod_hydrolox_rocket"), "steel", .5));
        assertTrue(result.valid(), result.errors().toString());
        var ship = new ShipInstance("ship", "design", "owner", 100, 0, 0, Map.of("steel", 1250.0, "food_matrix", 1250.0))
                .withPowerState(ShipPowerState.empty());
        var fleet = new Fleet("fleet", "Freighter", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.docked("earth-port")));
        initial = initial.withShipDesigns(List.of(result.design())).withFleets(List.of(fleet));
        var fueled = ShipFueling.refuel(initial, "ship", "earth-port", 120000);
        assertEquals(880000, fueled.empires().getFirst().treasuryCredits(), .00001);
        var charged = ShipBatteryCharging.charge(fueled, "ship", "earth-port", 500 / .9);
        assertEquals(500, charged.fleets().getFirst().ships().getFirst().powerState().batteryChargeKwh(), .00001);
        assertEquals(2000 - 500 / .9, charged.powerGrids().getFirst().currentStoredKwh(), .00001);
        return charged;
    }
    private GameState day(GameState state) {
        var powered = ShipPowerProcessor.advanceDay(state);
        return state.withFleets(new FleetProcessor().processFleetMovements(powered, state.orbitalStations(), List.of()))
                .withTurn(state.turn() + 1);
    }
    private GameState depart(GameState state) {
        var fleet = state.fleets().getFirst();
        var target = FleetLocation.Site.docked("mars-port");
        var preview = OrbitalTravel.preview(state, fleet, target);
        assertNotNull(preview.plan(), preview.problem());
        assertTrue(ShipPowerForecast.ready(ShipPowerForecast.departure(state, fleet, target, preview.plan(), null)));
        return state.withFleets(List.of(LocalTravel.depart(fleet, target, preview.plan())));
    }

    @Test void paidVoyageMatchesForecastAndDockingRequiresItsOwnFundedManeuver() throws Exception {
        var initial = funded();
        var fleet = initial.fleets().getFirst();
        var target = FleetLocation.Site.docked("mars-port");
        var plan = LocalTravel.plan(initial, fleet, target);
        var forecast = ShipPowerForecast.departure(initial, fleet, target, plan, null).getFirst();
        assertNotNull(plan.orbital());
        assertEquals(258.79, plan.orbital().coast().coastSeconds() / 86400, .02);
        var expected = LocalTravel.arrivalPreview(initial, fleet, target, plan);
        var actual = depart(initial);
        int ticks = 0;
        while (actual.fleets().getFirst().location().inTransit() && ticks < 1200) { actual = day(actual); ticks++; }
        var arrived = actual.fleets().getFirst();
        assertTrue(arrived.location().isAt(target));
        assertEquals(Math.ceil(plan.days()), ticks);
        assertEquals(expected.ships().getFirst().currentFuelKg(), arrived.ships().getFirst().currentFuelKg(), .00001);
        assertEquals(forecast.remainingBatteryKwh(), arrived.ships().getFirst().powerState().batteryChargeKwh(), .00001);
        assertEquals(fleet.ships().getFirst().storedCargoKg(), arrived.ships().getFirst().storedCargoKg());
        assertTrue(arrived.ships().getFirst().currentFuelKg() > 24000, "Unused fuel remains aboard as insurance.");
        assertEquals(initial.empires(), actual.empires(), "Travel grants no free purchase or refund.");
        assertEquals(8000, OrbitalTravel.parking(actual, "sol", arrived.location().current()).altitudeKm());
    }

    @Test void missingCaptureFuelContinuesGravityDrivenMotionAndCannotClaimArrival() throws Exception {
        var state = depart(funded());
        while (state.fleets().getFirst().location().orbitalFlight().atSource()) state = day(state);
        var fleet = state.fleets().getFirst();
        var ship = fleet.ships().getFirst();
        fleet = fleet.withShips(List.of(new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), 100, 0, 0,
                ship.storedCargoKg(), 0, "", ship.transitMode(), ship.powerState(), ship.supplyState())));
        state = state.withFleets(List.of(fleet));
        while (state.fleets().getFirst().location().orbitalFlight().status() != OrbitalFlight.Status.MISSED) state = day(state);
        var failed = state.fleets().getFirst();
        assertFalse(failed.location().isAt(FleetLocation.Site.docked("mars-port")));
        assertEquals(1, failed.location().orbitalFlight().nextManeuver());
        var position = failed.location().orbitalFlight().position();
        var later = day(state).fleets().getFirst().location().orbitalFlight().position();
        assertNotEquals(position, later);
        assertTrue(Math.abs(position.speedMps() - later.speedMps()) > .01, "Gravity changes velocity without engine fuel.");
        assertTrue(OrbitalTravel.recoveryProblem(failed).contains("unsupported"));
        assertNull(PausedTravelRecovery.preview(state, failed));
    }

    @Test void cancellationAndSaveLoadPreserveMotionAndNeverRepeatEscape() throws Exception {
        var waiting = depart(funded());
        var cancelled = OrbitalFlightProcessor.cancel(waiting.fleets().getFirst());
        assertTrue(cancelled.location().isAt(FleetLocation.Site.docked("earth-port")));
        var state = waiting;
        while (state.fleets().getFirst().location().orbitalFlight().atSource()) state = day(state);
        var manager = new SaveGameManager(directory);
        var file = directory.resolve("orbital.scsave").toFile();
        manager.save(file, state, 1, "2027-01-01T08:00:00");
        var restored = manager.load(file).toGameState(9999, "RUNNING");
        assertEquals(state.fleets(), restored.fleets());
        assertEquals(state.orbitalStations(), restored.orbitalStations());
        assertEquals(day(state).fleets(), day(restored).fleets(), "Frozen trajectory does not depend on a replacement load turn.");
        var drifting = OrbitalFlightProcessor.cancel(state.fleets().getFirst());
        assertEquals(OrbitalFlight.Status.MISSED, drifting.location().orbitalFlight().status());
        assertEquals(state.fleets().getFirst().ships(), drifting.ships());
        assertEquals(state.fleets().getFirst().location().orbitalFlight().position(), drifting.location().orbitalFlight().position());
    }

    @Test void fleetSplitsAndMergesPartitionOrbitalBudgetsWithoutChangingPaidMotion() throws Exception {
        var state = funded();
        var original = state.fleets().getFirst();
        var ship = original.ships().getFirst();
        var second = new ShipInstance("second", ship.designId(), ship.ownerEntityId(), 100, 0, ship.currentFuelKg(),
                ship.storedCargoKg(), 0, "", ship.transitMode(), ship.powerState(), ship.supplyState());
        state = depart(state.withFleets(List.of(original.withShips(List.of(ship, second)))));
        state = FleetOrganization.split(state, "owner", "fleet", List.of("second"), "split", "Split");
        assertEquals(2, state.fleets().size());
        for (var fleet : state.fleets()) assertEquals(1, fleet.location().orbitalFlight().itinerary().maneuvers().getFirst().burns().size());
        assertTrue(FleetOrganization.together(state.fleets().getFirst(), state.fleets().getLast()));
        state = FleetOrganization.transfer(state, "owner", "split", "fleet", List.of("second"));
        assertEquals(1, state.fleets().size());
        assertEquals(2, state.fleets().getFirst().location().orbitalFlight().itinerary().maneuvers().getFirst().burns().size());
        assertEquals(List.of(ship, second), state.fleets().getFirst().ships());
    }

    @Test void lowParkingOrbitsAreRejectedWithoutStraightLineFallback() throws Exception {
        var state = funded();
        state = state.toBuilder().orbitalStations(state.orbitalStations().stream().map(port -> port.withParkingAltitudeKm(500.0)).toList()).build();
        var result = OrbitalTravel.preview(state, state.fleets().getFirst(), FleetLocation.Site.docked("mars-port"));
        assertNull(result.plan());
        assertTrue(result.problem().contains("propellant"));
        assertNull(LocalTravel.plan(state, state.fleets().getFirst(), FleetLocation.Site.docked("mars-port")));
    }

    @Test void waitingRefuelIsPaidAndLiveDepartureRechecksTheCompleteMainBudget() throws Exception {
        var state = depart(funded());
        var fleet = state.fleets().getFirst();
        var ship = fleet.ships().getFirst();
        var shortShip = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), 100, 0, 80000,
                ship.storedCargoKg(), 0, "", ship.transitMode(), ship.powerState(), ship.supplyState());
        state = state.withFleets(List.of(fleet.withShips(List.of(shortShip))));
        while (!state.fleets().getFirst().location().orbitalFlight().failed() && state.turn() < 1000) state = day(state);
        assertEquals(OrbitalFlight.Status.WAITING_FAILED, state.fleets().getFirst().location().orbitalFlight().status());
        assertEquals(80000, state.fleets().getFirst().ships().getFirst().currentFuelKg(), .00001,
                "An affordable escape alone must not authorize an unfunded capture.");
        var orders = new HashMap<String, MarketOrder>();
        PropulsionCatalog.drive("mod_hydrolox_rocket").propellantMaterials(40000)
                .forEach((id, kg) -> orders.put(id, new MarketOrder(id, kg, 0, 1, 0)));
        state = state.toBuilder().commercialHubs(List.of(new CommercialHub("restocked", "earth-port", 0, 1e6, 40000, 10, orders))).build();
        var replenished = ShipFueling.refuel(state, "ship", "earth-port", 40000);
        assertEquals(120000, replenished.fleets().getFirst().ships().getFirst().currentFuelKg());
        assertEquals(state.empires().getFirst().treasuryCredits() - 40000, replenished.empires().getFirst().treasuryCredits(), .00001);
        assertEquals(OrbitalFlight.Status.WAITING_FAILED, replenished.fleets().getFirst().location().orbitalFlight().status(),
                "Restocking does not invent a replacement launch opportunity.");
    }

    @Test void reactorPoweredThermalVoyageRecomputesBurnsFromActualMassAndMatchesItsProjection() throws Exception {
        var initial = funded();
        var modules = new java.util.ArrayList<>(ShipComponentCatalog.workbenchModules("mod_fission_thruster", false,
                ShipComponentCatalog.FISSION_REACTOR_ID, false));
        modules.add(PropulsionCatalog.FUEL_TANK_MODULE_ID);
        var blueprint = ShipBlueprintFactory.evaluate(initial, new ShipDesignSpecification("thermal", "Thermal", "owner",
                ShipRole.CARGO_TRANSPORT, "steel", modules, "steel", .5));
        assertTrue(blueprint.valid(), blueprint.errors().toString());
        var ship = new ShipInstance("thermal-ship", "thermal", "owner", 100, 0, 30000, Map.of("refined_uranium", 50.0))
                .withPowerState(new ShipPowerState(Map.of("refined_uranium", 100.0), "rp1", "uranium", 500, false, 1, 1, 0, 0, 0, 0, 0));
        var fleet = initial.fleets().getFirst().withShips(List.of(ship));
        initial = initial.withShipDesigns(List.of(blueprint.design())).withFleets(List.of(fleet));
        var target = FleetLocation.Site.docked("mars-port");
        var preview = OrbitalTravel.preview(initial, fleet, target);
        assertNotNull(preview.plan(), preview.problem());
        var plan = preview.plan();
        assertTrue(ShipPowerForecast.ready(ShipPowerForecast.departure(initial, fleet, target, plan, null)));
        var expected = LocalTravel.arrivalPreview(initial, fleet, target, plan);
        var actual = initial.withFleets(List.of(LocalTravel.depart(fleet, target, plan)));
        int ticks = 0;
        while (actual.fleets().getFirst().location().inTransit() && ticks++ < 1200) actual = day(actual);
        var arrived = actual.fleets().getFirst();
        assertTrue(arrived.location().isAt(target));
        assertEquals(expected.ships().getFirst().currentFuelKg(), arrived.ships().getFirst().currentFuelKg(), .00001);
        assertEquals(expected.ships().getFirst().generatorFuelMassKg(), arrived.ships().getFirst().generatorFuelMassKg(), .00001);
        assertEquals(expected.ships().getFirst().storedCargoKg().get("refined_uranium"),
                arrived.ships().getFirst().storedCargoKg().get("refined_uranium"), .00001);
        assertTrue(arrived.ships().getFirst().currentFuelKg() > ship.currentFuelKg() - plan.propellantKg().get(ship.id()),
                "Reduced electrical feed mass lowers actual maneuver fuel rather than wasting the difference.");
    }

    @Test void automatedMixedFreightUsesOrbitalDispatchAndSellsOnlyAfterFundedDocking() throws Exception {
        var state = funded();
        var destination = new CommercialHub("mars-market", "mars-port", 0, 1e6, 0, 10,
                Map.of("steel", new MarketOrder("steel", 0, 1250, 3, 0),
                        "food_matrix", new MarketOrder("food_matrix", 0, 1250, 3, 0)));
        var manifest = Map.of("steel", new com.spaceconquest.engine.logistics.TradeCargo(1250, 1250),
                "food_matrix", new com.spaceconquest.engine.logistics.TradeCargo(1250, 1250));
        var route = new com.spaceconquest.engine.logistics.TradeRoute("route", "Mixed freight", "owner", "market", "mars-market",
                "steel", 2500, 0, 1e6, List.of("ship"), 0, true,
                com.spaceconquest.engine.logistics.TradeRoute.DELIVERING, 2500, 2500, 0, 0, true, "Loaded", manifest);
        state = state.toBuilder().commercialHubs(List.of(state.commercialHubs().getFirst(), destination))
                .marketAccounts(List.of(new com.spaceconquest.engine.economy.MarketAccount(destination.id(), 100000)))
                .tradeRoutes(List.of(route)).build();
        var logistics = new com.spaceconquest.engine.logistics.LogisticsProcessor();
        state = logistics.processTradeRoutes(state).state();
        assertNotNull(state.fleets().getFirst().location().orbitalFlight());
        assertEquals(0, state.tradeRoutes().getFirst().totalVolumeMovedKg());
        int ticks = 0;
        while (state.tradeRoutes().getFirst().onboardKg() > 0 && ticks++ < 1200) {
            state = logistics.processTradeRoutes(day(state)).state();
        }
        assertTrue(state.fleets().getFirst().location().isAt(FleetLocation.Site.docked("mars-port")));
        assertEquals(2500, state.tradeRoutes().getFirst().totalVolumeMovedKg());
        assertTrue(state.tradeRoutes().getFirst().cargoManifest().isEmpty());
        assertEquals(0, state.fleets().getFirst().ships().getFirst().storedCargoKg().values().stream()
                .mapToDouble(Double::doubleValue).sum(), .00001);
        assertEquals(1250, state.commercialHubs().getLast().activeOrders().get("steel").supplyKg());
        assertEquals(1250, state.commercialHubs().getLast().activeOrders().get("food_matrix").supplyKg());
    }
}
