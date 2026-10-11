package com.spaceconquest.engine.ship;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.engine.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KineticPointDefenseTest {
    private static final double HOURS = 1.0 / 60;
    private final ShipPointDefenseTest fixture = new ShipPointDefenseTest();
    private ShipInstance ammo(ShipInstance ship, double mass) {
        return new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), ship.currentHullHealth(), ship.currentShieldHealth(),
                ship.currentFuelKg(), Map.of(ShipPointDefense.AMMUNITION, mass, "steel", 10.0), ship.passengerCount(), ship.passengerRaceId(),
                ship.transitMode(), ship.powerState(), ship.supplyState(), ship.moduleDamage());
    }
    private ShipCombatPower.Result pay(ShipInstance ship, ShipDesign design, List<Double> distances) {
        return ShipCombatPower.round(ship, design, HOURS, false, false, 0, distances);
    }

    @Test void wholeCartridgesAndExactEnergyLimitDuplicateMountsAndDepletion() {
        var design = fixture.design("defender", 1000, CombatModuleCatalog.KINETIC_POINT_DEFENSE, CombatModuleCatalog.KINETIC_POINT_DEFENSE);
        var original = ammo(fixture.ship("guard", design, 0), 12);
        var paid = pay(original, design, List.of(0.0, 0.0, 0.0, 0.0));
        assertEquals(2, paid.pointDefenseInterceptions()); assertEquals(1, paid.paidPointDefenseKwh());
        assertEquals(2, paid.ship().storedCargoKg().get(ShipPointDefense.AMMUNITION));
        assertEquals(10, paid.ship().storedCargoKg().get("steel")); assertEquals(0, paid.firepower());
        assertEquals(10 - (32 * HOURS + 1) / 6000, paid.ship().generatorFuelMassKg(), 1e-12);
        assertEquals(0, pay(paid.ship(), design, List.of(0.0)).pointDefenseInterceptions());
        assertEquals(0, ShipPointDefense.capacity(design, paid.ship()));
        assertEquals(12, original.storedCargoKg().get(ShipPointDefense.AMMUNITION));
        assertEquals(0, ShipWeaponRange.maximumRange(design, original));
        assertEquals(0, ShipCombatPower.fundedFirepower(original, design, HOURS, true));
    }

    @Test void kineticsKeepTheirShorterRangeAndOverflowStillHitsNormally() {
        var design = fixture.design("defender", 1000, CombatModuleCatalog.KINETIC_POINT_DEFENSE);
        var source = ammo(fixture.ship("guard", design, 0), 20);
        assertEquals(0, pay(source, design, List.of(25001.0)).pointDefenseInterceptions());
        assertEquals(1, pay(source, design, List.of(25000.0)).pointDefenseInterceptions());
        var result = fixture.fight(4, 0, design, List.of(source), true);
        assertEquals(180, result.roundReports().get(1).attackerDamageDealt());
        assertEquals(820, result.survivingDefenderFleet().ships().getFirst().currentHullHealth());
        assertEquals(5, result.survivingDefenderFleet().ships().getFirst().storedCargoKg().get(ShipPointDefense.AMMUNITION));
        var farther = fixture.fight(1, 650000, design, List.of(source), true);
        assertTrue(farther.roundReports().get(1).detailedActions().stream().noneMatch(a -> a.phase().equals("POINT_DEFENSE")));
        assertTrue(farther.roundReports().get(2).detailedActions().stream().anyMatch(a -> a.phase().equals("POINT_DEFENSE")));
    }

    @Test void scarcePowerNeverConsumesFractionalAmmunitionAndDamageReducesCapacity() {
        var insufficient = fixture.design("defender", 32, CombatModuleCatalog.KINETIC_POINT_DEFENSE); // 0.25 kWh after upkeep.
        var source = ammo(fixture.ship("guard", insufficient, 0), 20);
        var denied = pay(source, insufficient, List.of(0.0));
        assertEquals(0, denied.pointDefenseInterceptions()); assertEquals(0, denied.paidPointDefenseKwh());
        assertEquals(source.storedCargoKg(), denied.ship().storedCargoKg());
        assertEquals(10 - 17 * HOURS / 6000, denied.ship().generatorFuelMassKg(), 1e-12);
        var limited = fixture.design("defender", 47, CombatModuleCatalog.KINETIC_POINT_DEFENSE);
        assertEquals(1, pay(source, limited, List.of(0.0, 0.0)).pointDefenseInterceptions());
        var full = fixture.design("defender", 1000, CombatModuleCatalog.KINETIC_POINT_DEFENSE);
        assertEquals(1, pay(source.withModuleDamage(new ShipModuleDamage(Map.of(0, 50.0))), full, List.of(0.0, 0.0, 0.0)).pointDefenseInterceptions());
        assertEquals(0, pay(source.withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0))), full, List.of(0.0)).pointDefenseInterceptions());
    }

    @Test void mixedTypesShareTargetsAndCheapKineticShotsCanUsePowerUnavailableToLaser() {
        var full = fixture.design("defender", 1000, CombatModuleCatalog.LASER_POINT_DEFENSE, CombatModuleCatalog.KINETIC_POINT_DEFENSE);
        var source = ammo(fixture.ship("guard", full, 0), 20);
        var mixed = pay(source, full, List.of(0.0, 0.0, 0.0, 40000.0));
        assertEquals(3, mixed.pointDefenseInterceptions()); assertEquals(2.5, mixed.paidPointDefenseKwh());
        assertEquals(15, mixed.ship().storedCargoKg().get(ShipPointDefense.AMMUNITION));
        var limited = fixture.design("defender", 67, CombatModuleCatalog.LASER_POINT_DEFENSE, CombatModuleCatalog.KINETIC_POINT_DEFENSE);
        var fallback = pay(source, limited, List.of(0.0)); // 0.5 kWh after 37 kW upkeep.
        assertEquals(1, fallback.pointDefenseInterceptions()); assertEquals(.5, fallback.paidPointDefenseKwh());
        assertEquals(CombatModuleCatalog.KINETIC_POINT_DEFENSE, fallback.pointDefenseShots().getFirst().weaponId());
        var result = fixture.fight(4, 0, full, List.of(source), true);
        assertTrue(result.roundReports().stream().allMatch(r -> r.attackerDamageDealt() == 0));
        assertEquals(10, result.survivingDefenderFleet().ships().getFirst().storedCargoKg().get(ShipPointDefense.AMMUNITION));
    }

    @Test void saveLoadRetainsSpentCartridgesAndContinuesFromActualStores() throws Exception {
        var design = fixture.design("defender", 1000, CombatModuleCatalog.KINETIC_POINT_DEFENSE);
        var paid = pay(ammo(fixture.ship("guard", design, 0), 12), design, List.of(0.0));
        var state = GameState.builder().shipDesigns(List.of(design)).fleets(List.of(fixture.fleet("defender", List.of(paid.ship())))).build();
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var restored = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(state, "now", 1, "now")), SaveGame.class).toGameState(0, "RUNNING");
        assertEquals(state.fleets(), restored.fleets());
        var next = pay(restored.fleets().getFirst().ships().getFirst(), restored.shipDesigns().getFirst(), List.of(0.0, 0.0));
        assertEquals(1, next.pointDefenseInterceptions()); assertEquals(2, next.ship().storedCargoKg().get(ShipPointDefense.AMMUNITION));
    }
}
