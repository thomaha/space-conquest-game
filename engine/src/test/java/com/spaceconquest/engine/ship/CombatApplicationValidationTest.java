package com.spaceconquest.engine.ship;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CombatApplicationValidationTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private ObjectNode app(String id) { return mapper.valueToTree(CombatModuleCatalog.application(id)); }
    private void rejected(ObjectNode json, String field) throws Exception {
        var decoded = mapper.treeToValue(json, TechnicalApplication.class);
        var failure = assertThrows(IllegalArgumentException.class, () -> CombatApplicationValidation.validate(decoded));
        assertTrue(failure.getMessage().contains(decoded.id())); assertTrue(failure.getMessage().contains(field), failure.getMessage());
    }
    @Test void allProductionCombatAndAuxiliaryApplicationsValidate() throws Exception {
        for (var technology : DataModelLoader.loadTechnologies()) for (var app : technology.applications())
            assertDoesNotThrow(() -> CombatApplicationValidation.validate(app), app.id());
    }
    @Test void missingOffensiveProjectileDefenseAndShieldCapabilitiesNameTheirApplicationAndField() throws Exception {
        for (var entry : Map.of(CombatModuleCatalog.LASER, "accuracy", CombatModuleCatalog.MASS_DRIVER, "projectileHasElectronics",
                CombatModuleCatalog.ION, "electronicDamageMultiplier", CombatModuleCatalog.LASER_POINT_DEFENSE, "pointDefenseRangeMeters",
                CombatModuleCatalog.SHIELD, "shieldRechargePointsPerHour").entrySet()) {
            var json = app(entry.getKey()); ((ObjectNode) json.get("combatModule").get("operationalStats")).remove(entry.getValue()); rejected(json, entry.getValue());
        }
    }
    @Test void invalidFractionsWholeCountsArmorDependenciesAndBooleanFlagsFailClearly() throws Exception {
        for (var entry : Map.of("accuracy", 1.1, "shotsPerRound", 1.5, "pointDefenseInterceptable", .5).entrySet()) {
            var json = app(CombatModuleCatalog.LASER); ((ObjectNode) json.get("combatModule").get("operationalStats")).put(entry.getKey(), entry.getValue()); rejected(json, entry.getKey());
        }
        var kinetic = app(CombatModuleCatalog.MASS_DRIVER); ((ObjectNode) kinetic.get("combatModule").get("operationalStats")).remove("heavyArmorThresholdCm");
        rejected(kinetic, "heavyArmorThresholdCm");
    }
    @Test void missingRepairRatesBadEffectKeysAndAbsentMaterialRecipesFailClearly() throws Exception {
        var json = (ObjectNode) mapper.valueToTree(AuxiliaryApplicationCatalog.application("surface_weapon_emplacements"));
        ((ObjectNode) json.get("auxiliaryHardware").get("stats")).remove("repairMountWorkHoursPerPoint"); rejected(json, "repairMountWorkHoursPerPoint");
        var unknown = app(CombatModuleCatalog.LASER); ((ArrayNode) unknown.get("effectStats")).add("not_a_capability"); rejected(unknown, "not_a_capability");
        var noRecipe = app(CombatModuleCatalog.LASER); ((ObjectNode) noRecipe.get("combatModule").get("materialCostsKg")).removeAll(); rejected(noRecipe, "material recipe");
    }
    @Test void validApplicationTweaksRemainTunableAndDoNotChangeCapturedBaseline() throws Exception {
        var baseline = CombatModuleCatalog.module(CombatModuleCatalog.LASER); var json = app(baseline.id());
        ((ObjectNode) json.get("combatModule").get("operationalStats")).put("weaponDamage", 123.0).put("weaponRangeMeters", 456000.0);
        var edited = mapper.treeToValue(json, TechnicalApplication.class); assertDoesNotThrow(() -> CombatApplicationValidation.validate(edited));
        assertEquals(123, edited.combatModule().operationalStats().get("weaponDamage")); assertEquals(baseline, CombatModuleCatalog.module(baseline.id()));
    }
}
