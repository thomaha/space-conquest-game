package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.combat.TacticalCombatProcessor;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShipPointDefenseTest {
    private static final double HOURS = 1.0 / 60;
    ShipDesign design(String id, double output, String... modules) {
        double hotel = 2 + Arrays.stream(modules).map(ShipComponentCatalog::module).mapToDouble(ShipModule::powerDrawKw).sum();
        var power = new ShipPowerProfile(0, 0, output, 1000, 0, 0, 0, 10, hotel, 0, 0, .65,
                Map.of("uranium", new ShipPowerProfile.Fuel("refined_uranium", null, 1, 6000)));
        return new ShipDesign(id, id, id, ShipRole.COMBAT_SHIP, "steel", List.of(modules), "steel", 0,
                1000, 1000, 1000, 100, 10, 0, 1000, false, false, ShipManufacturingProfile.baseline(), power);
    }
    ShipInstance ship(String id, ShipDesign design, double ammo) {
        return new ShipInstance(id, design.id(), design.ownerEntityId(), 1000, 0, 0,
                ammo > 0 ? Map.of(ShipMissileWeapons.AMMUNITION, ammo) : Map.of())
                .withPowerState(new ShipPowerState(Map.of("refined_uranium", 10.0), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0));
    }
    Fleet fleet(String id, List<ShipInstance> ships) {
        return new Fleet(id, id, id, "sol", "", 0, 0, 0, false, "PASSIVE", ships)
                .withShieldPolicy(FleetShieldPolicy.Mode.CONSERVE_ENERGY);
    }
    TacticalCombatProcessor.CombatEngagementResult fight(int missiles, double meters, ShipDesign defender,
                                                               List<ShipInstance> ships, boolean defendContact) {
        var shooter = design("attacker", 1000, Collections.nCopies(missiles, CombatModuleCatalog.MISSILE).toArray(String[]::new));
        return new TacticalCombatProcessor(new Random(42)).resolveFleetEngagement(
                fleet("attacker", List.of(ship("launcher", shooter, missiles * ShipMissileWeapons.MASS_KG))),
                fleet("defender", ships), List.of(shooter, defender), null,
                (a, b) -> a.id().equals("attacker") || defendContact, (a, b) -> meters);
    }
    private long interceptions(TacticalCombatProcessor.CombatEngagementResult result) {
        return result.roundReports().stream().flatMap(r -> r.detailedActions().stream()).filter(a -> a.phase().equals("POINT_DEFENSE")).count();
    }

    @Test void interceptionWaitsForDefensiveRangeThenPaysBeforeImpact() {
        var design = design("defender", 1000, CombatModuleCatalog.LASER_POINT_DEFENSE);
        var original = ship("guard", design, 0); var result = fight(2, 1_200_000, design, List.of(original), true);
        assertTrue(result.roundReports().stream().allMatch(r -> r.attackerDamageDealt() == 0));
        assertTrue(result.roundReports().get(0).detailedActions().isEmpty()
                || result.roundReports().get(0).detailedActions().stream().noneMatch(a -> a.phase().equals("POINT_DEFENSE")));
        assertTrue(result.roundReports().get(1).detailedActions().stream().noneMatch(a -> a.phase().equals("POINT_DEFENSE")));
        assertTrue(result.roundReports().get(2).detailedActions().stream().anyMatch(a -> a.phase().equals("POINT_DEFENSE")));
        var after = result.survivingDefenderFleet().ships().getFirst();
        assertEquals(1000, after.currentHullHealth());
        assertEquals(10 - (22 * HOURS * result.roundReports().size() + 2) / 6000, after.generatorFuelMassKg(), 1e-12);
        assertEquals(10, original.generatorFuelMassKg()); assertEquals(0, ShipWeaponRange.maximumRange(design, original));
        assertEquals(0, ShipCombatPower.fundedFirepower(original, design, HOURS, true));
    }

    @Test void overflowMissilesHitNormallyAndMultipleDefendersDoNotPayForTheSameMissile() {
        var design = design("defender", 1000, CombatModuleCatalog.LASER_POINT_DEFENSE);
        var result = fight(3, 600000, design, List.of(ship("guard", design, 0)), true);
        assertEquals(180, result.roundReports().get(1).attackerDamageDealt());
        assertEquals(820, result.survivingDefenderFleet().ships().getFirst().currentHullHealth());
        var shared = fight(3, 600000, design, List.of(ship("first", design, 0), ship("second", design, 0)), true);
        assertTrue(shared.roundReports().stream().allMatch(r -> r.attackerDamageDealt() == 0));
        assertEquals(2, interceptions(shared));
        double upkeep = 22 * HOURS * shared.roundReports().size();
        assertEquals(10 - (upkeep + 2) / 6000, shared.survivingDefenderFleet().ships().get(0).generatorFuelMassKg(), 1e-12);
        assertEquals(10 - (upkeep + 1) / 6000, shared.survivingDefenderFleet().ships().get(1).generatorFuelMassKg(), 1e-12);
    }

    @Test void damagedAndDisabledMountsReduceWholeInterceptionCapacity() {
        var design = design("defender", 1000, CombatModuleCatalog.LASER_POINT_DEFENSE);
        var source = ship("guard", design, 0);
        var half = source.withModuleDamage(new ShipModuleDamage(Map.of(0, 50.0)));
        var result = fight(2, 0, design, List.of(half), true);
        assertEquals(180, result.roundReports().get(1).attackerDamageDealt());
        var disabled = source.withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0)));
        assertEquals(360, fight(2, 0, design, List.of(disabled), true).roundReports().get(1).attackerDamageDealt());
        assertEquals(0, ShipPointDefense.capacity(design, disabled));
        assertEquals(0, source.moduleDamage().hit(design, 100, "weaponDamage").condition(0));
    }

    @Test void powerLimitsWholeShotsAndDefensiveShotsPrecedeOffensiveFire() {
        var scarce = design("defender", 82, CombatModuleCatalog.LASER_POINT_DEFENSE);
        var result = fight(2, 0, scarce, List.of(ship("guard", scarce, 0)), true);
        assertEquals(180, result.roundReports().get(1).attackerDamageDealt());
        var mixed = design("mixed", 84, CombatModuleCatalog.LASER_POINT_DEFENSE, CombatModuleCatalog.GUN);
        var source = ship("guard", mixed, 0);
        var paid = ShipCombatPower.round(source, mixed, HOURS, true, false, 0, 2);
        assertEquals(1, paid.pointDefenseInterceptions()); assertEquals(1, paid.paidPointDefenseKwh(), 1e-9);
        assertEquals(0, paid.directFirepower(), 1e-7); assertEquals(1, paid.paidWeaponKwh(), 1e-7);
        assertEquals(10 - (24 * HOURS + 1) / 6000, paid.ship().generatorFuelMassKg(), 1e-12);
        var insufficient = design("defender", 52, CombatModuleCatalog.LASER_POINT_DEFENSE);
        var unable = ShipCombatPower.round(ship("guard", insufficient, 0), insufficient, HOURS, false, false, 0, 1);
        assertEquals(0, unable.pointDefenseInterceptions()); assertEquals(0, unable.paidWeaponKwh());
        assertEquals(10 - 22 * HOURS / 6000, unable.ship().generatorFuelMassKg(), 1e-12);
        assertEquals(0, ShipCombatPower.round(source, mixed, HOURS, true, false, 0).paidPointDefenseKwh());
    }

    @Test void localSensorCanDetectIncomingMissilesWithoutSeeingLauncherAndEvadingStillDefends() {
        var design = design("defender", 1000, CombatModuleCatalog.LASER_POINT_DEFENSE, SensorModuleCatalog.BASIC);
        var source = ship("guard", design, 0);
        var result = fight(1, 650000, design, List.of(source), false);
        assertEquals(1, interceptions(result)); assertEquals(1000, result.survivingDefenderFleet().ships().getFirst().currentHullHealth());
        var blind = source.withModuleDamage(new ShipModuleDamage(Map.of(1, 100.0)));
        assertEquals(180, fight(1, 650000, design, List.of(blind), false).roundReports().get(2).attackerDamageDealt());
        var evading = ShipCombatPower.round(source, design, HOURS, false, false, 0, 1);
        assertEquals(1, evading.pointDefenseInterceptions()); assertEquals(0, evading.firepower());
    }
}
