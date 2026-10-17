package com.spaceconquest.engine.ship;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spaceconquest.engine.SaveGame;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.combat.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeaponHitResolutionTest {
    private final ShipPointDefenseTest fixture = new ShipPointDefenseTest();
    private static final double HOURS = 1.0 / 60;

    @Test void impactReceiptsReportActualBypassArmorHullAndElectronicsWithoutChangingDamage() {
        var armored = capture(fixture.design("target", 10000, CombatModuleCatalog.SHIELD), 10);
        var source = target(armored, 200);
        var kinetic = configure(CombatModuleCatalog.MASS_DRIVER, Map.of("shieldBypassFraction", .25,
                "heavyArmorThresholdCm", 5.0, "heavyArmorDamageMultiplier", .5));
        var receipt = WeaponImpactResolver.resolve(source, armored, new WeaponHit(kinetic, 100, 0), "ALL");
        assertEquals(75, receipt.shieldAbsorbed()); assertEquals(25, receipt.shieldBypass());
        assertEquals(18.75, receipt.armorPrevented()); assertEquals(6.25, receipt.hullDamage());
        assertEquals(0, receipt.electronicsDamage());
        assertEquals(hit(armored, source, kinetic, 100), receipt.ship());
        var electronic = fixture.design("sensor", 10000, SensorModuleCatalog.BASIC);
        var ion = CombatModuleCatalog.module(CombatModuleCatalog.ION);
        var overloaded = WeaponImpactResolver.resolve(target(electronic, 0), electronic, new WeaponHit(ion, 1000, 0), "ALL");
        assertEquals(100, overloaded.electronicsDamage(), "Only actual remaining mount durability counts");
        assertEquals(0, overloaded.hullDamage()); assertEquals(List.of(1), overloaded.disabledMounts());
        assertThrows(UnsupportedOperationException.class, () -> overloaded.disabledMounts().add(2));
        var shielded = WeaponImpactResolver.resolve(source, armored, new WeaponHit(ion, 100, 0), "ALL");
        assertEquals(100, shielded.shieldAbsorbed()); assertEquals(0, shielded.electronicsDamage());
    }

    private TacticalCombatProcessor.CombatEngagementResult singleRound(ShipDesign design, ShipInstance ship, double meters, boolean contact) {
        var target = fixture.design("victim", 10000);
        return new TacticalCombatProcessor(new Random(42)).resolveFleetEngagement(
                fixture.fleet("source", List.of(ship)), fixture.fleet("victim", List.of(target(target, 0))), List.of(design, target),
                (a, b) -> new TacticalCombatProcessor.Withdrawal(a, b, true), (a, b) -> contact || a.id().equals("victim"), (a, b) -> meters);
    }

    @Test void arenaLogsExplainRangeEmptyMagazinePowerAndLostTargetingFromThePaidRound() {
        var cannon = fixture.design("cannon", 10000, CombatModuleCatalog.GUN);
        var empty = singleRound(cannon, fixture.ship("source", cannon, 0), 0, true);
        assertTrue(empty.visualRounds().getFirst().eventLogs().stream().anyMatch(s -> s.contains("no whole round")));
        var loaded = new ShipInstance("source", cannon.id(), cannon.ownerEntityId(), 1000, 0, 0,
                Map.of("cannon_round", 5.0)).withPowerState(fixture.ship("source", cannon, 0).powerState());
        var far = singleRound(cannon, loaded, 100001, true);
        assertTrue(far.visualRounds().getFirst().eventLogs().stream().anyMatch(s -> s.contains("out of range") && s.contains("100.001")));
        assertEquals(5, far.survivingAttackerFleet().ships().getFirst().storedCargoKg().get("cannon_round"));
        var low = fixture.design("low", 34, CombatModuleCatalog.GUN);
        var powerless = singleRound(low, new ShipInstance("source", low.id(), low.ownerEntityId(), 1000, 0, 0,
                Map.of("cannon_round", 5.0)).withPowerState(loaded.powerState()), 0, true);
        assertTrue(powerless.visualRounds().getFirst().eventLogs().stream().anyMatch(s -> s.contains("insufficient firing power")));
        var blind = singleRound(cannon, loaded, 0, false);
        assertTrue(blind.visualRounds().getFirst().eventLogs().stream().anyMatch(s -> s.contains("no live shared sensor contact")));
    }

    @Test void arenaDisplaysActualHitEffectsAndAccuracyMissesWithoutBreakingHitVisuals() {
        var particle = fixture.design("particle", 10000, CombatModuleCatalog.PARTICLE_BEAM);
        var hit = singleRound(particle, fixture.ship("source", particle, 0), 1000, true);
        assertTrue(hit.visualRounds().getFirst().eventLogs().stream().anyMatch(s -> s.contains("hull damage 180.000")));
        assertTrue(hit.visualRounds().getFirst().activeProjectiles().getFirst().isHit());
        var missWeapon = configure(CombatModuleCatalog.PARTICLE_BEAM, Map.of("accuracy", 0.0));
        var inaccurate = capture(particle, 0, missWeapon);
        var miss = singleRound(inaccurate, fixture.ship("source", inaccurate, 0), 1000, true);
        assertTrue(miss.visualRounds().getFirst().eventLogs().stream().anyMatch(s -> s.contains("accuracy 0.0% at 1.000 km")));
        assertFalse(miss.visualRounds().getFirst().activeProjectiles().getFirst().isHit());
        assertEquals(0, miss.roundReports().getFirst().attackerDamageDealt());
    }

    private ShipModule configure(String id, Map<String, Double> changes) {
        var base = CombatModuleCatalog.module(id);
        var stats = new HashMap<>(base.operationalStats()); stats.putAll(changes);
        return new ShipModule(base.id(), base.name(), base.slotSize(), base.slotCost(), base.dryMassKg(),
                base.powerDrawKw(), base.powerOutputKw(), base.thrustOutputN(), base.complexityLevel(),
                base.materialCostsKg(), stats, base.ammunitionId());
    }
    private ShipDesign capture(ShipDesign d, double armor, ShipModule... modules) {
        return new ShipDesign(d.id(), d.name(), d.ownerEntityId(), d.role(), d.hullMaterialId(), d.equippedModuleIds(),
                d.armorMaterialId(), armor, d.totalDryMassKg(), d.maxCargoMassKg(), d.fuelCapacityKg(), d.powerBalanceKw(),
                d.calculatedStructuralIntegrity(), d.minLaunchThrustRequiredN(), d.totalThrustN(), d.isValidForLaunch(),
                d.isProprietaryCorporateDesign(), d.manufacturingProfile(), d.powerProfile(), d.sensorProfile(),
                modules.length == 0 ? d.combatModules() : CombatModuleCatalog.capture(List.of(modules)));
    }
    private ShipInstance target(ShipDesign d, double shields) {
        var s = fixture.ship("target", d, 0);
        return new ShipInstance(s.id(), s.designId(), s.ownerEntityId(), 1000, shields, 0, Map.of())
                .withPowerState(s.powerState());
    }
    private ShipInstance hit(ShipDesign d, ShipInstance s, ShipModule weapon, double damage) {
        return WeaponImpactResolver.hit(s, d, new WeaponHit(weapon, damage, 0), "ALL");
    }

    @Test void kineticBypassHitsArmorBeforeShieldsFailButDoesNotGrantBypassToTheNextWeapon() {
        var d = fixture.design("target", 10000, CombatModuleCatalog.SHIELD);
        var kinetic = configure(CombatModuleCatalog.MASS_DRIVER, Map.of("shieldBypassFraction", .25));
        var after = hit(d, target(d, 200), kinetic, 100);
        assertEquals(975, after.currentHullHealth()); assertEquals(125, after.currentShieldHealth());
        var beam = hit(d, after, CombatModuleCatalog.module(CombatModuleCatalog.PARTICLE_BEAM), 100);
        assertEquals(975, beam.currentHullHealth()); assertEquals(25, beam.currentShieldHealth());
    }

    @Test void armorPenaltyAndPerPulseAbsorptionComeFromTheHitApplication() {
        var d = capture(fixture.design("target", 10000), 10);
        var kinetic = configure(CombatModuleCatalog.MASS_DRIVER, Map.of("heavyArmorThresholdCm", 5.0,
                "heavyArmorDamageMultiplier", .5));
        assertEquals(975, hit(d, target(d, 0), kinetic, 100).currentHullHealth());
        var beam = CombatModuleCatalog.module(CombatModuleCatalog.PARTICLE_BEAM);
        assertEquals(950, hit(d, target(d, 0), beam, 100).currentHullHealth());
        var pulse = configure(CombatModuleCatalog.LASER, Map.of("armorAbsorptionPerCm", 2.0));
        var s = target(d, 0);
        for (int i = 0; i < 8; i++) s = hit(d, s, pulse, 20);
        assertEquals(1000, s.currentHullHealth());
        assertEquals(940, hit(d, target(d, 0), pulse, 160).currentHullHealth());
    }

    @Test void laserPulsesAndBeamRangeUseCapturedApplicationValuesAndPaidPower() {
        var laser = fixture.design("laser", 10000, CombatModuleCatalog.LASER);
        var source = fixture.ship("laser", laser, 0);
        var paid = ShipCombatPower.round(source, laser, HOURS, true, false, 25000);
        assertEquals(16, paid.directHits().size()); assertEquals(20, paid.directHits().getFirst().damage());
        assertEquals(320, paid.directHits().stream().mapToDouble(WeaponHit::damage).sum());
        assertEquals(8, paid.paidWeaponKwh()); assertTrue(paid.missileLaunches().isEmpty());
        assertTrue(ShipCombatPower.round(source, laser, HOURS, true, false, 25001).directHits().isEmpty());
        var custom = configure(CombatModuleCatalog.LASER, Map.of("shotsPerRound", 4.0, "weaponDamage", 120.0));
        var changed = capture(laser, 0, custom);
        var changedPaid = ShipCombatPower.round(source, changed, HOURS, true, false, 0);
        assertEquals(4, changedPaid.directHits().size()); assertEquals(30, changedPaid.directHits().getFirst().damage());
    }

    @Test void kineticAccuracyFallsWithRangeAndAccelerationWhileBeamsTrackPerfectly() {
        var kinetic = CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER);
        var near = new WeaponHit(kinetic, 220, 0); var far = new WeaponHit(kinetic, 220, 500000);
        assertTrue(far.accuracy(0) < near.accuracy(0)); assertTrue(far.accuracy(10) < far.accuracy(0));
        var beam = new WeaponHit(CombatModuleCatalog.module(CombatModuleCatalog.PARTICLE_BEAM), 180, 1000000);
        assertEquals(1, beam.accuracy(10000));
        var target = fixture.design("target", 10000);
        var actions = new ArrayList<TacticalCombatProcessor.CombatActionReport>();
        var result = WeaponImpactResolver.apply(List.of(target(target, 0)), List.of(beam), List.of(target), "ALL",
                new Random(42), actions);
        assertEquals(180, result.landedDamage()); assertEquals(820, result.ships().getFirst().currentHullHealth());
        var alwaysMiss = configure(CombatModuleCatalog.PARTICLE_BEAM, Map.of("accuracy", 0.0));
        var missed = WeaponImpactResolver.apply(result.ships(), List.of(new WeaponHit(alwaysMiss, 180, 0)),
                List.of(target), "ALL", new Random(42), actions);
        assertEquals(result.ships(), missed.ships()); assertEquals(0, missed.landedDamage());
    }

    @Test void ionDisablesElectronicsWithoutHullDamageAndItsDamageSavesAndRepairs() throws Exception {
        var d = fixture.design("target", 10000, SensorModuleCatalog.BASIC);
        var s = target(d, 0);
        var ion = configure(CombatModuleCatalog.ION, Map.of("electronicDamageMultiplier", 2.0, "electronicDamageFraction", 1.0));
        var after = hit(d, s, ion, 50);
        assertEquals(1000, after.currentHullHealth()); assertEquals(0, after.moduleDamage().condition(0));
        assertEquals(0, SensorModuleCatalog.profile(d, after).rangeKm());
        assertEquals(1, after.moduleDamage().repair(0, 100).condition(0));
        var mapper = new ObjectMapper().configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); var fleet = fixture.fleet("target", List.of(after));
        var state = GameState.builder().shipDesigns(List.of(d)).fleets(List.of(fleet)).build();
        var saved = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(state, "now", 1, "now")), SaveGame.class);
        assertEquals(after.moduleDamage(), saved.toGameState(0, "RUNNING").fleets().getFirst().ships().getFirst().moduleDamage());
        assertEquals(1, s.moduleDamage().condition(0));
    }

    @Test void ionShieldInteractionIsIndependentOfItsElectronicEffect() {
        var d = fixture.design("target", 10000, SensorModuleCatalog.BASIC, CombatModuleCatalog.SHIELD);
        var ion = CombatModuleCatalog.module(CombatModuleCatalog.ION);
        var after = hit(d, target(d, 200), ion, 100);
        assertEquals(100, after.currentShieldHealth()); assertTrue(after.moduleDamage().damageByMount().isEmpty());
        var penetrating = configure(CombatModuleCatalog.ION, Map.of("shieldBypassFraction", 1.0, "electronicDamageFraction", 1.0));
        var bypassed = hit(d, target(d, 200), penetrating, 100);
        assertEquals(1000, bypassed.currentHullHealth()); assertEquals(.5, bypassed.moduleDamage().condition(0));
        var overload = hit(d, target(d, 0), ion, 100);
        assertEquals(1000, overload.currentHullHealth(), 1e-9);
        assertTrue(overload.moduleDamage().condition(0) < 1);
        assertTrue(ShipShieldRecharge.apply(after, d, 20).currentShieldHealth() > after.currentShieldHealth());
    }

    @Test void ionDefensePaysOnlyForElectronicTargetsAndKeepsTheExactAssignment() {
        var d = fixture.design("guard", 10000, CombatModuleCatalog.ION_POINT_DEFENSE);
        var s = fixture.ship("guard", d, 0);
        var threats = List.of(new ShipPointDefense.Threat(10, 0, false), new ShipPointDefense.Threat(11, 0, true));
        var paid = ShipCombatPower.roundThreats(s, d, HOURS, false, false, 0, threats);
        assertEquals(1, paid.pointDefenseInterceptions()); assertEquals(2, paid.paidPointDefenseKwh());
        assertEquals(11, paid.pointDefenseShots().getFirst().targetId());
        assertTrue(paid.pointDefenseShots().getFirst().requiresTargetElectronics());
        var inert = ShipCombatPower.roundThreats(s, d, HOURS, false, false, 0, threats.subList(0, 1));
        assertEquals(0, inert.pointDefenseInterceptions()); assertEquals(0, inert.paidWeaponKwh());
    }

    @Test void ionDamageCanDisableEveryActiveSystemWhileHullAndPassiveStoresSurvive() {
        var modules = new String[]{ShipComponentCatalog.FISSION_REACTOR_ID, ShipComponentCatalog.BATTERY_ID,
                "mod_chemical_rocket", CombatModuleCatalog.GUN, CombatModuleCatalog.SHIELD,
                SensorModuleCatalog.BASIC, ShipRepairModuleCatalog.WORKSHOP, PropulsionCatalog.FUEL_TANK_MODULE_ID};
        var d = fixture.design("target", 10000, modules);
        var source = target(d, 0);
        var ion = CombatModuleCatalog.module(CombatModuleCatalog.ION);
        var degraded = hit(d, source, ion, 350);
        assertEquals(1000, degraded.currentHullHealth());
        for (int mount = 0; mount < 7; mount++) assertEquals(.5, degraded.moduleDamage().condition(mount));
        assertEquals(500, PropulsionDamage.thrust(d, degraded));
        assertEquals(5000, d.powerProfile(degraded).fissionKw());
        assertEquals(40, degraded.moduleDamage().capability(d, "weaponDamage"));
        assertEquals(10, degraded.moduleDamage().capability(d, "repairPointsPerDay"));
        var disabled = hit(d, degraded, ion, 350);
        assertEquals(1000, disabled.currentHullHealth());
        for (int mount = 0; mount < 7; mount++) assertEquals(0, disabled.moduleDamage().condition(mount));
        assertEquals(0, PropulsionDamage.thrust(d, disabled));
        assertEquals(0, d.powerProfile(disabled).fissionKw());
        assertEquals(0, d.powerProfile(disabled).batteryKwh());
        assertEquals(0, SensorModuleCatalog.profile(d, disabled).rangeKm());
        assertEquals(0, ShipCombatPower.round(disabled, d, HOURS, true, false).firepower());
        assertEquals(0, ShipShieldRecharge.requestedKw(d, disabled, HOURS));
        assertEquals(0, disabled.moduleDamage().capability(d, "repairPointsPerDay"));
        assertEquals(1, disabled.moduleDamage().condition(7));
        assertEquals(source.storedCargoKg(), disabled.storedCargoKg());
        assertEquals(source.currentFuelKg(), disabled.currentFuelKg());
        assertEquals(source.generatorFuelMassKg(), disabled.generatorFuelMassKg());
    }

    @Test void combatDelaysKineticsButParticleBeamsHitImmediatelyWithoutDefenseSpending() {
        var guard = fixture.design("guard", 10000, CombatModuleCatalog.LASER_POINT_DEFENSE);
        for (String id : List.of(CombatModuleCatalog.MASS_DRIVER, CombatModuleCatalog.PARTICLE_BEAM)) {
            var shooter = fixture.design("shooter", 10000, id);
            var result = new TacticalCombatProcessor(new Random(42)).resolveFleetEngagement(
                    fixture.fleet("shooter", List.of(new ShipInstance("shooter", shooter.id(), shooter.ownerEntityId(), 1000, 0, 0,
                            Map.of(CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER).ammunitionId(), 20.0))
                            .withPowerState(fixture.ship("shooter", shooter, 0).powerState()))),
                    fixture.fleet("guard", List.of(fixture.ship("guard", guard, 0))), List.of(shooter, guard),
                    (a, b) -> new TacticalCombatProcessor.Withdrawal(a, b, true), (a, b) -> true, (a, b) -> 500000);
            if (id.equals(CombatModuleCatalog.MASS_DRIVER)) assertEquals(0, result.roundReports().getFirst().attackerDamageDealt());
            else assertEquals(180, result.roundReports().getFirst().attackerDamageDealt());
            assertTrue(result.roundReports().getFirst().detailedActions().stream().noneMatch(a -> a.phase().equals("POINT_DEFENSE")));
        }
    }
}
