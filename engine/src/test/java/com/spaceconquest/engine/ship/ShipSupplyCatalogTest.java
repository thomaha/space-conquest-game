package com.spaceconquest.engine.ship;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ShipSupplyCatalogTest {
    private ShipDesign design(List<String> modules) {
        return new ShipDesign("tanker", "Tanker", "owner", ShipRole.CARGO_TRANSPORT, "steel", modules,
                "steel", 0, 10000, 1000, 15000, 100, 1, 0, 600000, false, false);
    }
    @Test void storageUsesSeparateCompatibleCompartmentBudgetsAndDoesNotGrantCargoOrWorkingFuelCapacity() {
        var design = design(List.of(ShipSupplyCatalog.LIQUID_TANK, ShipSupplyCatalog.CRYOGENIC_TANK,
                ShipSupplyCatalog.REACTOR_MAGAZINE, ShipSupplyCatalog.TRANSFER_PUMP));
        assertEquals(80100, ShipSupplyCatalog.totalCapacity(design));
        assertTrue(ShipSupplyCatalog.fits(design, Map.of("rp1_kerosene", 40000.0, "liquid_oxygen", 20000.0,
                "liquid_hydrogen", 20000.0, "refined_uranium", 100.0)));
        assertFalse(ShipSupplyCatalog.fits(design, Map.of("liquid_oxygen", 20000.0, "liquid_hydrogen", 20001.0)));
        assertFalse(ShipSupplyCatalog.fits(design, Map.of("food_matrix", 1.0)));
        assertFalse(ShipSupplyCatalog.fits(design, Map.of("antimatter_containment_cell", 1.0)));
        assertEquals(1000, ShipSupplyCatalog.transferKgPerHour(design));
        var module = ShipComponentCatalog.module(ShipSupplyCatalog.CRYOGENIC_TANK);
        assertEquals(40, module.powerDrawKw());
        assertFalse(module.operationalStats().containsKey("cargoCapacityKg"));
        assertFalse(module.operationalStats().containsKey("fuelCapacityKg"));
    }
    @Test void repeatedStorageAndPumpsAddCapacityWithoutRolePrivileges() {
        var design = design(List.of(ShipSupplyCatalog.CRYOGENIC_TANK, ShipSupplyCatalog.CRYOGENIC_TANK,
                ShipSupplyCatalog.TRANSFER_PUMP, ShipSupplyCatalog.TRANSFER_PUMP));
        assertEquals(80000, ShipSupplyCatalog.capacity(design, "liquid_oxygen"));
        assertEquals(2000, ShipSupplyCatalog.transferKgPerHour(design));
        assertTrue(ShipSupplyCatalog.fits(design, Map.of("liquid_oxygen", 80000.0)));
        assertFalse(ShipSupplyCatalog.fits(design, Map.of("rp1_kerosene", 1.0)));
        assertFalse(ShipSupplyCatalog.fits(design, Map.of("liquid_oxygen", Double.NaN)));
        assertEquals(0, ShipSupplyCatalog.totalCapacity(design(List.of())));
    }
}
