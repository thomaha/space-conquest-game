package com.spaceconquest.control.command;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.*;
import com.spaceconquest.engine.industry.IndustryAccount;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BatteryStatusTest {
    private GameState world() throws Exception {
        var state = new AuxiliaryConstructionTest().world();
        var weapon = CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER);
        var salvo = WeaponSalvo.of(weapon);
        var battery = new PlanetaryDefenseBattery("battery", "earth", CombatModuleCatalog.application(weapon.id()).id(), 1, true)
                .withStores(new AuxiliaryWeaponStores(Map.of(weapon.ammunitionId(), weapon.operationalStats().get("ammunitionMassKg") * salvo.shots()),
                        weapon.powerDrawKw() / 60 + salvo.energyKwh()));
        return state.toBuilder().auxiliaryForces(new AuxiliaryForces(List.of(), List.of(new AuxiliaryForces.OwnedBattery("own", battery))))
                .industryAccounts(List.of(new IndustryAccount("yard", Map.of(), Map.of(), Map.of(), 0, 0, 0, 0, 0, 0, 0, false, 0, 0, 100))).build();
    }
    private PlanetaryDefenseBattery battery(GameState state) { return state.auxiliaryForces().batteries().getFirst().battery(); }
    private GameState replace(GameState state, PlanetaryDefenseBattery battery) {
        return state.toBuilder().auxiliaryForces(state.auxiliaryForces().withBattery(battery)).build();
    }
    private BatteryStatus status(GameState state) { return BatteryStatus.inspect(state, "own", "earth").getFirst(); }
    @Test void readinessQuotesCapturedSalvosWithoutSpendingSuppliesAndReportsEmptyOrUnpoweredWeapons() throws Exception {
        var state = world(); var before = battery(state); var ready = status(state).firing();
        assertEquals(WeaponSalvo.of(before.weapon()).shots(), ready.fundedShots()); assertTrue(ready.status().startsWith("Ready"));
        assertEquals(before.weapon().operationalStats().get("weaponRangeMeters"), ready.rangeMeters());
        assertEquals(before.stores(), battery(state).stores());
        var empty = replace(state, before.withStores(AuxiliaryWeaponStores.empty()));
        assertEquals("Empty magazine: no whole round", status(empty).firing().status()); assertEquals(0, status(empty).firing().fundedShots());
        var unpowered = replace(state, before.withStores(new AuxiliaryWeaponStores(before.stores().ammunitionKg(), 0)));
        assertTrue(status(unpowered).firing().status().contains("Insufficient energy"));
        assertTrue(BatteryStatus.inspect(state, "foreign", "earth").isEmpty()); assertTrue(BatteryStatus.inspect(state, "own", "moon").isEmpty());
    }
    @Test void ionDisabledAndDestroyedBatteriesNeverClaimReadyAndConditionDamageIsExplained() throws Exception {
        var state = world(); var original = battery(state);
        var disabled = replace(state, original.withDamage(original.hullHealth(), new ShipModuleDamage(Map.of(0, 100.0))));
        assertEquals("Weapon electronics disabled", status(disabled).firing().status()); assertEquals(0, status(disabled).firing().fundedShots());
        var damaged = replace(state, original.withDamage(original.hullHealth(), new ShipModuleDamage(Map.of(0, 10.0))));
        assertEquals("Ready with reduced weapon condition", status(damaged).firing().status());
        var wreck = replace(state, original.withDamage(0, ShipModuleDamage.empty()));
        assertEquals("Destroyed battery", status(wreck).firing().status()); assertEquals(0, status(wreck).firing().fundedShots());
        assertTrue(status(wreck).repair().status().contains("dismantle"));
    }
    @Test void repairProjectionMatchesPaidConstructionProcessorAndLeavesSnapshotResourcesIntact() throws Exception {
        var state = world(); state = replace(state, battery(state).withDamage(900, new ShipModuleDamage(Map.of(0, 10.0))));
        var hubs = state.commercialHubs(); var empires = state.empires(); var forces = state.auxiliaryForces();
        var quote = status(state).repair(); var actual = new ShipConstructionProcessor().process(state);
        assertEquals(10, quote.projectedMountPoints()); assertEquals(battery(actual).hullHealth() - 900, quote.projectedHullPoints());
        assertEquals("Weapon electronics", quote.firstJob()); assertTrue(quote.status().contains("available"));
        assertEquals(hubs, state.commercialHubs()); assertEquals(empires, state.empires()); assertEquals(forces, state.auxiliaryForces());
        assertThrows(UnsupportedOperationException.class, () -> quote.shortages().put("steel", 1.0));
        assertThrows(UnsupportedOperationException.class, () -> BatteryStatus.inspect(stateSnapshot(forces), "own", "earth").clear());
    }
    private GameState stateSnapshot(AuxiliaryForces forces) { return GameState.builder().auxiliaryForces(forces).build(); }
    @Test void repairExplainsStaffingYardControlHubMaterialsAndFundsBlockers() throws Exception {
        var state = world(); state = replace(state, battery(state).withDamage(1000, new ShipModuleDamage(Map.of(0, 100.0))));
        assertTrue(status(state.toBuilder().industryAccounts(List.of()).build()).repair().status().contains("paid yard staffing"));
        assertTrue(status(state.withIndustrialFacilities(List.of())).repair().status().contains("surface yard"));
        assertTrue(status(state.withEmpires(List.of())).repair().status().contains("does not control"));
        assertEquals("No local commercial hub", status(state.withCommercialHubs(List.of())).repair().status());
        var hub = state.commercialHubs().getFirst(); var dryHub = new CommercialHub(hub.id(), hub.entityId(), 0, hub.storageCapacityKg(), 0, hub.logisticsRangeUnits(), Map.of());
        var dry = status(state.withCommercialHubs(List.of(dryHub))).repair();
        assertEquals("Missing repair materials", dry.status()); assertFalse(dry.shortages().isEmpty()); assertEquals(0, dry.projectedMountPoints());
        var owner = state.empires().getFirst();
        var poor = state.withEmpires(List.of(new Empire(owner.id(), owner.name(), owner.raceId(), owner.societyStructure(), 0, owner.corporateTaxRate(),
                owner.controlledSystemIds(), owner.ministries(), owner.systemGovernorAssignments(), owner.unlockedTechIds(), owner.activeShipDesignIds())));
        assertTrue(status(poor).repair().status().contains("Insufficient funds"));
    }
    @Test void sharedConstructionBudgetCannotBeAdvertisedAsFundedBatteryRepair() throws Exception {
        var state = world(); state = replace(state, battery(state).withDamage(1000, new ShipModuleDamage(Map.of(0, 100.0))));
        var order = new ShipConstructionOrder("full", "own", "carrier-design", "sol", "earth", 0, 10000, Map.of("steel", 10000.0), Map.of());
        var quote = status(state.withShipConstructionOrders(List.of(order))).repair();
        assertEquals(0, quote.projectedMountPoints()); assertEquals(0, quote.projectedHullPoints()); assertTrue(quote.status().contains("shared yard work"));
    }
}
