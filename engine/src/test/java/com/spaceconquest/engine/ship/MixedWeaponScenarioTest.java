package com.spaceconquest.engine.ship;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.engine.TechnicalApplication;
import com.spaceconquest.engine.combat.TacticalCombatProcessor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Small deterministic mechanics scenarios, not fleet or economy balance simulations. */
class MixedWeaponScenarioTest {
    private static final List<String> OFFENSE = List.of(CombatModuleCatalog.LASER, CombatModuleCatalog.PARTICLE_BEAM,
            CombatModuleCatalog.ION, CombatModuleCatalog.MASS_DRIVER);
    private final ShipPointDefenseTest fixture = new ShipPointDefenseTest();
    private final ObjectMapper mapper = new ObjectMapper();
    private record Run(ShipDesign attacker, ShipDesign defender, ShipInstance source, ShipInstance target,
                       TacticalCombatProcessor.CombatEngagementResult result) {}

    private ShipDesign hardware(ShipDesign base, double armor, List<ShipModule> modules) {
        return new ShipDesign(base.id(), base.name(), base.ownerEntityId(), base.role(), base.hullMaterialId(),
                base.equippedModuleIds(), base.armorMaterialId(), armor, base.totalDryMassKg(), base.maxCargoMassKg(),
                base.fuelCapacityKg(), base.powerBalanceKw(), base.calculatedStructuralIntegrity(),
                base.minLaunchThrustRequiredN(), base.totalThrustN(), base.isValidForLaunch(), base.isProprietaryCorporateDesign(),
                base.manufacturingProfile(), base.powerProfile(), base.sensorProfile(), CombatModuleCatalog.capture(modules));
    }

    private Run fight(String defense, double meters, List<ShipModule> weapons) {
        return fight(defense, meters, weapons, 1);
    }

    private Run fight(String defense, double meters, List<ShipModule> weapons, int shieldMounts) {
        var attacker = hardware(fixture.design("mixed", 10000, OFFENSE.toArray(String[]::new)), 0, weapons);
        var targetIds = new ArrayList<>(Collections.nCopies(shieldMounts, CombatModuleCatalog.SHIELD));
        targetIds.add(SensorModuleCatalog.BASIC);
        if (!defense.isBlank()) targetIds.add(defense);
        var defenderBase = fixture.design("defender", 10000, targetIds.toArray(String[]::new));
        var defender = hardware(defenderBase, 10, List.copyOf(defenderBase.combatModules().values()));
        var cargo = new HashMap<String, Double>();
        for (var weapon : weapons) if (!weapon.ammunitionId().isBlank())
            cargo.merge(weapon.ammunitionId(), 2 * weapon.operationalStats().get("ammunitionMassKg"), Double::sum);
        var source = new ShipInstance("mixed", attacker.id(), attacker.ownerEntityId(), 1000, 0, 0, cargo)
                .withPowerState(fixture.ship("mixed", attacker, 0).powerState());
        var defensiveCargo = new HashMap<String, Double>();
        if (!defense.isBlank()) {
            var module = CombatModuleCatalog.module(defender, defense);
            if (!module.ammunitionId().isBlank()) defensiveCargo.put(module.ammunitionId(),
                    2 * module.operationalStats().get("ammunitionMassKg"));
        }
        var target = new ShipInstance("defender", defender.id(), defender.ownerEntityId(), 1000,
                CombatModuleCatalog.shieldCapacity(defender), 0, defensiveCargo)
                .withPowerState(fixture.ship("defender", defender, 0).powerState());
        int[] rounds = {0};
        var result = new TacticalCombatProcessor(new Random(42)).resolveFleetEngagement(
                fixture.fleet("mixed", List.of(source)), fixture.fleet("defender", List.of(target)),
                List.of(attacker, defender), (a, b) -> new TacticalCombatProcessor.Withdrawal(a, b, ++rounds[0] >= 2),
                (a, b) -> true, (a, b) -> meters);
        return new Run(attacker, defender, source, target, result);
    }

