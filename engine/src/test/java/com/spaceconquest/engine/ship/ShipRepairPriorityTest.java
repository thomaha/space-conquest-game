package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShipRepairPriorityTest {
    @Test void electricalRestorationPrecedesEarlierDamageWhenItRecoversAShedWorkshop() {
        var design = design(CombatModuleCatalog.GUN, ShipRepairModuleCatalog.WORKSHOP, SensorModuleCatalog.BASIC,
                ShipComponentCatalog.FISSION_REACTOR_ID);
        var host = ship("host", design, Map.of(0, 10.0, 3, 95.0), materials());
        var p = design.powerProfile(host);
        var budget = ShipPowerProcessor.interval(p, host.powerState(), 24, 0, p.essentialKw(host, design), 0, 0);
        assertEquals(0, budget.unmetEssentialKwh()); assertTrue(budget.state().shedServices().contains(ShipServicePower.WORKSHOPS));
        var context = new ShipRepairPriority.Context(false, FleetThreatAssessment.Action.NO_CONTACT, false);
        assertEquals(List.of(3, 0), ShipRepairPriority.mounts(context, design, host));
        assertEquals(95, host.moduleDamage().damageByMount().get(3)); assertEquals(1, host.generatorFuelMassKg());
    }

    private ShipDesign design(String... modules) {
        var power = ShipPowerProfile.capture(Arrays.stream(modules).map(ShipComponentCatalog::module).toList());
        return new ShipDesign("design", "Design", "owner", ShipRole.COMBAT_SHIP, "steel", List.of(modules), "steel", 0,
                1000, 1000, 1000, 100, 10, 0, 1000, false, false, ShipManufacturingProfile.baseline(), power);
    }
    private ShipInstance ship(String id, ShipDesign design, Map<Integer, Double> damage, Map<String, Double> cargo) {
        return new ShipInstance(id, design.id(), "owner", 1000, 0, 100, cargo)
                .withModuleDamage(new ShipModuleDamage(damage))
                .withPowerState(new ShipPowerState(Map.of("refined_uranium", 1.0), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0));
    }
    private Fleet fleet(String id, ShipInstance ship) {
        return new Fleet(id, id, "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship));
    }
    private GameState world(ShipDesign design, Fleet... fleets) {
        var empire = new Empire("owner", "Owner", "human", "Individualist", 1000, 0, List.of(), List.of(), Map.of(), List.of(), List.of());
        return GameState.builder().empires(List.of(empire)).shipDesigns(List.of(design)).fleets(List.of(fleets))
                .solarSystems(List.of(new SolarSystem("sol", "Sol", "", 0, 0, 0, 1.989e30, 1392700, "yellow", List.of(), List.of()))).build();
    }
    private ShipInstance repair(GameState state) {
        return ShipRepairProcessor.advanceDay(state, state.fleets()).getFirst().ships().getFirst();
    }
    private Map<String, Double> materials() {
        return Map.of("steel", 200.0, "refined_copper", 100.0, "silicon", 100.0,
                "refined_titanium", 100.0, "superconducting_cuprates", 100.0);
    }

    @Test void anActiveWorkshopRestoresADisabledWorkshopBeforeEarlierWeaponsWithoutExtraCapacity() {
        var design = design(ShipRepairModuleCatalog.WORKSHOP, CombatModuleCatalog.GUN,
                ShipRepairModuleCatalog.WORKSHOP, ShipComponentCatalog.FISSION_REACTOR_ID);
        var host = ship("host", design, Map.of(1, 20.0, 2, 100.0), materials());
        var after = repair(world(design, fleet("own", host)));
        assertEquals(80, after.moduleDamage().damageByMount().get(2));
        assertEquals(20, after.moduleDamage().damageByMount().get(1));
        assertEquals(150, after.storedCargoKg().get("steel"));
        assertEquals(90, after.storedCargoKg().get("refined_copper"));
        assertEquals(90, after.storedCargoKg().get("silicon"));
        assertEquals(100, host.moduleDamage().damageByMount().get(2));
    }

    @Test void absentCoverageRestoresSensorsBeforeWeaponsButLiveBackupAllowsOrdinaryOrder() {
        var design = design(ShipRepairModuleCatalog.WORKSHOP, CombatModuleCatalog.GUN, SensorModuleCatalog.BASIC,
                ShipComponentCatalog.FISSION_REACTOR_ID);
        var host = ship("host", design, Map.of(1, 20.0, 2, 100.0), materials());
        var own = fleet("own", host); var initial = world(design, own);
        assertTrue(ShipRepairPriority.context(initial, own).missingCoverage());
        var repaired = repair(initial);
        assertEquals(80, repaired.moduleDamage().damageByMount().get(2));
        assertEquals(20, repaired.moduleDamage().damageByMount().get(1));
        assertEquals(92, repaired.storedCargoKg().get("refined_copper"));
        var backup = fleet("backup", ship("backup", design, Map.of(), Map.of()));
        var supported = world(design, own, backup);
        assertFalse(ShipRepairPriority.context(supported, own).missingCoverage());
        repaired = repair(supported);
        assertFalse(repaired.moduleDamage().damageByMount().containsKey(1));
        assertEquals(100, repaired.moduleDamage().damageByMount().get(2));
        var coldShip = backup.ships().getFirst().withPowerState(new ShipPowerState(Map.of(), "rp1", "uranium", 0,
                false, 1, 1, 0, 0, 0, 1, 0));
        var afterPower = ShipRepairProcessor.advanceDay(supported, List.of(own, backup.withShips(List.of(coldShip))))
                .getFirst().ships().getFirst();
        assertEquals(80, afterPower.moduleDamage().damageByMount().get(2));
        assertEquals(20, afterPower.moduleDamage().damageByMount().get(1));
    }

    @Test void unpayableCriticalMountDoesNotWasteCapacityOrInventMaterials() {
        var design = design(ShipRepairModuleCatalog.WORKSHOP, CombatModuleCatalog.GUN, SensorModuleCatalog.BASIC,
                ShipComponentCatalog.FISSION_REACTOR_ID);
        var host = ship("host", design, Map.of(1, 20.0, 2, 100.0), Map.of("steel", 20.0));
        var repaired = repair(world(design, fleet("own", host)));
        assertFalse(repaired.moduleDamage().damageByMount().containsKey(1));
        assertEquals(100, repaired.moduleDamage().damageByMount().get(2));
        assertTrue(repaired.storedCargoKg().isEmpty());
    }

    @Test void threatAndWithdrawalChooseCombatHardwareWithoutChangingTheSnapshotOrQuietOrder() {
        var design = design(ShipRepairModuleCatalog.WORKSHOP, CombatModuleCatalog.GUN, CombatModuleCatalog.SHIELD,
                SensorModuleCatalog.BASIC, ShipComponentCatalog.FISSION_REACTOR_ID);
        var host = ship("host", design, Map.of(1, 50.0, 2, 50.0), materials()); var own = fleet("own", host);
        var initial = world(design, own);
        assertEquals(List.of(1, 2), ShipRepairPriority.mounts(ShipRepairPriority.context(initial, own), design, host));
        var enemy = new Fleet("enemy", "Enemy", "enemy", "sol", "", 0, 0, 0, false, "PASSIVE",
                List.of(ship("enemy", design, Map.of(1, 50.0), Map.of())));
        var threatened = initial.toBuilder().fleets(List.of(own, enemy))
                .diplomaticRelations(List.of(new DiplomaticRelation("owner", "enemy", "TOTAL_WAR", 0))).build();
        assertEquals(FleetThreatAssessment.Action.HOLD, ShipRepairPriority.context(threatened, own).threat());
        var fighting = repair(threatened);
        assertEquals(30, fighting.moduleDamage().damageByMount().get(1));
        assertEquals(50, fighting.moduleDamage().damageByMount().get(2));
        var retreat = own.withStance(com.spaceconquest.engine.combat.TacticalCombatProcessor.STANCE_EVASIVE_RETREAT);
        var escaping = repair(threatened.withFleets(List.of(retreat, enemy)));
        assertEquals(50, escaping.moduleDamage().damageByMount().get(1));
        assertEquals(30, escaping.moduleDamage().damageByMount().get(2));
        assertEquals(50, host.moduleDamage().damageByMount().get(1));
        assertEquals(materials(), host.storedCargoKg());
    }
    @Test void usefulGeneratorRestorationPrecedesUnpoweredSensorsAndWeaponsWithoutSpendingQuotes() {
        var design = design(ShipRepairModuleCatalog.WORKSHOP, CombatModuleCatalog.GUN, SensorModuleCatalog.BASIC,
                ShipComponentCatalog.FISSION_REACTOR_ID);
        var target = ship("target", design, Map.of(1, 20.0, 2, 100.0, 3, 100.0), Map.of());
        var helper = ship("helper", design, Map.of(), Map.of("steel", 60.0, "refined_copper", 24.0));
        var own = fleet("own", target).withShips(List.of(target, helper)); var state = world(design, own);
        var context = ShipRepairPriority.context(state, own);
        assertFalse(context.missingCoverage()); // The helper contributes to the shared view.
        assertEquals(List.of(3, 1, 2), ShipRepairPriority.mounts(context, design, target));
        assertEquals(List.of(3, 2, 1), ShipRepairPriority.mounts(new ShipRepairPriority.Context(true,
                FleetThreatAssessment.Action.NO_CONTACT, false, context.environment()), design, target));
        assertEquals(List.of(0, 3, 1, 2), ShipRepairPriority.mounts(context, design,
                target.withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0, 1, 20.0, 2, 100.0, 3, 100.0)))));
        var after = ShipPowerProcessor.advanceDay(state).getFirst(); var repaired = after.ships().getFirst();
        assertEquals(80, repaired.moduleDamage().damageByMount().get(3));
        assertEquals(100, repaired.moduleDamage().damageByMount().get(2));
        assertEquals(20, repaired.moduleDamage().damageByMount().get(1));
        assertTrue(after.ships().getLast().storedCargoKg().isEmpty());
        assertEquals(100, target.moduleDamage().damageByMount().get(3));
        assertEquals(1, target.generatorFuelMassKg());
        assertEquals(24, helper.storedCargoKg().get("refined_copper"));
    }

    @Test void workingBackupAndAvailableFuelDetermineWhetherGeneratorRepairIsUseful() {
        var design = design(ShipRepairModuleCatalog.WORKSHOP, CombatModuleCatalog.GUN, SensorModuleCatalog.BASIC,
                ShipComponentCatalog.FISSION_REACTOR_ID, ShipComponentCatalog.FISSION_REACTOR_ID);
        var target = ship("target", design, Map.of(1, 20.0, 2, 100.0, 3, 100.0), Map.of());
        var own = fleet("own", target); var state = world(design, own);
        assertEquals(List.of(2, 1, 3), ShipRepairPriority.mounts(ShipRepairPriority.context(state, own), design, target));
        var empty = target.withModuleDamage(new ShipModuleDamage(Map.of(1, 20.0, 2, 100.0, 3, 100.0, 4, 100.0)))
                .withPowerState(ShipPowerState.empty());
        assertEquals(List.of(2, 1, 3, 4), ShipRepairPriority.mounts(ShipRepairPriority.context(state, own), design, empty));
        var fueled = empty.withPowerState(target.powerState());
        assertEquals(List.of(3, 4, 2, 1), ShipRepairPriority.mounts(ShipRepairPriority.context(state, own), design, fueled));
    }

    @Test void repairingAnEmptyBatteryDoesNotAssumeChargeOrPromoteItAheadOfSensors() {
        var design = design(ShipRepairModuleCatalog.WORKSHOP, CombatModuleCatalog.GUN, SensorModuleCatalog.BASIC,
                ShipComponentCatalog.BATTERY_ID);
        var target = ship("target", design, Map.of(1, 20.0, 2, 100.0, 3, 100.0), Map.of());
        var context = new ShipRepairPriority.Context(true, FleetThreatAssessment.Action.NO_CONTACT, false);
        assertEquals(List.of(2, 1, 3), ShipRepairPriority.mounts(context, design, target));
        assertEquals(0, target.powerState().batteryChargeKwh());
    }

    @Test void solarRestorationRequiresActualSunlightAndDeployedArrays() {
        var design = design(ShipRepairModuleCatalog.WORKSHOP, CombatModuleCatalog.GUN, SensorModuleCatalog.BASIC,
                ShipComponentCatalog.SOLAR_ARRAY_ID);
        var target = ship("target", design, Map.of(1, 20.0, 2, 100.0, 3, 100.0), Map.of())
                .withPowerState(ShipPowerState.empty());
        var sun = new ShipRepairPriority.Context(true, FleetThreatAssessment.Action.NO_CONTACT, false,
                new ShipSolarEnvironment(1, 24, 0, "Full sunlight"));
        assertEquals(List.of(3, 2, 1), ShipRepairPriority.mounts(sun, design, target));
        var dark = new ShipRepairPriority.Context(true, FleetThreatAssessment.Action.NO_CONTACT, false);
        assertEquals(List.of(2, 1, 3), ShipRepairPriority.mounts(dark, design, target));
        target = target.withPowerState(new ShipPowerState(Map.of(), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0));
        assertEquals(List.of(2, 1, 3), ShipRepairPriority.mounts(sun, design, target));
    }

    @Test void missingGeneratorMaterialsLeaveCapacityForFundableWork() {
        var design = design(ShipRepairModuleCatalog.WORKSHOP, CombatModuleCatalog.GUN, SensorModuleCatalog.BASIC,
                ShipComponentCatalog.FISSION_REACTOR_ID);
        var target = ship("target", design, Map.of(1, 20.0, 2, 100.0, 3, 100.0), Map.of());
        var helper = ship("helper", design, Map.of(), Map.of("steel", 20.0));
        var own = fleet("own", target).withShips(List.of(target, helper));
        var repaired = ShipPowerProcessor.advanceDay(world(design, own)).getFirst();
        assertFalse(repaired.ships().getFirst().moduleDamage().damageByMount().containsKey(1));
        assertEquals(100, repaired.ships().getFirst().moduleDamage().damageByMount().get(2));
        assertEquals(100, repaired.ships().getFirst().moduleDamage().damageByMount().get(3));
        assertTrue(repaired.ships().getLast().storedCargoKg().isEmpty());
    }
}
