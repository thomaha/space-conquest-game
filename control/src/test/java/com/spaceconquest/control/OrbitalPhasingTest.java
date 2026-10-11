package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.spaceconquest.engine.ship.VectorFlight.*;

class OrbitalPhasingTest {
    private Fleet fleet(GameState state) { return FleetOrganization.find(state, "hunter"); }
    private GameState world(String id, double angle) throws Exception {
        var state = new OrbitalRendezvousTest().world(); var before = fleet(state);
        var body = OrbitalBody.find(state, "sol", id);
        var port = new OrbitalStation("port", "Port", "sol", id, "owner", OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                20, List.of(), Map.of(), 100, 0, 0, 0, 100, 100, "steel", 0, true).withParkingAltitudeKm(500.0);
        state = state.toBuilder().orbitalStations(List.of(port)).build();
        var station = StationRendezvous.at(state, "port", state.turn());
        var p = rotate(station.position(), angle); var v = rotate(station.velocity(), angle);
        var gravity = new TwoBodyFlight.Gravity(PlanetaryTransferComparison.GRAVITATIONAL_CONSTANT * body.mass(),
                body.diameter() * 500, body.frame(), body.sphereKm(state.solarSystems().stream().filter(system -> system.id().equals("sol")).findFirst().orElseThrow()) * 1000);
        var tiny = new LocalSiteGeometry.Point(1e-30, 0, 0);
        var vector = new VectorFlight(new VectorFlight.Trajectory(p, v, tiny, tiny, 1, 0, gravity), p, v, 2);
        var geometry = new LocalSiteGeometry.Leg(p, add(p, v), norm(v));
        var flight = new LocalFlight(geometry, VectorFlight.motion(vector, geometry), Map.of(), Map.of(), true, false, null, vector);
        var location = new FleetLocation(FleetLocation.Site.orbit(id), FleetLocation.Site.orbit(id, 500), 0, 1).withFlight(flight);
        return state.toBuilder().fleets(List.of(before.withLocation(location))).orbitalStations(List.of(port)).build();
    }
    private LocalSiteGeometry.Point rotate(LocalSiteGeometry.Point p, double angle) {
        return new LocalSiteGeometry.Point(p.xMeters() * Math.cos(angle) - p.yMeters() * Math.sin(angle),
                p.xMeters() * Math.sin(angle) + p.yMeters() * Math.cos(angle), 0);
    }
    private GameState day(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), state.orbitalStations(), List.of()))
                .withTurn(state.turn() + 1);
    }

    @Test void distantEarthAndMoonPhasesUsePaidEllipticalCoastAndDockAfterReload() throws Exception {
        for (String id : List.of("earth", "moon")) {
            var state = world(id, Math.PI); var before = fleet(state); var destination = FleetLocation.Site.docked("port");
            var plan = LocalTravel.plan(state, before, destination); assertNotNull(plan, id);
            var trajectory = plan.physical().vectorTrajectory();
            assertTrue(trajectory.coastSeconds() > 20 * trajectory.burnSeconds(), id);
            assertTrue(Math.abs(norm(trajectory.at(trajectory.totalSeconds() / 2).position()) - norm(trajectory.start())) > 10000);
            var goal = StationRendezvous.at(state, "port", state.turn() + plan.days());
            var end = trajectory.at(trajectory.totalSeconds());
            assertTrue(norm(subtract(goal.position(), end.position())) <= .05);
            assertTrue(norm(subtract(goal.velocity(), end.velocity())) <= .00005);
            var expected = LocalTravel.arrivalPreview(state, before, destination, plan);
            var command = new MoveFleetLocalCommand("hunter", FleetLocation.Kind.DOCKED, "port"); assertTrue(command.validate(state));
            var launched = command.apply(state);
            assertEquals(before.ships().getFirst().currentFuelKg(), fleet(launched).ships().getFirst().currentFuelKg());
            var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            var restored = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(launched, "now", 1, "now")), SaveGame.class)
                    .toGameState(launched.turn(), "RUNNING");
            assertEquals(fleet(launched), fleet(restored));
            for (int ticks = 0; ticks < Math.ceil(plan.days()); ticks++) restored = day(restored);
            assertTrue(fleet(restored).location().isAt(destination), id);
            assertEquals(expected.ships().getFirst().currentFuelKg(), fleet(restored).ships().getFirst().currentFuelKg(), .00001);
            assertEquals(expected.ships().getFirst().powerState().batteryChargeKwh(), fleet(restored).ships().getFirst().powerState().batteryChargeKwh(), .00001);
            assertTrue(fleet(restored).ships().getFirst().currentFuelKg() < before.ships().getFirst().currentFuelKg());
        }
    }

    @Test void cancellationAfterDepartureBurnPreservesPaidFuelAndActualEllipticalCoast() throws Exception {
        var state = world("moon", -2.8); var before = fleet(state); var destination = FleetLocation.Site.docked("port");
        var plan = LocalTravel.plan(state, before, destination); assertNotNull(plan);
        var launched = LocalTravel.depart(before, destination, plan); var trajectory = plan.physical().vectorTrajectory();
        assertTrue(trajectory.coastSeconds() > 20 * trajectory.burnSeconds());
        assertTrue(norm(trajectory.at(trajectory.burnSeconds() + trajectory.coastSeconds() / 6).position()) < norm(trajectory.start()) - 10000);
        var docked = fleet(day(state.withFleets(List.of(launched))));
        assertTrue(docked.location().isAt(destination));
        assertEquals(before.ships().getFirst().currentFuelKg() - plan.propellantKg().get(before.ships().getFirst().id()),
                docked.ships().getFirst().currentFuelKg(), .00001);
        var coasting = LocalFlightProcessor.advance(launched, trajectory.burnSeconds() / 3600, false);
        assertTrue(coasting.ships().getFirst().currentFuelKg() < launched.ships().getFirst().currentFuelKg());
        var actual = coasting.location().localFlight().vectorFlight();
        var canceled = new CancelOrbitalTravelCommand("owner", "hunter").apply(state.withFleets(List.of(coasting)));
        assertTrue(fleet(canceled).location().localFlight().interrupted());
        assertEquals(actual, fleet(canceled).location().localFlight().vectorFlight());
        assertEquals(coasting.ships(), fleet(canceled).ships());
        var drifted = LocalFlightProcessor.advance(fleet(canceled), 1, false);
        assertEquals(actual.advance(3600, true), drifted.location().localFlight().vectorFlight());
        assertEquals(coasting.ships().getFirst().currentFuelKg(), drifted.ships().getFirst().currentFuelKg());
        assertFalse(drifted.location().isAt(destination));
    }

    @Test void emptyFuelCannotAuthorizePhasing() throws Exception {
        var state = world("earth", .8); var before = fleet(state); var ship = before.ships().getFirst();
        var empty = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), 100, 0, 0, ship.storedCargoKg()).withPowerState(ship.powerState());
        state = state.withFleets(List.of(before.withShips(List.of(empty))));
        var command = new MoveFleetLocalCommand("hunter", FleetLocation.Kind.DOCKED, "port");
        assertFalse(command.validate(state)); assertSame(state, command.apply(state));
    }
}
