package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FleetRepairStatusTest {
    private GameState world(double steel, boolean outage) {
        var modules = List.of(ShipRepairModuleCatalog.WORKSHOP, CombatModuleCatalog.GUN, ShipComponentCatalog.FISSION_REACTOR_ID);
        var profile = ShipPowerProfile.capture(modules.stream().map(ShipComponentCatalog::module).toList());
        var design = new ShipDesign("design", "Design", "owner", ShipRole.COMBAT_SHIP, "steel", modules, "steel", 0,
                1000, 1000, 1000, 100, 10, 0, 1000, false, false, ShipManufacturingProfile.baseline(), profile);
        var ship = new ShipInstance("ship", design.id(), "owner", 1000, 0, 100, Map.of("steel", steel))
                .withModuleDamage(new ShipModuleDamage(Map.of(1, 20.0)))
                .withPowerState(new ShipPowerState(Map.of("refined_uranium", 1.0), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, outage ? 1 : 0, 0));
        var fleet = new Fleet("fleet", "Fleet", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship));
        return GameState.builder().shipDesigns(List.of(design)).fleets(List.of(fleet)).build();
    }

    @Test void explainsActualMaterialLimitedWorkWithoutSpendingStoresOrChangingTheSnapshot() {
        var initial = world(5, false); var fleet = initial.fleets().getFirst(); var ship = fleet.ships().getFirst();
        var status = FleetRepairStatus.inspect(initial, fleet);
        assertEquals(20, status.providers().getFirst().availablePoints());
        assertEquals(5, status.fundedOnboardPoints());
        assertEquals(Map.of("steel", 15.0), status.providers().getFirst().firstRecipeShortages());
        assertEquals(1, status.ships().getFirst().mounts().getFirst().index());
        assertEquals(20, status.ships().getFirst().mounts().getFirst().damagePoints());
        assertSame(ship, initial.fleets().getFirst().ships().getFirst());
        assertEquals(5, ship.storedCargoKg().get("steel"));
        assertEquals(20, ship.moduleDamage().damageByMount().get(1));
        assertEquals(status, FleetRepairStatus.inspect(initial, fleet));
        assertThrows(UnsupportedOperationException.class, () -> status.providers().getFirst().firstRecipeShortages().put("steel", 0.0));
        assertNull(status.base());
    }

    @Test void explainsPowerBlockersAndTheMissionRetainedForReturn() {
        var initial = world(100, true); var fleet = initial.fleets().getFirst();
        var guard = new FleetGuardOrder(FleetGuardOrder.TargetType.BODY, "earth", "sol", FleetLocation.Site.orbit("earth", 500));
        var saved = new FleetProtectionAssignment(guard, "", fleet.id(), guard.site(), FleetProtectionAssignment.Stage.REPAIRING, "");
        fleet = fleet.withInterceptOrder(new FleetInterceptOrder("owner", "old-target", "sol", saved));
        initial = initial.withFleets(List.of(fleet)); var status = FleetRepairStatus.inspect(initial, fleet);
        assertEquals(0, status.providers().getFirst().availablePoints());
        assertEquals(0, status.fundedOnboardPoints());
        assertTrue(status.providers().getFirst().blockers().contains("Recorded power shortfall"));
        assertEquals("Guard earth", status.savedMission());
        assertEquals(saved, initial.fleets().getFirst().interceptOrder().protection());
    }
}
