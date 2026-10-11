package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.ShipCombatStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShipShieldRechargeTest {
    @TempDir Path directory;
    private ShipPowerProfile profile(double solar, double reactor, double battery, double output, double hotel, double cargo) {
        return new ShipPowerProfile(solar, 0, reactor, battery, output, output, 0, 10, hotel, 0, cargo, .65,
                Map.of("uranium", new ShipPowerProfile.Fuel("refined_uranium", null, 1, 6_000_000)));
    }
    private ShipDesign design(ShipPowerProfile profile, String... modules) {
        return new ShipDesign("design", "Design", "owner", ShipRole.COMBAT_SHIP, "steel", List.of(modules), "steel", 0,
                1000, 1000, 1000, 100, 1, 0, 1000, false, false, ShipManufacturingProfile.baseline(), profile);
    }
    private ShipInstance ship(double uranium, double battery, double shields, Map<Integer, Double> damage) {
        return new ShipInstance("ship", "design", "owner", 1000, shields, 100, Map.of("steel", 10.0))
                .withPowerState(new ShipPowerState(Map.of("refined_uranium", uranium), "rp1", "uranium", battery, true, 1, 1, 0, 0, 0, 0, 0))
                .withModuleDamage(new ShipModuleDamage(damage));
    }
    private GameState world(ShipDesign design, ShipInstance ship) {
        return GameState.builder().shipDesigns(List.of(design)).fleets(List.of(new Fleet("fleet", "Fleet", "owner", "sol", "", 0, 0, 0,
                false, "PASSIVE", List.of(ship)))).build();
    }
    private GameState day(GameState state) { return state.withFleets(ShipPowerProcessor.advanceDay(state)).withTurn(state.turn() + 1); }
    private ShipInstance ship(GameState state) { return state.fleets().getFirst().ships().getFirst(); }

    @Test void conserveDefersStationaryRechargeWithoutErasingChargeOrEmitterUpkeep() {
        var design = design(profile(0, 100, 0, 0, 32, 0), CombatModuleCatalog.SHIELD);
        var initial = world(design, ship(1, 0, 7, Map.of()));
        initial = initial.withFleets(List.of(initial.fleets().getFirst().withShieldPolicy(FleetShieldPolicy.Mode.CONSERVE_ENERGY)));
        var after = day(initial);
        assertEquals(7, ship(after).currentShieldHealth());
        assertEquals(1 - 32 * 24 / 6_000_000.0, ship(after).generatorFuelMassKg(), 1e-12);
        assertEquals(FleetShieldPolicy.Mode.CONSERVE_ENERGY, after.fleets().getFirst().shieldPolicy());
        assertEquals(7, ship(initial).currentShieldHealth());
    }

    @Test void reactorRechargePaysExactExtraEnergyAndFullShieldsStopTheExtraLoad() {
        var design = design(profile(0, 100, 0, 0, 32, 0), CombatModuleCatalog.SHIELD);
        var initial = world(design, ship(1, 0, 0, Map.of())); var after = day(initial);
        assertEquals(200, ship(after).currentShieldHealth(), 1e-9);
        assertEquals(1 - (32 * 24 + 200 * ShipShieldRecharge.KWH_PER_POINT) / 6_000_000, ship(after).generatorFuelMassKg(), 1e-12);
        assertEquals(200, ShipCombatStats.of(design, ship(after)).shields());
        assertEquals(ship(initial).storedCargoKg(), ship(after).storedCargoKg()); assertEquals(100, ship(after).currentFuelKg());
        assertEquals(ship(initial).moduleDamage(), ship(after).moduleDamage()); assertEquals(0, ship(initial).currentShieldHealth());
        var full = day(after);
        assertEquals(ship(after).generatorFuelMassKg() - 32 * 24 / 6_000_000.0, ship(full).generatorFuelMassKg(), 1e-12);
        assertEquals(200, ship(full).currentShieldHealth());
    }

    @Test void outputShortfallBoundsRechargeWithoutInventingEssentialOrCargoOutages() {
        var design = design(profile(0, 40, 0, 0, 32, 10), CombatModuleCatalog.SHIELD);
        // Cargo upkeep retains priority over shield recharge.
        var initial = world(design, ship(1, 0, 0, Map.of())); var after = day(initial);
        double cargo = ShipPowerProcessor.cargoKw(design.powerProfile(), ship(initial), design);
        double charge = (40 - 32 - cargo) * 24 / ShipShieldRecharge.KWH_PER_POINT;
        assertEquals(charge, ship(after).currentShieldHealth(), 1e-9);
        assertEquals(1 - 40 * 24 / 6_000_000.0, ship(after).generatorFuelMassKg(), 1e-12);
        assertEquals(0, ship(after).powerState().lastUnmetEssentialKwh()); assertEquals(0, ship(after).powerState().unmetCargoKwh());
        assertEquals(0, ship(after).powerState().unmetDriveKwh()); assertEquals(ship(initial).storedCargoKg(), ship(after).storedCargoKg());
    }

    @Test void damagedDuplicateEmittersLimitCapacityAndDisabledOrDestroyedHardwareCannotRecharge() {
        var design = design(profile(0, 100, 0, 0, 62, 0), CombatModuleCatalog.SHIELD, CombatModuleCatalog.SHIELD);
        var after = day(world(design, ship(1, 0, 0, Map.of(0, 50.0))));
        assertEquals(300, ship(after).currentShieldHealth());
        var disabled = day(world(design, ship(1, 0, 400, Map.of(0, 100.0, 1, 100.0))));
        assertEquals(0, ship(disabled).currentShieldHealth());
        assertEquals(1 - 2 * 24 / 6_000_000.0, ship(disabled).generatorFuelMassKg(), 1e-12);
        var old = ship(1, 0, 100, Map.of());
        var dead = new ShipInstance(old.id(), old.designId(), old.ownerEntityId(), 0, 100, 100, old.storedCargoKg())
                .withPowerState(old.powerState());
        assertEquals(0, ship(day(world(design, dead))).currentShieldHealth());
    }

    @Test void finiteBatteryRechargePreservesTheEssentialReserveAndSpendsExactlyItsDeliveredEnergy() {
        var design = design(profile(0, 0, 3000, 60, 32, 0), CombatModuleCatalog.SHIELD);
        var initial = world(design, ship(0, 2500, 0, Map.of())); var after = day(initial); var charged = ship(after);
        assertTrue(charged.currentShieldHealth() > 97.9 && charged.currentShieldHealth() <= 98.000001);
        assertEquals(2500 - 32 * 24 - charged.currentShieldHealth() * ShipShieldRecharge.KWH_PER_POINT,
                charged.powerState().batteryChargeKwh(), 1e-9);
        assertTrue(charged.powerState().batteryChargeKwh() >= 32 * 48 - 1e-6);
        var reserveOnly = day(world(design, ship(0, 32 * 72, 0, Map.of())));
        assertEquals(0, ship(reserveOnly).currentShieldHealth());
    }

    @Test void powerShortagesAndLegacyOrAbsentEmittersDoNotGrantCharge() {
        var design = design(profile(0, 0, 3000, 60, 32, 0), CombatModuleCatalog.SHIELD);
        var shortage = day(world(design, ship(0, 10, 0, Map.of())));
        assertEquals(0, ship(shortage).currentShieldHealth()); assertTrue(ship(shortage).powerState().lastUnmetEssentialKwh() > 0);
        var noEmitter = design(profile(0, 100, 0, 0, 2, 0), CombatModuleCatalog.GUN);
        assertEquals(0, ship(day(world(noEmitter, ship(1, 0, 0, Map.of())))).currentShieldHealth());
        var legacy = design(null, CombatModuleCatalog.SHIELD);
        assertEquals(0, ship(day(world(legacy, ship(1, 0, 0, Map.of())))).currentShieldHealth());
        var uncaptured = design(profile(0, 100, 0, 0, 2, 0), CombatModuleCatalog.SHIELD);
        assertEquals(0, ship(day(world(uncaptured, ship(1, 0, 0, Map.of())))).currentShieldHealth());
    }

    @Test void solarRechargeUsesRealIncidentEnergyAndStowedArraysCannotSupplyIt() {
        var design = design(profile(120, 0, 1000, 100, 32, 0), CombatModuleCatalog.SHIELD);
        var initial = world(design, ship(0, 500, 0, Map.of()));
        var earth = new Planet("earth", "Earth", "", 5.972e24, 9.81, ShipSolarEnvironment.AU_KM, 0, 12000,
                "terrestrial", "air", true, 0, List.of(), List.of(), List.of());
        var system = new SolarSystem("sol", "Sol", "", 0, 0, 0, 1.989e30, 1_392_700, "yellow", List.of(earth), List.of());
        initial = initial.toBuilder().solarSystems(List.of(system)).build();
        var after = day(initial);
        assertEquals(200, ship(after).currentShieldHealth(), 1e-9); assertEquals(0, ship(after).generatorFuelMassKg());
        assertEquals(1000, ship(after).powerState().batteryChargeKwh(), 1e-9);
        double flux = ShipSolarEnvironment.at(initial, initial.fleets().getFirst(), initial.fleets().getFirst().location().current()).fluxRelativeToEarth();
        var limitedDesign = design(profile(45 / flux, 0, 1000, 100, 32, 0), CombatModuleCatalog.SHIELD);
        var limited = day(initial.withShipDesigns(List.of(limitedDesign)));
        assertEquals(200, ship(limited).currentShieldHealth());
        // 1,080 solar kWh fund 768 essential kWh and 400 shield kWh; the battery supplies the remaining 88.
        assertEquals(412, ship(limited).powerState().batteryChargeKwh(), 1e-9);
        var old = ship(initial).powerState();
        var stowed = new ShipPowerState(old.generatorMaterialsKg(), old.chemicalMixture(), old.reactorFuel(), old.batteryChargeKwh(),
                false, old.arrayCondition(), old.orientationFraction(), 0, 0, 0, 0, 0);
        assertEquals(0, ship(day(initial.withFleets(List.of(initial.fleets().getFirst().withShips(List.of(ship(initial).withPowerState(stowed)))))))
                .currentShieldHealth());
    }

    @Test void quietTravelDefersRechargeAndKeepsItsFundedPowerBudget() {
        var design = design(profile(0, 100, 0, 0, 32, 0), CombatModuleCatalog.SHIELD);
        var initial = world(design, ship(1, 0, 0, Map.of()));
        var moving = initial.fleets().getFirst().withLocation(initial.fleets().getFirst().location().depart(FleetLocation.Site.orbit("earth"), 2));
        var after = day(initial.withFleets(List.of(moving)));
        assertEquals(0, ship(after).currentShieldHealth());
        assertEquals(1 - 32 * 24 / 6_000_000.0, ship(after).generatorFuelMassKg(), 1e-12);
    }

    @Test void partialRechargeSurvivesSaveLoadAndTheRealEngineFleetPhase() throws Exception {
        var design = design(profile(0, 40, 0, 0, 32, 0), CombatModuleCatalog.SHIELD);
        var partial = day(world(design, ship(1, 0, 0, Map.of()))); assertEquals(96, ship(partial).currentShieldHealth());
        var manager = new SaveGameManager(directory); var file = directory.resolve("shield-recharge.scsave").toFile();
        manager.save(file, partial, 1, "2027-01-01T08:00:00"); var restored = manager.load(file).toGameState(partial.turn(), "RUNNING");
        assertEquals(day(partial).fleets(), day(restored).fleets());
        var engine = new SpaceConquestEngine(); engine.applyGameState(restored);
        var phase = SpaceConquestEngine.class.getDeclaredMethod("updateFleets"); phase.setAccessible(true); phase.invoke(engine);
        assertEquals(192, ship(engine.getGameState()).currentShieldHealth());
        assertEquals(ship(day(restored)).powerState(), ship(engine.getGameState()).powerState());
    }
}
