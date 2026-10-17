package com.spaceconquest.control;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShipRepairModuleTest {
    @Test void ownedRoboticsUnlocksPhysicalWorkshopConstructionWithoutUpgradingExistingShips() {
        var fixture = new SensorModuleTest(); var basicResearch = List.of("rocketry", "electricity", "industrial_production");
        var basic = fixture.world(basicResearch);
        var modules = new ArrayList<>(ShipComponentCatalog.workbenchModules("mod_chemical_rocket", false));
        var oldSpec = new ShipDesignSpecification("old", "Old", "owner", ShipRole.COMBAT_SHIP, "steel", modules, "steel", .5);
        var old = ShipBlueprintFactory.evaluate(basic, oldSpec); assertTrue(old.valid(), old.errors().toString());
        modules.add(ShipRepairModuleCatalog.WORKSHOP);
        var specification = new ShipDesignSpecification("repair", "Repair", "owner", ShipRole.COMBAT_SHIP, "steel", modules, "steel", .5);
        assertFalse(ShipBlueprintFactory.evaluate(basic, specification).valid());
        var foreign = new Empire("foreign", "Foreign", "human", "Individualist", 1000, 0, List.of(), List.of(), Map.of(),
                List.of("robotics"), List.of());
        var empires = new ArrayList<>(basic.empires()); empires.add(foreign);
        assertFalse(ShipBlueprintFactory.evaluate(basic.withEmpires(empires), specification).valid());
        var researched = fixture.world(List.of("rocketry", "electricity", "industrial_production", "robotics"))
                .withShipDesigns(List.of(old.design()));
        var workshop = ShipBlueprintFactory.evaluate(researched, specification); assertTrue(workshop.valid(), workshop.errors().toString());
        assertEquals(old.design().powerProfile().hotelKw() + 20, workshop.design().powerProfile().hotelKw());
        assertTrue(workshop.design().totalDryMassKg() >= old.design().totalDryMassKg() + 2000);
        var bill = ShipConstructionRequirements.estimate(workshop.design()).materialsKg();
        var withoutWorkshopReceipt = ShipConstructionRequirements.estimate(old.design()).materialsKg();
        assertEquals(250, bill.get("steel") - withoutWorkshopReceipt.get("steel"), 1e-6);
        assertEquals(50, bill.get("refined_copper") - withoutWorkshopReceipt.get("refined_copper"), 1e-6);
        assertEquals(50, bill.get("silicon") - withoutWorkshopReceipt.get("silicon"), 1e-6);
        assertFalse(researched.shipDesigns().getFirst().equippedModuleIds().contains(ShipRepairModuleCatalog.WORKSHOP));
    }
}
