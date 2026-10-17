package com.spaceconquest.engine.ship;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuxiliaryWeaponStoresTest {
    private final ShipPointDefenseTest fixture = new ShipPointDefenseTest();
    private final ObjectMapper mapper = new ObjectMapper();
    private ShipModule variant(String id, Map<String, Double> values) throws Exception {
        ObjectNode app = mapper.valueToTree(CombatModuleCatalog.application(id));
        var stats = (ObjectNode) app.get("combatModule").get("operationalStats"); values.forEach(stats::put);
        return mapper.treeToValue(app, TechnicalApplication.class).combatModule();
    }
    private double upkeep(ShipModule weapon) { return weapon.powerDrawKw() / 60; }
    private AuxiliaryWeaponStores stores(ShipModule weapon, int rounds, double energy) {
        return new AuxiliaryWeaponStores(weapon.ammunitionId().isBlank() ? Map.of()
                : Map.of(weapon.ammunitionId(), rounds * weapon.operationalStats().get("ammunitionMassKg")), energy);
    }
    private TacticalCombatProcessor.CombatEngagementResult fight(CarrierWing wing, PlanetaryDefenseBattery battery) {
        return fight(wing, battery, new String[0]);
    }
    private TacticalCombatProcessor.CombatEngagementResult fight(CarrierWing wing, PlanetaryDefenseBattery battery, String... defense) {
        var a = fixture.design("carrier", 10000); var d = fixture.design("target", 10000, defense);
        return new TacticalCombatProcessor(new Random(42)).resolveFleetEngagementWithCarrierWings(
                fixture.fleet("carrier", List.of(fixture.ship("carrier", a, 0))),
                fixture.fleet("target", List.of(fixture.ship("target", d, 0))), List.of(a, d),
                wing == null ? List.of() : List.of(wing), List.of(), battery, "ALL");
    }

    @Test void emptyLegacyStoresCannotFireAndDepletedStoresReturnInTheCombatResult() {
        var weapon = CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER);
        String app = CombatModuleCatalog.application(weapon.id()).id();
        var wing = new CarrierWing("wing", "carrier", "INTERCEPTOR_FIGHTER", 1, 1, 100, app, "CAPITAL_SHIPS");
        var battery = new PlanetaryDefenseBattery("battery", "earth", app, 1, true);
        var empty = fight(wing, battery);
        assertTrue(empty.roundReports().stream().allMatch(r -> r.attackerDamageDealt() == 0 && r.defenderDamageDealt() == 0));
        var loaded = stores(weapon, 1, upkeep(weapon) + weapon.operationalStats().get("weaponSalvoKwh"));
        var result = fight(wing.withStores(loaded), battery.withStores(loaded));
        assertEquals(1, result.roundReports().stream().flatMap(r -> r.detailedActions().stream()).filter(a -> a.phase().equals("STRIKE_WING")).count());
        assertEquals(1, result.roundReports().stream().flatMap(r -> r.detailedActions().stream()).filter(a -> a.phase().equals("SURFACE_BATTERY")).count());
        assertEquals(0, result.survivingAttackerWings().getFirst().stores().energyKwh(), 1e-9);
        assertTrue(result.survivingPlanetaryBattery().stores().ammunitionKg().isEmpty());
        assertEquals(loaded, wing.withStores(loaded).stores());
        assertThrows(UnsupportedOperationException.class, () -> result.survivingAttackerWings().clear());
        var resumed = fight(result.survivingAttackerWings().getFirst(), result.survivingPlanetaryBattery());
        assertTrue(resumed.roundReports().stream().allMatch(r -> r.attackerDamageDealt() == 0 && r.defenderDamageDealt() == 0));
    }

    @Test void wholeConfiguredShotsPayEnergyAndAmmunitionWithoutSpendingOutOfRangeStores() throws Exception {
        var weapon = variant(CombatModuleCatalog.MISSILE, Map.of("ammunitionMassKg", 2.0, "weaponSalvoKwh", 3.0,
                "launchesPerRound", 3.0, "weaponRangeMeters", 100.0));
        var source = new AuxiliaryWeaponStores(Map.of(weapon.ammunitionId(), 5.0), upkeep(weapon) + 6);
        var out = source.fire(weapon, 1, 101);
        assertEquals(source, out.stores()); assertTrue(out.hits().isEmpty());
        var paid = source.fire(weapon, 1, 100);
        assertEquals(2, paid.hits().size()); assertEquals(1, paid.stores().ammunitionKg().get(weapon.ammunitionId()));
        assertEquals(upkeep(weapon) + 6, paid.spentKwh(), 1e-9); assertEquals(0, paid.stores().energyKwh(), 1e-9);
        var scarce = stores(weapon, 3, upkeep(weapon) + 2.9).fire(weapon, 1, 0);
        assertTrue(scarce.hits().isEmpty()); assertEquals(6, scarce.stores().ammunitionKg().get(weapon.ammunitionId()));
        var beam = variant(CombatModuleCatalog.LASER, Map.of("shotsPerRound", 4.0, "weaponDamage", 80.0, "weaponSalvoKwh", 8.0));
        var pulse = stores(beam, 0, upkeep(beam) + 2).fire(beam, 1, 0);
        assertEquals(1, pulse.hits().size()); assertEquals(20, pulse.hits().getFirst().damage());
        assertThrows(UnsupportedOperationException.class, () -> source.ammunitionKg().clear());
        assertThrows(IllegalArgumentException.class, () -> new AuxiliaryWeaponStores(Map.of(), Double.NaN));
    }

    @Test void actualMissesSpendLaunchStoresAndSerializationPreservesDepletion() throws Exception {
        var weapon = variant(CombatModuleCatalog.MASS_DRIVER, Map.of("accuracy", 0.0));
        var wing = new CarrierWing("wing", "carrier", "INTERCEPTOR_FIGHTER", 1, 1, 100,
                CombatModuleCatalog.application(weapon.id()).id(), "CAPITAL_SHIPS", weapon)
                .withStores(stores(weapon, 1, upkeep(weapon) + weapon.operationalStats().get("weaponSalvoKwh")));
        var result = fight(wing, null);
        assertTrue(result.roundReports().stream().anyMatch(r -> r.detailedActions().stream().anyMatch(a -> a.phase().equals("WEAPON_MISS"))));
        var spent = result.survivingAttackerWings().getFirst();
        assertTrue(spent.stores().ammunitionKg().isEmpty());
        assertEquals(spent, mapper.readValue(mapper.writeValueAsString(spent), CarrierWing.class));
        ObjectNode legacy = mapper.valueToTree(wing); legacy.remove("stores");
        assertEquals(AuxiliaryWeaponStores.empty(), mapper.treeToValue(legacy, CarrierWing.class).stores());
        var missingHost = new CarrierWing("wing", "absent", "INTERCEPTOR_FIGHTER", 1, 1, 100,
                wing.weaponApplicationId(), "CAPITAL_SHIPS", weapon).withStores(wing.stores());
        assertEquals(wing.stores(), fight(missingHost, null).survivingAttackerWings().getFirst().stores());
        var intercepted = fight(wing, null, CombatModuleCatalog.LASER_POINT_DEFENSE);
        assertTrue(intercepted.roundReports().stream().flatMap(r -> r.detailedActions().stream()).anyMatch(a -> a.phase().equals("POINT_DEFENSE")));
        assertTrue(intercepted.survivingAttackerWings().getFirst().stores().ammunitionKg().isEmpty());
    }

    private GameState market(ShipModule weapon, double funds, double stock, ShipInstance source) {
        var owner = new Empire("owner", "Owner", "human", "Individualist", funds, 0, List.of(), List.of(), Map.of(), List.of(), List.of());
        var hub = new CommercialHub("hub", "earth", 0, 10000, stock, 10,
                Map.of(weapon.ammunitionId(), new MarketOrder(weapon.ammunitionId(), stock, 0, 2, 0)));
        var fleet = new Fleet("source", "Source", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(source),
                FleetLocation.at(FleetLocation.Site.surface("earth")));
        return GameState.builder().empires(List.of(owner)).commercialHubs(List.of(hub)).fleets(List.of(fleet)).build();
    }

    @Test void batteryResupplyPaysFiniteStockAndCreditsAndTransfersExistingElectricityAtomically() {
        var weapon = CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER);
        var battery = new PlanetaryDefenseBattery("battery", "earth", CombatModuleCatalog.application(weapon.id()).id(), 1, true);
        var source = new ShipInstance("source", "source", "owner", 1000, 0, 0, Map.of())
                .withPowerState(new ShipPowerState(Map.of(), "rp1", "uranium", 20, false, 1, 1, 0, 0, 0, 0, 0));
        double mass = weapon.operationalStats().get("ammunitionMassKg");
        var state = market(weapon, 100, mass * 2, source);
        var bought = AuxiliaryWeaponResupply.buyBatteryAmmunition(state, "owner", battery, 1);
        assertTrue(bought.purchased()); assertEquals(100 - mass * 2, bought.state().empires().getFirst().treasuryCredits());
        assertEquals(mass, bought.state().commercialHubs().getFirst().activeOrders().get(weapon.ammunitionId()).supplyKg());
        assertEquals(100, state.empires().getFirst().treasuryCredits());
        assertFalse(AuxiliaryWeaponResupply.buyBatteryAmmunition(market(weapon, 0, mass * 2, source), "owner", battery, 1).purchased());
        var shortStock = market(weapon, 100, mass / 2, source);
        assertSame(shortStock, AuxiliaryWeaponResupply.buyBatteryAmmunition(shortStock, "owner", battery, 1).state());
        double charge = upkeep(weapon) + weapon.operationalStats().get("weaponSalvoKwh");
        var charged = AuxiliaryWeaponResupply.chargeBattery(bought.state(), "owner", bought.battery(), "source", charge);
        assertEquals(charge, charged.battery().stores().energyKwh()); assertEquals(20 - charge, charged.source().powerState().batteryChargeKwh());
        var fired = fight(null, charged.battery());
        assertEquals(1, fired.roundReports().stream().flatMap(r -> r.detailedActions().stream()).filter(a -> a.phase().equals("SURFACE_BATTERY")).count());
        assertTrue(fired.survivingPlanetaryBattery().stores().ammunitionKg().isEmpty());
        assertEquals(0, fired.survivingPlanetaryBattery().stores().energyKwh(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> AuxiliaryWeaponResupply.chargeBattery(state, "foreign", battery, "source", 5));
        assertThrows(IllegalArgumentException.class, () -> AuxiliaryWeaponResupply.chargeBattery(state, "owner", battery, "source", 21));
        assertEquals(20, source.powerState().batteryChargeKwh());
        var orbit = state.withFleets(List.of(state.fleets().getFirst().withLocation(FleetLocation.at(FleetLocation.Site.orbit("earth")))));
        assertThrows(IllegalArgumentException.class, () -> AuxiliaryWeaponResupply.chargeBattery(orbit, "owner", battery, "source", 5));
        assertEquals(bought.battery(), mapperRoundTrip(bought.battery()));
        var inactive = new PlanetaryDefenseBattery("battery", "earth", battery.weaponApplicationId(), 1, false)
                .withStores(charged.battery().stores());
        assertEquals(inactive, fight(null, inactive).survivingPlanetaryBattery());
    }

    private PlanetaryDefenseBattery mapperRoundTrip(PlanetaryDefenseBattery battery) {
        try { return mapper.readValue(mapper.writeValueAsString(battery), PlanetaryDefenseBattery.class); }
        catch (java.io.IOException e) { throw new AssertionError(e); }
    }

    @Test void wingLoadingRemovesPhysicalCarrierRoundsAndChargeAndRejectsAnotherCarrier() {
        var weapon = CombatModuleCatalog.module(CombatModuleCatalog.MASS_DRIVER);
        var wing = new CarrierWing("wing", "carrier", "INTERCEPTOR_FIGHTER", 1, 1, 100,
                CombatModuleCatalog.application(weapon.id()).id(), "CAPITAL_SHIPS");
        double mass = weapon.operationalStats().get("ammunitionMassKg");
        var carrier = new ShipInstance("carrier", "design", "owner", 1000, 0, 0, Map.of(weapon.ammunitionId(), mass))
                .withPowerState(new ShipPowerState(Map.of(), "rp1", "uranium", 20, false, 1, 1, 0, 0, 0, 0, 0));
        double charge = upkeep(weapon) + weapon.operationalStats().get("weaponSalvoKwh");
        var loaded = AuxiliaryWeaponResupply.loadWing(wing, carrier, 1, charge);
        assertEquals(mass, loaded.wing().stores().ammunitionKg().get(weapon.ammunitionId()));
        assertTrue(loaded.carrier().storedCargoKg().isEmpty()); assertEquals(20 - charge, loaded.carrier().powerState().batteryChargeKwh());
        var fired = fight(loaded.wing(), null);
        assertEquals(1, fired.roundReports().stream().flatMap(r -> r.detailedActions().stream()).filter(a -> a.phase().equals("STRIKE_WING")).count());
        assertTrue(fired.survivingAttackerWings().getFirst().stores().ammunitionKg().isEmpty());
        assertEquals(0, fired.survivingAttackerWings().getFirst().stores().energyKwh(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> AuxiliaryWeaponResupply.loadWing(loaded.wing(), loaded.carrier(), 1, 1));
        assertThrows(IllegalArgumentException.class, () -> AuxiliaryWeaponResupply.loadWing(wing,
                new ShipInstance("other", "design", "owner", 1000, 0, 0, Map.of()), 0, 0));
        assertEquals(mass, carrier.storedCargoKg().get(weapon.ammunitionId()));
    }
}
