package com.spaceconquest.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CombatWithdrawalTest {
    private Fleet own(GameState state) { return FleetOrganization.find(state, "hunter"); }
    private GameState world(int enemies) throws Exception {
        var state = new FleetProtectionTest().world(enemies);
        var enemy = FleetOrganization.find(state, "target").withLocation(own(state).location());
        return FleetReturnTravel.replace(state, enemy);
    }
    private GameState fight(GameState state) {
        return new TacticalFleetEncounterResolver(new TacticalCombatProcessor(new Random(42)))
                .resolveEncounters(state);
    }
    private GameState day(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state),
                state.orbitalStations(), List.of())).withTurn(state.turn() + 1);
    }

    @Test void simultaneousDamageTriggersWithdrawalBeforeTheSecondRound() throws Exception {
        var initial = world(2);
        assertEquals(FleetThreatAssessment.Action.HOLD, FleetThreatAssessment.assess(initial, own(initial)).action());
        var result = fight(initial);
        assertEquals(1, result.fleetEngagements().getFirst().rounds());
        assertTrue(own(result).location().inTransit());
        assertTrue(own(result).ships().getFirst().currentHullHealth() < own(initial).ships().getFirst().currentHullHealth());
        assertTrue(FleetOrganization.find(result, "target").ships().getFirst().currentHullHealth()
                < FleetOrganization.find(initial, "target").ships().getFirst().currentHullHealth());
        assertEquals(own(initial).guardOrder(), own(result).interceptOrder().protection().guard());
        assertTrue(own(result).interceptOrder().protection().escaped());
        assertEquals(100, own(initial).ships().getFirst().currentHullHealth());
    }

    @Test void blockedFuelQuoteKeepsFightingAndDoesNotCreateAnEscapeOrder() throws Exception {
        var initial = world(3); var ship = own(initial).ships().getFirst();
        var dry = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(),
                ship.currentShieldHealth(), 0, ship.storedCargoKg()).withPowerState(ship.powerState());
        initial = FleetReturnTravel.replace(initial, own(initial).withShips(List.of(dry)));
        var result = fight(initial);
        assertTrue(result.fleetEngagements().getFirst().rounds() > 1);
        if (own(result) != null) {
            assertFalse(own(result).location().inTransit());
            assertNull(own(result).interceptOrder());
            assertEquals(own(initial).guardOrder(), own(result).guardOrder());
        }
    }

    @Test void nearbySupportEscapesTogetherAndRetainsItsOriginalEscortMembership() throws Exception {
        var initial = world(6); var ship = own(initial).ships().getFirst();
        var supportShip = new ShipInstance("support-ship", ship.designId(), "owner", 100, 0,
                ship.currentFuelKg(), ship.storedCargoKg()).withPowerState(ship.powerState());
        var support = new Fleet("support", "Support", "owner", "sol", "", 0, 0, 0, false,
                "ESCORT", List.of(supportShip), own(initial).location())
                .withEscortOrder(new FleetEscortOrder("owner", "old-escort", "Escort", "PASSIVE", List.of("support-ship")));
        var fleets = new ArrayList<>(initial.fleets()); fleets.add(support); initial = initial.withFleets(fleets);
        var result = fight(initial); var departed = FleetOrganization.find(result, "support");
        assertEquals(1, result.fleetEngagements().size());
        assertTrue(own(result).location().inTransit()); assertTrue(departed.location().inTransit());
        assertEquals(own(result).location().destination(), departed.location().destination());
        assertEquals(support.escortOrder(), departed.escortOrder());
        assertEquals(support.escortOrder(), departed.interceptOrder().protection().repairIntent().escort());
        assertEquals(100, departed.ships().getFirst().currentHullHealth());
    }

    @Test void combatCanSuspendExplicitPursuitAndSaveItsMissionWithoutChangingOrdinaryOrderPrecedence() throws Exception {
        var initial = world(3);
        var pursuit = new FleetInterceptOrder("owner", "target", "sol");
        initial = FleetReturnTravel.replace(initial, own(initial).withGuardOrder(null).withInterceptOrder(pursuit));
        assertEquals(FleetProtectionProcessor.Outcome.ESCAPE_BLOCKED,
                FleetProtectionProcessor.decide(initial, own(initial), List.of()).outcome());
        var result = fight(initial);
        assertTrue(own(result).location().inTransit());
        assertEquals(pursuit, own(result).interceptOrder().protection().repairIntent().intercept());
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var loaded = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(result, "now", 1, "now")),
                SaveGame.class).toGameState(result.turn(), "RUNNING");
        assertEquals(result.fleets(), loaded.fleets());
    }

    @Test void paidEscapeReturnsAndRestoresGuardAfterPeace() throws Exception {
        var initial = world(2); var result = fight(initial);
        for (int tick = 0; tick < 10 && own(result).location().inTransit(); tick++) result = day(result);
        assertFalse(own(result).location().inTransit());
        assertTrue(own(result).ships().getFirst().currentFuelKg() < own(initial).ships().getFirst().currentFuelKg());
        result = result.toBuilder().diplomaticRelations(List.of()).build();
        for (int tick = 0; tick < 20 && own(result).guardOrder() == null; tick++)
            result = FleetInterceptProcessor.advance(day(result));
        assertEquals(own(initial).guardOrder(), own(result).guardOrder());
        assertNull(own(result).interceptOrder());
    }

    @Test void missingCurrentSensorsDoNotAuthorizeWithdrawalUsingHiddenStrength() throws Exception {
        var initial = world(3); var ship = own(initial).ships().getFirst();
        var design = initial.shipDesigns().stream().filter(item -> item.id().equals(ship.designId())).findFirst().orElseThrow();
        var damage = new HashMap<Integer, Double>();
        for (int mount = 0; mount < design.equippedModuleIds().size(); mount++)
            if (SensorModuleCatalog.module(design.equippedModuleIds().get(mount)) != null) damage.put(mount, 100.0);
        initial = FleetReturnTravel.replace(initial, own(initial).withShips(List.of(ship.withModuleDamage(new ShipModuleDamage(damage)))));
        assertEquals(FleetThreatAssessment.Action.NO_CONTACT, FleetThreatAssessment.assess(initial, own(initial)).action());
        var result = fight(initial);
        assertTrue(result.fleetEngagements().getFirst().rounds() > 1);
        if (own(result) != null) assertFalse(own(result).location().inTransit());
    }
}
