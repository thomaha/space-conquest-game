package com.spaceconquest.control;

import com.spaceconquest.control.command.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import com.spaceconquest.engine.technology.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CustomHullDesignTest {
    @TempDir Path directory;
    private GameState world(boolean advanced) {
        var known = new ArrayList<>(List.of("rocketry", "electricity", "industrial_production", "rocket_engines"));
        if (advanced) known.addAll(List.of("spacecraft", "large_hull_engineering"));
        return new SensorModuleTest().world(known);
    }
    private ShipDesignSpecification specification(String id, String role, int capacity, List<String> modules) {
        return new ShipDesignSpecification(id, id, "owner", role, "steel", modules, "steel", .5, capacity);
    }
    @Test void designerChoosesDifferentHullSizesForTheSameRoleWithoutPresetModels() {
        var state = world(false); var modules = ShipComponentCatalog.workbenchModules("mod_chemical_rocket", false);
        var small = ShipBlueprintFactory.evaluate(state, specification("small", ShipRole.COMBAT_SHIP, 24, modules));
        var larger = ShipBlueprintFactory.evaluate(state, specification("larger", ShipRole.COMBAT_SHIP, 29, modules));
        assertTrue(small.valid(), small.errors().toString()); assertTrue(larger.valid(), larger.errors().toString());
        assertEquals(24, small.design().hullFrame().totalSlots()); assertEquals(29, larger.design().hullFrame().totalSlots());
        assertTrue(larger.design().totalDryMassKg() > small.design().totalDryMassKg());
        assertEquals(small.design().equippedModuleIds(), larger.design().equippedModuleIds());
    }
    @Test void structuralApplicationUnlocksLargerCustomCapacityAndAnyFittingModuleCount() {
        var modules = new ArrayList<>(ShipComponentCatalog.workbenchModules("mod_chemical_rocket", false));
        for (int count = 0; count < 20; count++) modules.add(ShipComponentCatalog.BATTERY_ID);
        var requested = specification("custom", ShipRole.EXPLORER, 67, modules);
        assertFalse(ShipBlueprintFactory.evaluate(world(false), requested).valid());
        var researched = world(true); var result = ShipBlueprintFactory.evaluate(researched, requested);
        assertTrue(result.valid(), result.errors().toString()); assertEquals(67, result.design().hullFrame().totalSlots());
        assertEquals(modules, result.design().equippedModuleIds()); assertEquals("large_hull_engineering", result.design().hullFrame().applicationId());
        assertFalse(ShipBlueprintFactory.evaluate(researched, specification("too-big", ShipRole.EXPLORER, HullTechnology.maximumCapacity(researched, "owner") + 1, modules)).valid());
        assertFalse(ShipBlueprintFactory.evaluate(researched, specification("too-small", ShipRole.EXPLORER, 10, modules)).valid());
    }
    @Test void refinementRaisesEngineeringLimitWithoutChangingRegisteredModelsAndSaveRetainsFrame() throws Exception {
        var state = world(true); var modules = ShipComponentCatalog.workbenchModules("mod_chemical_rocket", false);
        var before = new DesignShipCommand(specification("captured", ShipRole.COMBAT_SHIP, 67, modules)).apply(state);
        var captured = before.shipDesigns().getFirst(); int oldLimit = HullTechnology.maximumCapacity(before, "owner");
        var researched = new SelectOptimizationPathCommand("owner", "large_hull_engineering", "PATH_A").apply(before);
        assertTrue(HullTechnology.maximumCapacity(researched, "owner") > oldLimit); assertEquals(captured, researched.shipDesigns().getFirst());
        assertTrue(ShipApplicationProduction.developmentChanged(researched, captured));
        assertEquals(67, ShipDesignSpecification.from(captured).hullCapacity());
        var file = directory.resolve("hull.scsave").toFile(); var manager = new SaveGameManager(directory); manager.save(file, researched, 1, GameClock.START_TIME.toString());
        var loaded = manager.load(file).toGameState(researched.turn(), researched.status()); assertEquals(researched.shipDesigns(), loaded.shipDesigns());
        assertEquals(captured.hullFrame(), loaded.shipDesigns().getFirst().hullFrame());
    }
    @Test void queuedDesignRevalidatesResearchAndHullCapacityAtCommandExecution() {
        var state = world(true); var command = new DesignShipCommand(specification("large", ShipRole.COMBAT_SHIP, 67, ShipComponentCatalog.workbenchModules("mod_chemical_rocket", false)));
        assertTrue(command.validate(state)); assertFalse(command.validate(world(false)));
        var rejected = world(false); assertSame(rejected, command.apply(rejected));
        var oversized = new DesignShipCommand(specification("huge", ShipRole.COMBAT_SHIP, Integer.MAX_VALUE, ShipComponentCatalog.workbenchModules("mod_chemical_rocket", false)));
        assertFalse(oversized.validate(state)); assertSame(state, oversized.apply(state));
    }
}