    private List<ShipModule> baseline() { return OFFENSE.stream().map(CombatModuleCatalog::module).toList(); }
    private double closeRange() {
        return baseline().stream().mapToDouble(m -> m.operationalStats().get("weaponRangeMeters")).min().orElseThrow() / 2;
    }
    private List<TacticalCombatProcessor.CombatActionReport> actions(Run run, int round) {
        return run.result().roundReports().get(round).detailedActions();
    }
    private boolean action(Run run, int round, String phase, String text) {
        return actions(run, round).stream().anyMatch(a -> a.phase().equals(phase) && a.description().contains(text));
    }
    private String hitPrefix(String id) { return CombatModuleCatalog.module(id).name() + " hit:"; }

    @Test void shortMixedEncounterShowsInstantBeamsDelayedSlugsDefenseAndFiniteStores() throws Exception {
        var report = new StringBuilder("# Mixed weapon scenario output\n\n"
                + "Seed 42; two 60-second rounds; 10 cm armor; charged shields; live contact; abundant firing power. "
                + "Optional shield recharge is held. Values load from the application catalog.\n\n");
        for (String defense : List.of("", CombatModuleCatalog.LASER_POINT_DEFENSE,
                CombatModuleCatalog.KINETIC_POINT_DEFENSE, CombatModuleCatalog.ION_POINT_DEFENSE)) {
            var run = fight(defense, closeRange(), baseline());
            assertEquals(run.result(), fight(defense, closeRange(), baseline()).result(), "Fixed seed must reproduce the encounter");
            assertEquals(2, run.result().roundReports().size());
            assertTrue(action(run, 0, "WEAPON_HIT", hitPrefix(CombatModuleCatalog.LASER)));
            assertTrue(action(run, 0, "WEAPON_HIT", hitPrefix(CombatModuleCatalog.PARTICLE_BEAM)));
            assertTrue(action(run, 0, "WEAPON_HIT", hitPrefix(CombatModuleCatalog.ION)));
            assertFalse(action(run, 0, "WEAPON_HIT", hitPrefix(CombatModuleCatalog.MASS_DRIVER)));
            assertTrue(action(run, 0, "MISSILE_LAUNCH", CombatModuleCatalog.MASS_DRIVER));
            boolean intercepts = defense.equals(CombatModuleCatalog.LASER_POINT_DEFENSE)
                    || defense.equals(CombatModuleCatalog.KINETIC_POINT_DEFENSE);
            assertEquals(intercepts, action(run, 1, "POINT_DEFENSE", defense));
            assertEquals(!intercepts, action(run, 1, "WEAPON_HIT", hitPrefix(CombatModuleCatalog.MASS_DRIVER)));
            assertTrue(actions(run, 0).stream().filter(a -> a.description().startsWith(hitPrefix(CombatModuleCatalog.ION)))
                    .allMatch(a -> a.description().contains("hull damage 0.000") && !a.description().contains("electronics damage 0.000")));
            var surviving = run.result().survivingAttackerFleet().ships().getFirst();
            var slug = CombatModuleCatalog.module(run.attacker(), CombatModuleCatalog.MASS_DRIVER);
            assertEquals(0, ShipWeaponAmmunition.rounds(surviving.storedCargoKg(), slug));
            assertEquals(2 * slug.operationalStats().get("ammunitionMassKg"), run.source().storedCargoKg().get(slug.ammunitionId()));
            assertTrue(surviving.generatorFuelMassKg() < run.source().generatorFuelMassKg());
            assertTrue(run.target().moduleDamage().damageByMount().isEmpty());
            if (defense.equals(CombatModuleCatalog.KINETIC_POINT_DEFENSE)) {
                var module = CombatModuleCatalog.module(run.defender(), defense);
                assertEquals(module.operationalStats().get("ammunitionMassKg"),
                        run.result().survivingDefenderFleet().ships().getFirst().storedCargoKg().get(module.ammunitionId()));
            }
            appendReport(report, defense.isBlank() ? "No point defense" : CombatModuleCatalog.module(defense).name(), closeRange(), run);
        }
        writeReport("mixed-weapon-scenario.md", report);
    }

