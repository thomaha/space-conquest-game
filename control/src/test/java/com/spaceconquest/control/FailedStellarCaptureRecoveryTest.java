package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.spaceconquest.engine.ship.VectorFlight.*;

class FailedStellarCaptureRecoveryTest {
    private Fleet fleet(GameState state) { return FleetOrganization.find(state, "hunter"); }
    private GameState coast() throws Exception {
        var state = new OrbitalRendezvousTest().world();
        var hunter = fleet(state); var template = FleetOrganization.find(state, "target").location().orbitalFlight().itinerary();
        var ship = hunter.ships().getFirst();
        var burn = new OrbitalFlight.Burn(10, 1e6, 1e6, ship.currentFuelKg(), null, 0);
        var events = template.maneuvers().stream().map(event -> new OrbitalFlight.Maneuver(event.seconds(), event.auxiliaryHours(),
                Map.of(ship.id(), burn))).toList();
        var itinerary = new OrbitalFlight.Itinerary(template.sourceBodyId(), template.targetBodyId(), template.sourceAltitudeKm(),
                template.targetAltitudeKm(), template.epochDays(), template.waitSeconds(), template.coast(), template.environment(), events, template.frame());
        var location = new FleetLocation(FleetLocation.Site.orbit("earth", 500), FleetLocation.Site.orbit("earth", 1000),
                0, itinerary.totalSeconds() / 86400, null,
                new OrbitalFlight(itinerary, itinerary.coast().coastSeconds() - 43200, 1, OrbitalFlight.Status.COAST, ""));
        return state.withFleets(List.of(hunter.withLocation(location)));
    }
    private GameState day(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()))
                .withTurn(state.turn() + 1);
    }
    private RecoverStellarTravelCommand recovery(GameState state) {
        var actual = fleet(state).location().orbitalFlight().position();
        return new RecoverStellarTravelCommand("owner", "hunter", actual.radiusMeters(), Math.atan2(actual.yMeters(), actual.xMeters()));
    }
    private GameState reload(GameState state) throws Exception {
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(state, "now", 1, "now")), SaveGame.class).toGameState(0, "RUNNING");
    }

    @Test void rejectedCaptureRecoversFromActualBallisticStateThroughPaidTicksAndReload() throws Exception {
        var initial = coast(); double fuel = fleet(initial).ships().getFirst().currentFuelKg();
        var state = day(initial); var orbital = fleet(state).location().orbitalFlight();
        assertEquals(OrbitalFlight.Status.MISSED, orbital.status()); assertEquals(1, orbital.nextManeuver());
        assertEquals(fuel, fleet(state).ships().getFirst().currentFuelKg());
        assertFalse(fleet(state).location().isAt(FleetLocation.Site.orbit("earth", 1000)));
        state = reload(state); assertEquals(orbital, fleet(state).location().orbitalFlight());
        var command = recovery(state); var plan = command.preview(state); assertNotNull(plan);
        var actual = orbital.position();
        assertEquals(new LocalSiteGeometry.Point(actual.xMeters(), actual.yMeters(), 0), plan.vectorTrajectory().start());
        assertEquals(new LocalSiteGeometry.Point(actual.vxMps(), actual.vyMps(), 0), plan.vectorTrajectory().initialVelocity());
        var controller = new HumanController(); controller.stageCommand(command);
        assertEquals(orbital, fleet(state).location().orbitalFlight());
        var launched = controller.getCommandQueue().drainAndExecute(state);
        assertNull(fleet(launched).location().orbitalFlight()); assertNotNull(fleet(launched).location().localFlight());
        assertEquals(fuel, fleet(launched).ships().getFirst().currentFuelKg());
        launched = reload(launched);
        for (int days = 0; days < plan.days(); days++) launched = day(launched);
        var end = fleet(launched).location().localFlight().vectorFlight();
        assertTrue(fleet(launched).location().localFlight().interrupted());
        assertEquals(fuel - plan.propellantKg().get("owner-ship"), fleet(launched).ships().getFirst().currentFuelKg(), .0001);
        assertEquals(command.radiusMeters(), norm(end.position()), 1);
        assertEquals(Math.sqrt(end.trajectory().gravity().gravitationalParameter() / command.radiusMeters()), norm(end.velocity()), .001);
        assertEquals(FleetLocation.Kind.DEEP_SPACE, fleet(launched).location().destination().kind());
        assertEquals(command.radiusMeters(), norm(fleet(day(launched)).location().localFlight().vectorFlight().position()), 1);
    }

    @Test void explicitCancellationEnablesRecoveryWithoutMovingOrRefundingTheFleet() throws Exception {
        var state = coast(); var command = recovery(state); assertFalse(command.validate(state));
        var before = fleet(state); var position = before.location().orbitalFlight().position();
        var canceled = new CancelOrbitalTravelCommand("owner", "hunter").apply(state);
        assertEquals(position, fleet(canceled).location().orbitalFlight().position()); assertEquals(before.ships(), fleet(canceled).ships());
        assertTrue(command.validate(canceled));
        var launched = command.apply(canceled); var vector = fleet(launched).location().localFlight().vectorFlight();
        assertEquals(new LocalSiteGeometry.Point(position.xMeters(), position.yMeters(), 0), vector.position());
        assertEquals(new LocalSiteGeometry.Point(position.vxMps(), position.vyMps(), 0), vector.velocity());
        assertNull(fleet(launched).location().orbitalFlight());
        assertTrue(new CancelOrbitalTravelCommand("owner", "hunter").validate(canceled));
    }

    @Test void unsupportedFramesAndUnfundedCaptureStatesRemainUnchanged() throws Exception {
        var state = day(coast()); var command = recovery(state); var hunter = fleet(state); var ship = hunter.ships().getFirst();
        var empty = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), 100, 0, 0, ship.storedCargoKg()).withPowerState(ship.powerState());
        var dry = state.withFleets(List.of(hunter.withShips(List.of(empty))));
        assertFalse(command.validate(dry)); assertSame(dry, command.apply(dry));
        var observed = hunter.location().orbitalFlight(); var saved = observed.itinerary();
        for (var kind : List.of(OrbitalFlight.CenterKind.PLANETARY, OrbitalFlight.CenterKind.LUNAR)) {
            var itinerary = new OrbitalFlight.Itinerary(saved.sourceBodyId(), saved.targetBodyId(), saved.sourceAltitudeKm(), saved.targetAltitudeKm(),
                    saved.epochDays(), saved.waitSeconds(), saved.coast(), saved.environment(), saved.maneuvers(), new OrbitalFlight.Frame("earth", kind));
            var body = state.withFleets(List.of(hunter.withLocation(hunter.location().withOrbitalFlight(
                    new OrbitalFlight(itinerary, observed.elapsedSeconds(), 1, OrbitalFlight.Status.MISSED, "")))));
            assertFalse(command.validate(body)); assertSame(body, command.apply(body));
        }
        var captured = state.withFleets(List.of(hunter.withLocation(hunter.location().withOrbitalFlight(
                new OrbitalFlight(saved, observed.elapsedSeconds(), 2, OrbitalFlight.Status.APPROACH_FAILED, "")))));
        assertFalse(command.validate(captured)); assertSame(captured, command.apply(captured));
        var waiting = state.withFleets(List.of(hunter.withLocation(hunter.location().withOrbitalFlight(
                new OrbitalFlight(saved, 0, 0, OrbitalFlight.Status.WAITING_FAILED, "")))));
        assertFalse(command.validate(waiting)); assertSame(waiting, command.apply(waiting));
    }
}
