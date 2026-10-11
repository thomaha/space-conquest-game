package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ShipElectricalDamageTest {
    private ShipDesign design(List<String> modules) {
        var power = ShipPowerProfile.capture(modules.stream().map(ShipComponentCatalog::module).toList());
        return new ShipDesign("design", "Electrical", "owner", ShipRole.COMBAT_SHIP, "steel", modules,
                "steel", 0, 1000, 10000, 1000, 100, 10, 0, 2000, false, false,
                ShipManufacturingProfile.baseline(), power);
    }
    private ShipInstance ship(String id, Map<Integer, Double> damage, double charge, Map<String, Double> cargo) {
        return new ShipInstance(id, "design", "owner", 1000, 0, 1000, cargo)
                .withPowerState(new ShipPowerState(Map.of("refined_uranium", 1.0), "rp1", "uranium", charge,
                        false, 1, 1, 0, 0, 0, 0, 0))
                .withModuleDamage(new ShipModuleDamage(damage));
    }
    private Fleet fleet(List<ShipInstance> ships) {
        return new Fleet("fleet", "Fleet", "owner", "system", "", 0, 0, 0, false, "PASSIVE", ships);
    }

    @Test void redundantGeneratorsRetainSurvivingOutputAndCapturedRatings() {
        var design = design(List.of(ShipComponentCatalog.FISSION_REACTOR_ID, ShipComponentCatalog.FISSION_REACTOR_ID));
        var ship = ship("ship", Map.of(0, 100.0, 1, 50.0), 0, Map.of());
        assertEquals(1000, design.powerProfile().fissionKw());
        assertEquals(250, design.powerProfile(ship).fissionKw());
        var supplied = ShipPowerProcessor.interval(design.powerProfile(ship), ship.powerState(), 1, 0, 200, 0, 0);
        assertTrue(supplied.supplied());
        assertEquals(200, supplied.generatedKwh(), 1e-9);
        assertEquals(1 - 200.0 / 6_000_000, supplied.state().generatorMaterialsKg().get("refined_uranium"), 1e-12);
        assertEquals(1, ship.generatorFuelMassKg());
    }

    @Test void batteryDamageDropsExcessEnergyAndRepairRestoresCapacityWithoutCharge() {
        var design = design(List.of(ShipComponentCatalog.BATTERY_ID, ShipComponentCatalog.BATTERY_ID));
        var damaged = ship("ship", Map.of(0, 100.0, 1, 50.0), 900, Map.of());
        var p = design.powerProfile(damaged);
        assertEquals(250, p.batteryKwh()); assertEquals(50, p.chargeKw()); assertEquals(100, p.dischargeKw());
        var clamped = ShipElectricalDamage.clampCharge(design, damaged);
        assertEquals(250, clamped.powerState().batteryChargeKwh());
        var limited = ShipPowerProcessor.interval(p, clamped.powerState(), 1, 0, 150, 0, 0);
        assertEquals(50, limited.unmetEssentialKwh(), 1e-9);
        assertEquals(150, limited.state().batteryChargeKwh(), 1e-9);
        var repaired = clamped.withModuleDamage(ShipModuleDamage.empty());
        assertEquals(1000, design.powerProfile(repaired).batteryKwh());
        assertEquals(250, ShipElectricalDamage.clampCharge(design, repaired).powerState().batteryChargeKwh());
    }

    @Test void solarHardwareConditionCombinesWithDeploymentAndExistingArrayCondition() {
        var design = design(List.of(ShipComponentCatalog.SOLAR_ARRAY_ID));
        var ship = ship("ship", Map.of(0, 50.0), 0, Map.of());
        var p = design.powerProfile(ship);
        var deployed = new ShipPowerState(Map.of(), "rp1", "uranium", 0, true, .8, .5, 0, 0, 0, 0, 0);
        assertEquals(24, ShipPowerProcessor.solarKw(p, deployed, new ShipSolarEnvironment(1, 24, 0, "Full sunlight")), 1e-9);
        assertEquals(0, ShipPowerProcessor.solarKw(p, ship.powerState(), new ShipSolarEnvironment(1, 24, 0, "Full sunlight")));
    }

    @Test void disabledElectricalSupplyBlocksFundedWeaponsAndShieldRecharge() {
        var design = design(List.of(ShipComponentCatalog.FISSION_REACTOR_ID, CombatModuleCatalog.GUN, CombatModuleCatalog.SHIELD));
        var healthy = ship("ship", Map.of(), 0, Map.of());
        assertTrue(ShipCombatPower.round(healthy, design, 1.0 / 60, true).firepower() > 0);
        var damaged = healthy.withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0)));
        var result = ShipCombatPower.round(damaged, design, 1.0 / 60, true);
        assertEquals(0, result.firepower()); assertEquals(0, result.paidWeaponKwh());
        assertEquals(0, result.ship().currentShieldHealth());
        assertTrue(result.ship().powerState().lastUnmetEssentialKwh() > 0);
        assertEquals(damaged.generatorFuelMassKg(), result.ship().generatorFuelMassKg());
    }

    @Test void powerOutageDropsSensorsAndAnotherPoweredWorkshopCanRepairTheGenerator() {
        var design = design(List.of(ShipComponentCatalog.FISSION_REACTOR_ID, SensorModuleCatalog.BASIC, ShipRepairModuleCatalog.WORKSHOP));
        var failed = ship("failed", Map.of(0, 100.0), 0, Map.of());
        var helper = ship("helper", Map.of(), 0, Map.of("steel", 60.0, "refined_copper", 24.0));
        var fleet = new Fleet("fleet", "Fleet", "owner", "system", "b", 0, 0, 0, false, "PASSIVE",
                List.of(failed, helper), FleetLocation.at(FleetLocation.Site.deepSpace()), Fleet.MODE_POWER_INTERRUPTED,
                10, 500_000_000, 1, 1, 1000, Map.of());
        var state = GameState.builder().shipDesigns(List.of(design)).fleets(List.of(fleet)).build();
        var powered = ShipPowerProcessor.advanceDay(state).getFirst();
        var recipient = powered.ships().getFirst();
        assertEquals(0, SensorModuleCatalog.profile(design, recipient).rangeKm());
        assertEquals(80, recipient.moduleDamage().damageByMount().get(0));
        assertEquals(100, design.powerProfile(recipient).fissionKw(), 1e-9);
        assertTrue(powered.ships().getLast().storedCargoKg().isEmpty());
        assertEquals(Fleet.MODE_POWER_INTERRUPTED, powered.interstellarMode());
        assertEquals(100, failed.moduleDamage().damageByMount().get(0));
    }

    @Test void generatorDamageReducesSustainedIonThrustAndCollateralIncludesElectricalHardware() {
        var design = design(List.of(ShipComponentCatalog.FISSION_REACTOR_ID, "mod_ion_drive", ShipComponentCatalog.BATTERY_ID));
        var healthy = ship("ship", Map.of(), 500, Map.of());
        var damaged = healthy.withModuleDamage(new ShipModuleDamage(Map.of(0, 90.0)));
        double full = ShipPowerProcessor.poweredThrust(design, healthy, ShipSolarEnvironment.DARK);
        double partial = ShipPowerProcessor.poweredThrust(design, damaged, ShipSolarEnvironment.DARK);
        assertTrue(partial > 0 && partial < full);
        assertEquals(0, ShipPowerProcessor.poweredThrust(design,
                healthy.withModuleDamage(new ShipModuleDamage(Map.of(0, 100.0))), ShipSolarEnvironment.DARK));
        var collateral = ShipModuleDamage.empty().hit(design, 120, null);
        assertEquals(.9, collateral.condition(0), 1e-12);
        assertEquals(.9, collateral.condition(2), 1e-12);
        assertFalse(ShipModuleDamage.damageableModule(ShipComponentCatalog.BATTERY_ID).materialCostsKg().isEmpty());
    }
    @Test void actualCombatDamagePersistsBatteryEnergyLossBeforeTheNextTick() {
        var target = design(List.of(ShipComponentCatalog.BATTERY_ID));
        var battery = ship("battery", Map.of(), 500, Map.of());
        var attacker = new ShipDesign("gun", "Gun", "owner", ShipRole.COMBAT_SHIP, "steel",
                List.of(CombatModuleCatalog.GUN), "steel", 0, 1000, 0, 1000, 100, 10, 0, 2000, false, false);
        var gun = new ShipInstance("gun", "gun", "owner", 10000, 0, 1000, Map.of());
        var result = new com.spaceconquest.engine.combat.TacticalCombatProcessor(new java.util.Random(42))
                .resolveFleetEngagementWithCarrierWings(fleet(List.of(gun)),
                        new Fleet("target", "Target", "enemy", "system", "", 0, 0, 0, false, "PASSIVE", List.of(battery)),
                        List.of(attacker, target), List.of(), List.of(), null, null);
        var survivor = result.survivingDefenderFleet().ships().getFirst();
        assertTrue(survivor.moduleDamage().condition(0) < 1);
        assertTrue(survivor.powerState().batteryChargeKwh() <= target.powerProfile(survivor).batteryKwh());
        double retained = survivor.powerState().batteryChargeKwh();
        assertEquals(retained, ShipElectricalDamage.clampCharge(target,
                survivor.withModuleDamage(ShipModuleDamage.empty())).powerState().batteryChargeKwh());
        assertEquals(500, battery.powerState().batteryChargeKwh());
    }
}
