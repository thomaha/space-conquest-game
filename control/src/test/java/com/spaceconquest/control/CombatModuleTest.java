package com.spaceconquest.control;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CombatModuleTest {
    @Test void ionAndParticleWeaponsRequireDistinctApplicationsAndCaptureConstructionCosts() {
        var fixture = new SensorModuleTest();
        var base = List.of("rocketry", "electricity", "industrial_production", "energy_fields", "electromagnetic_weapons");
        for (String id : List.of(CombatModuleCatalog.ION, CombatModuleCatalog.PARTICLE_BEAM)) {
            String application = CombatModuleCatalog.application(id).id();
            var developed = new ArrayList<>(base); developed.add(application);
            var result = ShipBlueprintFactory.evaluate(fixture.world(developed), specification(id));
            assertTrue(result.valid(), result.errors().toString());
            assertEquals(CombatModuleCatalog.module(id), CombatModuleCatalog.module(result.design(), id));
            assertTrue(ShipConstructionRequirements.estimate(result.design()).materialsKg().get("superconducting_cuprates") >= 100);
            assertFalse(ShipBlueprintFactory.evaluate(fixture.world(base), specification(id)).valid());
            var other = new ArrayList<>(base); other.add(id.equals(CombatModuleCatalog.ION) ? "particle_beams" : "ion_weapons");
            assertFalse(ShipBlueprintFactory.evaluate(fixture.world(other), specification(id)).valid());
        }
    }
    @Test void ionDefenseRequiresOwnedParticleDevelopmentAndItsMatchingApplication() throws Exception {
        var fixture = new SensorModuleTest();
        var tech = List.of("rocketry", "electricity", "industrial_production", "energy_fields", "electromagnetic_weapons", "ion_weapons", "pd_ion_beams");
        var state = fixture.world(tech);
        var result = ShipBlueprintFactory.evaluate(state, specification(CombatModuleCatalog.ION_POINT_DEFENSE));
        assertTrue(result.valid(), result.errors().toString()); assertEquals(0, CombatModuleCatalog.weaponDamage(result.design()));
        assertTrue(result.design().powerProfile().serviceLoadsKw().get(ShipServicePower.WEAPONS) >= 30);
        var bill = ShipConstructionRequirements.estimate(result.design()).materialsKg();
        assertTrue(bill.get("superconducting_cuprates") >= 50); assertTrue(bill.get("radiation_hardened_semiconductors") >= 25);
        for (var missing : List.of("electricity", "energy_fields", "electromagnetic_weapons", "ion_weapons", "pd_ion_beams"))
            assertFalse(ShipBlueprintFactory.evaluate(fixture.world(tech.stream().filter(id -> !id.equals(missing)).toList()), specification(CombatModuleCatalog.ION_POINT_DEFENSE)).valid());
        var catalog = DataModelLoader.loadTechnologies();
        var ready = fixture.world(tech.stream().filter(id -> !id.equals("pd_ion_beams")).toList()).empires().getFirst();
        assertTrue(com.spaceconquest.engine.technology.ApplicationProduction.canResearch(ready, "pd_ion_beams", true, catalog));
        var locked = fixture.world(tech.stream().filter(id -> !id.equals("ion_weapons") && !id.equals("pd_ion_beams")).toList());
        assertFalse(com.spaceconquest.engine.technology.ApplicationProduction.canResearch(locked.empires().getFirst(), "pd_ion_beams", true, catalog));
        var foreign = new Empire("foreign", "Foreign", "human", "Individualist", 1000000, 0, List.of(), List.of(), Map.of(), tech, List.of());
        var empires = new ArrayList<>(locked.empires()); empires.add(foreign);
        assertFalse(ShipBlueprintFactory.evaluate(locked.withEmpires(empires), specification(CombatModuleCatalog.ION_POINT_DEFENSE)).valid());
        assertFalse(CombatModuleCatalog.researched(List.of(CombatModuleCatalog.ION_POINT_DEFENSE),
                List.of("electricity", "energy_fields", "energy_weapons", "laser_weapons", "pd_lasers", "electromagnetic_weapons", "mass_drivers", "pd_mass_drivers", "defensive_weapons")));
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var saved = state.withShipDesigns(List.of(result.design()));
        var restored = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(saved, "now", 1, "now")), SaveGame.class).toGameState(0, "RUNNING");
        assertEquals(saved.shipDesigns(), restored.shipDesigns());
    }
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
        var state = fixture.world(List.of("rocketry", "electricity", "industrial_production", "guided_missile_weapons"));
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
        var fixture = new SensorModuleTest(); var basic = fixture.world(List.of("rocketry", "electricity", "industrial_production", "ship_cannons"));
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
                "energy_weapons", "laser_weapons", "electromagnetic_weapons", "mass_drivers", "shields", "ship_cannons"));
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
