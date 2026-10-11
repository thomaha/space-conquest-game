package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.galaxy.SensorPosition;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.spaceconquest.engine.ship.VectorFlight.*;

class RecoveredParkingTravelTest {
    private Fleet fleet(GameState state) { return FleetOrganization.find(state, "hunter"); }
    private GameState day(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), state.orbitalStations(), List.of()))
                .withTurn(state.turn() + 1);
    }
    private GameState recovered(String id) throws Exception {
        var state = new OrbitalRecoveryTest().failed(id); var actual = OrbitalRecovery.initialState(state, fleet(state));
        double radius = actual.gravity().minimumRadiusMeters() + 500000;
        var command = new RecoverOrbitalTravelCommand("owner", "hunter", radius, Math.atan2(actual.position().yMeters(), actual.position().xMeters()));
        state = day(command.apply(state)); assertTrue(RecoveredParkingTravel.ready(fleet(state)));
        var port = new OrbitalStation("port", "Port", "sol", id, "owner", OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                20, List.of(), Map.of(), 100, 0, 0, 0, 100, 100, "steel", 0, true).withParkingAltitudeKm(500.0);
        var stations = new ArrayList<>(state.orbitalStations()); stations.add(port);
        return state.toBuilder().orbitalStations(stations).build();
    }

    private GameState nearStation(GameState state) {
        var before = fleet(state); var gravity = before.location().localFlight().vectorFlight().trajectory().gravity();
        var body = OrbitalBody.find(state, "sol", gravity.frame().centerBodyId());
        var station = StationRendezvous.at(state, "port", state.turn());
        double angle = -.001, c = Math.cos(angle), s = Math.sin(angle);
        var p = station.position(); var v = station.velocity();
        var point = new LocalSiteGeometry.Point(p.xMeters() * c - p.yMeters() * s, p.xMeters() * s + p.yMeters() * c, 0);
        var velocity = new LocalSiteGeometry.Point(v.xMeters() * c - v.yMeters() * s, v.xMeters() * s + v.yMeters() * c, 0);
        var tiny = new LocalSiteGeometry.Point(1e-30, 0, 0);
        var vector = new VectorFlight(new VectorFlight.Trajectory(point, velocity, tiny, tiny, 1, 0, gravity), point, velocity, 2);
        var geometry = new LocalSiteGeometry.Leg(point, add(point, velocity), norm(velocity));
        var flight = new LocalFlight(geometry, VectorFlight.motion(vector, geometry), Map.of(), Map.of(), true, false, null, vector);
        return state.withFleets(List.of(before.withLocation(before.location().withFlight(flight))));
    }

    @Test void paidDockingQueuesFromActualEarthAndMoonCoastsAndSurvivesReload() throws Exception {
        for (String id : List.of("earth", "moon")) {
            var state = nearStation(recovered(id)); var before = fleet(state); var vector = before.location().localFlight().vectorFlight();
            var destination = FleetLocation.Site.docked("port"); var plan = LocalTravel.plan(state, before, destination);
            assertNotNull(plan, id); assertNull(plan.orbital()); assertEquals(vector.position(), plan.physical().vectorTrajectory().start());
            var motion = StationRendezvous.at(state, "port", state.turn() + plan.days());
            var end = plan.physical().vectorTrajectory().at(plan.days() * 86400);
            assertTrue(norm(subtract(motion.position(), end.position())) <= .05);
            assertTrue(norm(subtract(motion.velocity(), end.velocity())) <= .00005);
            var expected = LocalTravel.arrivalPreview(state, before, destination, plan);
            var controller = new HumanController(); var command = new MoveFleetLocalCommand("hunter", FleetLocation.Kind.DOCKED, "port");
            assertTrue(command.validate(state)); controller.stageCommand(command); assertEquals(before, fleet(state));
            var launched = controller.getCommandQueue().drainAndExecute(state);
            assertEquals(before.ships().getFirst().currentFuelKg(), fleet(launched).ships().getFirst().currentFuelKg());
            assertEquals(vector.position(), fleet(launched).location().localFlight().vectorFlight().position());
            assertEquals(vector.velocity(), fleet(launched).location().localFlight().vectorFlight().velocity());
            assertEquals(SensorPosition.at(state, "sol", before.location()), SensorPosition.at(launched, "sol", fleet(launched).location()));
            var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            var restored = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(launched, "now", 1, "now")), SaveGame.class).toGameState(launched.turn(), "RUNNING");
            assertEquals(fleet(launched), fleet(restored));
            var docked = fleet(day(restored)); assertTrue(docked.location().isAt(destination), id + " " + docked.location());
            assertEquals(expected.ships().getFirst().currentFuelKg(), docked.ships().getFirst().currentFuelKg(), .00001);
            assertTrue(docked.ships().getFirst().currentFuelKg() < before.ships().getFirst().currentFuelKg());
        }
    }

    @Test void altitudeDepartureKeepsActualPhaseAndPaysOrdinaryTransferBudgets() throws Exception {
        for (String id : List.of("earth", "moon")) {
            var state = recovered(id); var vector = fleet(state).location().localFlight().vectorFlight();
            var destination = FleetLocation.Site.orbit(id, 1000); var plan = LocalTravel.plan(state, fleet(state), destination); assertNotNull(plan);
            assertEquals(3, plan.orbital().maneuvers().size());
            assertEquals(Math.atan2(vector.position().yMeters(), vector.position().xMeters()),
                    Math.atan2(plan.orbital().coast().at(0).yMeters(), plan.orbital().coast().at(0).xMeters()), 1e-12);
            var expected = LocalTravel.arrivalPreview(state, fleet(state), destination, plan);
            var command = new MoveFleetLocalCommand("hunter", FleetLocation.Kind.ORBIT, id, false, 1000.0);
            assertTrue(command.validate(state)); var actual = command.apply(state);
            assertEquals(vector, fleet(actual).location().orbitalFlight().itinerary().sourceMotion());
            for (int days = 0; days < Math.ceil(plan.days()); days++) actual = day(actual);
            assertTrue(RecoveredParkingTravel.ready(fleet(actual)));
            var end = fleet(actual).location().localFlight().vectorFlight();
            var predicted = new OrbitalFlight(plan.orbital(), Math.ceil(plan.days()) * 86400,
                    plan.orbital().maneuvers().size(), OrbitalFlight.Status.CAPTURED, "").position();
            assertEquals(new LocalSiteGeometry.Point(predicted.xMeters(), predicted.yMeters(), 0), end.position());
            assertEquals(1000, fleet(actual).location().destination().parkingAltitudeKm(), .00001);
            assertEquals(expected.ships().getFirst().currentFuelKg(), fleet(actual).ships().getFirst().currentFuelKg(), .00001);
        }
    }

    @Test void cancellationAndUnfundedApproachKeepPhysicalCoastWithoutDocking() throws Exception {
        var state = nearStation(recovered("moon")); var before = fleet(state); var command = new MoveFleetLocalCommand("hunter", FleetLocation.Kind.DOCKED, "port");
        assertTrue(command.validate(state));
        var launched = command.apply(state); var canceled = new CancelOrbitalTravelCommand("owner", "hunter").apply(launched);
        assertEquals(before.location().localFlight().vectorFlight().position(), fleet(canceled).location().localFlight().vectorFlight().position());
        assertEquals(before.location().localFlight().vectorFlight().velocity(), fleet(canceled).location().localFlight().vectorFlight().velocity());
        assertEquals(fleet(launched).ships(), fleet(canceled).ships());
        var ship = before.ships().getFirst(); var cold = ship.withPowerState(new ShipPowerState(Map.of(), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0));
        var failed = day(launched.withFleets(List.of(fleet(launched).withShips(List.of(cold)))));
        assertTrue(fleet(failed).location().localFlight().interrupted());
        assertFalse(fleet(failed).location().isAt(FleetLocation.Site.docked("port")));
        var expected = fleet(launched).location().localFlight().vectorFlight().advance(86400, true);
        assertEquals(expected, fleet(failed).location().localFlight().vectorFlight());
        var empty = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), 100, 0, 0, ship.storedCargoKg()).withPowerState(ship.powerState());
        var dry = state.withFleets(List.of(before.withShips(List.of(empty)))); assertFalse(command.validate(dry)); assertSame(dry, command.apply(dry));
        assertFalse(new MoveFleetLocalCommand("hunter", FleetLocation.Kind.ORBIT, "earth", false, 500.0).validate(state));
    }

    @Test void missedContactAndLostStationRetainPhysicalCoast() throws Exception {
        var state = nearStation(recovered("earth")); var destination = FleetLocation.Site.docked("port");
        var plan = LocalTravel.plan(state, fleet(state), destination); assertNotNull(plan);
        var launched = LocalTravel.depart(fleet(state), destination, plan); var flight = launched.location().localFlight();
        var vector = flight.vectorFlight();
        var shifted = new VectorFlight(vector.trajectory(), add(vector.position(), new LocalSiteGeometry.Point(0, 0, 10)), vector.velocity(), 0);
        var missed = LocalFlightProcessor.advance(launched.withLocation(launched.location().withFlight(new LocalFlight(flight.geometry(),
                VectorFlight.motion(shifted, flight.geometry()), flight.propellantKg(), flight.propulsion(), false, false, null, shifted))), 24, false);
        assertFalse(missed.location().isAt(destination)); assertTrue(missed.location().localFlight().interrupted());
        assertTrue(missed.ships().getFirst().currentFuelKg() < launched.ships().getFirst().currentFuelKg());
        var end = vector.trajectory().at(vector.trajectory().totalSeconds());
        var mismatched = new VectorFlight(vector.trajectory(), flight.geometry().destination(),
                add(end.velocity(), new LocalSiteGeometry.Point(0, 0, .01)), end.elapsedSeconds());
        var fast = LocalFlightProcessor.advance(launched.withLocation(launched.location().withFlight(new LocalFlight(flight.geometry(),
                VectorFlight.motion(mismatched, flight.geometry()), flight.propellantKg(), flight.propulsion(), false, false, null, mismatched))), 0, false);
        assertFalse(fast.location().isAt(destination)); assertTrue(fast.location().localFlight().interrupted());
        var removed = day(state.toBuilder().fleets(List.of(launched)).orbitalStations(List.of()).build());
        assertFalse(fleet(removed).location().isAt(destination)); assertTrue(fleet(removed).location().localFlight().interrupted());
        assertEquals(launched.ships().getFirst().currentFuelKg(), fleet(removed).ships().getFirst().currentFuelKg());
    }

    @Test void cancellationAfterPaidCircularizationRetainsActualPhaseAndSpentFuel() throws Exception {
        var state = recovered("moon"); var vector = fleet(state).location().localFlight().vectorFlight();
        var gravity = vector.trajectory().gravity();
        double axis = Math.cbrt(gravity.gravitationalParameter() * Math.pow((86400 - 1800) / Math.PI, 2));
        double altitude = (2 * axis - norm(vector.position()) - gravity.minimumRadiusMeters()) / 1000;
        var command = new MoveFleetLocalCommand("hunter", FleetLocation.Kind.ORBIT, "moon", false, altitude);
        assertTrue(command.validate(state)); var captured = day(command.apply(state));
        var orbital = fleet(captured).location().orbitalFlight(); assertNotNull(orbital); assertEquals(2, orbital.nextManeuver());
        var actual = orbital.position(); var canceled = new CancelOrbitalTravelCommand("owner", "hunter").apply(captured);
        var restored = fleet(canceled).location().localFlight().vectorFlight();
        assertEquals(new LocalSiteGeometry.Point(actual.xMeters(), actual.yMeters(), 0), restored.position());
        assertEquals(new LocalSiteGeometry.Point(actual.vxMps(), actual.vyMps(), 0), restored.velocity());
        assertEquals(fleet(captured).ships(), fleet(canceled).ships()); assertTrue(RecoveredParkingTravel.ready(fleet(canceled)));
    }
}