    @Test void longerRangeHoldsPulseLasersWhileBeamIonAndKineticApplicationsRemainReachable() throws Exception {
        double meters = (CombatModuleCatalog.module(CombatModuleCatalog.LASER).operationalStats().get("weaponRangeMeters")
                + CombatModuleCatalog.module(CombatModuleCatalog.ION).operationalStats().get("weaponRangeMeters")) / 2;
        var run = fight("", meters, baseline());
        assertTrue(action(run, 0, "WEAPON_HOLD", "out of range"));
        assertFalse(action(run, 0, "WEAPON_HIT", hitPrefix(CombatModuleCatalog.LASER)));
        assertTrue(action(run, 0, "WEAPON_HIT", hitPrefix(CombatModuleCatalog.PARTICLE_BEAM)));
        assertTrue(action(run, 0, "WEAPON_HIT", hitPrefix(CombatModuleCatalog.ION)));
        assertTrue(action(run, 1, "WEAPON_HIT", hitPrefix(CombatModuleCatalog.MASS_DRIVER)));
        assertTrue(actions(run, 1).stream().filter(a -> a.description().startsWith(hitPrefix(CombatModuleCatalog.MASS_DRIVER)))
                .allMatch(a -> !a.description().contains("shield bypass 0.000") && !a.description().contains("armor prevented 0.000")));
        var report = new StringBuilder("# Longer-range mixed weapon output\n\n");
        appendReport(report, "Baseline applications", meters, run);
        writeReport("mixed-weapon-range-scenario.md", report);
    }

    @Test void kineticBypassDamagesArmorUnderSurvivingShieldsWhileIonRemainsShieldBlocked() throws Exception {
        double meters = CombatModuleCatalog.module(CombatModuleCatalog.ION).operationalStats().get("weaponRangeMeters") / 2;
        var run = fight("", meters, baseline(), 4);
        var survivor = run.result().survivingDefenderFleet().ships().getFirst();
        assertTrue(survivor.currentShieldHealth() > 0, "Shields must remain standing during bypass");
        assertTrue(survivor.currentHullHealth() < run.target().currentHullHealth());
        var kinetic = actions(run, 1).stream().filter(a -> a.description().startsWith(hitPrefix(CombatModuleCatalog.MASS_DRIVER)))
                .findFirst().orElseThrow();
        assertFalse(kinetic.description().contains("shield bypass 0.000"));
        assertFalse(kinetic.description().contains("hull damage 0.000"));
        for (int round = 0; round < 2; round++) {
            var ion = actions(run, round).stream().filter(a -> a.description().startsWith(hitPrefix(CombatModuleCatalog.ION)))
                    .findFirst().orElseThrow();
            assertTrue(ion.description().contains("hull damage 0.000"));
            assertTrue(ion.description().contains("electronics damage 0.000"));
        }
        var report = new StringBuilder("# Shield bypass mixed weapon output\n\nFour charged shield emitters; two rounds.\n\n");
        appendReport(report, "Shields survive kinetic penetration", meters, run);
        var edited = new ArrayList<>(baseline());
        edited.set(3, editApplication(CombatModuleCatalog.MASS_DRIVER, Map.of("shieldBypassFraction", 0.0)));
        var noBypass = fight("", meters, edited, 4);
        var protectedShip = noBypass.result().survivingDefenderFleet().ships().getFirst();
        assertEquals(noBypass.target().currentHullHealth(), protectedShip.currentHullHealth());
        assertTrue(protectedShip.currentShieldHealth() > 0);
        appendReport(report, "Test-only zero-bypass application", meters, noBypass);
        writeReport("mixed-weapon-shield-scenario.md", report);
    }

    private ShipModule editApplication(String id, Map<String, Double> changes) throws Exception {
        ObjectNode application = mapper.valueToTree(CombatModuleCatalog.application(id));
        var stats = (ObjectNode) application.get("combatModule").get("operationalStats");
        changes.forEach(stats::put);
        return mapper.treeToValue(application, TechnicalApplication.class).combatModule();
    }

