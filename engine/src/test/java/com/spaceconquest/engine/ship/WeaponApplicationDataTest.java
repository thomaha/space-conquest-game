package com.spaceconquest.engine.ship;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.engine.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WeaponApplicationDataTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ShipPointDefenseTest fixture = new ShipPointDefenseTest();
    private static final double HOURS = 1.0 / 60;

    private ShipModule configured(String id, Map<String, Double> changes, String ammo) throws Exception {
        ObjectNode application = mapper.valueToTree(CombatModuleCatalog.application(id));
        ObjectNode module = (ObjectNode) application.get("combatModule");
        ObjectNode stats = (ObjectNode) module.get("operationalStats");
        changes.forEach(stats::put);
        if (ammo != null) module.put("ammunitionId", ammo);
        return mapper.treeToValue(application, TechnicalApplication.class).combatModule();
    }
    private ShipDesign capture(ShipDesign d, ShipModule... modules) {
        return new ShipDesign(d.id(), d.name(), d.ownerEntityId(), d.role(), d.hullMaterialId(), d.equippedModuleIds(),
                d.armorMaterialId(), d.armorThicknessCm(), d.totalDryMassKg(), d.maxCargoMassKg(), d.fuelCapacityKg(),
                d.powerBalanceKw(), d.calculatedStructuralIntegrity(), d.minLaunchThrustRequiredN(), d.totalThrustN(),
                d.isValidForLaunch(), d.isProprietaryCorporateDesign(), d.manufacturingProfile(), d.powerProfile(),
                d.sensorProfile(), CombatModuleCatalog.capture(List.of(modules)));
    }

    @Test void everyCombatModuleHasAnApplicationAndDataDefinedHardware() throws Exception {
        var applications = DataModelLoader.loadTechnologies().stream().flatMap(t -> t.applications().stream()).toList();
        for (String id : CombatModuleCatalog.IDS) {
            var app = CombatModuleCatalog.application(id);
            assertTrue(applications.contains(app));
            assertEquals(app.id(), ShipApplicationProduction.applicationId(id));
            assertEquals(app.combatModule(), CombatModuleCatalog.module(id));
            assertFalse(CombatModuleCatalog.researched(List.of(id), List.of()));
        }
    }

    @Test void limitedTuningProbeUsesApplicationValuesAcrossEveryOffensiveFamily() throws Exception {
        for (String id : List.of(CombatModuleCatalog.GUN, CombatModuleCatalog.LASER, CombatModuleCatalog.PARTICLE_BEAM,
                CombatModuleCatalog.ION, CombatModuleCatalog.MASS_DRIVER, CombatModuleCatalog.MISSILE)) {
            var baseline = CombatModuleCatalog.module(id);
            ObjectNode app = mapper.valueToTree(CombatModuleCatalog.application(id));
            var module = (ObjectNode) app.get("combatModule");
            module.put("powerDrawKw", 11); module.put("dryMassKg", 700); module.put("complexityLevel", 7);
            module.putObject("materialCostsKg").put("steel", 17);
            var stats = (ObjectNode) module.get("operationalStats");
            stats.put("weaponDamage", 96); stats.put("weaponSalvoKwh", 6); stats.put("weaponRangeMeters", 80000);
            stats.put("shotsPerRound", 2);
            if (!baseline.ammunitionId().isBlank()) stats.put("ammunitionMassKg", 2);
            if (ShipMissileWeapons.projectile(baseline)) stats.put("launchesPerRound", 2);
            var custom = mapper.treeToValue(app, TechnicalApplication.class).combatModule();
            var original = fixture.design("probe", 10000, id);
            ObjectNode snapshot = mapper.valueToTree(capture(original, custom));
            ((ObjectNode) snapshot.get("powerProfile")).put("hotelKw", 13);
            var design = mapper.treeToValue(snapshot, ShipDesign.class);
            var source = fixture.ship("probe", design, 0);
            source = new ShipInstance(source.id(), source.designId(), source.ownerEntityId(), 1000, 0, 0,
                    custom.ammunitionId().isBlank() ? Map.of() : Map.of(custom.ammunitionId(), 5.0)).withPowerState(source.powerState());
            var paid = ShipCombatPower.round(source, design, HOURS, true, false, 80000);
            boolean projectile = ShipMissileWeapons.projectile(custom);
            assertEquals(projectile ? 192 : 96, paid.firepower(), 1e-9, id);
            assertEquals(projectile ? 12 : 6, paid.paidWeaponKwh(), 1e-9, id);
            assertEquals(10 - (13 * HOURS + paid.paidWeaponKwh()) / 6000, paid.ship().generatorFuelMassKg(), 1e-12, id);
            assertEquals(2, projectile ? paid.missileLaunches().size() : paid.directHits().size(), id);
            assertEquals(0, ShipCombatPower.round(source, design, HOURS, true, false, 80001).firepower(), id);
            if (!custom.ammunitionId().isBlank()) assertEquals(1, paid.ship().storedCargoKg().get(custom.ammunitionId()), id);
            var oldBill = com.spaceconquest.engine.industry.ConstructionMaterialCatalog.ship(original);
            var newBill = com.spaceconquest.engine.industry.ConstructionMaterialCatalog.ship(design);
            assertEquals(17 - baseline.materialCostsKg().getOrDefault("steel", 0.0),
                    newBill.getOrDefault("steel", 0.0) - oldBill.getOrDefault("steel", 0.0), 1e-9, id);
            assertEquals(700, CombatModuleCatalog.module(design, id).dryMassKg());
            assertEquals(7, CombatModuleCatalog.module(design, id).complexityLevel());
            assertEquals(baseline, CombatModuleCatalog.module(id));
            assertEquals(baseline, CombatModuleCatalog.module(original, id));
        }
    }

    @Test void allAmmunitionManufacturingBalanceValuesComeFromApplications() throws Exception {
        var applications = DataModelLoader.loadTechnologies().stream().flatMap(t -> t.applications().stream()).toList();
        for (String id : List.of("cannon_ammunition_mfg", "mass_driver_ammunition_mfg", "kinetic_pd_ammunition_mfg", "guided_missile_mfg")) {
            var app = applications.stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow();
            assertEquals(app.productionRecipe(), com.spaceconquest.engine.industry.IndustryRecipeCatalog.find(id));
            ObjectNode changed = mapper.valueToTree(app);
            var recipe = (ObjectNode) changed.get("productionRecipe");
            recipe.put("workersPerBatch", 3); recipe.put("powerDrawKw", 123); recipe.put("minTier", 2);
            recipe.putObject("inputsKg").put("steel", 7);
            recipe.putObject("outputsKg").put(app.productionRecipe().outputsKg().keySet().iterator().next(), 4);
            var decoded = mapper.treeToValue(changed, TechnicalApplication.class).productionRecipe();
            assertEquals(3, decoded.workersPerBatch()); assertEquals(123, decoded.powerDrawKw()); assertEquals(2, decoded.minTier());
            assertEquals(Map.of("steel", 7.0), decoded.inputsKg()); assertEquals(List.of(4.0), List.copyOf(decoded.outputsKg().values()));
        }
    }

    @Test void changedApplicationDataControlsDefenseRangeCapacityEnergyAndDesignerText() throws Exception {
        var custom = configured(CombatModuleCatalog.ION_POINT_DEFENSE,
                Map.of("pointDefenseCapacity", 3.0, "pointDefenseShotKwh", 3.0, "pointDefenseRangeMeters", 90_000.0), null);
        var design = capture(fixture.design("guard", 1000, custom.id()), custom);
        var source = fixture.ship("guard", design, 0);
        var paid = ShipCombatPower.round(source, design, HOURS, false, false, 0, List.of(90_000.0, 90_000.0, 90_000.0, 90_001.0));
        assertEquals(3, paid.pointDefenseInterceptions()); assertEquals(9, paid.paidPointDefenseKwh());
        assertEquals(10 - (32 * HOURS + 9) / 6000, paid.ship().generatorFuelMassKg(), 1e-12);
        assertTrue(CombatModuleCatalog.description(custom).contains("90.0 km"));
        assertTrue(CombatModuleCatalog.description(custom).contains("3.0 kWh"));
        assertEquals(40_000, CombatModuleCatalog.module(custom.id()).operationalStats().get("pointDefenseRangeMeters"));
    }

    @Test void customProjectileDataControlsDamageEnergyRangePackagedMassSpeedAndLaunchCount() throws Exception {
        var custom = configured(CombatModuleCatalog.MISSILE, Map.of("weaponDamage", 42.0, "weaponSalvoKwh", 3.0,
                "weaponRangeMeters", 3_000_000.0, "ammunitionMassKg", 25.0, "projectileSpeedMetersPerSecond", 20_000.0,
                "launchesPerRound", 2.0, "requiresLiveGuidance", 0.0), ShipMissileWeapons.AMMUNITION);
        var design = capture(fixture.design("launcher", 1000, custom.id()), custom);
        var source = fixture.ship("launcher", design, 60);
        var paid = ShipCombatPower.round(source, design, HOURS, true, false, 3_000_000);
        assertEquals(2, paid.missileLaunches().size()); assertEquals(84, paid.firepower()); assertEquals(6, paid.paidWeaponKwh());
        assertEquals(10, paid.ship().storedCargoKg().get(ShipMissileWeapons.AMMUNITION));
        assertEquals(20_000, paid.missileLaunches().getFirst().speedMetersPerSecond());
        assertFalse(paid.missileLaunches().getFirst().requiresLiveGuidance());
        assertEquals(3_000_000, ShipWeaponRange.maximumRange(design, source));
        assertEquals(0, ShipCombatPower.round(source, design, HOURS, true, false, 3_000_001).missileLaunches().size());
        assertEquals(60, source.storedCargoKg().get(ShipMissileWeapons.AMMUNITION));
    }

    @Test void defenseAndOffenseShareConfiguredAmmunitionWithoutDoubleSpending() throws Exception {
        var defense = configured(CombatModuleCatalog.KINETIC_POINT_DEFENSE, Map.of("ammunitionMassKg", 100.0), ShipMissileWeapons.AMMUNITION);
        var launcher = CombatModuleCatalog.module(CombatModuleCatalog.MISSILE);
        var design = capture(fixture.design("mixed", 1000, defense.id(), launcher.id()), defense, launcher);
        var source = fixture.ship("mixed", design, 200);
        var paid = ShipCombatPower.round(source, design, HOURS, true, false, 0, 2);
        assertEquals(2, paid.pointDefenseInterceptions()); assertTrue(paid.missileLaunches().isEmpty());
        assertFalse(paid.ship().storedCargoKg().containsKey(ShipMissileWeapons.AMMUNITION));
        assertEquals(0, paid.firepower());
    }

    @Test void changedShieldApplicationControlsRechargeEnergy() throws Exception {
        var shield = configured(CombatModuleCatalog.SHIELD, Map.of("shieldRechargeKwhPerPoint", 4.0), null);
        var design = capture(fixture.design("shield", 1000, shield.id()), shield);
        var ship = fixture.ship("shield", design, 0);
        assertEquals(40, ShipShieldRecharge.requestedKw(design, ship, HOURS));
        assertEquals(1, ShipShieldRecharge.apply(ship, design, 4).currentShieldHealth());
        assertEquals(0, ShipShieldRecharge.requestedKw(design, ship.withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0))), HOURS));
        var unshielded = fixture.design("unshielded", 1000, CombatModuleCatalog.GUN);
        assertEquals(0, ShipShieldRecharge.requestedKw(unshielded, fixture.ship("guard", unshielded, 0), HOURS));
    }

    @Test void savedCapabilitiesSurviveReloadAndOlderSavesResolveApplicationBaselines() throws Exception {
        var custom = configured(CombatModuleCatalog.ION_POINT_DEFENSE, Map.of("pointDefenseRangeMeters", 90_000.0), null);
        var design = capture(fixture.design("guard", 1000, custom.id()), custom);
        var state = GameState.builder().shipDesigns(List.of(design)).build();
        var save = SaveGame.fromGameState(state, "now", 1, "now");
        var restored = mapper.readValue(mapper.writeValueAsString(save), SaveGame.class).toGameState(0, "RUNNING");
        assertEquals(design, restored.shipDesigns().getFirst());
        ObjectNode old = mapper.valueToTree(design); old.remove("combatModules");
        var legacy = mapper.treeToValue(old, ShipDesign.class);
        assertEquals(40_000, CombatModuleCatalog.module(legacy, custom.id()).operationalStats().get("pointDefenseRangeMeters"));
        assertThrows(UnsupportedOperationException.class, () -> design.combatModules().clear());
    }
}
