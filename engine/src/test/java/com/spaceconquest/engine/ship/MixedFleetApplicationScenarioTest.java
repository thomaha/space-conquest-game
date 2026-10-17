package com.spaceconquest.engine.ship;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.engine.TechnicalApplication;
import com.spaceconquest.engine.combat.TacticalCombatProcessor;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Two-round fleet probes with one weapon family per ship, not a balance simulation. */
class MixedFleetApplicationScenarioTest {
    private static final List<String> WEAPONS = List.of(CombatModuleCatalog.LASER,
            CombatModuleCatalog.PARTICLE_BEAM, CombatModuleCatalog.ION, CombatModuleCatalog.MASS_DRIVER);
    private final ShipPointDefenseTest fixture = new ShipPointDefenseTest();
    private final ObjectMapper mapper = new ObjectMapper();
    private record Run(TacticalCombatProcessor.CombatEngagementResult result, List<ShipDesign> designs) {}

    private Run fight(double meters, Map<String, Map<String, Double>> edits, boolean shields) throws Exception {
        return fight(meters, edits, shields, 1000);
    }
    private Run fight(double meters, Map<String, Map<String, Double>> edits, boolean shields, double targetThrust) throws Exception {
        var designs = new ArrayList<ShipDesign>(); var ships = new ArrayList<ShipInstance>();
        for (String id : WEAPONS) {
            ObjectNode app = mapper.valueToTree(CombatModuleCatalog.application(id));
            var stats = (ObjectNode) app.get("combatModule").get("operationalStats");
            edits.getOrDefault(id, Map.of()).forEach(stats::put);
            var weapon = mapper.treeToValue(app, TechnicalApplication.class).combatModule();
            ObjectNode blueprint = mapper.valueToTree(fixture.design(id, 10000, id));
            blueprint.put("ownerEntityId", "mixed");
            ((ObjectNode) blueprint.get("combatModules")).set(id, mapper.valueToTree(weapon));
            var design = mapper.treeToValue(blueprint, ShipDesign.class); designs.add(design);
            var cargo = weapon.ammunitionId().isBlank() ? Map.<String, Double>of()
                    : Map.of(weapon.ammunitionId(), 2 * weapon.operationalStats().get("ammunitionMassKg"));
            ships.add(new ShipInstance(id, id, "mixed", 1000, 0, 0, cargo)
                    .withPowerState(fixture.ship(id, design, 0).powerState()));
        }
        var modules = new ArrayList<String>();
        if (shields) modules.addAll(Collections.nCopies(16, CombatModuleCatalog.SHIELD));
        modules.add(SensorModuleCatalog.BASIC);
        var defender = fixture.design("target", 10000, modules.toArray(String[]::new));
        ObjectNode armored = mapper.valueToTree(defender); armored.put("armorThicknessCm", 10); armored.put("totalThrustN", targetThrust);
        defender = mapper.treeToValue(armored, ShipDesign.class); designs.add(defender);
        var target = new ShipInstance("target", "target", "target", 1000,
                CombatModuleCatalog.shieldCapacity(defender), 0, Map.of())
                .withPowerState(fixture.ship("target", defender, 0).powerState());
        int[] rounds = {0};
        var result = new TacticalCombatProcessor(new Random(42)).resolveFleetEngagement(
                fixture.fleet("mixed", ships), fixture.fleet("target", List.of(target)), designs,
                (a, b) -> new TacticalCombatProcessor.Withdrawal(a, b, ++rounds[0] >= 2),
                (a, b) -> true, (a, b) -> meters);
        return new Run(result, List.copyOf(designs));
    }
    private List<TacticalCombatProcessor.CombatActionReport> hits(Run run, String id) {
        String prefix = CombatModuleCatalog.module(id).name() + " hit:";
        return run.result().roundReports().stream().flatMap(round -> round.detailedActions().stream())
                .filter(action -> action.phase().equals("WEAPON_HIT") && action.description().startsWith(prefix)).toList();
    }
    private double closeRange() {
        return WEAPONS.stream().map(CombatModuleCatalog::module)
                .mapToDouble(module -> module.operationalStats().get("weaponRangeMeters")).min().orElseThrow() / 2;
    }
    @Test void separateShipsRetainTheirRolesAndApplicationBypassControlsDamageUnderStandingShields() throws Exception {
        double meters = closeRange(); var baseline = fight(meters, Map.of(), true);
        assertEquals(baseline, fight(meters, Map.of(), true)); assertEquals(2, baseline.result().roundReports().size());
        for (String id : WEAPONS) assertFalse(hits(baseline, id).isEmpty(), id);
        assertTrue(baseline.result().survivingDefenderFleet().ships().getFirst().currentShieldHealth() > 0);
        assertTrue(hits(baseline, CombatModuleCatalog.MASS_DRIVER).stream()
                .anyMatch(hit -> !hit.description().contains("shield bypass 0.000") && !hit.description().contains("hull damage 0.000")
                        && !hit.description().contains("armor prevented 0.000")));
        assertTrue(hits(baseline, CombatModuleCatalog.ION).stream().allMatch(hit -> hit.description().contains("hull damage 0.000")));
        var changed = fight(meters, Map.of(CombatModuleCatalog.MASS_DRIVER, Map.of("shieldBypassFraction", 0.0)), true);
        assertEquals(1000, changed.result().survivingDefenderFleet().ships().getFirst().currentHullHealth());
        assertTrue(changed.result().survivingDefenderFleet().ships().getFirst().currentShieldHealth() > 0);
        var kinetic = baseline.result().survivingAttackerFleet().ships().stream()
                .filter(ship -> ship.id().equals(CombatModuleCatalog.MASS_DRIVER)).findFirst().orElseThrow();
        assertEquals(0, ShipWeaponAmmunition.rounds(kinetic.storedCargoKg(), CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER)));
        report("mixed-fleet-shields.md", baseline, changed);
    }
    @Test void applicationRangeAccuracyAndElectronicsEditsChangeOnlyNewCapturedHardware() throws Exception {
        double meters = closeRange(); var baseline = fight(meters, Map.of(), false);
        var edited = fight(meters, Map.of(CombatModuleCatalog.LASER, Map.of("weaponRangeMeters", meters / 2),
                CombatModuleCatalog.PARTICLE_BEAM, Map.of("accuracy", 0.0),
                CombatModuleCatalog.ION, Map.of("electronicDamageMultiplier", 0.0)), false);
        assertFalse(hits(baseline, CombatModuleCatalog.LASER).isEmpty());
        assertFalse(hits(baseline, CombatModuleCatalog.PARTICLE_BEAM).isEmpty());
        assertTrue(hits(edited, CombatModuleCatalog.LASER).isEmpty()); assertTrue(hits(edited, CombatModuleCatalog.PARTICLE_BEAM).isEmpty());
        assertTrue(hits(baseline, CombatModuleCatalog.ION).stream().anyMatch(hit -> !hit.description().contains("electronics damage 0.000")));
        assertTrue(hits(edited, CombatModuleCatalog.ION).stream().allMatch(hit -> hit.description().contains("electronics damage 0.000")
                && hit.description().contains("hull damage 0.000")));
        assertEquals(baseline, fight(meters, Map.of(), false), "Test edits cannot change catalog or captured baseline designs");
        double distant = (CombatModuleCatalog.module(CombatModuleCatalog.LASER).operationalStats().get("weaponRangeMeters")
                + CombatModuleCatalog.module(CombatModuleCatalog.ION).operationalStats().get("weaponRangeMeters")) / 2;
        var ranged = fight(distant, Map.of(), false);
        assertTrue(hits(ranged, CombatModuleCatalog.LASER).isEmpty()); assertFalse(hits(ranged, CombatModuleCatalog.PARTICLE_BEAM).isEmpty());
        report("mixed-fleet-applications.md", baseline, edited, ranged);
    }
    @Test void agileLongRangeTargetKeepsPerfectBeamTrackingWhileApplicationSensitivityReducesKineticAccuracy() throws Exception {
        double meters = CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER).operationalStats().get("weaponRangeMeters") * .9;
        var baseline = fight(meters, Map.of(), false, 100000000);
        assertEquals(2, hits(baseline, CombatModuleCatalog.PARTICLE_BEAM).size());
        assertTrue(hits(baseline, CombatModuleCatalog.LASER).isEmpty());
        assertTrue(hits(baseline, CombatModuleCatalog.MASS_DRIVER).isEmpty());
        assertTrue(baseline.result().roundReports().stream().flatMap(round -> round.detailedActions().stream())
                .anyMatch(action -> action.phase().equals("WEAPON_MISS")
                        && action.description().startsWith(CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER).name())));
        var edited = fight(meters, Map.of(CombatModuleCatalog.MASS_DRIVER,
                Map.of("accuracy", 1.0, "rangeAccuracyFalloff", 0.0, "targetAccelerationSensitivity", 0.0)), false, 100000000);
        assertFalse(hits(edited, CombatModuleCatalog.MASS_DRIVER).isEmpty());
        assertEquals(baseline, fight(meters, Map.of(), false, 100000000));
        report("mixed-fleet-tracking.md", baseline, edited);
    }
    private void report(String file, Run... runs) throws Exception {
        var output = new StringBuilder("# Mixed fleet scenario output\n\nSeed 42; two rounds; four weapon ships. Test application edits leave production values unchanged.\n\n");
        for (int index = 0; index < runs.length; index++) {
            output.append("## Probe ").append(index + 1).append("\n\n");
            for (var round : runs[index].result().roundReports()) {
                output.append("### Round ").append(round.roundNumber()).append("\n\n");
                round.detailedActions().forEach(action -> output.append("- ").append(action.description()).append('\n'));
                output.append('\n');
            }
        }
        Files.writeString(Path.of("target", file), output);
    }
}
