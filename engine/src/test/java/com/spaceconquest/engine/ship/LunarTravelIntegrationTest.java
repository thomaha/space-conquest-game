package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.industry.PowerGridState;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LunarTravelIntegrationTest {
    @TempDir Path directory;

    private OrbitalStation port(String id, String body, double altitude) {
        return new OrbitalStation(id, id, "sol", body, "owner", OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                20, List.of(), Map.of(), 100, 10, 0, 0, 100, 100, "steel", 0, true).withParkingAltitudeKm(altitude);
    }
    private GameState funded(boolean outbound) throws Exception {
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e6, 0, List.of("sol"), List.of(), Map.of(),
                List.of("rocketry", "hydrolox_propulsion", "electricity", "solar_power", "industrial_production", "space_stations"), List.of());
        var stock = new HashMap<String, MarketOrder>();
        PropulsionCatalog.drive("mod_hydrolox_rocket").propellantMaterials(120000)
                .forEach((id, kg) -> stock.put(id, new MarketOrder(id, kg, 0, 1, 0)));
        var state = GameState.builder().solarSystems(DataModelLoader.loadSolarSystems()).empires(List.of(owner))
                .orbitalStations(List.of(port("source", outbound ? "earth" : "moon", outbound ? 2000 : 500),
                        port("target", outbound ? "moon" : "earth", outbound ? 500 : 2000)))
                .commercialHubs(List.of(new CommercialHub("market", "source", 0, 1e6, 120000, 10, stock)))
                .powerGrids(List.of(new PowerGridState("source", 1000, 0, 1000, 2000, 2000, false))).build();
        var blueprint = ShipBlueprintFactory.evaluate(state, new ShipDesignSpecification("design", "Freighter", "owner",
                ShipRole.CARGO_TRANSPORT, "steel", ChemicalFreighterCatalog.modules("mod_hydrolox_rocket"), "steel", .5));
        assertTrue(blueprint.valid(), blueprint.errors().toString());
        var ship = new ShipInstance("ship", "design", "owner", 100, 0, 0, Map.of("steel", 1250.0, "food_matrix", 1250.0))
                .withPowerState(ShipPowerState.empty());
        var fleet = new Fleet("fleet", "Freighter", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.docked("source")));
        state = state.withShipDesigns(List.of(blueprint.design())).withFleets(List.of(fleet));
        return ShipBatteryCharging.charge(ShipFueling.refuel(state, "ship", "source", 120000), "ship", "source", 500 / .9);
    }
    private GameState day(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state),
                state.orbitalStations(), List.of())).withTurn(state.turn() + 1);
    }
    private LocalTravel.Plan plan(GameState state, FleetLocation.Site target) {
        var preview = OrbitalTravel.preview(state, state.fleets().getFirst(), target);
        assertNotNull(preview.plan(), preview.problem());
        assertEquals(preview.plan(), LocalTravel.plan(state, state.fleets().getFirst(), target));
        assertTrue(ShipPowerForecast.ready(ShipPowerForecast.departure(state, state.fleets().getFirst(), target, preview.plan(), null)));
        return preview.plan();
    }

    @Test void paidOutwardAndInwardVoyagesMatchForecastAndRetainLunarParking() throws Exception {
        for (boolean outward : List.of(true, false)) {
            var initial = funded(outward); var fleet = initial.fleets().getFirst();
            var target = FleetLocation.Site.docked("target"); var plan = plan(initial, target);
            assertTrue(plan.orbital().lunarTransfer());
            assertEquals(new OrbitalFlight.Frame("earth", OrbitalFlight.CenterKind.PLANETARY), plan.orbital().frame());
            assertEquals(outward ? "Lunar capture" : "Lunar escape", plan.orbital().maneuverNames().get(outward ? 1 : 0));
            var expected = LocalTravel.arrivalPreview(initial, fleet, target, plan);
            var actual = initial.withFleets(List.of(LocalTravel.depart(fleet, target, plan)));
            int ticks = 0;
            while (actual.fleets().getFirst().location().inTransit() && ticks++ < 40) actual = day(actual);
            assertTrue(actual.fleets().getFirst().location().isAt(target));
            assertEquals(Math.ceil(plan.days()), ticks);
            var arrived = actual.fleets().getFirst().ships().getFirst();
            assertEquals(expected.ships().getFirst().currentFuelKg(), arrived.currentFuelKg(), .00001);
            assertEquals(expected.ships().getFirst().powerState().batteryChargeKwh(), arrived.powerState().batteryChargeKwh(), .00001);
            assertEquals(fleet.ships().getFirst().storedCargoKg(), arrived.storedCargoKg());
            assertEquals(outward ? "moon" : "earth", OrbitalTravel.parking(actual, "sol", target).body().id());
            assertEquals(outward ? 500 : 2000, OrbitalTravel.parking(actual, "sol", target).altitudeKm());
            assertEquals(initial.empires(), actual.empires());
            if (outward) {
                var orbit = FleetLocation.Site.orbit("moon", 500); var undock = plan(actual, orbit);
                assertEquals(OrbitalFlight.CenterKind.LUNAR, undock.orbital().frame().kind());
                var moon = OrbitalBody.find(actual, "sol", "moon");
                assertEquals(moon.mass() * PlanetaryTransferComparison.GRAVITATIONAL_CONSTANT, undock.orbital().coast().gravitationalParameter());
                assertTrue(day(actual.withFleets(List.of(LocalTravel.depart(actual.fleets().getFirst(), orbit, undock))))
                        .fleets().getFirst().location().isAt(orbit));
            }
        }
    }

    @Test void failedCaptureKeepsParentBallisticFrameAcrossReloadCancellationAndOrganization() throws Exception {
        var initial = funded(true); var target = FleetLocation.Site.docked("target"); var plan = plan(initial, target);
        var state = initial.withFleets(List.of(LocalTravel.depart(initial.fleets().getFirst(), target, plan)));
        state = day(state); var fleet = state.fleets().getFirst();
        assertEquals(1, fleet.location().orbitalFlight().nextManeuver());
        var ship = fleet.ships().getFirst();
        var empty = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), 100, 0, 0,
                ship.storedCargoKg(), 0, "", ship.transitMode(), ship.powerState(), ship.supplyState());
        state = state.withFleets(List.of(fleet.withShips(List.of(empty))));
        for (int days = 0; days < 20 && !state.fleets().getFirst().location().orbitalFlight().failed(); days++) state = day(state);
        fleet = state.fleets().getFirst(); var flight = fleet.location().orbitalFlight();
        assertEquals(OrbitalFlight.Status.MISSED, flight.status());
        assertTrue(flight.message().contains("planet-centered")); assertFalse(fleet.location().isAt(target));
        assertNotEquals(flight.position(), day(state).fleets().getFirst().location().orbitalFlight().position());
        assertEquals(flight.itinerary().frame(), OrbitalFlightProcessor.cancel(fleet).location().orbitalFlight().itinerary().frame());
        var manager = new SaveGameManager(directory); var file = directory.resolve("lunar.scsave").toFile();
        manager.save(file, state, 1, "2027-01-01T08:00:00");
        var restored = manager.load(file).toGameState(9999, "RUNNING");
        assertEquals(state.fleets(), restored.fleets());
        assertEquals(day(state).fleets(), day(restored).fleets());
        assertEquals(flight.itinerary().frame(), flight.retain(fleet.ships()).itinerary().frame());
        assertEquals(flight, flight.retain(List.of()).join(flight));
    }

    @Test void capturedCancellationAndMoonParkingUseLunarGravityAndUnsupportedGeometryCannotFallBack() throws Exception {
        var state = funded(true); var fleet = state.fleets().getFirst(); var target = FleetLocation.Site.docked("target");
        var plan = plan(state, target); var itinerary = plan.orbital();
        // Stage the physical capture boundary by paying the first two saved burns before cancelling the approach.
        var ship = fleet.ships().getFirst(); var design = FleetSupplySimulation.design(state, ship);
        for (int event = 0; event < 2; event++) {
            var maneuver = itinerary.maneuvers().get(event);
            var power = OrbitalPowerAccounting.burst(design, ship, maneuver.auxiliaryHours());
            assertTrue(power.supplied());
            ship = OrbitalFuelAccounting.pay(ship, design, maneuver.burns().get(ship.id()), power.state());
            assertNotNull(ship);
        }
        var captured = LocalTravel.depart(fleet.withShips(List.of(ship)), target, plan);
        captured = captured.withLocation(captured.location().withOrbitalFlight(new OrbitalFlight(itinerary,
                itinerary.maneuvers().get(1).seconds(), 2, OrbitalFlight.Status.CAPTURED, "Funded lunar capture")));
        var cancelled = OrbitalFlightProcessor.cancel(captured);
        assertEquals(FleetLocation.Site.orbit("moon", 500), cancelled.location().current());
        state = state.withFleets(List.of(cancelled));
        var lunarParking = plan(state, FleetLocation.Site.orbit("moon", 1000));
        assertEquals(new OrbitalFlight.Frame("moon", OrbitalFlight.CenterKind.LUNAR), lunarParking.orbital().frame());
        assertNull(LocalTravel.plan(state, cancelled, FleetLocation.Site.orbit("moon", 100000)));
        assertNull(LocalTravel.plan(state, cancelled, FleetLocation.Site.orbit("mars", 500)));
        assertNull(LocalTravel.plan(state, cancelled, FleetLocation.Site.orbit("earth", 400000)));
        var low = funded(true).withOrbitalStations(List.of(port("source", "earth", 500), port("target", "moon", 500)));
        var rejected = OrbitalTravel.preview(low, low.fleets().getFirst(), target);
        assertNull(rejected.plan()); assertTrue(rejected.problem().contains("parking-period"));
        assertNull(LocalTravel.plan(low, low.fleets().getFirst(), target));
    }

    @Test void splittingAndMergingAnActiveLunarFleetRetainItsFrameAndPartitionBurns() throws Exception {
        var state = funded(true); var fleet = state.fleets().getFirst(); var first = fleet.ships().getFirst();
        var second = new ShipInstance("second", first.designId(), first.ownerEntityId(), 100, 0, first.currentFuelKg(),
                first.storedCargoKg(), 0, "", first.transitMode(), first.powerState(), first.supplyState());
        fleet = fleet.withShips(List.of(first, second)); state = state.withFleets(List.of(fleet));
        var target = FleetLocation.Site.docked("target"); var plan = plan(state, target);
        state = state.withFleets(List.of(LocalTravel.depart(fleet, target, plan)));
        state = FleetOrganization.split(state, "owner", "fleet", List.of("second"), "split", "Split");
        for (var part : state.fleets()) {
            assertEquals(plan.orbital().frame(), part.location().orbitalFlight().itinerary().frame());
            assertTrue(part.location().orbitalFlight().itinerary().maneuvers().stream().allMatch(event -> event.burns().size() == 1));
        }
        state = FleetOrganization.transfer(state, "owner", "split", "fleet", List.of("second"));
        assertEquals(plan.orbital(), state.fleets().getFirst().location().orbitalFlight().itinerary());
    }
}
