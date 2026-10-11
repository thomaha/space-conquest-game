package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DetachedEscortConvoyTest {
    private Fleet find(GameState state, String id) { return FleetOrganization.find(state, id); }
    private GameState replace(GameState state, Fleet fleet) { return FleetReturnTravel.replace(state, fleet); }
    private GameState world() throws Exception {
        var fixture = new AutomaticPursuitWithdrawalTest(); return fixture.reinforce(fixture.pursuing(true));
    }
    private GameState reload(GameState state) throws Exception {
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(state, "now", 1, "now")), SaveGame.class)
                .toGameState(state.turn(), "RUNNING");
    }
    private GameState day(GameState state) {
        state = FleetInterceptProcessor.advance(state);
        state = state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), state.orbitalStations(), List.of()))
                .withTurn(state.turn() + 1);
        return FleetInterceptProcessor.finishContacts(state);
    }
    @Test void waitingConvoyEvacuatesWithoutBorrowingAbsentEscortStrengthAndSurvivesSave() throws Exception {
        var initial = world(); var convoy = find(initial, "convoy"); var escort = find(initial, "hunter");
        assertNull(convoy.escortOrder()); assertFalse(MovingFleetEscape.together(initial, convoy, escort));
        assertEquals(List.of("convoy"), FleetThreatAssessment.assess(initial, convoy).friendlyFleetIds());
        var state = FleetProtectionProcessor.advance(initial, List.of()); var escaped = find(state, "convoy");
        assertTrue(escaped.location().inTransit()); assertNull(escaped.escortOrder()); assertNull(escaped.interceptOrder());
        assertEquals(convoy.ships().stream().map(ShipInstance::id).toList(), escaped.ships().stream().map(ShipInstance::id).toList());
        assertEquals("convoy", find(state, "hunter").interceptOrder().protection().convoyFleetId());
        assertEquals(state.fleets(), reload(state).fleets());
        var moved = day(state); assertTrue(find(moved, "convoy").ships().getFirst().currentFuelKg() < convoy.ships().getFirst().currentFuelKg());
        for (var stage : List.of(FleetProtectionAssignment.Stage.RETURNING, FleetProtectionAssignment.Stage.REFUELING)) {
            var recovering = escort.withInterceptOrder(escort.interceptOrder().withProtection(escort.interceptOrder().protection().at(stage, "port")));
            var waiting = reload(replace(initial, recovering));
            assertTrue(find(FleetProtectionProcessor.advance(waiting, List.of()), "convoy").location().inTransit());
        }
    }
    @Test void dryConvoyWaitsAndRemovedForeignOrSupplierRelationshipsDoNotAuthorizeEscape() throws Exception {
        var initial = world(); var convoy = find(initial, "convoy"); var ship = convoy.ships().getFirst();
        var dry = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                0, ship.storedCargoKg()).withPowerState(ship.powerState());
        var state = replace(initial, convoy.withShips(List.of(dry)));
        assertEquals(FleetProtectionProcessor.Outcome.ESCAPE_BLOCKED, FleetProtectionProcessor.decide(state, find(state, "convoy"), List.of()).outcome());
        assertEquals(find(state, "convoy"), find(FleetProtectionProcessor.advance(state, List.of()), "convoy"));
        state = replace(initial, convoy.withInterceptOrder(new FleetInterceptOrder("owner", "target", "sol")));
        assertEquals(find(state, "convoy"), find(FleetProtectionProcessor.advance(state, List.of()), "convoy"));
        var escort = find(initial, "hunter"); var assignment = escort.interceptOrder().protection();
        for (Fleet replacement : List.of(escort.withInterceptOrder(null),
                escort.withInterceptOrder(escort.interceptOrder().withProtection(assignment.at(FleetProtectionAssignment.Stage.SUPPLYING, "recipient"))),
                new Fleet("hunter", "Foreign", "enemy", "sol", "", 0, 0, 0, false, "PASSIVE", escort.ships(), escort.location())
                        .withInterceptOrder(new FleetInterceptOrder("enemy", "target", "sol", assignment)), escort.withShips(List.of()))) {
            state = replace(initial, replacement);
            assertEquals(convoy, find(FleetProtectionProcessor.advance(state, List.of()), "convoy"));
        }
    }
    @Test void movingConvoyEscapesAndReturningEscortRejoinsActualRefugeAfterPeace() throws Exception {
        var initial = world(); var convoy = find(initial, "convoy");
        var coast = find(new BodyFleetRendezvousTest().world("earth", false), "target").location();
        initial = replace(initial, convoy.withLocation(coast));
        var state = FleetProtectionProcessor.advance(initial, List.of()); var escaped = find(state, "convoy");
        assertNotEquals(coast.localFlight().vectorFlight().trajectory(), escaped.location().localFlight().vectorFlight().trajectory());
        assertEquals(coast.localFlight().vectorFlight().position(), escaped.location().localFlight().vectorFlight().position());
        assertEquals(coast.localFlight().vectorFlight().velocity(), escaped.location().localFlight().vectorFlight().velocity());
        state = reload(state).toBuilder().diplomaticRelations(List.of()).build();
        for (int tick = 0; tick < 15 && find(state, "hunter") != null; tick++) state = day(state);
        assertNull(find(state, "hunter")); var joined = find(state, "convoy");
        assertEquals("hunter", joined.escortOrder().escortFleetId()); assertEquals(3, joined.ships().size());
        assertTrue(RecoveredParkingTravel.ready(joined)); assertEquals(state.fleets(), reload(state).fleets());
    }
}
