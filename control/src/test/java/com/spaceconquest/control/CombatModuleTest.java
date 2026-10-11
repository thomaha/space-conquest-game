package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CombatModuleTest {
    @Test void kineticDefenseRequiresMatchingMassDriversAndItsOwnDevelopedApplication() throws Exception {
        var fixture = new SensorModuleTest();
        var tech = List.of("rocketry", "electricity", "industrial_production", "energy_fields", "electromagnetic_weapons", "mass_drivers", "pd_mass_drivers");
        var result = ShipBlueprintFactory.evaluate(fixture.world(tech), specification(CombatModuleCatalog.KINETIC_POINT_DEFENSE));
        assertTrue(result.valid(), result.errors().toString()); assertEquals(0, CombatModuleCatalog.weaponDamage(result.design()));
        assertTrue(result.design().powerProfile().serviceLoadsKw().get(ShipServicePower.WEAPONS) >= 15);
        assertTrue(ShipConstructionRequirements.estimate(result.design()).materialsKg().get("tungsten_ore") >= 25);
        for (var missing : List.of("mass_drivers", "pd_mass_drivers"))
            assertFalse(ShipBlueprintFactory.evaluate(fixture.world(tech.stream().filter(id -> !id.equals(missing)).toList()), specification(CombatModuleCatalog.KINETIC_POINT_DEFENSE)).valid());
        var catalog = DataModelLoader.loadTechnologies();
        var ready = fixture.world(tech.stream().filter(id -> !id.equals("pd_mass_drivers")).toList()).empires().getFirst();
        assertTrue(com.spaceconquest.engine.technology.ApplicationProduction.canResearch(ready, "pd_mass_drivers", true, catalog));
        var locked = fixture.world(tech.stream().filter(id -> !id.equals("mass_drivers") && !id.equals("pd_mass_drivers")).toList()).empires().getFirst();
        assertFalse(com.spaceconquest.engine.technology.ApplicationProduction.canResearch(locked, "pd_mass_drivers", true, catalog));
        assertFalse(CombatModuleCatalog.researched(List.of(CombatModuleCatalog.KINETIC_POINT_DEFENSE),
                List.of("electricity", "energy_fields", "energy_weapons", "laser_weapons", "pd_lasers")));
    }
    @Test void laserPointDefenseRequiresMatchingWeaponDevelopmentAndItsOwnApplication() throws Exception {
        var fixture = new SensorModuleTest();
        var tech = List.of("rocketry", "electricity", "industrial_production", "energy_fields", "energy_weapons", "laser_weapons", "pd_lasers");
        var state = fixture.world(tech);
        var result = ShipBlueprintFactory.evaluate(state, specification(CombatModuleCatalog.LASER_POINT_DEFENSE));
        assertTrue(result.valid(), result.errors().toString()); assertEquals(0, CombatModuleCatalog.weaponDamage(result.design()));
        assertTrue(result.design().powerProfile().serviceLoadsKw().get(ShipServicePower.WEAPONS) >= 20);
        assertTrue(ShipConstructionRequirements.estimate(result.design()).materialsKg().get("industrial_ceramics") >= 50);
        for (var missing : List.of("laser_weapons", "pd_lasers")) {
            var locked = tech.stream().filter(id -> !id.equals(missing)).toList();
            assertFalse(ShipBlueprintFactory.evaluate(fixture.world(locked), specification(CombatModuleCatalog.LASER_POINT_DEFENSE)).valid());
        }
        var catalog = DataModelLoader.loadTechnologies();
        var noLaser = fixture.world(tech.stream().filter(id -> !id.equals("laser_weapons") && !id.equals("pd_lasers")).toList()).empires().getFirst();
        assertFalse(com.spaceconquest.engine.technology.ApplicationProduction.canResearch(noLaser, "pd_lasers", true, catalog));
        var ready = fixture.world(tech.stream().filter(id -> !id.equals("pd_lasers")).toList()).empires().getFirst();
        assertTrue(com.spaceconquest.engine.technology.ApplicationProduction.canResearch(ready, "pd_lasers", true, catalog));
        assertTrue(catalog.stream().filter(t -> t.id().equals("energy_weapons")).flatMap(t -> t.applications().stream()).anyMatch(a -> a.id().equals("pd_lasers")));
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var saved = state.withShipDesigns(List.of(result.design()));
        var loaded = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(saved, "now", 1, "now")), SaveGame.class).toGameState(0, "RUNNING");
        assertEquals(saved.shipDesigns(), loaded.shipDesigns());
    }
    @Test void missileLauncherRequiresOwnersRocketryAndIndustryAndCapturesPowerAndReceipts() {
        var fixture = new SensorModuleTest();
        var state = fixture.world(List.of("rocketry", "electricity", "industrial_production"));
        var result = ShipBlueprintFactory.evaluate(state, specification(CombatModuleCatalog.MISSILE));
        assertTrue(result.valid(), result.errors().toString());
        assertEquals(180, CombatModuleCatalog.weaponDamage(result.design()));
        assertTrue(result.design().powerProfile().serviceLoadsKw().get(ShipServicePower.WEAPONS) >= 10);
        var bill = ShipConstructionRequirements.estimate(result.design()).materialsKg();
        assertTrue(bill.get("steel") >= 100); assertTrue(bill.get("silicon") >= 50);
        assertFalse(ShipBlueprintFactory.evaluate(fixture.world(List.of("rocketry", "electricity")), specification(CombatModuleCatalog.MISSILE)).valid());
        assertFalse(CombatModuleCatalog.researched(List.of(CombatModuleCatalog.MISSILE), List.of("electricity", "industrial_production")));
    }
    private ShipDesignSpecification specification(String module) {
        var modules = new ArrayList<>(ShipComponentCatalog.workbenchModules("mod_chemical_rocket", false)); modules.add(module);
        return new ShipDesignSpecification(module, module, "owner", ShipRole.COMBAT_SHIP, "steel", modules, "steel", .5);
    }
    @Test void onlyOwnedResearchUnlocksCombatHardwareAndCostsEnterBlueprint() {
        var fixture = new SensorModuleTest(); var basic = fixture.world(List.of("rocketry", "electricity", "industrial_production"));
        var gun = ShipBlueprintFactory.evaluate(basic, specification(CombatModuleCatalog.GUN)); assertTrue(gun.valid(), gun.errors().toString());
        assertEquals(80, CombatModuleCatalog.weaponDamage(gun.design()));
        assertFalse(ShipBlueprintFactory.evaluate(fixture.world(List.of("rocketry", "electricity")), specification(CombatModuleCatalog.GUN)).valid());
        var foreign = new Empire("foreign", "Foreign", "human", "Individualist", 1000000, 0, List.of(), List.of(), Map.of(),
                List.of("energy_fields", "energy_weapons", "laser_weapons"), List.of());
        var empires = new ArrayList<>(basic.empires()); empires.add(foreign); basic = basic.withEmpires(empires);
        assertFalse(ShipBlueprintFactory.evaluate(basic, specification(CombatModuleCatalog.LASER)).valid());
        assertTrue(gun.design().equippedModuleIds().contains(CombatModuleCatalog.GUN));
        assertTrue(gun.design().powerProfile().hotelKw() >= 4);
    }
    @Test void advancedResearchEnablesNewHardwareAndExistingDesignsSurviveSaveUnchanged() throws Exception {
        var state = new SensorModuleTest().world(List.of("rocketry", "electricity", "industrial_production", "energy_fields",
                "energy_weapons", "laser_weapons", "electromagnetic_weapons", "mass_drivers", "shields"));
        var designs = new ArrayList<ShipDesign>();
        for (var module : List.of(CombatModuleCatalog.GUN, CombatModuleCatalog.LASER, CombatModuleCatalog.MASS_DRIVER, CombatModuleCatalog.SHIELD)) {
            var result = ShipBlueprintFactory.evaluate(state, specification(module)); assertTrue(result.valid(), result.errors().toString()); designs.add(result.design());
        }
        assertTrue(CombatModuleCatalog.weaponDamage(designs.get(1)) > CombatModuleCatalog.weaponDamage(designs.getFirst()));
        assertEquals(200, CombatModuleCatalog.shieldCapacity(designs.getLast()));
        var bill = ShipConstructionRequirements.estimate(designs.getLast()).materialsKg();
        assertTrue(bill.getOrDefault("superconducting_cuprates", 0.0) >= 50);
        assertTrue(bill.getOrDefault("refined_titanium", 0.0) >= 100);
        state = state.withShipDesigns(designs); var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var restored = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(state, "now", 1, "now")), SaveGame.class).toGameState(0, "RUNNING");
        assertEquals(designs, restored.shipDesigns()); assertEquals(80, CombatModuleCatalog.weaponDamage(restored.shipDesigns().getFirst()));
    }
}
