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

class ParkingOrbitTravelTest {
    @TempDir Path directory;
    private OrbitalStation port(String id, double altitude) {
        var yard = new StationModule("yard-" + id, "Yard", StationModule.TYPE_CAPITAL_SLIPWAY,
                4, 10000, 10, 0, Map.of(), "engineer", 0, true);
        return new OrbitalStation(id, id, "sol", "earth", "owner", OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                20, List.of(yard), Map.of(), 100, 10, 0, 0, 100, 100, "steel", 0, true).withParkingAltitudeKm(altitude);
    }
    private GameState funded(double from, double to) throws Exception {
        var owner = new Empire("owner", "Owner", "human", "Individualist", 1e6, 0, List.of("sol"), List.of(), Map.of(),
                List.of("rocketry", "hydrolox_propulsion", "electricity", "solar_power", "industrial_production", "space_stations"), List.of());
        var stock = new HashMap<String, MarketOrder>();
        PropulsionCatalog.drive("mod_hydrolox_rocket").propellantMaterials(120000)
                .forEach((id, kg) -> stock.put(id, new MarketOrder(id, kg, 0, 1, 0)));
        var state = GameState.builder().solarSystems(DataModelLoader.loadSolarSystems()).empires(List.of(owner))
                .orbitalStations(List.of(port("source", from), port("target", to)))
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
        var powered = ShipPowerProcessor.advanceDay(state);
        return state.withFleets(new FleetProcessor().processFleetMovements(powered, state.orbitalStations(), List.of()))
                .withTurn(state.turn() + 1);
    }
    private LocalTravel.Plan plan(GameState state, FleetLocation.Site target) {
        var preview = OrbitalTravel.preview(state, state.fleets().getFirst(), target);
        assertNotNull(preview.plan(), preview.problem());
        assertEquals(preview.plan(), LocalTravel.plan(state, state.fleets().getFirst(), target));
        assertTrue(ShipPowerForecast.ready(ShipPowerForecast.departure(state, state.fleets().getFirst(), target, preview.plan(), null)));
        return preview.plan();
    }

    @Test void outwardAndInwardPaidTransfersMatchForecastAndKeepActualParkingAltitude() throws Exception {
        for (boolean outward : List.of(true, false)) {
            var initial = funded(outward ? 2000 : 400000, outward ? 400000 : 2000);
            var fleet = initial.fleets().getFirst(); var target = FleetLocation.Site.docked("target");
            var plan = plan(initial, target); var itinerary = plan.orbital();
            assertTrue(itinerary.parkingTransfer());
            var earth = initial.solarSystems().getFirst().planets().stream().filter(body -> body.id().equals("earth")).findFirst().orElseThrow();
            assertEquals(PlanetaryTransferComparison.GRAVITATIONAL_CONSTANT * earth.mass(), itinerary.coast().gravitationalParameter());
            assertEquals(0, itinerary.waitSeconds());
            assertTrue(itinerary.coastDurationSeconds() > 86400);
            var expected = LocalTravel.arrivalPreview(initial, fleet, target, plan);
            var actual = initial.withFleets(List.of(LocalTravel.depart(fleet, target, plan)));
            int ticks = 0;
            while (actual.fleets().getFirst().location().inTransit() && ticks++ < 30) actual = day(actual);
            assertTrue(actual.fleets().getFirst().location().isAt(target));
            assertEquals(Math.ceil(plan.days()), ticks);
            var arrived = actual.fleets().getFirst().ships().getFirst();
            assertEquals(expected.ships().getFirst().currentFuelKg(), arrived.currentFuelKg(), .00001);
            assertEquals(expected.ships().getFirst().powerState().batteryChargeKwh(), arrived.powerState().batteryChargeKwh(), .00001);
            assertEquals(fleet.ships().getFirst().storedCargoKg(), arrived.storedCargoKg());
            assertEquals(outward ? 400000 : 2000, OrbitalTravel.parking(actual, "sol", target).altitudeKm());
            assertEquals(initial.empires(), actual.empires());
        }
    }

