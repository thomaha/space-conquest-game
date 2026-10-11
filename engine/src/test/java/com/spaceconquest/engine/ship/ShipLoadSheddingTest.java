package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.ShipCombatStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ShipLoadSheddingTest {
    @TempDir Path directory;
    private ShipDesign design() {
        var modules = List.of(ShipComponentCatalog.FISSION_REACTOR_ID, SensorModuleCatalog.BASIC,
                ShipRepairModuleCatalog.WORKSHOP, CombatModuleCatalog.GUN, CombatModuleCatalog.SHIELD,
                ShipComponentCatalog.BATTERY_ID, "mod_cargo_vault");
        return new ShipDesign("design", "Design", "owner", ShipRole.COMBAT_SHIP, "steel", modules,
                "steel", 0, 1000, 10000, 1000, 100, 10, 0, 2000, false, false,
                ShipManufacturingProfile.baseline(), ShipPowerProfile.capture(modules.stream().map(ShipComponentCatalog::module).toList()));
    }
    private ShipInstance ship(String id, double reactorDamage, double charge, Map<String, Double> cargo) {
        return new ShipInstance(id, "design", "owner", 1000, 100, 1000, cargo)
                .withModuleDamage(new ShipModuleDamage(Map.of(0, reactorDamage)))
                .withPowerState(new ShipPowerState(Map.of("refined_uranium", 1.0), "rp1", "uranium", charge,
                        false, 1, 1, 0, 0, 0, 0, 0));
    }
    private GameState world(ShipDesign design, ShipInstance... ships) {
        var fleet = new Fleet("fleet", "Fleet", "owner", "system", "", 0, 0, 0, false, "PASSIVE", List.of(ships));
        return GameState.builder().shipDesigns(List.of(design)).fleets(List.of(fleet)).build();
    }
    private GameState day(GameState state) { return state.withFleets(ShipPowerProcessor.advanceDay(state)); }

    @Test void cargoAndLifeSupportSurviveWhileUnfundedWorkshopsAndShieldsStop() {
        var design = design(); var source = ship("ship", 96, 0, Map.of());
        var state = day(world(design, source)); var actual = state.fleets().getFirst().ships().getFirst();
        assertEquals(Set.of(ShipServicePower.WORKSHOPS, ShipServicePower.SHIELDS), actual.powerState().shedServices());
        assertEquals(0, actual.powerState().lastUnmetEssentialKwh()); assertEquals(0, actual.powerState().unmetCargoKwh());
        assertEquals(1 - 17 * 24.0 / 6_000_000, actual.generatorFuelMassKg(), 1e-12);
        assertEquals(100, actual.currentShieldHealth()); assertEquals(0, ShipCombatStats.of(design, actual).shields());
        assertTrue(ShipCombatStats.of(design, actual).firepower() > 0); assertTrue(SensorModuleCatalog.profile(design, actual).rangeKm() > 0);
        assertEquals(0, ShipRepairProcessor.capacity(state, state.fleets().getFirst(), actual));
        assertTrue(FleetRepairStatus.inspect(state, state.fleets().getFirst()).providers().getFirst().blockers()
                .contains("Workshop switched off for power shortage"));
        assertThrows(UnsupportedOperationException.class, () -> actual.powerState().shedServices().clear());
        assertEquals(Set.of(), source.powerState().shedServices()); assertEquals(1, source.generatorFuelMassKg());
    }

    @Test void scheduledDriveGetsPowerBeforeSensorsAndUnfundedWeaponsCannotFire() {
        var design = design(); var source = ship("ship", 96, 0, Map.of()); var p = design.powerProfile(source);
        var interval = ShipPowerProcessor.interval(p, source.powerState(), 1, 0, p.essentialKw(source, design), 3, 14);
        assertTrue(interval.supplied()); assertEquals(Double.POSITIVE_INFINITY, interval.firstUnpoweredHour());
        assertEquals(Set.copyOf(ShipServicePower.PRIORITY), interval.state().shedServices());
        var actual = source.withPowerState(interval.state());
        assertEquals(0, SensorModuleCatalog.profile(design, actual).rangeKm()); assertEquals(0, ShipCombatStats.of(design, actual).firepower());
        var weaker = source.withModuleDamage(new ShipModuleDamage(Map.of(0, 98.8)));
        var combat = ShipCombatPower.round(weaker, design, 1.0 / 60, true);
        assertEquals(0, combat.firepower()); assertEquals(0, combat.paidWeaponKwh());
        assertEquals(0, combat.ship().powerState().lastUnmetEssentialKwh()); assertEquals(100, combat.ship().currentShieldHealth());
        assertTrue(combat.ship().powerState().shedServices().contains(ShipServicePower.WEAPONS));
    }

    @Test void finiteBatteryExhaustionShedsServicesWithoutStoppingRequiredPower() {
        var design = design(); var source = ship("ship", 95, 5, Map.of()); var p = design.powerProfile(source);
        var interval = ShipPowerProcessor.interval(p, source.powerState(), 24, 0, p.essentialKw(source, design), 3, 0);
        assertTrue(interval.supplied()); assertEquals(0, interval.state().batteryChargeKwh(), 1e-9);
        assertEquals(Set.of(ShipServicePower.WORKSHOPS, ShipServicePower.SHIELDS), interval.state().shedServices());
        double first = 5.0 / (67 - 25); // Full equipment and cargo draw until the battery is exhausted.
        assertEquals(1 - (25 * first + 17 * (24 - first)) / 6_000_000, interval.state().fuelMassKg(), 1e-12);
        assertEquals(Double.POSITIVE_INFINITY, interval.firstUnpoweredHour());
    }

    @Test void passengerLifeSupportCannotBeSwitchedOffToKeepEquipmentWorking() {
        var design = design(); var source = ship("ship", 96, 0, Map.of());
        var booked = new ShipInstance(source.id(), source.designId(), source.ownerEntityId(), 1000, 100, 1000,
                Map.of(), 10, "human", "NORMAL", source.powerState(), source.supplyState(), source.moduleDamage());
        var p = design.powerProfile(booked);
        assertEquals(12, p.criticalKw(p.essentialKw(booked, design)));
        var funded = ShipPowerProcessor.interval(p, booked.powerState(), 1, 0, p.essentialKw(booked, design), 3, 0);
        assertTrue(funded.supplied()); assertTrue(funded.state().shedServices().contains(ShipServicePower.SENSORS));
        assertEquals(1 - 17.0 / 6_000_000, funded.state().fuelMassKg(), 1e-12);
        var weaker = booked.withModuleDamage(new ShipModuleDamage(Map.of(0, 98.0)));
        var insufficient = ShipPowerProcessor.interval(design.powerProfile(weaker), weaker.powerState(), 1, 0,
                p.essentialKw(weaker, design), 3, 0);
        assertEquals(2, insufficient.unmetEssentialKwh(), 1e-7); assertEquals(3, insufficient.unmetCargoKwh(), 1e-7);
        assertEquals(Set.copyOf(ShipServicePower.PRIORITY), insufficient.state().shedServices());
    }

    @Test void paidHelperRepairRestoresServicesOnlyAfterTheirNextFundedTickAndPersists() throws Exception {
        var design = design(); var source = ship("source", 96, 0, Map.of());
        var helper = ship("helper", 0, 0, Map.of("steel", 60.0, "refined_copper", 24.0));
        var partial = day(world(design, source, helper)); var target = partial.fleets().getFirst().ships().getFirst();
        assertEquals(76, target.moduleDamage().damageByMount().get(0));
        assertTrue(target.powerState().shedServices().contains(ShipServicePower.WORKSHOPS));
        assertTrue(partial.fleets().getFirst().ships().getLast().storedCargoKg().isEmpty());
        var manager = new SaveGameManager(directory); var file = directory.resolve("shedding.scsave").toFile();
        manager.save(file, partial, 1, "2027-01-01T08:00:00");
        var restored = manager.load(file).toGameState(partial.turn(), "RUNNING");
        assertEquals(partial.fleets(), restored.fleets()); assertEquals(day(partial).fleets(), day(restored).fleets());
        var next = day(restored).fleets().getFirst().ships().getFirst();
        assertTrue(next.powerState().shedServices().isEmpty()); assertTrue(next.currentShieldHealth() > target.currentShieldHealth());
        assertTrue(ShipCombatStats.of(design, next).shields() > 0);
    }

    @Test void olderRecordsDefaultToNoRecordedSheddingAndCopiesRetainCurrentStatus() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var source = ship("ship", 96, 0, Map.of()); var p = design().powerProfile(source);
        var power = ShipPowerProcessor.interval(p, source.powerState(), 1, 0, p.essentialKw(source, design()), 3, 0).state();
        var json = mapper.valueToTree(power); ((com.fasterxml.jackson.databind.node.ObjectNode) json).remove("shedServices");
        assertTrue(mapper.treeToValue(json, ShipPowerState.class).shedServices().isEmpty());
        assertEquals(power.shedServices(), power.withChargeInputToday(0).shedServices());
        assertEquals(power.shedServices(), power.resetPassengerOutage().shedServices());
        var oldProfile = mapper.valueToTree(p); ((com.fasterxml.jackson.databind.node.ObjectNode) oldProfile).remove("serviceLoadsKw");
        assertTrue(mapper.treeToValue(oldProfile, ShipPowerProfile.class).serviceLoadsKw().isEmpty());
    }
}
