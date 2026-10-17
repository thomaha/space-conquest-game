package com.spaceconquest.engine.ship;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.TacticalCombatProcessor;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ShipMissileWeaponsTest {
    private static final double HOURS = 1.0 / 60;
    private ShipDesign design(double output, String... ids) {
        double idle = 2 + Arrays.stream(ids).map(CombatModuleCatalog::module).mapToDouble(ShipModule::powerDrawKw).sum();
        var power = new ShipPowerProfile(0, 0, output, 1000, 0, 0, 0, 10, idle, 0, 0, .65,
                Map.of("uranium", new ShipPowerProfile.Fuel("refined_uranium", null, 1, 6000)));
        return new ShipDesign("design", "Design", "owner", ShipRole.COMBAT_SHIP, "steel", List.of(ids), "steel", 0,
                1000, 1000, 1000, 100, 10, 0, 1000, false, false, ShipManufacturingProfile.baseline(), power);
    }
    private ShipInstance ship(double ammunition) {
        return new ShipInstance("ship", "design", "owner", 1000, 0, 100,
                Map.of(ShipMissileWeapons.AMMUNITION, ammunition, "steel", 12.0, "cannon_round", 100.0))
                .withPowerState(new ShipPowerState(Map.of("refined_uranium", 10.0), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0));
    }
    private Fleet fleet(ShipInstance ship) {
        return new Fleet("own", "Own", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship))
                .withShieldPolicy(FleetShieldPolicy.Mode.CONSERVE_ENERGY);
    }
    private Fleet enemy() {
        return new Fleet("enemy", "Enemy", "enemy", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(
                new ShipInstance("target", "unknown", "enemy", 10000, 0, 0, Map.of())));
    }
    private ShipCombatPower.Result pay(ShipInstance ship, ShipDesign design, double meters) {
        return ShipCombatPower.round(ship, design, HOURS, true, false, meters);
    }

    @Test void quoteAndLaunchPayWholeAmmunitionAndFullLaunchEnergyWithoutInstantDamage() {
        var design = design(1000, CombatModuleCatalog.MISSILE); var original = ship(250);
        assertEquals(180, ShipCombatPower.fundedFirepower(original, design, HOURS, true, 2_000_000));
        assertEquals(250, original.storedCargoKg().get(ShipMissileWeapons.AMMUNITION));
        var paid = pay(original, design, 2_000_000);
        assertEquals(1, paid.missileLaunches().size()); assertEquals(0, paid.directFirepower());
        assertEquals(2, paid.paidWeaponKwh()); assertEquals(2, paid.requestedWeaponKwh());
        assertEquals(150, paid.ship().storedCargoKg().get(ShipMissileWeapons.AMMUNITION));
        assertEquals(12, paid.ship().storedCargoKg().get("steel")); assertEquals(100, paid.ship().currentFuelKg());
        assertEquals(10 - (12 * HOURS + 2) / 6000, paid.ship().generatorFuelMassKg(), 1e-12);
        var second = pay(paid.ship(), design, 0); var empty = pay(second.ship(), design, 0);
        assertEquals(50, empty.ship().storedCargoKg().get(ShipMissileWeapons.AMMUNITION));
        assertTrue(empty.missileLaunches().isEmpty()); assertEquals(0, empty.firepower());
        assertEquals(0, ShipWeaponRange.maximumRange(design, empty.ship()));
    }

    @Test void outputLimitsNeverPayForOrConsumeAFractionalMissileAndDirectWeaponsKeepTheirShot() {
        var insufficient = design(72, CombatModuleCatalog.MISSILE); var original = ship(100);
        var paid = pay(original, insufficient, 0); // Only 1 kWh beyond idle is available.
        assertEquals(0, paid.paidWeaponKwh()); assertTrue(paid.missileLaunches().isEmpty());
        assertEquals(original.storedCargoKg(), paid.ship().storedCargoKg());
        assertEquals(10 - 12 * HOURS / 6000, paid.ship().generatorFuelMassKg(), 1e-12);
        var mixed = pay(original, design(74, CombatModuleCatalog.GUN, CombatModuleCatalog.MISSILE), 0);
        assertEquals(80, mixed.directFirepower()); assertEquals(1, mixed.paidWeaponKwh());
        assertTrue(mixed.missileLaunches().isEmpty());
        assertEquals(95, mixed.ship().storedCargoKg().get("cannon_round"));
        assertEquals(original.storedCargoKg().get(ShipMissileWeapons.AMMUNITION), mixed.ship().storedCargoKg().get(ShipMissileWeapons.AMMUNITION));
        var depleted = original.withPowerState(new ShipPowerState(Map.of(), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0));
        assertEquals(0, pay(depleted, insufficient, 0).firepower());
    }

    @Test void duplicateMountsShareFiniteAmmunitionAndDamageDoesNotDiscountCartridgeEnergy() {
        var design = design(1000, CombatModuleCatalog.MISSILE, CombatModuleCatalog.MISSILE);
        var original = ship(100).withModuleDamage(new ShipModuleDamage(Map.of(0, 50.0)));
        var paid = pay(original, design, 0);
        assertEquals(1, paid.missileLaunches().size()); assertEquals(90, paid.firepower()); assertEquals(2, paid.paidWeaponKwh());
        assertEquals(90, ShipWeaponRange.firepower(design, original, 0));
        var disabled = original.withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0, 1, 100.0)));
        assertTrue(pay(disabled, design, 0).missileLaunches().isEmpty());
        assertTrue(pay(original, design, 2_000_001).missileLaunches().isEmpty());
        assertTrue(ShipCombatPower.round(original, design, HOURS, false).missileLaunches().isEmpty());
        assertEquals(0, pay(original, design(1000, CombatModuleCatalog.GUN), 2_000_000).firepower());
    }

    @Test void actualCombatDelaysImpactAccordingToFlightTimeAndKeepsOrdinaryDamageOnImpact() {
        var design = design(1000, CombatModuleCatalog.MISSILE); var source = ship(100);
        var result = new TacticalCombatProcessor(new Random(42)).resolveFleetEngagement(fleet(source), enemy(), List.of(design),
                null, (a, b) -> true, (a, b) -> 1_200_000);
        assertEquals(0, result.roundReports().get(0).attackerDamageDealt());
        assertEquals(0, result.roundReports().get(1).attackerDamageDealt());
        assertEquals(180, result.roundReports().get(2).attackerDamageDealt());
        assertEquals(9820, result.survivingDefenderFleet().ships().getFirst().currentHullHealth());
        assertTrue(result.roundReports().getFirst().detailedActions().stream().anyMatch(a -> a.phase().equals("MISSILE_LAUNCH") && a.damageAmount() == 0));
        assertTrue(result.roundReports().get(2).detailedActions().stream().anyMatch(a -> a.phase().equals("MISSILE_IMPACT")));
        assertFalse(result.survivingAttackerFleet().ships().getFirst().storedCargoKg().containsKey(ShipMissileWeapons.AMMUNITION));
        assertEquals(100, source.storedCargoKg().get(ShipMissileWeapons.AMMUNITION));
    }

    @Test void losingGuidancePermanentlyDiscardsLaunchedMissilesEvenAfterReacquisition() {
        var completed = new AtomicInteger(); var design = design(1000, CombatModuleCatalog.MISSILE);
        var result = new TacticalCombatProcessor(new Random(42)).resolveFleetEngagement(fleet(ship(100)), enemy(), List.of(design),
                (a, b) -> { completed.incrementAndGet(); return new TacticalCombatProcessor.Withdrawal(a, b, false); },
                (a, b) -> a.id().equals("enemy") || completed.get() != 1, (a, b) -> 1_200_000);
        assertTrue(result.roundReports().stream().allMatch(r -> r.attackerDamageDealt() == 0));
        assertEquals(10000, result.survivingDefenderFleet().ships().getFirst().currentHullHealth());
        assertTrue(result.visualRounds().stream().flatMap(r -> r.eventLogs().stream()).anyMatch(s -> s.contains("live guidance contact lost")));
        assertEquals(0, ShipMissileWeapons.rounds(result.survivingAttackerFleet().ships().getFirst()));
    }

    @Test void withdrawalCancelsPendingFlightWithoutRefundAndSaveLoadKeepsRemainingStores() throws Exception {
        var design = design(1000, CombatModuleCatalog.MISSILE);
        var result = new TacticalCombatProcessor(new Random(42)).resolveFleetEngagement(fleet(ship(250)), enemy(), List.of(design),
                (a, b) -> new TacticalCombatProcessor.Withdrawal(a, b, true), (a, b) -> true, (a, b) -> 0);
        assertEquals(0, result.roundReports().getFirst().attackerDamageDealt());
        assertEquals(10000, result.survivingDefenderFleet().ships().getFirst().currentHullHealth());
        assertTrue(result.visualRounds().getFirst().eventLogs().stream().anyMatch(s -> s.contains("discarded")));
        var state = new GameState.Builder().shipDesigns(List.of(design)).fleets(List.of(result.survivingAttackerFleet())).build();
        var mapper = new ObjectMapper().configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var loaded = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(state, "now", 1, "now")), SaveGame.class).toGameState(0, "RUNNING");
        assertEquals(state.fleets(), loaded.fleets());
        var again = pay(loaded.fleets().getFirst().ships().getFirst(), loaded.shipDesigns().getFirst(), 0);
        assertEquals(50, again.ship().storedCargoKg().get(ShipMissileWeapons.AMMUNITION));
    }
}
