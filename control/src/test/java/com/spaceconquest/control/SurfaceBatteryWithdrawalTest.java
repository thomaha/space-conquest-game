package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SurfaceBatteryWithdrawalTest {
    private GameState world() throws Exception {
        var state = new FleetProtectionTest().world(1);
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var observerDesign = state.shipDesigns().stream().filter(d -> d.ownerEntityId().equals("enemy")).findFirst().orElseThrow();
        ObjectNode json = mapper.valueToTree(observerDesign); var modules = (ArrayNode) json.get("equippedModuleIds"); modules.removeAll();
        observerDesign.equippedModuleIds().stream().filter(id -> CombatModuleCatalog.module(observerDesign, id) == null
                || !CombatModuleCatalog.module(observerDesign, id).operationalStats().containsKey("weaponDamage")).forEach(modules::add);
        var observer = mapper.treeToValue(json, ShipDesign.class);
        state = state.withShipDesigns(state.shipDesigns().stream().map(d -> d.id().equals(observer.id()) ? observer : d).toList());
        var weapon = CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER); var batteries = new ArrayList<AuxiliaryForces.OwnedBattery>();
        for (int i = 0; i < 2; i++) {
            var stores = new AuxiliaryWeaponStores(Map.of(weapon.ammunitionId(), 20 * weapon.operationalStats().get("ammunitionMassKg")),
                    20 * (weapon.powerDrawKw() / 60 + weapon.operationalStats().get("weaponSalvoKwh")));
            batteries.add(new AuxiliaryForces.OwnedBattery("enemy", new PlanetaryDefenseBattery("battery-" + i, "earth",
                    CombatModuleCatalog.application(weapon.id()).id(), 1, true).withStores(stores)));
        }
        return state.toBuilder().auxiliaryForces(new AuxiliaryForces(List.of(), batteries)).build();
    }
    private Fleet hunter(GameState state) { return FleetOrganization.find(state, "hunter"); }
    private GameState fight(GameState state) { return SurfaceBatteryEncounterResolver.resolve(state, new TacticalCombatProcessor(new Random(42))); }
    @Test void fundedWithdrawalEndsGroupedCombatAfterOneRoundAndRetainsGuardAndPaidStores() throws Exception {
        var initial = world(); var result = fight(initial); var departed = hunter(result);
        assertTrue(departed.location().inTransit()); assertEquals(1, result.fleetEngagements().size());
        assertEquals(1, result.fleetEngagements().getFirst().rounds());
        assertEquals(hunter(initial).guardOrder(), departed.interceptOrder().protection().guard());
        assertTrue(departed.interceptOrder().protection().escaped());
        assertTrue(result.fleetEngagements().getFirst().surfaceCombat().fleetWithdrew());
        assertTrue(result.fleetEngagements().getFirst().surfaceCombat().batteries().stream().allMatch(b -> b.paidShots() == 1));
        for (int i = 0; i < 2; i++) {
            var before = initial.auxiliaryForces().batteries().get(i).battery(); var after = result.auxiliaryForces().batteries().get(i).battery();
            assertEquals(before.stores().ammunitionKg().get(before.weapon().ammunitionId()) - before.weapon().operationalStats().get("ammunitionMassKg"),
                    after.stores().ammunitionKg().get(after.weapon().ammunitionId()));
            assertTrue(after.stores().energyKwh() < before.stores().energyKwh());
        }
        assertEquals(initial.shipDesigns(), result.shipDesigns()); assertEquals(initial.fleets().size(), result.fleets().size());
        var moving = result.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(result), result.orbitalStations(), List.of()));
        assertTrue(hunter(moving).ships().getFirst().currentFuelKg() < hunter(initial).ships().getFirst().currentFuelKg());
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var loaded = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(result, "now", 1, "now")), SaveGame.class).toGameState(0, "RUNNING");
        assertEquals(result.fleets(), loaded.fleets()); assertEquals(result.auxiliaryForces(), loaded.auxiliaryForces());
        assertEquals(result.fleetEngagements(), loaded.fleetEngagements());
    }
    @Test void emptyDriveTankBlocksWithdrawalAndKeepsTheBattleRunning() throws Exception {
        var state = world(); var fleet = hunter(state); var original = fleet.ships().getFirst();
        var dry = new ShipInstance(original.id(), original.designId(), original.ownerEntityId(), original.currentHullHealth(),
                original.currentShieldHealth(), 0, original.storedCargoKg()).withPowerState(original.powerState());
        state = FleetReturnTravel.replace(state, fleet.withShips(List.of(dry))); var result = fight(state);
        assertTrue(result.fleetEngagements().getFirst().rounds() > 1);
        if (hunter(result) != null) { assertFalse(hunter(result).location().inTransit()); assertNull(hunter(result).interceptOrder()); }
    }
    @Test void exhaustedBatteriesDoNotTriggerWithdrawalUsingTheirOriginalStores() throws Exception {
        var state = world();
        for (var owned : state.auxiliaryForces().batteries()) {
            var battery = owned.battery(); var weapon = battery.weapon();
            var stores = new AuxiliaryWeaponStores(Map.of(weapon.ammunitionId(), weapon.operationalStats().get("ammunitionMassKg")),
                    weapon.powerDrawKw() / 60 + weapon.operationalStats().get("weaponSalvoKwh"));
            state = state.toBuilder().auxiliaryForces(state.auxiliaryForces().withBattery(battery.withStores(stores))).build();
        }
        var result = fight(state);
        assertTrue(result.fleetEngagements().getFirst().rounds() > 1);
        assertFalse(hunter(result).location().inTransit()); assertNull(hunter(result).interceptOrder());
        result.auxiliaryForces().batteries().forEach(b -> assertTrue(b.battery().stores().ammunitionKg().isEmpty()));
    }
}
