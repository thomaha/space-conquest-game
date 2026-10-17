package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SurfaceBatteryCampaignTest {
    private final ShipPointDefenseTest fixture = new ShipPointDefenseTest();
    private Empire empire(String id) { return new Empire(id, id, "human", "Individualist", 0, 0, List.of(), List.of(), Map.of(), List.of(), List.of()); }
    GameState world(String... enemyWeapons) {
        var observer = fixture.design("own", 1000, SensorModuleCatalog.BASIC);
        var modules = new ArrayList<>(List.of(SensorModuleCatalog.BASIC)); modules.addAll(List.of(enemyWeapons));
        var enemy = fixture.design("enemy", 1000, modules.toArray(String[]::new));
        var earth = new Planet("earth", "Earth", "", 5.97e24, 9.81, 149600000, 0, 12742, "terrestrial", "", true, 0, List.of(), List.of(), List.of());
        var ownFleet = fixture.fleet("own", List.of(fixture.ship("observer", observer, 0)))
                .withLocation(FleetLocation.at(FleetLocation.Site.surface("earth")));
        var enemyFleet = fixture.fleet("enemy", List.of(fixture.ship("target", enemy, 0)))
                .withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth", 100)));
        var weapon = CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER);
        var battery = new PlanetaryDefenseBattery("battery", "earth", CombatModuleCatalog.application(weapon.id()).id(), 1, true)
                .withStores(new AuxiliaryWeaponStores(Map.of(weapon.ammunitionId(), weapon.operationalStats().get("ammunitionMassKg")),
                        weapon.powerDrawKw() / 60 + weapon.operationalStats().get("weaponSalvoKwh")));
        return GameState.builder().shipDesigns(List.of(observer, enemy)).empires(List.of(empire("own"), empire("enemy")))
                .solarSystems(List.of(new SolarSystem("sol", "Sol", "", 0, 0, 0, 1.989e30, 1392700, "yellow", List.of(earth), List.of())))
                .fleets(List.of(ownFleet, enemyFleet)).diplomaticRelations(List.of(new DiplomaticRelation("own", "enemy", "TOTAL_WAR", 0)))
                .auxiliaryForces(new AuxiliaryForces(List.of(), List.of(new AuxiliaryForces.OwnedBattery("own", battery)))).build();
    }
    private GameState fight(GameState state) { return SurfaceBatteryEncounterResolver.resolve(state, new TacticalCombatProcessor(new Random(42))); }
    @Test void surfaceFirePaysOneProjectileAndCampaignResultsRetainDepletionWithoutProxyFleets() {
        var state = world(); var result = fight(state);
        assertEquals(1, result.fleetEngagements().size()); assertEquals("SURFACE_BATTERY", result.fleetEngagements().getFirst().siteKind());
        assertTrue(result.fleets().getLast().ships().getFirst().currentHullHealth() < 1000);
        var battery = result.auxiliaryForces().batteries().getFirst().battery();
        assertEquals(0, battery.stores().energyKwh(), 1e-9); assertTrue(battery.stores().ammunitionKg().isEmpty());
        assertEquals(List.of("own", "enemy"), result.fleets().stream().map(Fleet::id).toList());
        assertEquals(result.fleets(), fight(result).fleets()); assertEquals(1, fight(result).fleetEngagements().size());
        assertEquals(1, state.auxiliaryForces().batteries().getFirst().battery().stores().ammunitionKg().size());
        var summary = result.fleetEngagements().getFirst().surfaceCombat(); assertFalse(summary.fleetWithdrew());
        assertEquals(1, summary.batteries().getFirst().paidShots());
        assertEquals(state.auxiliaryForces().batteries().getFirst().battery().stores().energyKwh(), summary.batteries().getFirst().energySpentKwh(), 1e-9);
        assertTrue(summary.fleetHullLost() > 0);
    }
    @Test void liveCoverageWarRangeAndFiniteSuppliesGateBatteryFire() {
        var state = world();
        assertTrue(fight(state.withDiplomaticRelations(List.of())).fleetEngagements().isEmpty());
        var blind = state.withFleets(List.of(state.fleets().getLast()));
        assertTrue(fight(blind).fleetEngagements().isEmpty());
        var far = state.withFleets(List.of(state.fleets().getFirst(), state.fleets().getLast()
                .withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth", 100000)))));
        assertEquals(state.auxiliaryForces(), fight(far).auxiliaryForces());
        var battery = state.auxiliaryForces().batteries().getFirst().battery();
        var dry = state.toBuilder().auxiliaryForces(state.auxiliaryForces().withBattery(battery.withStores(AuxiliaryWeaponStores.empty()))).build();
        assertTrue(fight(dry).fleetEngagements().isEmpty());
    }
    @Test void projectilesUseEnemyPointDefenseAndIonCounterfireDamagesElectronicsWithoutBatteryHullDamage() {
        var defended = fight(world(CombatModuleCatalog.LASER_POINT_DEFENSE));
        assertEquals(1000, defended.fleets().getLast().ships().getFirst().currentHullHealth());
        assertTrue(defended.auxiliaryForces().batteries().getFirst().battery().stores().ammunitionKg().isEmpty());
        assertEquals(1, defended.fleetEngagements().getFirst().surfaceCombat().batteries().getFirst().paidShots());
        assertEquals(0, defended.fleetEngagements().getFirst().surfaceCombat().fleetHullLost());
        var initial = world(CombatModuleCatalog.ION); var disabled = fight(initial);
        var before = initial.auxiliaryForces().batteries().getFirst().battery(); var after = disabled.auxiliaryForces().batteries().getFirst().battery();
        assertEquals(before.hullHealth(), after.hullHealth()); assertEquals(0, after.moduleDamage().condition(0));
        assertTrue(after.stores().ammunitionKg().isEmpty());
        var receipt = disabled.fleetEngagements().getFirst().surfaceCombat().batteries().getFirst();
        assertEquals(0, receipt.hullLost()); assertTrue(receipt.componentDamageAdded() > 0); assertEquals(0, receipt.finalCondition());
    }
    @Test void physicalCounterfireDestroysTheBatteryAndPersistsItsWreck() {
        var result = fight(world(CombatModuleCatalog.PARTICLE_BEAM));
        var battery = result.auxiliaryForces().batteries().getFirst().battery();
        assertEquals(0, battery.hullHealth()); assertFalse(battery.isOperational());
        assertEquals(result.auxiliaryForces(), fight(result).auxiliaryForces());
    }
    @Test void groupedBatteriesShareOneEngagementAndPointDefenseCannotResetBetweenThem() {
        var state = world(CombatModuleCatalog.LASER_POINT_DEFENSE);
        var original = state.auxiliaryForces().batteries().getFirst().battery();
        int capacity = (int) CombatModuleCatalog.module(CombatModuleCatalog.LASER_POINT_DEFENSE).operationalStats().get("pointDefenseCapacity").doubleValue();
        var group = new ArrayList<AuxiliaryForces.OwnedBattery>();
        for (int i = 0; i <= capacity; i++) group.add(new AuxiliaryForces.OwnedBattery("own", new PlanetaryDefenseBattery("battery-" + i,
                "earth", original.weaponApplicationId(), 1, true).withStores(original.stores())));
        state = state.toBuilder().auxiliaryForces(new AuxiliaryForces(List.of(), group)).build();
        var result = fight(state);
        assertEquals(1, result.fleetEngagements().size()); assertEquals("earth", result.fleetEngagements().getFirst().siteEntityId());
        assertTrue(result.fleets().getLast().ships().getFirst().currentHullHealth() < 1000);
        assertTrue(result.auxiliaryForces().batteries().stream().allMatch(b -> b.battery().stores().ammunitionKg().isEmpty()));
        assertTrue(state.auxiliaryForces().batteries().stream().allMatch(b -> !b.battery().stores().ammunitionKg().isEmpty()));
        assertEquals(2, result.fleets().size());
    }
    @Test void eachGroupedBatteryKeepsIndependentRangePowerAndMagazineGates() {
        var state = world(); var ready = state.auxiliaryForces().batteries().getFirst().battery();
        var dry = new PlanetaryDefenseBattery("dry", "earth", ready.weaponApplicationId(), 1, true);
        var laser = CombatModuleCatalog.module(CombatModuleCatalog.LASER);
        var closeRange = new PlanetaryDefenseBattery("close", "earth", CombatModuleCatalog.application(laser.id()).id(), 1, true)
                .withStores(new AuxiliaryWeaponStores(Map.of(), 100));
        var disabled = new PlanetaryDefenseBattery("disabled", "earth", ready.weaponApplicationId(), 1, true).withStores(ready.stores())
                .withDamage(ready.hullHealth(), new ShipModuleDamage(Map.of(0, 100.0)));
        var group = List.of(new AuxiliaryForces.OwnedBattery("own", ready), new AuxiliaryForces.OwnedBattery("own", dry),
                new AuxiliaryForces.OwnedBattery("own", closeRange), new AuxiliaryForces.OwnedBattery("own", disabled));
        var result = fight(state.toBuilder().auxiliaryForces(new AuxiliaryForces(List.of(), group)).build());
        assertEquals(1, result.fleetEngagements().size());
        var stores = result.auxiliaryForces().batteries().stream().collect(java.util.stream.Collectors.toMap(b -> b.battery().id(), b -> b.battery().stores()));
        assertTrue(stores.get(ready.id()).ammunitionKg().isEmpty());
        assertEquals(closeRange.stores(), stores.get("close")); assertEquals(disabled.stores(), stores.get("disabled"));
        assertTrue(stores.get("dry").ammunitionKg().isEmpty());
    }
}
