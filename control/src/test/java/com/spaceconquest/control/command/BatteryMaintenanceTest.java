package com.spaceconquest.control.command;

import com.fasterxml.jackson.databind.*;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.*;
import com.spaceconquest.engine.ship.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BatteryMaintenanceTest {
    private GameState world(double damage, double hull) throws Exception {
        var state = new AuxiliaryConstructionTest().world();
        var battery = new PlanetaryDefenseBattery("battery", "earth", CombatModuleCatalog.application(CombatModuleCatalog.MASS_DRIVER).id(), 1, true)
                .withStores(new AuxiliaryWeaponStores(Map.of("mass_driver_slug", 20.0), 10))
                .withDamage(hull, new ShipModuleDamage(Map.of(0, damage)));
        return state.toBuilder().auxiliaryForces(new AuxiliaryForces(List.of(), List.of(new AuxiliaryForces.OwnedBattery("own", battery)))).build();
    }
    private PlanetaryDefenseBattery battery(GameState state) { return state.auxiliaryForces().batteries().getFirst().battery(); }
    private double cash(GameState state) { return state.empires().getFirst().treasuryCredits(); }
    @Test void repairsBuyCapturedRecipesAndShareMountAndHullWorkWithoutRefillingStores() throws Exception {
        var state = world(10, 900); var before = battery(state); var work = new HashMap<>(Map.of("sol/earth", 15.0));
        var result = BatteryMaintenance.advance(state, work, Map.of("yard", 100)); var after = battery(result);
        assertEquals(1, after.moduleDamage().condition(0)); assertEquals(910, after.hullHealth()); assertEquals(0, work.get("sol/earth"));
        double expected = before.weapon().materialCostsKg().values().stream().mapToDouble(Double::doubleValue).sum() * .1
                + before.hardware().materialCostsKg().values().stream().mapToDouble(Double::doubleValue).sum() * 10 / before.hardware().value("hullHealth");
        assertEquals(expected, cash(state) - cash(result), 1e-5); assertEquals(before.stores(), after.stores());
        assertEquals(900, battery(state).hullHealth()); assertEquals(.9, battery(state).moduleDamage().condition(0));
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var saved = SaveGame.fromGameState(result, "now", 1, "now");
        assertEquals(result.auxiliaryForces(), mapper.readValue(mapper.writeValueAsString(saved), SaveGame.class).toGameState(0, "RUNNING").auxiliaryForces());
    }
    @Test void shortagesAndInsufficientFundsRollbackTheEntireRepairRecipe() throws Exception {
        var state = world(100, 1000); var hub = state.commercialHubs().getFirst(); var orders = new HashMap<>(hub.activeOrders());
        String missing = battery(state).weapon().materialCostsKg().entrySet().stream().filter(e -> e.getValue() > 0).findFirst().orElseThrow().getKey();
        orders.remove(missing);
        var dry = state.withCommercialHubs(List.of(new CommercialHub(hub.id(), hub.entityId(), hub.transactionTariffRate(), hub.storageCapacityKg(),
                hub.currentStoredWeightKg(), hub.logisticsRangeUnits(), orders, hub.stockTargets(), hub.stockHistory(), hub.reservedTradeFuel())));
        var budget = new HashMap<>(Map.of("sol/earth", 20.0)); var blocked = BatteryMaintenance.advance(dry, budget, Map.of("yard", 100));
        assertSame(dry, blocked); assertEquals(20, budget.get("sol/earth"));
        var empire = state.empires().getFirst();
        var poor = state.withEmpires(List.of(new Empire(empire.id(), empire.name(), empire.raceId(), empire.societyStructure(), 0,
                empire.corporateTaxRate(), empire.controlledSystemIds(), empire.ministries(), empire.systemGovernorAssignments(), empire.unlockedTechIds(), empire.activeShipDesignIds())));
        assertSame(poor, BatteryMaintenance.advance(poor, new HashMap<>(Map.of("sol/earth", 20.0)), Map.of("yard", 100)));
    }
    @Test void dailyConstructionConsumesCapacityBeforeBatteryRepairsAndWrecksCannotRevive() throws Exception {
        var state = world(100, 900); var processor = new ShipConstructionProcessor();
        assertEquals(battery(state), battery(processor.process(state, Map.of())));
        var order = new ShipConstructionOrder("full", "own", "carrier-design", "sol", "earth", 0, 10000, Map.of("steel", 10000.0), Map.of());
        var occupied = processor.process(state.withShipConstructionOrders(List.of(order)), Map.of("yard", 100));
        assertTrue(occupied.shipConstructionOrders().getFirst().accumulatedWorkHours() > 0); assertEquals(battery(state), battery(occupied));
        var repaired = processor.process(state, Map.of("yard", 100)); assertTrue(battery(repaired).moduleDamage().condition(0) > 0);
        var wreck = world(100, 0); assertEquals(battery(wreck), battery(processor.process(wreck, Map.of("yard", 100))));
        assertFalse(battery(wreck).isOperational());
    }
    @Test void dismantlingRevalidatesOwnershipAndControlFreesSlotsAndNeverRefundsSupplies() throws Exception {
        var state = world(100, 0); var batteries = new ArrayList<AuxiliaryForces.OwnedBattery>();
        int slots = (int) battery(state).hardware().value("bodyDefenseSlots");
        for (int i = 0; i < slots; i++) batteries.add(new AuxiliaryForces.OwnedBattery("own", new PlanetaryDefenseBattery("battery-" + i,
                "earth", battery(state).weaponApplicationId(), 1, true).withDamage(0, ShipModuleDamage.empty())));
        state = state.toBuilder().auxiliaryForces(new AuxiliaryForces(List.of(), batteries)).build();
        var build = new BuildAuxiliaryWeaponCommand("own", "sol", "earth", null, "surface_weapon_emplacements", battery(state).weaponApplicationId(), 1);
        assertFalse(build.validate(state)); assertFalse(new DismantleBatteryCommand("foreign", "battery-0").validate(state));
        var dismantle = new DismantleBatteryCommand("own", "battery-0"); var queue = new CommandQueue();
        var first = queue.submitTracked(dismantle); var second = queue.submitTracked(dismantle); var result = queue.drainAndExecute(state);
        assertEquals(CommandOutcome.EXECUTED, first.join()); assertEquals(CommandOutcome.REJECTED, second.join());
        assertTrue(build.validate(result)); assertEquals(slots - 1, result.auxiliaryForces().batteries().size());
        assertEquals(state.commercialHubs(), result.commercialHubs()); assertEquals(cash(state), cash(result));
        assertFalse(dismantle.validate(state.withEmpires(List.of()))); assertEquals(slots, state.auxiliaryForces().batteries().size());
    }
    @Test void capturedRepairRatesControlWorkAndOlderReceiptsResolveApplicationDefaults() throws Exception {
        var state = world(20, 1000); var original = battery(state); var hardware = original.hardware(); var stats = new HashMap<>(hardware.stats());
        stats.put("repairMountWorkHoursPerPoint", 4.0);
        var slow = new PlanetaryDefenseBattery(original.id(), original.planetId(), original.weaponApplicationId(), original.surfaceSlotsOccupied(), true,
                original.weapon(), original.stores(), new AuxiliaryHardware("BATTERY", hardware.materialCostsKg(), stats, hardware.applicationId()),
                original.hullHealth(), original.moduleDamage());
        var input = state.toBuilder().auxiliaryForces(state.auxiliaryForces().withBattery(slow)).build();
        var result = BatteryMaintenance.advance(input, new HashMap<>(Map.of("sol/earth", 20.0)), Map.of("yard", 100));
        assertEquals(15, battery(result).moduleDamage().damageByMount().get(0));
        stats.remove("repairMountWorkHoursPerPoint"); stats.remove("repairHullWorkHoursPerPoint");
        var legacy = new AuxiliaryHardware("BATTERY", hardware.materialCostsKg(), stats, hardware.applicationId());
        assertEquals(hardware.value("repairMountWorkHoursPerPoint"), BatteryMaintenance.hoursPerPoint(legacy, "repairMountWorkHoursPerPoint"));
        var unnamed = new AuxiliaryHardware("BATTERY", hardware.materialCostsKg(), stats);
        assertEquals(hardware.value("repairMountWorkHoursPerPoint"), BatteryMaintenance.hoursPerPoint(unnamed, "repairMountWorkHoursPerPoint"));
        var older = new PlanetaryDefenseBattery(original.id(), original.planetId(), original.weaponApplicationId(), 1, true,
                original.weapon(), original.stores(), unnamed, original.hullHealth(), original.moduleDamage());
        var legacyState = state.toBuilder().auxiliaryForces(state.auxiliaryForces().withBattery(older)).build();
        assertEquals(1, battery(BatteryMaintenance.advance(legacyState, new HashMap<>(Map.of("sol/earth", 20.0)), Map.of("yard", 100))).moduleDamage().condition(0));
    }
}
