package com.spaceconquest.engine.ship;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.engine.TechnicalApplication;
import com.spaceconquest.engine.combat.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FleetCombatPositioningTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ShipPointDefenseTest fixture = new ShipPointDefenseTest();
    private ShipDesign design(String id, String weaponId, Map<String, Double> edits) throws Exception {
        var app = CombatModuleCatalog.application(weaponId);
        ObjectNode application = mapper.valueToTree(app);
        ObjectNode stats = (ObjectNode) application.get("combatModule").get("operationalStats");
        edits.forEach(stats::put);
        var module = mapper.treeToValue(application, TechnicalApplication.class).combatModule();
        ObjectNode blueprint = mapper.valueToTree(fixture.design(id, 10000, weaponId));
        blueprint.put("calculatedStructuralIntegrity", 10); blueprint.put("totalThrustN", 0);
        ((ObjectNode) blueprint.get("combatModules")).set(weaponId, mapper.valueToTree(module));
        return mapper.treeToValue(blueprint, ShipDesign.class);
    }
    private ShipInstance ship(ShipDesign design) {
        var module = CombatModuleCatalog.module(design, design.equippedModuleIds().getFirst());
        var original = fixture.ship(design.id(), design, 0);
        return new ShipInstance(original.id(), original.designId(), original.ownerEntityId(), 1000, 0, 0,
                module.ammunitionId().isBlank() ? Map.of() : Map.of(module.ammunitionId(), module.operationalStats().get("ammunitionMassKg") * 4))
                .withPowerState(original.powerState());
    }
    private FleetCombatPositioning.Quote quote(ShipDesign own, ShipDesign enemy, double distance) {
        return FleetCombatPositioning.quote(List.of(own, enemy), List.of(ship(own)), List.of(ship(enemy)), distance,
                CombatPositioningDoctrine.defaults());
    }
    @Test void beamsPreferSafeRangeWhileKineticsValueAccuracyAndFasterArrival() throws Exception {
        var beam = design("beam", CombatModuleCatalog.PARTICLE_BEAM, Map.of());
        var kinetic = design("kinetic", CombatModuleCatalog.MASS_DRIVER, Map.of());
        var enemy = design("enemy", CombatModuleCatalog.LASER, Map.of());
        assertTrue(quote(beam, enemy, 900000).score() > quote(beam, enemy, 10000).score());
        assertTrue(quote(kinetic, enemy, 100000).score() > quote(kinetic, enemy, 270000).score());
        var fast = design("fast", CombatModuleCatalog.MASS_DRIVER,
                Map.of("projectileSpeedMetersPerSecond", 1e9, "rangeAccuracyFalloff", 0.0));
        assertTrue(quote(fast, enemy, 270000).usefulDamage() > quote(kinetic, enemy, 270000).usefulDamage());
    }
    @Test void pulseLaserEscortsCloseAndApplicationRangeChangesRequoteCapturedHardware() throws Exception {
        var laser = design("laser", CombatModuleCatalog.LASER, Map.of());
        var ion = design("ion", CombatModuleCatalog.ION, Map.of());
        double range = CombatModuleCatalog.module(laser, CombatModuleCatalog.LASER).operationalStats().get("weaponRangeMeters");
        assertEquals(0, quote(laser, ion, range * 2).usefulDamage());
        assertTrue(quote(laser, ion, range * .5).usefulDamage() > 0);
        var longer = design("longer", CombatModuleCatalog.LASER, Map.of("weaponRangeMeters", range * 3));
        assertTrue(quote(longer, ion, range * 2).usefulDamage() > 0);
        assertEquals(0, quote(laser, ion, range * 2).usefulDamage(), "Captured baseline stays unchanged");
    }
    @Test void ionSupportValuesElectronicDisablementWithoutInventingHullDamage() throws Exception {
        var ion = design("ion", CombatModuleCatalog.ION, Map.of("weaponDamage", 100.0));
        var enemy = design("enemy", CombatModuleCatalog.PARTICLE_BEAM, Map.of("weaponDamage", 400.0));
        var source = ship(ion); var target = ship(enemy);
        var result = FleetCombatPositioning.quote(List.of(ion, enemy), List.of(source), List.of(target), 0,
                CombatPositioningDoctrine.defaults());
        assertTrue(result.threatReduction() > 0); assertTrue(result.usefulDamage() > 0);
        assertEquals(1000, target.currentHullHealth()); assertEquals(ShipModuleDamage.empty(), target.moduleDamage());
        assertEquals(10, source.generatorFuelMassKg());
    }
    @Test void emptyAndDisabledWeaponsDoNotPositionForImaginaryFireAndProjectionIsBounded() throws Exception {
        var kinetic = design("kinetic", CombatModuleCatalog.MASS_DRIVER, Map.of());
        var enemy = design("enemy", CombatModuleCatalog.ION, Map.of());
        var original = ship(kinetic);
        var empty = new ShipInstance(original.id(), original.designId(), original.ownerEntityId(), 1000, 0, 0, Map.of())
                .withPowerState(original.powerState());
        var disabled = original.withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0)));
        for (var source : List.of(empty, disabled)) assertEquals(0, FleetCombatPositioning.quote(List.of(kinetic, enemy),
                List.of(source), List.of(ship(enemy)), 0, CombatPositioningDoctrine.defaults()).usefulDamage());
        ObjectNode data = mapper.valueToTree(CombatPositioningDoctrine.defaults()); data.put("maxSalvoImpacts", 1);
        var doctrine = mapper.treeToValue(data, CombatPositioningDoctrine.class);
        var pulse = design("pulse", CombatModuleCatalog.LASER, Map.of("shotsPerRound", 10.0));
        assertEquals(1, FleetCombatPositioning.quote(List.of(pulse, enemy), List.of(ship(pulse)), List.of(ship(enemy)), 0, doctrine).projectedImpacts());
        assertFalse(original.storedCargoKg().isEmpty()); assertEquals(10, original.generatorFuelMassKg());
        data.put("incomingDamageWeight", -1);
        assertThrows(Exception.class, () -> mapper.treeToValue(data, CombatPositioningDoctrine.class));
    }
    @Test void doctrineEditsCanTradeKineticAccuracyForDistanceWithoutChangingWeaponHardware() throws Exception {
        var kinetic = design("kinetic", CombatModuleCatalog.MASS_DRIVER, Map.of());
        var ion = design("ion", CombatModuleCatalog.ION, Map.of());
        var designs = List.of(kinetic, ion); var own = List.of(ship(kinetic)); var enemy = List.of(ship(ion));
        var baseline = CombatPositioningDoctrine.defaults();
        assertTrue(FleetCombatPositioning.quote(designs, own, enemy, 100000, baseline).score()
                > FleetCombatPositioning.quote(designs, own, enemy, 450000, baseline).score());
        ObjectNode json = mapper.valueToTree(baseline); json.put("distanceWeight", 100);
        var retuned = mapper.treeToValue(json, CombatPositioningDoctrine.class);
        assertTrue(FleetCombatPositioning.quote(designs, own, enemy, 100000, retuned).score()
                < FleetCombatPositioning.quote(designs, own, enemy, 450000, retuned).score());
        assertFalse(own.getFirst().storedCargoKg().isEmpty()); assertEquals(10, own.getFirst().generatorFuelMassKg());
    }
}
