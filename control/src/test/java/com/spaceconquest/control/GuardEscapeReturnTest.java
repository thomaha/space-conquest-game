package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GuardEscapeReturnTest {
    private Fleet guard(GameState state) { return FleetOrganization.find(state, "hunter"); }
    private GameState day(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), state.orbitalStations(), List.of()))
                .withTurn(state.turn() + 1);
    }
    private GameState reload(GameState state) throws Exception {
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(state, "now", 1, "now")), SaveGame.class).toGameState(state.turn(), "RUNNING");
    }
    private GameState arrived(GameState state) {
        for (int tick = 0; tick < 10 && !FleetReturnTravel.available(guard(state)); tick++) state = day(state);
        assertTrue(FleetReturnTravel.available(guard(state))); return state;
    }
    @Test void guardEscapesPhysicallyThenReturnsAfterPeaceAndRestoresSavedAssignment() throws Exception {
        var initial = new FleetProtectionTest().world(20); var original = guard(initial).guardOrder();
        var escaped = FleetProtectionProcessor.advance(initial, List.of());
        assertNull(guard(escaped).guardOrder()); assertTrue(guard(escaped).interceptOrder().protection().escaped());
        assertEquals(original, guard(escaped).interceptOrder().protection().guard());
        assertEquals(escaped.fleets(), reload(escaped).fleets());
        var state = arrived(reload(escaped)); double spent = guard(state).ships().getFirst().currentFuelKg();
        assertTrue(spent < guard(initial).ships().getFirst().currentFuelKg());
        state = state.toBuilder().diplomaticRelations(List.of()).build();
        state = FleetInterceptProcessor.advance(state); assertNull(guard(state).guardOrder());
        assertTrue(guard(state).location().inTransit());
        assertEquals(spent, guard(state).ships().getFirst().currentFuelKg(), .000001);
        state = reload(state);
        for (int tick = 0; tick < 10 && guard(state).guardOrder() == null; tick++) state = FleetInterceptProcessor.advance(day(state));
        assertEquals(original, guard(state).guardOrder()); assertNull(guard(state).interceptOrder());
        assertTrue(guard(state).ships().getFirst().currentFuelKg() < spent);
    }
    @Test void evenWeakerRemainingDetectedThreatDelaysReturnWithoutChangingMotionOrStores() throws Exception {
        var initial = new FleetProtectionTest().world(20);
        var state = arrived(FleetProtectionProcessor.advance(initial, List.of()));
        assertEquals(state, FleetInterceptProcessor.advance(state));
        var enemy = FleetOrganization.find(state, "target");
        state = state.withFleets(List.of(guard(state), enemy.withShips(List.of(enemy.ships().getFirst()))));
        assertEquals(FleetThreatAssessment.Action.HOLD, FleetThreatAssessment.assess(state, guard(state)).action());
        assertEquals(state, FleetInterceptProcessor.advance(state));
        assertTrue(guard(state).interceptOrder().protection().escaped());
    }
    @Test void lostOwnedObjectCancelsSavedReturnWithoutRefundingEscape() throws Exception {
        var state = arrived(FleetProtectionProcessor.advance(new FleetProtectionTest().world(20), List.of()));
        var opening = guard(state);
        state = state.toBuilder().empires(List.of()).diplomaticRelations(List.of()).build();
        state = FleetInterceptProcessor.advance(state);
        assertNull(guard(state).guardOrder()); assertNull(guard(state).interceptOrder());
        assertEquals(opening.location(), guard(state).location()); assertEquals(opening.ships(), guard(state).ships());
    }
    @Test void noFuelWaitsWithSavedEscapeIntentAndExplicitStandDownSupersedesIt() throws Exception {
        var state = arrived(FleetProtectionProcessor.advance(new FleetProtectionTest().world(20), List.of()));
        var fleet = guard(state); var ship = fleet.ships().getFirst();
        var dry = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                0, ship.storedCargoKg(), ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState());
        state = FleetReturnTravel.replace(state.toBuilder().diplomaticRelations(List.of()).build(), fleet.withShips(List.of(dry)));
        assertEquals(state, FleetInterceptProcessor.advance(state)); assertEquals(state.fleets(), reload(state).fleets());
        var command = new SetFleetStanceCommand("hunter", "PASSIVE");
        assertTrue(command.validate(state)); var changed = command.apply(state);
        assertNull(guard(changed).interceptOrder()); assertNull(guard(changed).guardOrder());
        assertEquals(guard(state).location(), guard(changed).location()); assertEquals(guard(state).ships(), guard(changed).ships());
    }
    @Test void olderProtectionRecordsDefaultToOrdinaryPursuitReturn() throws Exception {
        var state = FleetProtectionProcessor.advance(new FleetProtectionTest().world(20), List.of());
        var mapper = new ObjectMapper(); var assignment = guard(state).interceptOrder().protection();
        var json = mapper.valueToTree(assignment); ((com.fasterxml.jackson.databind.node.ObjectNode) json).remove("escaped");
        var older = mapper.treeToValue(json, FleetProtectionAssignment.class);
        assertFalse(older.escaped()); assertEquals(assignment.guard(), older.guard());
    }
    @Test void sharedFuelFundsEscapeReturnWithoutLosingEscapeSafetyMarker() throws Exception {
        var state = arrived(FleetProtectionProcessor.advance(new FleetProtectionTest().world(20), List.of()));
        var fleet = guard(state); var original = fleet.ships().getFirst();
        var empty = new ShipInstance(original.id(), original.designId(), "owner", original.currentHullHealth(), original.currentShieldHealth(),
                0, original.storedCargoKg()).withPowerState(original.powerState());
        var donor = new ShipInstance("donor", original.designId(), "owner", original.currentHullHealth(), original.currentShieldHealth(),
                15000, original.storedCargoKg()).withPowerState(original.powerState());
        fleet = fleet.withShips(List.of(empty, donor));
        state = FleetReturnTravel.replace(state.toBuilder().diplomaticRelations(List.of()).build(), fleet);
        var returned = FleetInterceptProcessor.advance(state);
        assertEquals(2, guard(returned).ships().size()); assertTrue(guard(returned).location().inTransit());
        assertTrue(guard(returned).ships().getFirst().currentFuelKg() > 0);
        assertEquals(15000, guard(returned).ships().stream().mapToDouble(ShipInstance::currentFuelKg).sum(), .000001);
        assertTrue(guard(returned).interceptOrder().protection().escaped());
        assertTrue(guard(returned).interceptOrder().protection().at(FleetProtectionAssignment.Stage.REFUELING, "port").escaped());
    }
    @Test void quietRefugeStillWaitsForKnownThreatAtOriginalGuardSite() throws Exception {
        var state = arrived(FleetProtectionProcessor.advance(new FleetProtectionTest().world(20), List.of()));
        var fleet = guard(state); var home = fleet.interceptOrder().protection().origin();
        var observer = new Fleet("observer", "Observer", "owner", "sol", "", 0, 0, 0, false, "PASSIVE",
                List.of(new ShipInstance("observer-ship", fleet.ships().getFirst().designId(), "owner", 100, 0, 15000,
                        Map.of("fusion_fuel_pellets", 200.0)).withPowerState(fleet.ships().getFirst().powerState())), FleetLocation.at(home));
        fleet = fleet.withLocation(FleetLocation.at(FleetLocation.Site.orbit("moon", 500)));
        state = state.withFleets(List.of(fleet, FleetOrganization.find(state, "target"), observer));
        assertEquals(FleetThreatAssessment.Action.NO_CONTACT, FleetThreatAssessment.assess(state, fleet).action());
        assertFalse(FleetEscapeReturn.clearToReturn(state, fleet, home));
        assertEquals(state, FleetInterceptProcessor.advance(state));
    }
    @Test void disabledDriveGainsPriorityOnlyWhenRepairUnlocksTheCoordinatedEscapeQuote() throws Exception {
        var state = new FleetProtectionTest().world(20); var fleet = guard(state); var ship = fleet.ships().getFirst();
        var design = FleetSupplySimulation.design(state, ship); int drive = design.equippedModuleIds().indexOf("mod_fusion_drive");
        var damaged = ship.withModuleDamage(new ShipModuleDamage(Map.of(drive, 100.0)));
        fleet = fleet.withShips(List.of(damaged)); state = FleetReturnTravel.replace(state, fleet);
        assertEquals(FleetProtectionProcessor.Outcome.ESCAPE_BLOCKED, FleetProtectionProcessor.decide(state, fleet, List.of()).outcome());
        var priority = ShipRepairPriority.context(state, fleet);
        assertEquals(Set.of(drive), priority.mobility().get(damaged.id()));
        assertEquals(100, damaged.moduleDamage().damageByMount().get(drive));
        assertEquals(ship.currentFuelKg(), damaged.currentFuelKg());
        assertEquals(fleet.guardOrder(), guard(state).guardOrder());
        var candidate = fleet.withShips(List.of(damaged.withModuleDamage(ShipModuleDamage.empty())));
        assertEquals(FleetProtectionProcessor.Outcome.ESCAPING,
                FleetProtectionProcessor.decide(FleetReturnTravel.replace(state, candidate), candidate, List.of()).outcome());
    }
}
