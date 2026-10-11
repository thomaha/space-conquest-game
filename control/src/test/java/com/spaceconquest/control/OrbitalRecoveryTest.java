package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.galaxy.SensorPosition;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.spaceconquest.engine.ship.VectorFlight.*;

class OrbitalRecoveryTest {
    private Fleet fleet(GameState state) { return FleetOrganization.find(state, "hunter"); }
    GameState failed(String bodyId) throws Exception {
        var state = new OrbitalRendezvousTest().world(); var body = OrbitalBody.find(state, "sol", bodyId); assertNotNull(body);
        double mu = PlanetaryTransferComparison.GRAVITATIONAL_CONSTANT * body.mass(), radius = body.diameter() * 500;
        var coast = new HohmannCoast(mu, radius + 500000, radius + 550000, .4);
        var events = List.of(new OrbitalFlight.Maneuver(0, 1, Map.of()), new OrbitalFlight.Maneuver(coast.coastSeconds(), 1, Map.of()),
                new OrbitalFlight.Maneuver(coast.coastSeconds() + 3600, 1, Map.of()));
        var itinerary = new OrbitalFlight.Itinerary(bodyId, bodyId, 500, 550, 0, 0, coast, ShipSolarEnvironment.DARK, events, body.frame());
        var orbital = new OrbitalFlight(itinerary, coast.coastSeconds() + 300, 1, OrbitalFlight.Status.MISSED, "Missed circularization");
        return state.withFleets(List.of(fleet(state).withLocation(new FleetLocation(FleetLocation.Site.orbit(bodyId, 500),
                FleetLocation.Site.orbit(bodyId, 550), 1, itinerary.totalSeconds() / 86400, null, orbital))));
    }
    private RecoverOrbitalTravelCommand command(GameState state) {
        var actual = OrbitalRecovery.initialState(state, fleet(state));
        return new RecoverOrbitalTravelCommand("owner", "hunter", norm(actual.position()),
                Math.atan2(actual.position().yMeters(), actual.position().xMeters()), actual.gravity().frame());
    }
    private GameState day(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()))
                .withTurn(state.turn() + 1);
    }

    @Test void samePlannerRecoversPlanetAndMoonWithPaidTicksAndCorrectSensorCoordinates() throws Exception {
        for (String id : List.of("earth", "moon")) {
            var state = failed(id); var selected = command(state);
            var cmd = new RecoverOrbitalTravelCommand("owner", "hunter", OrbitalBody.find(state, "sol", id).diameter() * 500 + 500000,
                    selected.phaseRadians(), selected.expectedFrame());
            var plan = cmd.preview(state); assertNotNull(plan, id);
            var before = OrbitalRecovery.initialState(state, fleet(state));
            var position = SensorPosition.at(state, "sol", fleet(state).location());
            assertEquals(before.position(), plan.vectorTrajectory().start()); assertEquals(before.velocity(), plan.vectorTrajectory().initialVelocity());
            var launched = cmd.apply(state);
            assertEquals(before.gravity().frame(), fleet(launched).location().localFlight().vectorFlight().trajectory().gravity().frame());
            assertTrue(norm(subtract(position, SensorPosition.at(launched, "sol", fleet(launched).location()))) < .001);
            var actual = day(launched); var recovered = fleet(actual); var end = recovered.location().localFlight().vectorFlight();
            assertTrue(recovered.location().localFlight().interrupted()); assertNull(recovered.location().orbitalFlight());
            assertFalse(recovered.location().isAt(FleetLocation.Site.docked("port")));
            double speed = Math.sqrt(before.gravity().gravitationalParameter() / cmd.radiusMeters());
            double angle = cmd.phaseRadians() + speed / cmd.radiusMeters() * 86400;
            var goal = new LocalSiteGeometry.Point(cmd.radiusMeters() * Math.cos(angle), cmd.radiusMeters() * Math.sin(angle), 0);
            assertTrue(norm(subtract(goal, end.position())) < 1, id + " endpoint error " + norm(subtract(goal, end.position())));
            assertEquals(speed, norm(end.velocity()), .001);
            assertEquals(fleet(state).ships().getFirst().currentFuelKg() - plan.propellantKg().get("owner-ship"),
                    recovered.ships().getFirst().currentFuelKg(), .0001);
            assertEquals(cmd.radiusMeters(), norm(fleet(day(actual)).location().localFlight().vectorFlight().position()), 1);
        }
    }

    @Test void cancellationPowerFailureAndReloadRetainTheBodyFrameAndActualMotion() throws Exception {
        for (String id : List.of("earth", "moon")) {
            var state = failed(id); state = command(state).apply(state); var hunter = fleet(state); var ship = hunter.ships().getFirst();
            var canceled = new CancelOrbitalTravelCommand("owner", "hunter").apply(state);
            assertEquals(hunter.location().localFlight().vectorFlight(), fleet(canceled).location().localFlight().vectorFlight());
            var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            var restored = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(canceled, "now", 1, "now")), SaveGame.class).toGameState(0, "RUNNING");
            assertEquals(fleet(canceled), fleet(restored));
            var cold = ship.withPowerState(new ShipPowerState(Map.of(), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0));
            var failed = day(state.withFleets(List.of(hunter.withShips(List.of(cold))))); var actual = fleet(failed).location().localFlight().vectorFlight();
            assertTrue(fleet(failed).location().localFlight().interrupted());
            assertEquals(ship.currentFuelKg(), fleet(failed).ships().getFirst().currentFuelKg());
            assertEquals(OrbitalRecovery.frame(hunter), actual.trajectory().gravity().frame());
            assertNotEquals(hunter.location().localFlight().vectorFlight().position(), actual.position());
        }
    }

    @Test void invalidBoundsFrameChangesAndEmptyTanksRejectWithoutMutationAndOldGravityDefaultsToStellar() throws Exception {
        var state = failed("moon"); var cmd = command(state); var actual = OrbitalRecovery.initialState(state, fleet(state));
        assertFalse(new RecoverOrbitalTravelCommand("owner", "hunter", actual.gravity().minimumRadiusMeters(), 0).validate(state));
        assertFalse(new RecoverOrbitalTravelCommand("owner", "hunter", actual.gravity().maximumRadiusMeters(), 0).validate(state));
        assertFalse(new RecoverOrbitalTravelCommand("owner", "hunter", cmd.radiusMeters(), cmd.phaseRadians(),
                new OrbitalFlight.Frame("earth", OrbitalFlight.CenterKind.PLANETARY)).validate(state));
        var ship = fleet(state).ships().getFirst(); var empty = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), 100, 0, 0,
                ship.storedCargoKg()).withPowerState(ship.powerState());
        var dry = state.withFleets(List.of(fleet(state).withShips(List.of(empty)))); assertFalse(cmd.validate(dry)); assertSame(dry, cmd.apply(dry));
        var mapper = new ObjectMapper(); ObjectNode old = mapper.valueToTree(actual.gravity()); old.remove(List.of("frame", "maximumRadiusMeters"));
        var compatible = mapper.treeToValue(old, TwoBodyFlight.Gravity.class);
        assertEquals(OrbitalFlight.CenterKind.STELLAR, compatible.frame().kind()); assertEquals(Double.MAX_VALUE, compatible.maximumRadiusMeters());
    }
}
