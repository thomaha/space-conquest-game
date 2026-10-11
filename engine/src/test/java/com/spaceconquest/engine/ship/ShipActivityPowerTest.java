package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.TacticalCombatProcessor;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShipActivityPowerTest {
    private ShipDesign design(int guns) {
        var modules = new ArrayList<>(List.of(ShipComponentCatalog.FISSION_REACTOR_ID, SensorModuleCatalog.BASIC,
                ShipRepairModuleCatalog.WORKSHOP, CombatModuleCatalog.GUN, CombatModuleCatalog.SHIELD));
        for (int i = 1; i < guns; i++) modules.add(CombatModuleCatalog.GUN);
        return new ShipDesign("design", "Design", "owner", ShipRole.COMBAT_SHIP, "steel", modules,
                "steel", 0, 1000, 10000, 1000, 100, 10, 0, 2000, false, false,
                ShipManufacturingProfile.baseline(), ShipPowerProfile.capture(modules.stream().map(ShipComponentCatalog::module).toList()));
    }
    private ShipInstance ship(String id, String owner, double output, Map<String, Double> cargo) {
        return new ShipInstance(id, "enemy".equals(owner) ? "enemy-design" : "design", owner, 1000, 100, 1000, cargo)
                .withModuleDamage(new ShipModuleDamage(Map.of(0, 100 * (1 - output / 500))))
                .withPowerState(new ShipPowerState(Map.of("refined_uranium", 1.0), "rp1", "uranium", 0,
                        false, 1, 1, 0, 0, 0, 0, 0));
    }
    private Fleet fleet(String id, String owner, List<ShipInstance> ships) {
        return new Fleet(id, id, owner, "sol", "", 0, 0, 0, false, "PASSIVE", ships);
    }
    private GameState world(ShipDesign design, Fleet... fleets) {
        var enemyDesign = new ShipDesign("enemy-design", "Enemy design", "enemy", ShipRole.COMBAT_SHIP, "steel", design.equippedModuleIds(),
                "steel", 0, 1000, 10000, 1000, 100, 10, 0, 2000, false, false,
                ShipManufacturingProfile.baseline(), design.powerProfile());
        return GameState.builder().shipDesigns(List.of(design, enemyDesign)).fleets(List.of(fleets))
                .empires(List.of(new Empire("owner", "Owner", "human", "Individualist", 0, 0,
                        List.of(), List.of(), Map.of(), List.of(), List.of()),
                        new Empire("enemy", "Enemy", "human", "Individualist", 0, 0,
                                List.of(), List.of(), Map.of(), List.of(), List.of())))
                .solarSystems(List.of(new SolarSystem("sol", "Sol", "", 0, 0, 0, 1.989e30, 1392700, "yellow", List.of(), List.of()))).build();
    }
    private ShipPowerProcessor.Interval interval(ShipDesign design, double output, ShipServicePower.Activity activity, double cargo, double drive) {
        var ship = ship("ship", "owner", output, Map.of()); var p = design.powerProfile(ship);
        return ShipPowerProcessor.interval(p, ship.powerState(), 1, 0, p.essentialKw(ship, design), cargo, drive, activity);
    }

    @Test void combatEscapeAndRepairSelectDifferentWorkingEquipmentWithExactPaidPower() {
        var design = design(1);
        var cruising = interval(design, 50, ShipServicePower.Activity.CRUISE, 0, 0);
        var combat = interval(design, 50, ShipServicePower.Activity.COMBAT, 0, 0);
        assertEquals(Set.of(ShipServicePower.SHIELDS), cruising.state().shedServices());
        assertEquals(Set.of(ShipServicePower.WORKSHOPS), combat.state().shedServices());
        assertEquals(1 - 34.0 / 6_000_000, cruising.state().fuelMassKg(), 1e-12);
        assertEquals(1 - 44.0 / 6_000_000, combat.state().fuelMassKg(), 1e-12);
        var escape = interval(design, 35, ShipServicePower.Activity.ESCAPE, 0, 0);
        assertEquals(Set.of(ShipServicePower.SENSORS, ShipServicePower.WORKSHOPS), escape.state().shedServices());
        var repair = interval(design, 25, ShipServicePower.Activity.REPAIR, 0, 0);
        assertEquals(Set.of(ShipServicePower.SENSORS, ShipServicePower.SHIELDS), repair.state().shedServices());
        for (var activity : ShipServicePower.Activity.values()) {
            var required = interval(design, 35, activity, 18, 15);
            assertTrue(required.supplied()); assertEquals(Set.copyOf(ShipServicePower.PRIORITY), required.state().shedServices());
        }
    }

    @Test void fundedCombatShotsDisplaceWorkshopIdleDrawAndThreatQuotesMatchExecution() {
        var design = design(1); var ship = ship("ship", "owner", 104, Map.of());
        var result = ShipCombatPower.round(ship, design, 1.0 / 60, true);
        assertEquals(1, result.paidWeaponKwh(), 1e-7); assertEquals(80, result.firepower(), 1e-5);
        assertEquals(Set.of(ShipServicePower.WORKSHOPS), result.ship().powerState().shedServices());
        assertEquals(100, result.ship().currentShieldHealth(), 1e-7);
        assertEquals(1 - 104.0 / 60 / 6_000_000, result.ship().generatorFuelMassKg(), 1e-12);
        assertEquals(result.firepower(), ShipCombatPower.fundedFirepower(ship, design, 1.0 / 60, true), 1e-7);
        assertEquals(1, ship.generatorFuelMassKg()); assertTrue(ship.powerState().shedServices().isEmpty());
    }

    @Test void evasiveCombatFundsShieldChargingBeforeWeaponAndWorkshopIdlePower() {
        var design = design(5); var ship = ship("ship", "owner", 54, Map.of());
        var result = ShipCombatPower.round(ship, design, 1.0 / 60, false);
        assertEquals(0, result.firepower()); assertEquals(0, result.paidWeaponKwh());
        assertEquals(Set.of(ShipServicePower.WEAPONS, ShipServicePower.WORKSHOPS), result.ship().powerState().shedServices());
        assertEquals(100.1, result.ship().currentShieldHealth(), 1e-7);
        assertEquals(1 - 54.0 / 60 / 6_000_000, result.ship().generatorFuelMassKg(), 1e-12);
    }

    @Test void quietDamagedFleetFundsActualWorkshopRepairAndPreviewMatchesItsPaidPower() {
        var design = design(1); var source = ship("ship", "owner", 25, Map.of("steel", 20.0));
        source = source.withModuleDamage(new ShipModuleDamage(Map.of(0, 95.0, 3, 20.0)));
        var fleet = fleet("own", "owner", List.of(source)); var state = world(design, fleet);
        assertEquals(ShipServicePower.Activity.REPAIR, ShipServicePower.activity(state, fleet));
        var forecast = ShipShieldPower.forecast(source, design, List.of(new ShipShieldPower.Phase(24, 0,
                ShipSolarEnvironment.DARK, ShipServicePower.Activity.REPAIR)), ShipSolarEnvironment.DARK);
        var actual = ShipPowerProcessor.advanceDay(state).getFirst().ships().getFirst();
        assertEquals(forecast.firstDayShip().powerState(), actual.powerState());
        assertFalse(actual.moduleDamage().damageByMount().containsKey(3)); assertTrue(actual.storedCargoKg().isEmpty());
        assertEquals(95, actual.moduleDamage().damageByMount().get(0));
        assertEquals(Set.of(ShipServicePower.SENSORS, ShipServicePower.SHIELDS), actual.powerState().shedServices());
        var evading = fleet.withStance(TacticalCombatProcessor.STANCE_EVASIVE_RETREAT);
        assertEquals(ShipServicePower.Activity.ESCAPE, ShipServicePower.activity(state.withFleets(List.of(evading)), evading));
        assertEquals(20, source.storedCargoKg().get("steel")); assertEquals(20, source.moduleDamage().damageByMount().get(3));
    }

    @Test void currentDetectedDangerOverridesRepairButLostCoverageDoesNotInventCombat() {
        var design = design(1); var source = ship("own", "owner", 50, Map.of());
        var own = fleet("own", "owner", List.of(source));
        var enemy = fleet("enemy", "enemy", List.of(ship("enemy", "enemy", 50, Map.of())));
        var state = world(design, own, enemy).withDiplomaticRelations(List.of(new DiplomaticRelation("owner", "enemy", "TOTAL_WAR", 0)));
        assertEquals(ShipServicePower.Activity.COMBAT, ShipServicePower.activity(state, own));
        var fighting = ShipPowerProcessor.advanceDay(state).getFirst().ships().getFirst();
        assertEquals(Set.of(ShipServicePower.WORKSHOPS), fighting.powerState().shedServices());
        assertEquals(0, fighting.powerState().lastUnmetEssentialKwh());
        var reinforced = enemy.withShips(java.util.stream.IntStream.range(0, 20).mapToObj(i -> ship("enemy-" + i, "enemy", 500, Map.of())).toList());
        assertEquals(ShipServicePower.Activity.ESCAPE, ShipServicePower.activity(state.withFleets(List.of(own, reinforced)), own));
        var hidden = own.withShips(List.of(source.withModuleDamage(new ShipModuleDamage(Map.of(0, 90.0, 1, 100.0)))));
        assertEquals(ShipServicePower.Activity.REPAIR, ShipServicePower.activity(state.withFleets(List.of(hidden, reinforced)), hidden));
        assertEquals(ShipServicePower.Activity.REPAIR, ShipServicePower.activity(state.withDiplomaticRelations(List.of()), own));
        assertTrue(source.powerState().shedServices().isEmpty()); assertEquals(1, source.generatorFuelMassKg());
    }

    @Test void savedEscapeTravelUsesShieldPriorityButQuietRepairWaitDoesNotKeepItForever() {
        var design = design(1); var source = ship("own", "owner", 35, Map.of());
        var protection = new FleetProtectionAssignment(null, "", "own", FleetLocation.Site.deepSpace(),
                FleetProtectionAssignment.Stage.RETURNING, "", true);
        var own = fleet("own", "owner", List.of(source)).withInterceptOrder(new FleetInterceptOrder("owner", "target", "sol", protection));
        var state = world(design, own);
        var travelling = own.withLocation(own.location().depart(FleetLocation.Site.deepSpace("destination"), 2));
        assertEquals(ShipServicePower.Activity.ESCAPE, ShipServicePower.activity(state.withFleets(List.of(travelling)), travelling));
        var waiting = own.withInterceptOrder(own.interceptOrder().withProtection(protection.at(FleetProtectionAssignment.Stage.REPAIRING, "")));
        assertEquals(ShipServicePower.Activity.REPAIR, ShipServicePower.activity(state.withFleets(List.of(waiting)), waiting));
        assertEquals(protection, own.interceptOrder().protection()); assertEquals(1, source.generatorFuelMassKg());
    }
}
