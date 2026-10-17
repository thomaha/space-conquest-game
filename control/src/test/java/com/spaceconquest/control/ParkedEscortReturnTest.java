package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ParkedEscortReturnTest {
    private Fleet find(GameState state, String id) { return FleetOrganization.find(state, id); }
    private GameState world(String body) throws Exception {
        var state = new BodyFleetRendezvousTest().world(body, false); var escort = find(state, "hunter");
        var coast = find(state, "target").location(); var site = FleetLocation.Site.orbit(body, 500);
        var saved = new FleetProtectionAssignment(null, "convoy", "hunter", site, FleetProtectionAssignment.Stage.RETURNING, "");
        escort = escort.withLocation(coast).withInterceptOrder(new FleetInterceptOrder("owner", "old-enemy", "sol", saved));
        var ship = escort.ships().getFirst();
        var protectedShip = new ShipInstance("protected", ship.designId(), "owner", 100, 0, ship.currentFuelKg(), ship.storedCargoKg()).withPowerState(ship.powerState());
        var convoy = new Fleet("convoy", "Convoy", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(protectedShip), FleetLocation.at(site));
        return state.withFleets(List.of(escort, convoy)).toBuilder().diplomaticRelations(List.of()).build();
    }
    private GameState reload(GameState state) throws Exception {
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(state, "now", 1, "now")), SaveGame.class).toGameState(state.turn(), "RUNNING");
    }
    private GameState move(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), state.orbitalStations(), List.of()))
                .withTurn(state.turn() + 1);
    }
    @Test void paidEarthAndMoonApproachesMatchParkedConvoysBeforeRestoringEscortMembership() throws Exception {
        for (String body : List.of("earth", "moon")) {
            var initial = world(body); var before = find(initial, "hunter"); var convoy = find(initial, "convoy");
            assertTrue(RecoveredParkingTravel.ready(before)); assertFalse(BodyFleetRendezvous.matches(initial, before, convoy));
            assertEquals(initial, FleetEscortReturn.finishContacts(initial));
            var state = FleetInterceptProcessor.advance(initial); var launched = find(state, "hunter");
            assertEquals("convoy", launched.location().localFlight().rendezvousTargetId());
            assertEquals(before.location().localFlight().vectorFlight().position(), launched.location().localFlight().vectorFlight().position());
            assertEquals(before.location().localFlight().vectorFlight().velocity(), launched.location().localFlight().vectorFlight().velocity());
            assertEquals(before.ships().getFirst().currentFuelKg(), launched.ships().getFirst().currentFuelKg());
            assertEquals(convoy, find(state, "convoy")); assertEquals(state.fleets(), reload(state).fleets());
            state = move(reload(state)); var paid = find(state, "hunter").ships().getFirst();
            assertTrue(paid.currentFuelKg() < before.ships().getFirst().currentFuelKg());
            assertTrue(MovingFleetRendezvous.physicalMatches(state, find(state, "hunter"), find(state, "convoy")));
            assertFalse(MovingFleetRendezvous.matches(state, find(state, "hunter"), find(state, "convoy")));
            var protectedAfterTick = find(state, "convoy").ships().getFirst();
            state = FleetInterceptProcessor.finishContacts(state); var joined = find(state, "convoy");
            assertNull(find(state, "hunter")); assertEquals("hunter", joined.escortOrder().escortFleetId());
            assertEquals(convoy.location(), joined.location()); assertEquals(protectedAfterTick, joined.ships().getFirst());
            assertEquals(paid.currentFuelKg(), joined.ships().getLast().currentFuelKg()); assertEquals(2, joined.ships().size());
            assertEquals(state.fleets(), reload(state).fleets());
        }
    }
    @Test void emptyTankAndDifferentBodyWaitWithoutJoiningOrReplacingActualCoast() throws Exception {
        var initial = world("earth"); var escort = find(initial, "hunter"); var ship = escort.ships().getFirst();
        var empty = new ShipInstance(ship.id(), ship.designId(), "owner", ship.currentHullHealth(), ship.currentShieldHealth(), 0,
                ship.storedCargoKg()).withPowerState(ship.powerState());
        var state = FleetReturnTravel.replace(initial, escort.withShips(List.of(empty)));
        assertNull(FleetEscortReturn.plan(state, find(state, "hunter"), find(state, "convoy")));
        assertEquals(state, FleetInterceptProcessor.advance(state)); assertEquals(state.fleets(), reload(state).fleets());
        state = FleetReturnTravel.replace(initial, find(initial, "convoy").withLocation(FleetLocation.at(FleetLocation.Site.orbit("moon", 500))));
        assertNull(FleetEscortReturn.plan(state, find(state, "hunter"), find(state, "convoy")));
        assertEquals(state, FleetInterceptProcessor.advance(state));
    }
    @Test void convoyMovingAwayAfterLaunchMissesContactAndKeepsPaidReturnIntent() throws Exception {
        var state = FleetInterceptProcessor.advance(world("earth"));
        state = FleetReturnTravel.replace(state, find(state, "convoy").withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth", 1000))));
        state = move(state); var escort = find(state, "hunter");
        assertFalse(MovingFleetRendezvous.physicalMatches(state, escort, find(state, "convoy")));
        var finished = FleetInterceptProcessor.finishContacts(state); var missed = find(finished, "hunter");
        assertNotNull(missed); assertNull(find(finished, "convoy").escortOrder());
        assertEquals(escort.ships(), missed.ships()); assertTrue(missed.location().localFlight().interrupted());
        assertEquals(FleetProtectionAssignment.Stage.RETURNING, missed.interceptOrder().protection().stage());
        assertEquals("convoy", missed.interceptOrder().protection().convoyFleetId());
    }
    @Test void escortRejoinsConvoyAfterRealEvacuationHasFinishedAtParkingOrbit() throws Exception {
        var fixture = new AutomaticPursuitWithdrawalTest();
        var initial = fixture.reinforce(fixture.pursuing(true)).toBuilder().orbitalStations(List.of()).build();
        var state = FleetProtectionProcessor.advance(initial, List.of());
        state = move(reload(state)); assertFalse(find(state, "convoy").location().inTransit());
        var refuge = find(state, "convoy").location();
        state = state.toBuilder().diplomaticRelations(List.of()).build();
        assertNotNull(FleetEscortReturn.convoy(state, find(state, "hunter")), "Parked refuge: " + refuge);
        assertTrue(FleetEscapeReturn.clearToReturn(state, find(state, "hunter"), refuge.current()));
        assertNotNull(BodyFleetRendezvous.plan(state, find(state, "hunter"), find(state, "convoy")), "Funded physical parked approach");
        assertNotNull(FleetEscortReturn.plan(state, find(state, "hunter"), find(state, "convoy")), "Funded complete parked return");
        for (int tick = 0; tick < 15 && find(state, "hunter") != null; tick++) {
            state = FleetInterceptProcessor.finishContacts(move(FleetInterceptProcessor.advance(state)));
        }
        assertNull(find(state, "hunter")); var joined = find(state, "convoy");
        assertTrue(joined.location().stationaryAt(FleetLocation.Site.orbit("earth", 500)));
        assertEquals("hunter", joined.escortOrder().escortFleetId()); assertEquals(3, joined.ships().size());
        assertEquals(state.fleets(), reload(state).fleets());
    }
}
