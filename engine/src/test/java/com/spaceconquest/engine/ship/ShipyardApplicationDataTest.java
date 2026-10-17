package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.macrostructure.StationModule;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipyardApplicationDataTest {
    @Test void applicationsSupplyModuleStaffPowerAndPhysicalConstructionBills() throws Exception {
        var application = DataModelLoader.loadTechnologies().stream().flatMap(tech -> tech.applications().stream())
                .filter(app -> app.id().equals("space_stations")).findFirst().orElseThrow();
        var capability = application.shipyardTechnology().yards().get(StationModule.TYPE_SHIPYARD_GRID);
        assertEquals(capability, ShipyardTechnology.yard(StationModule.TYPE_SHIPYARD_GRID));
        var module = capability.module(StationModule.TYPE_SHIPYARD_GRID);
        assertEquals(capability.workers(), module.requiredWorkers()); assertEquals(capability.powerDrawKw(), module.powerDrawKw());
        assertEquals(capability.dryMassKg(), module.dryMassKg()); assertEquals(capability.slots(), module.slotSize());
        assertTrue(capability.materialsKg().get("refined_iron") > 0);
        assertEquals("space_stations", ShipyardTechnology.application(StationModule.TYPE_CAPITAL_SLIPWAY));
        assertEquals(capability.baseComplexity(), ShipManufacturingCapacity.limit(StationModule.TYPE_SHIPYARD_GRID, 1));
        var surface = application.shipyardTechnology().yards().get("surface_shipyard");
        assertEquals(surface.baseComplexity() + 3 * surface.complexityPerTier(), ShipManufacturingCapacity.limit("surface_shipyard", 4));
    }
    @Test void researchBonusesComeFromTheirApplicationDefinitions() throws Exception {
        var all = DataModelLoader.loadTechnologies().stream().flatMap(tech -> tech.applications().stream()).toList();
        var automation = all.stream().filter(app -> app.id().equals("automated_assembly_lines")).findFirst().orElseThrow().shipyardTechnology();
        var cybernetic = all.stream().filter(app -> app.id().equals("cybernetic_workforce_integration")).findFirst().orElseThrow().shipyardTechnology();
        assertEquals(automation.workMultiplier(), ShipyardTechnology.workMultiplier(List.of("automated_assembly_lines")));
        assertEquals(automation.workerMultiplier() * cybernetic.workerMultiplier(),
                ShipyardTechnology.workerMultiplier(List.of("automated_assembly_lines", "cybernetic_workforce_integration")));
        assertEquals(1, ShipyardTechnology.workMultiplier(List.of())); assertEquals(1, ShipyardTechnology.workerMultiplier(List.of()));
    }
    @Test void malformedCapabilitiesFailBeforeTheyEnterTheSimulation() {
        assertThrows(IllegalArgumentException.class, () -> new ShipyardTechnology(Map.of(), Double.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> new ShipyardTechnology.Yard(100, 0, "engineer", 1, 10000, 100, 2, Map.of("steel", 100.0), 1.5, .5));
        assertThrows(IllegalArgumentException.class, () -> new ShipyardTechnology.Yard(100, 100, "engineer", 1, 10000, 100, 2, Map.of("steel", -1.0), 1.5, .5));
    }
}
