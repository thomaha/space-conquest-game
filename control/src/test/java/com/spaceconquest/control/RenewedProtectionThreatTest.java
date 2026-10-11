package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RenewedProtectionThreatTest {
    private Fleet fleet(GameState state) { return FleetOrganization.find(state, "hunter"); }
    private GameState replace(GameState state, Fleet fleet) { return FleetReturnTravel.replace(state, fleet); }
    private GameState assigned(GameState state, boolean escort, FleetProtectionAssignment.Stage stage) {
        var fleet = fleet(state); var home = FleetLocation.Site.orbit("earth", 500);
        var guard = new FleetGuardOrder(FleetGuardOrder.TargetType.BODY, "earth", "sol", home);
        var saved = new FleetProtectionAssignment(escort ? null : guard, escort ? "protected" : "", "hunter", home, stage, "old-port");
        return replace(state, fleet.withGuardOrder(null).withEscortOrder(null)
                .withInterceptOrder(new FleetInterceptOrder("owner", "old-target", "sol", saved)));
    }
    private GameState day(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), state.orbitalStations(), List.of()))
                .withTurn(state.turn() + 1);
    }
    private GameState reload(GameState state) throws Exception {
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(state, "now", 1, "now")), SaveGame.class).toGameState(state.turn(), "RUNNING");
    }
    @Test void waitingGuardCanEscapeAgainWithoutReplacingItsOriginalAssignment() throws Exception {
        var initial = assigned(new FleetProtectionTest().world(20), false, FleetProtectionAssignment.Stage.RETURNING);
        var original = fleet(initial).interceptOrder().protection();
        assertEquals(FleetProtectionProcessor.Outcome.ESCAPING, FleetProtectionProcessor.decide(initial, fleet(initial), List.of()).outcome());
        var escaped = FleetProtectionProcessor.advance(initial, List.of());
        assertTrue(fleet(escaped).location().inTransit());
        assertEquals(original.guard(), fleet(escaped).interceptOrder().protection().guard());
        assertEquals(original.origin(), fleet(escaped).interceptOrder().protection().origin());
        assertTrue(fleet(escaped).interceptOrder().protection().escaped());
        assertEquals(escaped.fleets(), reload(escaped).fleets());
    }
    @Test void travelingGuardAndRefuelingEscortRetreatFromActualMotionThenResumeProtection() throws Exception {
        for (boolean escort : List.of(false, true)) {
            var initial = assigned(new MovingFleetProtectionTest().world("earth"), escort,
                    escort ? FleetProtectionAssignment.Stage.REFUELING : FleetProtectionAssignment.Stage.RETURNING);
            if (escort) {
                var ship = fleet(initial).ships().getFirst();
                var protectedShip = new ShipInstance("protected-ship", ship.designId(), "owner", 100, 0, 15000,
                        ship.storedCargoKg()).withPowerState(ship.powerState());
                var body = OrbitalBody.find(initial, "sol", "earth"); var motion = StationRendezvous.at(body, 8000, initial.turn());
                var gravity = new TwoBodyFlight.Gravity(PlanetaryTransferComparison.GRAVITATIONAL_CONSTANT * body.mass(),
                        body.diameter() * 500, body.frame(), body.sphereKm(initial.solarSystems().getFirst()) * 1000);
                var tiny = new LocalSiteGeometry.Point(1e-30, 0, 0);
                var vector = new VectorFlight(new VectorFlight.Trajectory(motion.position(), motion.velocity(), tiny, tiny, 1, 0, gravity),
                        motion.position(), motion.velocity(), 2);
                var geometry = new LocalSiteGeometry.Leg(motion.position(), VectorFlight.add(motion.position(), motion.velocity()), VectorFlight.norm(motion.velocity()));
                var flight = new LocalFlight(geometry, VectorFlight.motion(vector, geometry), Map.of(), Map.of(), true, false, null, vector);
                var convoy = new Fleet("protected", "Protected", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(protectedShip),
                        new FleetLocation(FleetLocation.Site.orbit("earth"), FleetLocation.Site.orbit("earth", 8000), 0, 1, flight));
                var all = new ArrayList<>(initial.fleets()); all.add(convoy); initial = initial.withFleets(all);
            }
            var before = fleet(initial); var motion = before.location().localFlight().vectorFlight();
            var decision = FleetProtectionProcessor.decide(initial, before, List.of());
            assertEquals(FleetProtectionProcessor.Outcome.ESCAPING, decision.outcome());
            var state = FleetProtectionProcessor.advance(initial, List.of()); var retreated = fleet(state);
            assertEquals(motion.position(), retreated.location().localFlight().vectorFlight().position());
            assertEquals(motion.velocity(), retreated.location().localFlight().vectorFlight().velocity());
            assertNotEquals(motion.trajectory(), retreated.location().localFlight().vectorFlight().trajectory());
            assertEquals(before.ships().getFirst().currentFuelKg(), retreated.ships().getFirst().currentFuelKg());
            assertEquals(before.interceptOrder().protection().guard(), retreated.interceptOrder().protection().guard());
            assertEquals(before.interceptOrder().protection().convoyFleetId(), retreated.interceptOrder().protection().convoyFleetId());
            assertEquals("", retreated.interceptOrder().protection().serviceTargetId());
            assertTrue(retreated.interceptOrder().protection().escaped());
            state = reload(state).toBuilder().diplomaticRelations(List.of()).build();
            for (int tick = 0; tick < 15 && fleet(state) != null && fleet(state).guardOrder() == null; tick++)
                state = FleetInterceptProcessor.advance(FleetInterceptProcessor.finishContacts(day(state)));
            if (escort) {
                assertNull(fleet(state)); assertNotNull(FleetOrganization.find(state, "protected").escortOrder());
                assertEquals("hunter", FleetOrganization.find(state, "protected").escortOrder().escortFleetId());
            } else {
                assertNotNull(fleet(state).guardOrder()); assertNull(fleet(state).interceptOrder());
                assertTrue(RecoveredParkingTravel.ready(fleet(state)), "Guard restoration retains actual circular motion.");
                assertTrue(fleet(state).guardOrder().validTarget(state, fleet(state)));
                assertFalse(RecoveredParkingTravel.atSite(fleet(state).location(), FleetLocation.Site.orbit("earth", 1000)));
                assertFalse(RecoveredParkingTravel.atSite(fleet(state).location(), FleetLocation.Site.orbit("moon", 500)));
                assertNotNull(fleet(day(state)).guardOrder());
            }
        }
    }
    @Test void fundedSaferCourseKeepsMotionAndStoresWhileRetainingEscapeReturnIntent() throws Exception {
        var state = assigned(new MovingFleetProtectionTest().world("earth"), false, FleetProtectionAssignment.Stage.REFUELING);
        var fleet = fleet(state);
        var plan = MovingFleetEscape.plan(state, fleet, FleetLocation.Site.orbit("earth", 8000)); assertNotNull(plan);
        fleet = MovingFleetEscape.depart(fleet, FleetLocation.Site.orbit("earth", 8000), plan).withInterceptOrder(fleet.interceptOrder());
        state = replace(state, fleet);
        assertEquals(FleetProtectionProcessor.Outcome.CONTINUE_TO_SAFETY, FleetProtectionProcessor.decide(state, fleet, List.of()).outcome());
        var continued = FleetProtectionProcessor.advance(state, List.of()); var actual = fleet(continued);
        assertEquals(fleet.location(), actual.location()); assertEquals(fleet.ships(), actual.ships());
        assertEquals(fleet.interceptOrder().protection().guard(), actual.interceptOrder().protection().guard());
        assertEquals(FleetProtectionAssignment.Stage.RETURNING, actual.interceptOrder().protection().stage());
        assertEquals("", actual.interceptOrder().protection().serviceTargetId()); assertTrue(actual.interceptOrder().protection().escaped());
        assertEquals(continued.fleets(), reload(continued).fleets());
    }
    @Test void blockedRenewedEscapeKeepsOrdersTrajectoryAndSpentStores() throws Exception {
        var state = assigned(new MovingFleetProtectionTest().world("earth"), false, FleetProtectionAssignment.Stage.RETURNING);
        var fleet = fleet(state); var ships = new ArrayList<>(fleet.ships()); var ship = ships.getLast();
        ships.set(ships.size() - 1, new ShipInstance(ship.id(), ship.designId(), "owner", ship.currentHullHealth(), ship.currentShieldHealth(),
                0, ship.storedCargoKg()).withPowerState(ship.powerState()));
        state = replace(state, fleet.withShips(ships));
        assertEquals(FleetProtectionProcessor.Outcome.ESCAPE_BLOCKED, FleetProtectionProcessor.decide(state, fleet(state), List.of()).outcome());
        assertEquals(state, FleetProtectionProcessor.advance(state, List.of()));
    }
    @Test void explicitPursuitKeepsPrecedence() throws Exception {
        var state = assigned(new MovingFleetProtectionTest().world("earth"), false, FleetProtectionAssignment.Stage.PURSUING);
        state = replace(state, fleet(state).withInterceptOrder(new FleetInterceptOrder("owner", "target", "sol")));
        assertEquals(FleetProtectionProcessor.Outcome.ESCAPE_BLOCKED, FleetProtectionProcessor.decide(state, fleet(state), List.of()).outcome());
        assertEquals(state, FleetProtectionProcessor.advance(state, List.of()));
    }
}
