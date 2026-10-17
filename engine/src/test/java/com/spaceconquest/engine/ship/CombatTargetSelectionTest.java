package com.spaceconquest.engine.ship;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.engine.TechnicalApplication;
import com.spaceconquest.engine.combat.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatTargetSelectionTest {
    private final ShipPointDefenseTest fixture = new ShipPointDefenseTest();
    private final ObjectMapper mapper = new ObjectMapper();
    private ShipModule application(String id, Map<String, Double> edits) throws Exception {
        ObjectNode json = mapper.valueToTree(CombatModuleCatalog.application(id));
        var stats = (ObjectNode) json.get("combatModule").get("operationalStats"); edits.forEach(stats::put);
        return mapper.treeToValue(json, TechnicalApplication.class).combatModule();
    }
    private ShipDesign design(String id, double damage, double armor, double thrust) throws Exception {
        var weapon = application(CombatModuleCatalog.PARTICLE_BEAM, Map.of("weaponDamage", damage));
        ObjectNode json = mapper.valueToTree(fixture.design(id, 10000, weapon.id()));
        json.put("calculatedStructuralIntegrity", 1); json.put("armorThicknessCm", armor); json.put("totalThrustN", thrust);
        ((ObjectNode) json.get("combatModules")).set(weapon.id(), mapper.valueToTree(weapon));
        return mapper.treeToValue(json, ShipDesign.class);
    }
    private ShipInstance ship(ShipDesign design, double hull) {
        return new ShipInstance(design.id(), design.id(), design.ownerEntityId(), hull, 0, 0, Map.of())
                .withPowerState(fixture.ship(design.id(), design, 0).powerState());
    }
    private WeaponHit physical(double damage) { return new WeaponHit(CombatModuleCatalog.module(CombatModuleCatalog.PARTICLE_BEAM), damage, 0); }
    private List<TacticalCombatProcessor.CombatActionReport> apply(List<ShipInstance> ships, List<ShipDesign> designs, List<WeaponHit> hits) {
        var actions = new ArrayList<TacticalCombatProcessor.CombatActionReport>();
        WeaponImpactResolver.apply(ships, hits, designs, "ALL", new Random(42), actions); return actions;
    }
    private List<String> hitTargets(List<TacticalCombatProcessor.CombatActionReport> actions) {
        return actions.stream().filter(action -> action.phase().equals("WEAPON_HIT"))
                .map(TacticalCombatProcessor.CombatActionReport::targetEntityId).toList();
    }
    @Test void coordinatedHitsFinishTheFastDangerousTargetThenSwitchWithoutOverkillOrSnapshotMutation() throws Exception {
        var fragile = design("fragile", 200, 0, 0); var cruiser = design("cruiser", 220, 0, 0);
        var original = List.of(ship(cruiser, 1000), ship(fragile, 100));
        var actions = new ArrayList<TacticalCombatProcessor.CombatActionReport>();
        var result = WeaponImpactResolver.apply(original, Collections.nCopies(5, physical(60)), List.of(cruiser, fragile),
                "ALL", new Random(42), actions);
        assertEquals(List.of("fragile", "fragile", "cruiser", "cruiser", "cruiser"), hitTargets(actions));
        assertEquals(1, result.ships().size()); assertEquals("cruiser", result.ships().getFirst().id());
        assertEquals(820, result.ships().getFirst().currentHullHealth());
        assertEquals(100, original.get(1).currentHullHealth()); assertEquals(ShipModuleDamage.empty(), original.get(1).moduleDamage());
        assertTrue(actions.stream().anyMatch(action -> action.phase().equals("TARGET_SELECTION") && action.description().contains("funded threat")));
        assertEquals(actions, apply(original, List.of(cruiser, fragile), Collections.nCopies(5, physical(60))));
    }
    @Test void highIncomingDamageOutweighsFinishingAnAlmostHarmlessShip() throws Exception {
        var weak = design("weak", 10, 0, 0); var dangerous = design("dangerous", 400, 0, 0);
        assertEquals(List.of("dangerous"), hitTargets(apply(List.of(ship(weak, 100), ship(dangerous, 1000)),
                List.of(weak, dangerous), List.of(physical(60)))));
    }
    @Test void ionDisablesFundedOffenseThenSwitchesAwayFromDisabledElectronicsWithoutHullDamage() throws Exception {
        var strong = design("strong", 400, 0, 0); var weak = design("weak", 180, 0, 0); var disabled = design("disabled", 1000, 0, 0);
        var disarmed = ship(disabled, 1000).withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0)));
        var actions = new ArrayList<TacticalCombatProcessor.CombatActionReport>();
        var result = WeaponImpactResolver.apply(List.of(disarmed, ship(weak, 1000), ship(strong, 1000)),
                Collections.nCopies(2, new WeaponHit(CombatModuleCatalog.module(CombatModuleCatalog.ION), 100, 0)),
                List.of(disabled, weak, strong), "ALL", new Random(42), actions);
        assertEquals(List.of("strong", "weak"), hitTargets(actions));
        assertTrue(result.ships().stream().allMatch(ship -> ship.currentHullHealth() == 1000));
        assertTrue(result.ships().stream().allMatch(ship -> ship.moduleDamage().condition(0) == 0));
        assertTrue(actions.stream().filter(action -> action.phase().equals("WEAPON_HIT"))
                .allMatch(action -> action.description().contains("hull damage 0.000")));
    }
    @Test void repeatedSelectionRechecksDisabledWeaponsAndExplainsTheRemainingFundedThreat() throws Exception {
        var strong = design("strong", 400, 0, 0); var weak = design("weak", 180, 0, 0);
        var targets = List.of(ship(strong, 1000), ship(weak, 1000));
        var ion = new WeaponHit(CombatModuleCatalog.module(CombatModuleCatalog.ION), 100, 0);
        var selector = new CombatTargetSelector(List.of(strong, weak), CombatTargetingDoctrine.defaults());
        var first = selector.choose(targets, ion, "ALL");
        assertEquals("strong", targets.get(first.index()).id());
        assertTrue(first.fundedFirepower() > 0); assertTrue(first.immediateReduction() > 0);
        var disabled = WeaponImpactResolver.hit(targets.getFirst(), strong, ion, "ALL");
        assertEquals(1000, disabled.currentHullHealth());
        var nextTargets = List.of(disabled, targets.getLast());
        var next = selector.choose(nextTargets, ion, "ALL");
        assertEquals("weak", nextTargets.get(next.index()).id(), "Previous focus cannot retain a disabled threat");
        assertTrue(next.explanation(ion.weapon().name(), "weak").contains("funded firepower"));
        assertTrue(next.explanation(ion.weapon().name(), "weak").contains("estimates"));
        assertEquals(1, targets.getFirst().moduleDamage().condition(0), "Discarded forecasts cannot damage live targets");
    }
    @Test void singleTargetAndHarmlessFallbackStillProduceTruthfulSelectionExplanations() throws Exception {
        var design = design("only", 180, 0, 0); var target = ship(design, 1000);
        var actions = apply(List.of(target), List.of(design), List.of(physical(60)));
        var decision = actions.stream().filter(action -> action.phase().equals("TARGET_SELECTION")).findFirst().orElseThrow();
        assertTrue(decision.description().contains("funded firepower 180.000"));
        var harmless = target.withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0)));
        var fallback = apply(List.of(harmless), List.of(design), List.of(physical(60))).stream()
                .filter(action -> action.phase().equals("TARGET_SELECTION")).findFirst().orElseThrow();
        assertTrue(fallback.description().contains("fallback to useful damage"));
        assertTrue(fallback.description().contains("funded firepower 0.000"));
    }
    @Test void beamTracksTheAgileThreatWhileKineticSuitabilityAndApplicationEditsChangeTheChosenTarget() throws Exception {
        var agile = design("agile", 300, 0, 100000000); var slow = design("slow", 180, 0, 0);
        var ships = List.of(ship(agile, 1000), ship(slow, 1000)); var designs = List.of(agile, slow);
        double meters = CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER).operationalStats().get("weaponRangeMeters") * .9;
        var kinetic = new WeaponHit(CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER), 220, meters);
        var beam = new WeaponHit(CombatModuleCatalog.module(CombatModuleCatalog.PARTICLE_BEAM), 180, meters);
        assertEquals(List.of("agile"), hitTargets(apply(ships, designs, List.of(beam))));
        assertEquals("slow", ships.get(new CombatTargetSelector(designs, CombatTargetingDoctrine.defaults())
                .choose(ships, kinetic, "ALL").index()).id());
        assertTrue(apply(ships, designs, List.of(kinetic)).stream()
                .anyMatch(action -> action.phase().equals("TARGET_SELECTION") && action.targetEntityId().equals("slow")));
        var tuned = new WeaponHit(application(CombatModuleCatalog.MASS_DRIVER,
                Map.of("accuracy", 1.0, "rangeAccuracyFalloff", 0.0, "targetAccelerationSensitivity", 0.0)), 220, meters);
        assertEquals(List.of("agile"), hitTargets(apply(ships, designs, List.of(tuned))));
        assertEquals("slow", ships.get(new CombatTargetSelector(designs, CombatTargetingDoctrine.defaults())
                .choose(ships, kinetic, "ALL").index()).id(), "Application copy cannot change captured baseline");
    }
    @Test void emptyMagazinesAreNotAnIncomingThreatAndQuotesDoNotSpendTheTargetsStores() throws Exception {
        var empty = fixture.design("empty", 10000, CombatModuleCatalog.MASS_DRIVER);
        var active = design("active", 180, 0, 0); var emptyShip = fixture.ship("empty", empty, 0);
        var activeShip = ship(active, 1000);
        assertEquals(List.of("active"), hitTargets(apply(List.of(emptyShip, activeShip), List.of(empty, active), List.of(physical(60)))));
        assertEquals(10, emptyShip.generatorFuelMassKg()); assertEquals(10, activeShip.generatorFuelMassKg());
        assertTrue(emptyShip.storedCargoKg().isEmpty());
    }
    @Test void doctrineWeightsAreDataDefinedAndCanChangeTheTradeoffWithoutChangingWeapons() throws Exception {
        var weak = design("weak", 40, 0, 0);
        ObjectNode json = mapper.valueToTree(design("shielded", 1000, 0, 0));
        ((com.fasterxml.jackson.databind.node.ArrayNode) json.get("equippedModuleIds")).add(CombatModuleCatalog.SHIELD);
        ((ObjectNode) json.get("combatModules")).set(CombatModuleCatalog.SHIELD, mapper.valueToTree(CombatModuleCatalog.module(CombatModuleCatalog.SHIELD)));
        ((ObjectNode) json.get("powerProfile")).put("hotelKw", json.get("powerProfile").get("hotelKw").asDouble()
                + CombatModuleCatalog.module(CombatModuleCatalog.SHIELD).powerDrawKw());
        var shielded = mapper.treeToValue(json, ShipDesign.class);
        var protectedShip = new ShipInstance("shielded", "shielded", "shielded", 1000, 300, 0, Map.of())
                .withPowerState(ship(shielded, 1000).powerState());
        var targets = List.of(ship(weak, 100), protectedShip); var designs = List.of(weak, shielded);
        assertEquals("shielded", targets.get(new CombatTargetSelector(designs, CombatTargetingDoctrine.defaults())
                .choose(targets, physical(60), "ALL").index()).id());
        ObjectNode doctrine = mapper.valueToTree(CombatTargetingDoctrine.defaults()); doctrine.put("immediateThreatReductionWeight", 100);
        var retuned = mapper.treeToValue(doctrine, CombatTargetingDoctrine.class);
        assertEquals("weak", targets.get(new CombatTargetSelector(designs, retuned).choose(targets, physical(60), "ALL").index()).id());
        assertThrows(IllegalArgumentException.class, () -> new CombatTargetingDoctrine(-1, 1, 0, 1, 8));
        assertThrows(IllegalArgumentException.class, () -> new CombatTargetingDoctrine(1, 0, 0, 1, 100000));
    }
    @Test void selectionRequotesRangeForDifferentPaidWeaponsInTheSameVolley() throws Exception {
        var shortRange = fixture.design("short", 10000, CombatModuleCatalog.LASER);
        var longRange = design("long", 180, 0, 0);
        var selector = new CombatTargetSelector(List.of(shortRange, longRange), CombatTargetingDoctrine.defaults());
        var targets = List.of(ship(shortRange, 1000), ship(longRange, 1000));
        assertEquals("short", targets.get(selector.choose(targets, physical(60), "ALL").index()).id());
        var distant = new WeaponHit(CombatModuleCatalog.module(CombatModuleCatalog.PARTICLE_BEAM), 60, 100000);
        assertEquals("long", targets.get(selector.choose(targets, distant, "ALL").index()).id());
    }
    @Test void actualPaidSalvoConcentrationRemovesMoreNextRoundIncomingDamageThanRotatingHits() throws Exception {
        var fragile = design("fragile", 200, 0, 0); var cruiser = design("cruiser", 220, 0, 0);
        var weapon = application(CombatModuleCatalog.PARTICLE_BEAM, Map.of("weaponDamage", 120.0, "shotsPerRound", 2.0));
        ObjectNode json = mapper.valueToTree(fixture.design("attacker", 10000, weapon.id()));
        ((ObjectNode) json.get("combatModules")).set(weapon.id(), mapper.valueToTree(weapon));
        var attacker = mapper.treeToValue(json, ShipDesign.class); var source = fixture.ship("attacker", attacker, 0);
        var paid = ShipCombatPower.round(source, attacker, 1.0 / 60, true, false);
        assertEquals(2, paid.directHits().size()); assertTrue(paid.paidWeaponKwh() > 0);
        assertTrue(paid.ship().generatorFuelMassKg() < source.generatorFuelMassKg());
        var targets = List.of(ship(cruiser, 1000), ship(fragile, 100)); var designs = List.of(cruiser, fragile);
        var actions = new ArrayList<TacticalCombatProcessor.CombatActionReport>();
        var focused = WeaponImpactResolver.apply(targets, paid.directHits(), designs, "ALL", new Random(42), actions);
        var rotated = List.of(WeaponImpactResolver.hit(targets.get(0), cruiser, paid.directHits().get(0), "ALL"),
                WeaponImpactResolver.hit(targets.get(1), fragile, paid.directHits().get(1), "ALL"));
        double rotatingThreat = ShipCombatPower.fundedFirepower(rotated.get(0), cruiser, 1.0 / 60, true)
                + ShipCombatPower.fundedFirepower(rotated.get(1), fragile, 1.0 / 60, true);
        assertEquals(1, focused.ships().size()); assertEquals(List.of("fragile", "fragile"), hitTargets(actions));
        assertTrue(ShipCombatPower.fundedFirepower(focused.ships().getFirst(), cruiser, 1.0 / 60, true) < rotatingThreat);
        assertEquals(10, source.generatorFuelMassKg());
    }
    @Test void capturedBypassMakesKineticsFinishAShieldedThreatWhileBeamsAndIonPreferTheExposedThreat() throws Exception {
        var exposed = design("exposed", 180, 0, 0);
        ObjectNode json = mapper.valueToTree(design("shielded", 180, 0, 0));
        for (int mount = 0; mount < 3; mount++)
            ((com.fasterxml.jackson.databind.node.ArrayNode) json.get("equippedModuleIds")).add(CombatModuleCatalog.SHIELD);
        ((ObjectNode) json.get("combatModules")).set(CombatModuleCatalog.SHIELD, mapper.valueToTree(CombatModuleCatalog.module(CombatModuleCatalog.SHIELD)));
        ((ObjectNode) json.get("powerProfile")).put("hotelKw", json.get("powerProfile").get("hotelKw").asDouble()
                + 3 * CombatModuleCatalog.module(CombatModuleCatalog.SHIELD).powerDrawKw());
        var shielded = mapper.treeToValue(json, ShipDesign.class);
        var protectedShip = new ShipInstance("shielded", "shielded", "shielded", 100, 600, 0, Map.of())
                .withPowerState(ship(shielded, 100).powerState());
        var targets = List.of(protectedShip, ship(exposed, 1000)); var designs = List.of(shielded, exposed);
        var kinetic = new WeaponHit(CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER), 220, 0);
        var beam = new WeaponHit(CombatModuleCatalog.module(CombatModuleCatalog.PARTICLE_BEAM), 180, 0);
        var ion = new WeaponHit(CombatModuleCatalog.module(CombatModuleCatalog.ION), 100, 0);
        assertEquals("shielded", targets.get(new CombatTargetSelector(designs, CombatTargetingDoctrine.defaults())
                .choose(targets, kinetic, "ALL").index()).id());
        assertEquals("exposed", targets.get(new CombatTargetSelector(designs, CombatTargetingDoctrine.defaults())
                .choose(targets, beam, "ALL").index()).id());
        assertEquals("exposed", targets.get(new CombatTargetSelector(designs, CombatTargetingDoctrine.defaults())
                .choose(targets, ion, "ALL").index()).id());
    }
}
