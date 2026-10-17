package com.spaceconquest.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spaceconquest.control.command.SelectOptimizationPathCommand;
import com.spaceconquest.engine.SaveGame;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WeaponApplicationOptimizationTest {
    @Test void optimizationChangesNewBlueprintsAndCombatUsesTheirFrozenApplicationValues() throws Exception {
        var state = new SensorModuleTest().world(List.of("rocketry", "electricity", "industrial_production", "energy_fields",
                "energy_weapons", "laser_weapons", "pd_lasers"));
        var modules = new ArrayList<>(ShipComponentCatalog.workbenchModules("mod_chemical_rocket", false));
        modules.add(CombatModuleCatalog.LASER);
        var spec = new ShipDesignSpecification("laser", "Laser", "owner", ShipRole.COMBAT_SHIP, "steel", modules, "steel", .5);
        var base = ShipBlueprintFactory.evaluate(state, spec); assertTrue(base.valid(), base.errors().toString());
        var command = new SelectOptimizationPathCommand("owner", "laser_weapons", "PATH_A");
        assertTrue(command.validate(state)); var selected = command.apply(state);
        var developed = ShipBlueprintFactory.evaluate(selected, spec); assertTrue(developed.valid(), developed.errors().toString());
        assertEquals(320, CombatModuleCatalog.weaponDamage(base.design()));
        assertEquals(368, CombatModuleCatalog.weaponDamage(developed.design()), 1e-9);
        var ship = new ShipInstance("laser", "laser", "owner", base.design().calculatedStructuralIntegrity() * 100, 0, 0, Map.of());
        assertEquals(0, ShipWeaponRange.directCapability(base.design(), ship, "weaponDamage", 27_500));
        assertEquals(368, ShipWeaponRange.directCapability(developed.design(), ship, "weaponDamage", 27_500), 1e-9);
        assertEquals(25_000, CombatModuleCatalog.module(base.design(), CombatModuleCatalog.LASER).operationalStats().get("weaponRangeMeters"));
        assertTrue(developed.design().manufacturingProfile().costMultiplier() > base.design().manufacturingProfile().costMultiplier());
        var mapper = new ObjectMapper().configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var saved = selected.withShipDesigns(List.of(base.design(), developed.design()));
        var restored = mapper.readValue(mapper.writeValueAsString(SaveGame.fromGameState(saved, "now", 1, "now")), SaveGame.class).toGameState(0, "RUNNING");
        assertEquals(saved.shipDesigns(), restored.shipDesigns());
    }
    @Test void cannonsAndGuidedLaunchersNeedTheirOwnDevelopedApplications() {
        assertFalse(CombatModuleCatalog.researched(List.of(CombatModuleCatalog.GUN), List.of("electricity", "industrial_production")));
        assertTrue(CombatModuleCatalog.researched(List.of(CombatModuleCatalog.GUN), List.of("electricity", "industrial_production", "ship_cannons")));
        assertFalse(CombatModuleCatalog.researched(List.of(CombatModuleCatalog.MISSILE), List.of("electricity", "industrial_production", "rocketry")));
        assertTrue(CombatModuleCatalog.researched(List.of(CombatModuleCatalog.MISSILE), List.of("electricity", "industrial_production", "rocketry", "guided_missile_weapons")));
    }
}
