package com.spaceconquest.engine.ship;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.engine.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IonPointDefenseTest {
    private static final double HOURS = 1.0 / 60;
    private final ShipPointDefenseTest fixture = new ShipPointDefenseTest();

    private ShipCombatPower.Result pay(ShipInstance ship, ShipDesign design, List<Double> distances) {
        return ShipCombatPower.round(ship, design, HOURS, false, false, 0, distances);
    }

    @Test void ionRangeIsInclusiveAndPaidWholeShotsLeaveCargoAndOriginalStoresUnchanged() {
        var design = fixture.design("defender", 1000, CombatModuleCatalog.ION_POINT_DEFENSE);
        var original = fixture.ship("guard", design, 100);
        var paid = pay(original, design, List.of(40_000.0, 40_001.0, Double.NaN, -1.0));
        assertEquals(1, paid.pointDefenseInterceptions()); assertEquals(2, paid.paidPointDefenseKwh());
        assertEquals(CombatModuleCatalog.ION_POINT_DEFENSE, paid.pointDefenseShots().getFirst().weaponId());
        assertEquals(original.storedCargoKg(), paid.ship().storedCargoKg());
        assertEquals(10 - (32 * HOURS + 2) / 6000, paid.ship().generatorFuelMassKg(), 1e-12);
        assertEquals(10, original.generatorFuelMassKg());
        assertEquals(0, ShipWeaponRange.maximumRange(design, original));
        assertEquals(0, ShipCombatPower.fundedFirepower(original, design, HOURS, true));
        assertEquals(0, pay(original, design, List.of()).paidPointDefenseKwh());
    }

    @Test void overflowImpactsAndDisabledOrDamagedMountsUseOrdinaryDamageRules() {
        var design = fixture.design("defender", 1000, CombatModuleCatalog.ION_POINT_DEFENSE);
        var original = fixture.ship("guard", design, 0);
        var result = fixture.fight(3, 0, design, List.of(original), true);
        assertEquals(180, result.roundReports().get(1).attackerDamageDealt());
        assertEquals(820, result.survivingDefenderFleet().ships().getFirst().currentHullHealth());
        assertTrue(result.roundReports().get(1).detailedActions().stream()
                .anyMatch(a -> a.phase().equals("POINT_DEFENSE") && a.description().contains(CombatModuleCatalog.ION_POINT_DEFENSE)));
        var half = original.withModuleDamage(new ShipModuleDamage(Map.of(0, 50.0)));
        assertEquals(1, pay(half, design, List.of(0.0, 0.0)).pointDefenseInterceptions());
        var disabled = original.withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0)));
        assertEquals(0, pay(disabled, design, List.of(0.0)).pointDefenseInterceptions());
        assertEquals(0, original.moduleDamage().hit(design, 100, "weaponDamage").condition(0));
    }

    @Test void shortagesNeverFundFractionalShotsAndDefensePrecedesOffense() {
        var scarce = fixture.design("defender", 122, CombatModuleCatalog.ION_POINT_DEFENSE);
        var denied = pay(fixture.ship("guard", scarce, 0), scarce, List.of(0.0));
        assertEquals(0, denied.pointDefenseInterceptions()); assertEquals(0, denied.paidWeaponKwh());
        assertEquals(10 - 32 * HOURS / 6000, denied.ship().generatorFuelMassKg(), 1e-12);
        var mixed = fixture.design("defender", 154, CombatModuleCatalog.ION_POINT_DEFENSE, CombatModuleCatalog.GUN);
        var paid = ShipCombatPower.round(fixture.ship("guard", mixed, 0), mixed, HOURS, true, false, 0, 2);
        assertEquals(1, paid.pointDefenseInterceptions()); assertEquals(2, paid.paidPointDefenseKwh());
        assertEquals(0, paid.directFirepower(), 1e-7); assertEquals(2, paid.paidWeaponKwh(), 1e-7);
    }

    @Test void allThreeTypesShareTargetsAndCheaperShotsCanUseAnInsufficientIonBudget() {
        var modules = new String[]{CombatModuleCatalog.ION_POINT_DEFENSE, CombatModuleCatalog.LASER_POINT_DEFENSE,
                CombatModuleCatalog.KINETIC_POINT_DEFENSE};
        var design = fixture.design("defender", 1000, modules);
        var source = new ShipInstance("guard", design.id(), design.ownerEntityId(), 1000, 0, 0,
                Map.of(ShipPointDefense.AMMUNITION, 15.0)).withPowerState(fixture.ship("guard", design, 0).powerState());
        var paid = pay(source, design, Collections.nCopies(7, 0.0));
        assertEquals(7, paid.pointDefenseInterceptions()); assertEquals(7.5, paid.paidPointDefenseKwh());
        assertFalse(paid.ship().storedCargoKg().containsKey(ShipPointDefense.AMMUNITION));
        assertEquals(3, paid.pointDefenseShots().stream().map(ShipPointDefense.Shot::weaponId).distinct().count());
        var limited = fixture.design("defender", 97, modules); // 0.5 kWh after 67 kW upkeep.
        var fallback = pay(source, limited, List.of(0.0));
        assertEquals(1, fallback.pointDefenseInterceptions()); assertEquals(.5, fallback.paidPointDefenseKwh());
        assertEquals(CombatModuleCatalog.KINETIC_POINT_DEFENSE, fallback.pointDefenseShots().getFirst().weaponId());
    }

    @Test void liveIncomingDetectionIsRequiredAndEvadingShipsStillDefend() {
        var blind = fixture.design("defender", 1000, CombatModuleCatalog.ION_POINT_DEFENSE);
        assertEquals(180, fixture.fight(1, 650000, blind, List.of(fixture.ship("guard", blind, 0)), false)
                .roundReports().get(2).attackerDamageDealt());
        var design = fixture.design("defender", 1000, CombatModuleCatalog.ION_POINT_DEFENSE, SensorModuleCatalog.BASIC);
        var source = fixture.ship("guard", design, 0);
        var defended = fixture.fight(1, 650000, design, List.of(source), false);
        assertTrue(defended.roundReports().stream().allMatch(r -> r.attackerDamageDealt() == 0));
        assertEquals(1000, defended.survivingDefenderFleet().ships().getFirst().currentHullHealth());
        assertEquals(1, pay(source, design, List.of(0.0)).pointDefenseInterceptions());
    }

    @Test void saveLoadPreservesInstalledIonMountsDamageAndSpentPower() throws Exception {
        var design = fixture.design("defender", 1000, CombatModuleCatalog.ION_POINT_DEFENSE);
        var damaged = fixture.ship("guard", design, 0).withModuleDamage(new ShipModuleDamage(Map.of(0, 50.0)));
        var paid = pay(damaged, design, List.of(0.0, 0.0));
        var state = GameState.builder().shipDesigns(List.of(design)).fleets(List.of(fixture.fleet("defender", List.of(paid.ship())))).build();
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var restored = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(state, "now", 1, "now")), SaveGame.class).toGameState(0, "RUNNING");
        assertEquals(state.shipDesigns(), restored.shipDesigns()); assertEquals(state.fleets(), restored.fleets());
        var next = pay(restored.fleets().getFirst().ships().getFirst(), restored.shipDesigns().getFirst(), List.of(0.0, 0.0));
        assertEquals(1, next.pointDefenseInterceptions());
        assertTrue(next.ship().generatorFuelMassKg() < paid.ship().generatorFuelMassKg());
    }
}
