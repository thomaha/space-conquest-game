package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AutomaticPursuitWithdrawalTest {
    private Fleet hunter(GameState state) { return FleetOrganization.find(state, "hunter"); }
    private GameState replace(GameState state, Fleet fleet) { return FleetReturnTravel.replace(state, fleet); }
    private ShipInstance copy(ShipInstance ship, String id, double health, double fuel) {
        return new ShipInstance(id, ship.designId(), ship.ownerEntityId(), health, ship.currentShieldHealth(), fuel,
                ship.storedCargoKg()).withPowerState(ship.powerState());
    }
    private GameState pursuing() throws Exception {
        return pursuing(false);
    }
    GameState pursuing(boolean escort) throws Exception {
        var state = new ProtectionInterceptTest().world();
        var enemy = FleetOrganization.find(state, "target");
        var observed = FleetOrganization.find(new BodyFleetRendezvousTest().world("earth", false), "target");
        state = replace(state, enemy.withLocation(observed.location()));
        if (escort) {
            var original = hunter(state); var ships = new ArrayList<>(original.ships()); var ship = ships.getFirst();
            ships.add(copy(ship, "protected", ship.currentHullHealth(), ship.currentFuelKg()));
            var convoy = new Fleet("convoy", "Convoy", "owner", "sol", "", 0, 0, 0, false, "ESCORT", ships, original.location())
                    .withEscortOrder(new FleetEscortOrder("owner", "hunter", "Hunter", "PASSIVE", original.ships().stream().map(ShipInstance::id).toList()));
            state = state.withFleets(List.of(convoy, FleetOrganization.find(state, "target")));
        }
        state = FleetProtectionProcessor.advance(state, List.of());
        assertEquals(FleetProtectionAssignment.Stage.PURSUING, hunter(state).interceptOrder().protection().stage());
        assertNotNull(hunter(state).location().localFlight());
        var flight = hunter(state).location().localFlight();
        double hours = flight.vectorFlight() == null ? .001 : Math.min(.001, flight.vectorFlight().trajectory().burnSeconds() / 7200);
        state = replace(state, LocalFlightProcessor.advance(hunter(state), hours, false));
        // A fresh hostile coast enters firing range after the defensive departure has committed.
        var close = FleetOrganization.find(new BodyFleetRendezvousTest().world("earth", false, -.005), "target");
        state = replace(state, FleetOrganization.find(state, "target").withLocation(close.location()));
        assertTrue(FleetThreatAssessment.available(hunter(state)));
        assertTrue(ShipWeaponRange.reaches(state, FleetOrganization.find(state, "target"), hunter(state)),
                "Withdrawal requires currently reachable enemy fire, not potential strength outside weapon range");
        assertTrue(FleetThreatAssessment.engagementStrength(state, List.of(FleetOrganization.find(state, "target")), List.of(hunter(state))) > 0);
        return state;
    }
    GameState reinforce(GameState state) {
        var enemy = FleetOrganization.find(state, "target"); var ship = enemy.ships().getFirst();
        var ships = new ArrayList<ShipInstance>();
        for (int i = 0; i < 20; i++) ships.add(copy(ship, "enemy-" + i, ship.currentHullHealth(), ship.currentFuelKg()));
        return replace(state, enemy.withShips(ships));
    }
    private GameState reload(GameState state) throws Exception {
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(state, "now", 1, "now")), SaveGame.class)
                .toGameState(state.turn(), "RUNNING");
    }
    @Test void distantOrUnfundedEnemyFireDoesNotTriggerWithdrawal() throws Exception {
        var initial = reinforce(pursuing());
        var enemy = FleetOrganization.find(initial, "target");
        var distant = FleetOrganization.find(new BodyFleetRendezvousTest().world("earth", false), "target");
        var outside = replace(initial, enemy.withLocation(distant.location()));
        assertFalse(ShipWeaponRange.reaches(outside, FleetOrganization.find(outside, "target"), hunter(outside)));
        assertEquals(outside, FleetProtectionProcessor.advance(outside, List.of()));
        var empty = replace(initial, enemy.withShips(enemy.ships().stream().map(ship -> new ShipInstance(ship.id(), ship.designId(),
                ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(), ship.currentFuelKg(),
                Map.of("fusion_fuel_pellets", ship.storedCargoKg().get("fusion_fuel_pellets"))).withPowerState(ship.powerState())).toList()));
        assertEquals(0, FleetThreatAssessment.engagementStrength(empty, List.of(FleetOrganization.find(empty, "target")), List.of(hunter(empty))));
        assertEquals(empty, FleetProtectionProcessor.advance(empty, List.of()));
    }
    @Test void enemyReinforcementsTriggerPaidWithdrawalAndLaterGuardRestoration() throws Exception {
        var initial = pursuing(); var fleet = hunter(initial); var original = fleet.interceptOrder().protection();
        assertEquals(FleetProtectionProcessor.Outcome.HOLD, FleetProtectionProcessor.decide(initial, fleet, List.of()).outcome());
        assertEquals(initial, FleetProtectionProcessor.advance(initial, List.of()));
        initial = reinforce(initial); fleet = hunter(initial);
        var decision = FleetProtectionProcessor.decide(initial, fleet, List.of());
        assertEquals(FleetProtectionProcessor.Outcome.ESCAPING, decision.outcome());
        var state = FleetProtectionProcessor.advance(initial, List.of()); var withdrawn = hunter(state);
        assertEquals(original.guard(), withdrawn.interceptOrder().protection().guard());
        assertEquals(original.homeFleetId(), withdrawn.interceptOrder().protection().homeFleetId());
        assertEquals(original.origin(), withdrawn.interceptOrder().protection().origin());
        assertEquals(FleetProtectionAssignment.Stage.RETURNING, withdrawn.interceptOrder().protection().stage());
        assertTrue(withdrawn.interceptOrder().protection().escaped());
        assertEquals(fleet.location().localFlight().vectorFlight().position(), withdrawn.location().localFlight().vectorFlight().position());
        assertEquals(fleet.location().localFlight().vectorFlight().velocity(), withdrawn.location().localFlight().vectorFlight().velocity());
        assertEquals(fleet.ships().getFirst().currentFuelKg(), withdrawn.ships().getFirst().currentFuelKg());
        assertTrue(withdrawn.ships().getFirst().storedCargoKg().get("fusion_fuel_pellets") < fleet.ships().getFirst().storedCargoKg().get("fusion_fuel_pellets"));
        assertEquals(state.fleets(), reload(state).fleets());
        assertEquals(withdrawn, hunter(FleetInterceptProcessor.advance(state)), "Withdrawal must not resume hostile pursuit.");
        state = reload(state).toBuilder().diplomaticRelations(List.of()).build();
        for (int tick = 0; tick < 15 && hunter(state).guardOrder() == null; tick++) {
            state = FleetInterceptProcessor.advance(state);
            state = state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), state.orbitalStations(), List.of()))
                    .withTurn(state.turn() + 1);
            state = FleetInterceptProcessor.finishContacts(state);
        }
        state = FleetInterceptProcessor.advance(state);
        assertEquals(original.guard(), hunter(state).guardOrder()); assertNull(hunter(state).interceptOrder());
        assertTrue(hunter(state).ships().getFirst().currentFuelKg() < fleet.ships().getFirst().currentFuelKg());
        assertEquals(state.fleets(), reload(state).fleets());
    }
    @Test void friendlyDamageChangesAssessmentAndUnfundedWithdrawalIsAtomic() throws Exception {
        var state = pursuing(); var fleet = hunter(state);
        state = replace(state, fleet.withShips(fleet.ships().stream().map(ship -> copy(ship, ship.id(), 1, ship.currentFuelKg())).toList()));
        assertEquals(FleetThreatAssessment.Action.ESCAPE, FleetThreatAssessment.assess(state, hunter(state)).action());
        assertEquals(FleetProtectionProcessor.Outcome.ESCAPING, FleetProtectionProcessor.decide(state, hunter(state), List.of()).outcome());
        assertEquals(FleetProtectionAssignment.Stage.RETURNING, hunter(FleetProtectionProcessor.advance(state, List.of())).interceptOrder().protection().stage());
        state = reinforce(pursuing()); fleet = hunter(state); var ships = new ArrayList<>(fleet.ships()); var dry = ships.getLast();
        ships.set(ships.size() - 1, copy(dry, dry.id(), dry.currentHullHealth(), 0)); state = replace(state, fleet.withShips(ships));
        assertEquals(FleetProtectionProcessor.Outcome.ESCAPE_BLOCKED, FleetProtectionProcessor.decide(state, hunter(state), List.of()).outcome());
        assertEquals(state, FleetProtectionProcessor.advance(state, List.of()));
    }
    @Test void detachedDefensiveEscortKeepsOriginalConvoyAndSavedIdentityThroughWithdrawal() throws Exception {
        var state = reinforce(pursuing(true)); var before = hunter(state); var convoy = FleetOrganization.find(state, "convoy");
        assertEquals("convoy", before.interceptOrder().protection().convoyFleetId());
        var withdrawn = FleetProtectionProcessor.advance(state, List.of()); var after = hunter(withdrawn);
        assertEquals(FleetProtectionAssignment.Stage.RETURNING, after.interceptOrder().protection().stage());
        assertTrue(after.interceptOrder().protection().escaped());
        assertEquals("convoy", after.interceptOrder().protection().convoyFleetId());
        assertEquals("hunter", after.interceptOrder().protection().homeFleetId());
        assertNotEquals(convoy.location(), FleetOrganization.find(withdrawn, "convoy").location());
        assertTrue(FleetOrganization.find(withdrawn, "convoy").location().inTransit());
        assertEquals(convoy.ships().stream().map(ShipInstance::id).toList(),
                FleetOrganization.find(withdrawn, "convoy").ships().stream().map(ShipInstance::id).toList());
        assertEquals(withdrawn.fleets(), reload(withdrawn).fleets());
    }
    @Test void explicitPursuitAndSupplyOrdersBlockCoordinatedWithdrawal() throws Exception {
        var state = reinforce(pursuing()); var fleet = hunter(state);
        var supportShip = copy(fleet.ships().getFirst(), "support-ship", 100, fleet.ships().getFirst().currentFuelKg());
        var support = new Fleet("support", "Support", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(supportShip), fleet.location())
                .withInterceptOrder(new FleetInterceptOrder("owner", "target", "sol"));
        var all = new ArrayList<>(state.fleets()); all.add(support); state = state.withFleets(all);
        assertEquals(FleetProtectionProcessor.Outcome.ESCAPE_BLOCKED, FleetProtectionProcessor.decide(state, hunter(state), List.of()).outcome());
        assertEquals(state, FleetProtectionProcessor.advance(state, List.of()));
        state = state.withFleets(state.fleets().stream().filter(member -> !member.id().equals("support")).toList());
        var assignment = fleet.interceptOrder().protection().at(FleetProtectionAssignment.Stage.SUPPLYING, "recipient");
        state = replace(state, fleet.withInterceptOrder(fleet.interceptOrder().withProtection(assignment)));
        assertEquals(FleetProtectionProcessor.Outcome.ESCAPE_BLOCKED, FleetProtectionProcessor.decide(state, hunter(state), List.of()).outcome());
        assertEquals(state, FleetProtectionProcessor.advance(state, List.of()));
        state = replace(state, hunter(state).withInterceptOrder(new FleetInterceptOrder("owner", "target", "sol")));
        assertEquals(state, FleetProtectionProcessor.advance(state, List.of()));
    }
}
