package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.spaceconquest.engine.ship.VectorFlight.*;

class StellarRecoveryTest {
    private Fleet hunter(GameState state) { return FleetOrganization.find(state, "hunter"); }
    private GameState drifting() throws Exception {
        var state = new OrbitalRendezvousTest().world();
        state = FleetInterceptProcessor.advance(new InterceptFleetCommand("owner", "hunter", "target").apply(state));
        var canceled = new CancelOrbitalTravelCommand("owner", "hunter").apply(state);
        return canceled.withFleets(canceled.fleets().stream().map(fleet -> fleet.id().equals("hunter")
                ? LocalFlightProcessor.advance(fleet, 12, false) : fleet).toList());
    }
    private RecoverStellarTravelCommand command(GameState state) {
        var position = hunter(state).location().localFlight().vectorFlight().position();
        return new RecoverStellarTravelCommand("owner", "hunter", norm(position), Math.atan2(position.yMeters(), position.xMeters()));
    }
    private GameState day(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()))
                .withTurn(state.turn() + 1);
    }

    @Test void recoveryStartsAtActualStateConsumesFuelAndRetainsCircularCoast() throws Exception {
        var state = drifting(); var cmd = command(state); var plan = cmd.preview(state); assertNotNull(plan);
        var before = hunter(state).location().localFlight().vectorFlight();
        assertEquals(before.position(), plan.vectorTrajectory().start());
        assertEquals(before.velocity(), plan.vectorTrajectory().initialVelocity());
        var launched = cmd.apply(state);
        assertEquals(before.position(), hunter(launched).location().localFlight().vectorFlight().position());
        double fuel = hunter(launched).ships().getFirst().currentFuelKg();
        for (int days = 0; days < plan.days(); days++) launched = day(launched);
        var fleet = hunter(launched); var end = fleet.location().localFlight().vectorFlight();
        assertTrue(fleet.location().localFlight().interrupted()); assertNull(fleet.interceptOrder());
        assertEquals(FleetLocation.Kind.DEEP_SPACE, fleet.location().destination().kind());
        assertEquals(fuel - plan.propellantKg().get("owner-ship"), fleet.ships().getFirst().currentFuelKg(), .0001);
        double speed = Math.sqrt(end.trajectory().gravity().gravitationalParameter() / cmd.radiusMeters());
        double angle = cmd.phaseRadians() + speed / cmd.radiusMeters() * plan.days() * 86400;
        var goal = new LocalSiteGeometry.Point(cmd.radiusMeters() * Math.cos(angle), cmd.radiusMeters() * Math.sin(angle), 0);
        var velocity = new LocalSiteGeometry.Point(-speed * Math.sin(angle), speed * Math.cos(angle), 0);
        assertTrue(norm(subtract(goal, end.position())) < 1); assertTrue(norm(subtract(velocity, end.velocity())) < .001);
        var coast = hunter(day(launched)).location().localFlight().vectorFlight();
        assertEquals(fleet.ships().getFirst().currentFuelKg(), hunter(day(launched)).ships().getFirst().currentFuelKg());
        assertEquals(cmd.radiusMeters(), norm(coast.position()), 1);
        assertTrue(norm(subtract(coast.position(), add(end.position(), scale(end.velocity(), 86400)))) > 1e6);
    }

    @Test void unavailableFuelPowerAndInvalidOwnershipLeaveStateUnchanged() throws Exception {
        var state = drifting(); var cmd = command(state); var fleet = hunter(state); var ship = fleet.ships().getFirst();
        var empty = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), 100, 0, 0, ship.storedCargoKg()).withPowerState(ship.powerState());
        var dry = state.withFleets(List.of(fleet.withShips(List.of(empty))));
        assertFalse(cmd.validate(dry)); assertSame(dry, cmd.apply(dry));
        var cold = ship.withPowerState(new ShipPowerState(Map.of(), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0));
        var unpowered = state.withFleets(List.of(fleet.withShips(List.of(cold))));
        assertFalse(cmd.validate(unpowered)); assertSame(unpowered, cmd.apply(unpowered));
        assertFalse(new RecoverStellarTravelCommand("enemy", "hunter", cmd.radiusMeters(), cmd.phaseRadians()).validate(state));
        assertFalse(new RecoverStellarTravelCommand("owner", "hunter", Double.NaN, 0).validate(state));
        assertFalse(new RecoverStellarTravelCommand("owner", "hunter", 1, 0).validate(state));
    }

    @Test void cancellationAndSaveLoadPreserveStateAndGravityWithoutFurtherFuelUse() throws Exception {
        var state = drifting(); state = command(state).apply(state);
        var before = hunter(state); var canceled = new CancelOrbitalTravelCommand("owner", "hunter").apply(state);
        assertEquals(before.location().localFlight().vectorFlight(), hunter(canceled).location().localFlight().vectorFlight());
        assertEquals(before.ships(), hunter(canceled).ships());
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var restored = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(canceled, "now", 1, "now")), SaveGame.class)
                .toGameState(0, "RUNNING");
        assertEquals(hunter(canceled), hunter(restored));
        var after = hunter(day(restored)); assertTrue(after.location().localFlight().interrupted());
        assertNotNull(after.location().localFlight().vectorFlight().trajectory().gravity());
        assertEquals(before.ships().getFirst().currentFuelKg(), after.ships().getFirst().currentFuelKg());
        assertNotEquals(before.location().localFlight().vectorFlight().position(), after.location().localFlight().vectorFlight().position());
    }

    @Test void electricalFailureDuringRecoveryRetainsBallisticMotionAndCanBeReplanned() throws Exception {
        var state = drifting(); state = command(state).apply(state); var fleet = hunter(state);
        var ship = fleet.ships().getFirst();
        var cold = ship.withPowerState(new ShipPowerState(Map.of(), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0));
        var before = fleet.location().localFlight().vectorFlight();
        var failed = day(state.withFleets(List.of(fleet.withShips(List.of(cold)))));
        var after = hunter(failed); assertTrue(after.location().localFlight().interrupted());
        assertEquals(ship.currentFuelKg(), after.ships().getFirst().currentFuelKg());
        var actual = after.location().localFlight().vectorFlight();
        assertTrue(norm(subtract(actual.position(), add(before.position(), scale(before.velocity(), 86400)))) > 1e6);
        assertNull(command(failed).preview(failed));
        var supplied = failed.withFleets(List.of(after.withShips(List.of(after.ships().getFirst().withPowerState(ship.powerState())))));
        var plan = command(supplied).preview(supplied); assertNotNull(plan);
        assertEquals(actual.position(), plan.vectorTrajectory().start()); assertEquals(actual.velocity(), plan.vectorTrajectory().initialVelocity());
    }
}