    @Test void unfundedCircularizationContinuesPlanetCenteredMotionWithoutDocking() throws Exception {
        var state = funded(2000, 400000); var fleet = state.fleets().getFirst();
        var target = FleetLocation.Site.docked("target"); var plan = plan(state, target);
        state = day(state.withFleets(List.of(LocalTravel.depart(fleet, target, plan))));
        fleet = state.fleets().getFirst(); assertEquals(1, fleet.location().orbitalFlight().nextManeuver());
        var ship = fleet.ships().getFirst();
        var empty = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), 100, 0, 0,
                ship.storedCargoKg(), 0, "", ship.transitMode(), ship.powerState(), ship.supplyState());
        state = state.withFleets(List.of(fleet.withShips(List.of(empty))));
        while (!state.fleets().getFirst().location().orbitalFlight().failed()) state = day(state);
        var failed = state.fleets().getFirst(); assertEquals(OrbitalFlight.Status.MISSED, failed.location().orbitalFlight().status());
        assertFalse(failed.location().isAt(target));
        var position = failed.location().orbitalFlight().position();
        assertNotEquals(position, day(state).fleets().getFirst().location().orbitalFlight().position());
        assertTrue(failed.location().orbitalFlight().message().contains("planet-centered"));
        assertEquals(0, failed.ships().getFirst().currentFuelKg());
    }

    @Test void sameAltitudeDockingAndUndockingArePaidOneEventOrdersAndSurviveSaveLoad() throws Exception {
        var state = funded(8000, 8000); var fleet = state.fleets().getFirst(); var target = FleetLocation.Site.docked("target");
        var plan = plan(state, target);
        assertEquals(1, plan.orbital().maneuvers().size()); assertEquals(0, plan.orbital().coastDurationSeconds());
        assertEquals(1.0 / 24, plan.days());
        var departed = state.withFleets(List.of(LocalTravel.depart(fleet, target, plan)));
        var display = OrbitalJourneySnapshot.from(departed.fleets().getFirst().location().orbitalFlight());
        assertTrue(display.parkingTransfer()); assertEquals(1, display.transferArc().size());
        assertEquals(1, display.maneuvers().size());
        assertTrue(display.maneuvers().getFirst().name().contains("Docking"));
        var manager = new SaveGameManager(directory); var file = directory.resolve("parking.scsave").toFile();
        manager.save(file, departed, 1, "2027-01-01T08:00:00");
        var restored = manager.load(file).toGameState(9999, "RUNNING");
        assertEquals(departed.fleets(), restored.fleets());
        var arrived = day(restored); assertTrue(arrived.fleets().getFirst().location().isAt(target));
        assertTrue(arrived.fleets().getFirst().ships().getFirst().currentFuelKg() < fleet.ships().getFirst().currentFuelKg());
        var orbit = FleetLocation.Site.orbit("earth", 8000);
        var undock = plan(arrived, orbit);
        assertEquals(1, undock.orbital().maneuvers().size());
        var undocked = day(arrived.withFleets(List.of(LocalTravel.depart(arrived.fleets().getFirst(), orbit, undock))));
        assertTrue(undocked.fleets().getFirst().location().isAt(orbit));
        assertTrue(undocked.fleets().getFirst().ships().getFirst().currentFuelKg() < arrived.fleets().getFirst().ships().getFirst().currentFuelKg());
    }

    @Test void invalidParkingAndFiniteBurnFailuresCannotUseTheLegacyPlanner() throws Exception {
        var low = funded(500, 400000);
        var result = OrbitalTravel.preview(low, low.fleets().getFirst(), FleetLocation.Site.docked("target"));
        assertNull(result.plan()); assertTrue(result.problem().contains("parking-period"));
        assertNull(LocalTravel.plan(low, low.fleets().getFirst(), FleetLocation.Site.docked("target")));
        var parked = low.fleets().getFirst().withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth")));
        assertNull(LocalTravel.plan(low, parked, FleetLocation.Site.orbit("earth", 500)),
                "Making the default parking altitude explicit must not spend maneuver fuel.");
        var outside = funded(2000, 2e6);
        assertNull(LocalTravel.plan(outside, outside.fleets().getFirst(), FleetLocation.Site.docked("target")));
    }

    @Test void oneEventSplitAndMergePreserveBudgetsAndAnUnfundedMemberBlocksTheWholeApproach() throws Exception {
        var state = funded(8000, 8000); var fleet = state.fleets().getFirst(); var first = fleet.ships().getFirst();
        var second = new ShipInstance("second", first.designId(), first.ownerEntityId(), 100, 0, first.currentFuelKg(),
                first.storedCargoKg(), 0, "", first.transitMode(), first.powerState(), first.supplyState());
        state = state.withFleets(List.of(fleet.withShips(List.of(first, second))));
        var target = FleetLocation.Site.docked("target"); var plan = plan(state, target);
        state = state.withFleets(List.of(LocalTravel.depart(state.fleets().getFirst(), target, plan)));
        state = FleetOrganization.split(state, "owner", "fleet", List.of("second"), "split", "Split");
        for (var part : state.fleets()) {
            assertEquals(1, part.location().orbitalFlight().itinerary().maneuvers().size());
            assertEquals(1, part.location().orbitalFlight().itinerary().maneuvers().getFirst().burns().size());
        }
        state = FleetOrganization.transfer(state, "owner", "split", "fleet", List.of("second"));
        fleet = state.fleets().getFirst();
        assertEquals(2, fleet.location().orbitalFlight().itinerary().maneuvers().getFirst().burns().size());
        var empty = new ShipInstance(first.id(), first.designId(), first.ownerEntityId(), 100, 0, 0,
                first.storedCargoKg(), 0, "", first.transitMode(), first.powerState(), first.supplyState());
        state = day(state.withFleets(List.of(fleet.withShips(List.of(empty, second)))));
        fleet = state.fleets().getFirst();
        assertEquals(OrbitalFlight.Status.WAITING_FAILED, fleet.location().orbitalFlight().status());
        assertTrue(fleet.location().stationaryAt(FleetLocation.Site.docked("source")));
        assertFalse(fleet.location().isAt(target));
        assertEquals(0, fleet.ships().getFirst().currentFuelKg());
        assertEquals(second.currentFuelKg(), fleet.ships().getLast().currentFuelKg(), "The fleet approach is atomic.");
    }
}