    @Test void applicationOnlyEditsChangeTheMixedEncounterWithoutChangingCapturedBaselineHardware() throws Exception {
        var original = fight("", closeRange(), baseline());
        var tuned = new ArrayList<>(baseline());
        tuned.set(0, editApplication(CombatModuleCatalog.LASER, Map.of("weaponRangeMeters", closeRange() / 2)));
        tuned.set(2, editApplication(CombatModuleCatalog.ION, Map.of("electronicDamageMultiplier", .5)));
        tuned.set(3, editApplication(CombatModuleCatalog.MASS_DRIVER, Map.of("shieldBypassFraction", 0.0, "ammunitionMassKg", 7.0)));
        var changed = fight("", closeRange(), tuned);
        assertFalse(action(changed, 0, "WEAPON_HIT", hitPrefix(CombatModuleCatalog.LASER)));
        assertTrue(action(changed, 0, "WEAPON_HOLD", "out of range"));
        double shieldsAfterBeam = Math.max(0, changed.target().currentShieldHealth()
                - tuned.get(1).operationalStats().get("weaponDamage"));
        double expectedElectronics = Math.max(0, tuned.get(2).operationalStats().get("weaponDamage") - shieldsAfterBeam)
                * tuned.get(2).operationalStats().get("electronicDamageMultiplier");
        assertTrue(action(changed, 0, "WEAPON_HIT", String.format(Locale.ROOT, "electronics damage %.3f", expectedElectronics)));
        assertTrue(actions(changed, 1).stream().filter(a -> a.description().startsWith(hitPrefix(CombatModuleCatalog.MASS_DRIVER)))
                .anyMatch(a -> a.description().contains("shield bypass 0.000")));
        assertEquals(14, changed.source().storedCargoKg().get(tuned.get(3).ammunitionId()));
        assertEquals(0, ShipWeaponAmmunition.rounds(changed.result().survivingAttackerFleet().ships().getFirst().storedCargoKg(), tuned.get(3)));
        assertEquals(CombatModuleCatalog.module(CombatModuleCatalog.LASER), CombatModuleCatalog.module(original.attacker(), CombatModuleCatalog.LASER));
        assertEquals(CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER), CombatModuleCatalog.module(original.attacker(), CombatModuleCatalog.MASS_DRIVER));
        assertTrue(action(original, 0, "WEAPON_HIT", hitPrefix(CombatModuleCatalog.LASER)));
        assertNotEquals(original.result().survivingDefenderFleet().ships(), changed.result().survivingDefenderFleet().ships());
        var report = new StringBuilder("# Application edit mixed weapon output\n\n"
                + "Test-only edits: laser range halved below encounter separation; ion electronics multiplier 0.5; "
                + "kinetic shield bypass 0 and slug mass 7 kg. Production application data is unchanged.\n\n");
        appendReport(report, "Baseline applications", closeRange(), original);
        appendReport(report, "Edited applications", closeRange(), changed);
        writeReport("mixed-weapon-application-scenario.md", report);
    }

    private void writeReport(String name, StringBuilder report) throws Exception {
        var directory = Path.of("target"); Files.createDirectories(directory);
        Files.writeString(directory.resolve(name), report);
    }

    private void appendReport(StringBuilder report, String name, double meters, Run run) {
        report.append("## ").append(name).append("\n\nRange: ").append(meters / 1000).append(" km.\n\n");
        for (var round : run.result().visualRounds()) {
            report.append("### Round ").append(round.roundNumber()).append("\n\n");
            round.eventLogs().forEach(log -> report.append("- ").append(log).append('\n'));
            report.append('\n');
        }
        var target = run.result().survivingDefenderFleet().ships().getFirst();
        var source = run.result().survivingAttackerFleet().ships().getFirst();
        report.append("Remaining attacker cargo: ").append(source.storedCargoKg()).append(" kg.\n\n")
                .append("Defender hull: ").append(target.currentHullHealth()).append("; shields: ")
                .append(target.currentShieldHealth()).append("; remaining cargo: ").append(target.storedCargoKg()).append(" kg.\n\n");
        for (int mount = 0; mount < run.defender().equippedModuleIds().size(); mount++) report.append("- Mount ")
                .append(mount + 1).append(" (").append(run.defender().equippedModuleIds().get(mount)).append("): ")
                .append(String.format(Locale.ROOT, "%.1f%% condition\n", target.moduleDamage().condition(mount) * 100));
        report.append('\n');
    }
}
