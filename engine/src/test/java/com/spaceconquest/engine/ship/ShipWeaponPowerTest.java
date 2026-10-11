package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.TacticalCombatProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShipWeaponPowerTest {
    @TempDir Path directory;
    private static final double HOURS = TacticalCombatProcessor.SECONDS_PER_ROUND / 3600;
    private ShipDesign design(double reactor, double discharge, double hotel, double cargo, String... modules) {
        var power = new ShipPowerProfile(0, 0, reactor, 10000, discharge, discharge, 0, 10, hotel, 0, cargo, .65,
                Map.of("uranium", new ShipPowerProfile.Fuel("refined_uranium", null, 1, 6000)));
        return new ShipDesign("design", "Design", "owner", ShipRole.COMBAT_SHIP, "steel", List.of(modules), "steel", 0,
                1000, 1000, 1000, 100, 10, 0, 1000, false, false, ShipManufacturingProfile.baseline(), power);
    }
    private ShipInstance ship(double fuel, double battery, double shield) {
        return new ShipInstance("ship", "design", "owner", 1000, shield, 100, Map.of("steel", 10.0))
                .withPowerState(new ShipPowerState(Map.of("refined_uranium", fuel), "rp1", "uranium", battery,
                        false, 1, 1, 0, 0, 0, 0, 0));
    }
    private ShipCombatPower.Result round(ShipInstance ship, ShipDesign design) { return ShipCombatPower.round(ship, design, HOURS, true); }
    private Fleet fleet(ShipInstance ship) {
        return new Fleet("fleet", "Fleet", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship));
    }

    @Test void tacticalConservePolicyDefersRechargeWhileKeepingTheSamePaidSalvo() {
        var design = design(2000, 0, 34, 0, CombatModuleCatalog.GUN, CombatModuleCatalog.SHIELD);
        var original = ship(10, 0, 100); var conserving = fleet(original).withShieldPolicy(FleetShieldPolicy.Mode.CONSERVE_ENERGY);
        var enemy = new Fleet("enemy", "Enemy", "enemy", "sol", "", 0, 0, 0, false, "EVASIVE_RETREAT", List.of(
                new ShipInstance("enemy-ship", "unknown", "enemy", 1000, 0, 0, Map.of())));
        var result = new TacticalCombatProcessor(new Random(42)).resolveFleetEngagement(conserving, enemy, List.of(design),
                (attacker, defender) -> new TacticalCombatProcessor.Withdrawal(attacker, defender, true));
        var after = result.survivingAttackerFleet().ships().getFirst();
        assertEquals(100, after.currentShieldHealth());
        assertEquals(10 - (34 * HOURS + 1) / 6000, after.generatorFuelMassKg(), 1e-12);
        assertEquals(FleetShieldPolicy.Mode.CONSERVE_ENERGY, result.survivingAttackerFleet().shieldPolicy());
        var paid = ShipCombatPower.round(original, design, HOURS, true, false);
        assertEquals(80, paid.firepower()); assertEquals(1, paid.paidWeaponKwh());
        assertTrue(round(original, design).ship().currentShieldHealth() > 100);
    }

    @Test void eachWeaponPaysItsCatalogSalvoCostInAdditionToMaintenance() {
        for (var id : List.of(CombatModuleCatalog.GUN, CombatModuleCatalog.LASER, CombatModuleCatalog.MASS_DRIVER)) {
            var module = CombatModuleCatalog.module(id); double hotel = 2 + module.powerDrawKw();
            var design = design(2000, 0, hotel, 0, id); var original = ship(10, 0, 0); var paid = round(original, design);
            double cost = module.operationalStats().get("weaponSalvoKwh");
            assertEquals(cost, paid.requestedWeaponKwh()); assertEquals(cost, paid.paidWeaponKwh(), 1e-9);
            assertEquals(module.operationalStats().get("weaponDamage"), paid.firepower(), 1e-9);
            assertEquals(10 - (hotel * HOURS + cost) / 6000, paid.ship().generatorFuelMassKg(), 1e-12);
            assertEquals(original.storedCargoKg(), paid.ship().storedCargoKg()); assertEquals(100, paid.ship().currentFuelKg());
            assertEquals(10, original.generatorFuelMassKg());
        }
    }

    @Test void generatorPeakAndBatteryDischargeLimitFundedDamageWithoutDriveOutages() {
        var design = design(34, 0, 4, 0, CombatModuleCatalog.GUN);
        var half = round(ship(10, 0, 0), design);
        assertEquals(.5, half.paidWeaponKwh(), 1e-9); assertEquals(40, half.firepower(), 1e-9);
        assertEquals(0, half.ship().powerState().lastUnmetEssentialKwh()); assertEquals(0, half.ship().powerState().unmetDriveKwh());
        var batteryDesign = design(0, 34, 4, 0, CombatModuleCatalog.GUN);
        var battery = round(ship(0, 100, 0), batteryDesign);
        assertEquals(.5, battery.paidWeaponKwh(), 1e-9); assertEquals(40, battery.firepower(), 1e-9);
        assertEquals(100 - 34 * HOURS, battery.ship().powerState().batteryChargeKwh(), 1e-9);
    }

    @Test void essentialAndCargoLoadsHavePriorityAndUnfundedUpkeepStopsWeapons() {
        var design = design(34, 0, 4, 10, CombatModuleCatalog.GUN); var original = ship(10, 0, 0);
        double cargo = ShipPowerProcessor.cargoKw(design.powerProfile(), original, design);
        var paid = round(original, design);
        assertEquals((34 - 4 - cargo) * HOURS, paid.paidWeaponKwh(), 1e-9);
        assertEquals(80 * paid.paidWeaponKwh(), paid.firepower(), 1e-9);
        assertEquals(0, paid.ship().powerState().unmetCargoKwh());
        var shortage = round(ship(0, 0, 0), design);
        assertEquals(0, shortage.paidWeaponKwh()); assertEquals(0, shortage.firepower());
        assertTrue(shortage.ship().powerState().lastUnmetEssentialKwh() > 0);
    }

    @Test void firingUsesTheLastFundedEnergyAndCannotRepeatAfterDepletion() {
        var design = design(0, 100, 4, 0, CombatModuleCatalog.GUN);
        var paid = round(ship(0, 4 * HOURS + 1, 0), design);
        assertEquals(80, paid.firepower(), 1e-9); assertEquals(1, paid.paidWeaponKwh(), 1e-9);
        assertEquals(0, paid.ship().powerState().batteryChargeKwh(), 1e-9);
        var empty = round(paid.ship(), design);
        assertEquals(0, empty.firepower()); assertEquals(0, empty.paidWeaponKwh());
    }

    @Test void finiteEnergyPaysTheWholeMinuteUpkeepBeforeReducingTheSalvo() {
        for (boolean battery : List.of(false, true)) {
            var design = design(battery ? 0 : 100, 100, 4, 0, CombatModuleCatalog.GUN);
            var paid = round(ship(battery ? 0 : .6 / 6000, battery ? .6 : 0, 0), design);
            double firing = .6 - 4 * HOURS;
            assertEquals(firing, paid.paidWeaponKwh(), 1e-7); assertEquals(80 * firing, paid.firepower(), 1e-5);
            assertEquals(0, paid.ship().powerState().lastUnmetEssentialKwh(), 1e-12);
            assertEquals(0, paid.ship().powerState().unmetCargoKwh(), 1e-12);
            assertEquals(0, paid.ship().generatorFuelMassKg(), 1e-12);
            assertEquals(0, paid.ship().powerState().batteryChargeKwh(), 1e-9);
        }
    }

    @Test void duplicateAndDamagedMountsPayForTheirWorkingOutputOnly() {
        var design = design(1000, 0, 8, 0, CombatModuleCatalog.GUN, CombatModuleCatalog.GUN, CombatModuleCatalog.GUN);
        var original = ship(10, 0, 0).withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0, 1, 50.0)));
        var paid = round(original, design);
        assertEquals(1.5, paid.paidWeaponKwh(), 1e-9); assertEquals(120, paid.firepower(), 1e-9);
        assertEquals(original.moduleDamage(), paid.ship().moduleDamage());
        var damagedHull = new ShipInstance(original.id(), original.designId(), original.ownerEntityId(), 500, 0, 100, original.storedCargoKg())
                .withPowerState(original.powerState()).withModuleDamage(original.moduleDamage());
        var half = round(damagedHull, design);
        assertEquals(.75, half.paidWeaponKwh(), 1e-9); assertEquals(60, half.firepower(), 1e-9);
    }

    @Test void firingTakesPriorityOverShieldsWithoutDuplicatingTheMinutePowerBudget() {
        for (double output : List.of(80.0, 94.0, 104.0, 114.0)) {
            var design = design(output, 0, 34, 0, CombatModuleCatalog.GUN, CombatModuleCatalog.SHIELD);
            var paid = round(ship(10, 0, 0), design);
            double weapon = Math.min(1, (output - 34) * HOURS);
            double recharge = Math.min(20, Math.max(0, output - 94)) * HOURS;
            assertEquals(weapon, paid.paidWeaponKwh(), 1e-9); assertEquals(80 * weapon, paid.firepower(), 1e-9);
            assertEquals(recharge / 2, paid.ship().currentShieldHealth(), 1e-9);
            assertEquals(10 - (34 * HOURS + weapon + recharge) / 6000, paid.ship().generatorFuelMassKg(), 1e-12);
            assertEquals(0, paid.ship().powerState().unmetDriveKwh());
        }
    }

    @Test void evadingDisabledOrUncapturedWeaponsDoNotSpendSalvoEnergy() {
        var design = design(100, 0, 4, 0, CombatModuleCatalog.GUN); var original = ship(10, 0, 0);
        var evading = ShipCombatPower.round(original, design, HOURS, false);
        var disabled = round(original.withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0))), design);
        var missingUpkeep = round(original, design(100, 0, 0, 0, CombatModuleCatalog.GUN));
        for (var result : List.of(evading, disabled, missingUpkeep)) {
            assertEquals(0, result.firepower()); assertEquals(0, result.paidWeaponKwh());
        }
        assertEquals(10 - 4 * HOURS / 6000, evading.ship().generatorFuelMassKg(), 1e-12);
        assertEquals(10 - 2 * HOURS / 6000, disabled.ship().generatorFuelMassKg(), 1e-12);
        assertEquals(10, missingUpkeep.ship().generatorFuelMassKg());
    }

    @Test void realCombatLimitsReportedDamageAndRetainsPaidStoresForLaterRounds() {
        var design = design(34, 0, 4, 0, CombatModuleCatalog.GUN);
        var target = new ShipInstance("target", "unknown", "enemy", 1e6, 0, 0, Map.of());
        var result = new TacticalCombatProcessor(new Random(42)).resolveFleetEngagement(fleet(ship(10, 0, 0)), fleet(target), List.of(design));
        assertEquals(10, result.roundReports().size());
        double expected = 40 * .8 * (.9 + new Random(42).nextDouble() * .2);
        assertEquals(expected, result.roundReports().getFirst().attackerDamageDealt(), 1e-9);
        assertEquals(10 - 34 * HOURS * 10 / 6000, result.survivingAttackerFleet().ships().getFirst().generatorFuelMassKg(), 1e-12);
        assertTrue(result.visualRounds().getFirst().eventLogs().stream().anyMatch(log -> log.contains("funded 0.500 / 1.000 kWh")));
        assertEquals(0, result.roundReports().getFirst().defenderDamageDealt());
    }

    @Test void aShipDestroyedInTheRoundStillReturnsItsAlreadyFundedFire() {
        var design = design(100, 0, 4, 0, CombatModuleCatalog.GUN);
        var defender = new ShipInstance("defender", "design", "enemy", 1, 0, 100, Map.of())
                .withPowerState(ship(10, 0, 0).powerState());
        var result = new TacticalCombatProcessor(new Random(42)).resolveFleetEngagement(fleet(ship(10, 0, 0)), fleet(defender), List.of(design));
        assertEquals(1, result.roundReports().size()); assertTrue(result.survivingDefenderFleet().ships().isEmpty());
        assertTrue(result.roundReports().getFirst().defenderDamageDealt() > 0);
        assertTrue(result.survivingAttackerFleet().ships().getFirst().currentHullHealth() < 1000);
        assertEquals(10 - (4 * HOURS + 1) / 6000,
                result.survivingAttackerFleet().ships().getFirst().generatorFuelMassKg(), 1e-12);
    }

    @Test void paidFiringAndRechargeResumeFromSavedStoresWithoutNewPersistentFields() throws Exception {
        var design = design(104, 0, 34, 0, CombatModuleCatalog.GUN, CombatModuleCatalog.SHIELD);
        var partial = round(ship(10, 0, 0), design).ship();
        var state = GameState.builder().shipDesigns(List.of(design)).fleets(List.of(fleet(partial))).build();
        var manager = new SaveGameManager(directory); var file = directory.resolve("weapon-power.scsave").toFile();
        manager.save(file, state, 1, "2027-01-01T08:00:00");
        var restored = manager.load(file).toGameState(0, "RUNNING");
        assertEquals(round(partial, design), round(restored.fleets().getFirst().ships().getFirst(), restored.shipDesigns().getFirst()));
    }
}
