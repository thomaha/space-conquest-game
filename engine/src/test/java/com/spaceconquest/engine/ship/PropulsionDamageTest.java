package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import com.spaceconquest.engine.combat.TacticalCombatProcessor;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PropulsionDamageTest {
    private static final double DISTANCE = 500_000_000, PEAK = 1000;
    private ShipDesign design(boolean workshop) {
        var modules = new ArrayList<>(List.of("mod_chemical_rocket"));
        if (workshop) modules.add(ShipRepairModuleCatalog.WORKSHOP);
        var power = new ShipPowerProfile(0, 0, 100, 0, 0, 0, 15000, 0, workshop ? 22 : 2, 20, 0, .65,
                Map.of("uranium", new ShipPowerProfile.Fuel("refined_uranium", null, 1, 6000)));
        return new ShipDesign("design", "Rocket", "owner", ShipRole.COMBAT_SHIP, "steel", modules, "steel", 0,
                1000, 10000, 1000, 100, 10, 0, 2000, false, false, ShipManufacturingProfile.baseline(), power);
    }
    private ShipInstance ship(ShipDesign design, double damage, Map<String, Double> cargo) {
        return new ShipInstance("ship", design.id(), "owner", 1000, 0, 1000, cargo)
                .withPowerState(new ShipPowerState(Map.of("refined_uranium", 1.0), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0))
                .withModuleDamage(new ShipModuleDamage(Map.of(0, damage)));
    }
    private GameState state(ShipDesign design, Fleet fleet) {
        return GameState.builder().shipDesigns(List.of(design)).fleets(List.of(fleet)).solarSystems(List.of(
                new SolarSystem("a", "A", "", 0, 0, 0, 1, 1, "yellow", List.of(), List.of()),
                new SolarSystem("b", "B", "", 1, 0, 0, 1, 1, "yellow", List.of(), List.of()))).build();
    }
    private Fleet crossing(ShipInstance ship, double seconds) {
        return new Fleet("fleet", "Fleet", "owner", "a", "b", 0, 0,
                InterstellarTravel.progress(DISTANCE, 1, PEAK, seconds), false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.deepSpace()), Fleet.MODE_SUBLIGHT,
                InterstellarTravel.travelSeconds(DISTANCE, 1, PEAK) / 86400, DISTANCE, 1, seconds / 86400, PEAK, Map.of("ship", 100.0));
    }
    private Fleet tick(GameState state) {
        return new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()).getFirst();
    }

    @Test void mainDriveConditionScalesCapturedThrustAndBlocksNewJourneysWhenDisabled() {
        var design = design(false); var full = ship(design, 0, Map.of()); var damaged = ship(design, 50, Map.of());
        assertEquals(2000, ShipPowerProcessor.poweredThrust(design, full, ShipSolarEnvironment.DARK));
        assertEquals(1000, ShipPowerProcessor.poweredThrust(design, damaged, ShipSolarEnvironment.DARK));
        var own = new Fleet("fleet", "Fleet", "owner", "a", "", 0, 0, 0, false, "PASSIVE", List.of(full));
        var world = state(design, own);
        var healthy = InterstellarTravel.plan(world, own, "b");
        var partial = InterstellarTravel.plan(world, own.withShips(List.of(damaged)), "b");
        assertNotNull(healthy); assertNotNull(partial);
        assertEquals(healthy.accelerationMps2() / 2, partial.accelerationMps2(), 1e-9);
        assertNull(InterstellarTravel.plan(world, own.withShips(List.of(ship(design, 100, Map.of()))), "b"));
        assertEquals(2000, design.totalThrustN());
    }

    @Test void ionDamageScalesElectricallyLimitedThrustRatherThanInventingRatedOutput() {
        var power = new ShipPowerProfile(0, 0, 100, 0, 0, 0, 15000, 0, 2, 120, 0, .65,
                Map.of("uranium", new ShipPowerProfile.Fuel("refined_uranium", null, 1, 6000)));
        var design = new ShipDesign("design", "Ion", "owner", ShipRole.COMBAT_SHIP, "steel",
                List.of("mod_ion_drive"), "steel", 0, 1000, 10000, 1000, 100, 10, 0, 2000,
                false, false, ShipManufacturingProfile.baseline(), power);
        var healthy = ship(design, 0, Map.of()); var damaged = ship(design, 50, Map.of());
        double limited = ShipPowerProcessor.poweredThrust(design, healthy, ShipSolarEnvironment.DARK);
        assertTrue(limited > 0 && limited < design.totalThrustN());
        assertEquals(limited / 2, ShipPowerProcessor.poweredThrust(design, damaged, ShipSolarEnvironment.DARK), 1e-12);
        assertEquals(0, ShipPowerProcessor.poweredThrust(design,
                damaged.withPowerState(ShipPowerState.empty()), ShipSolarEnvironment.DARK));
        assertEquals(0, ShipPowerProcessor.poweredThrust(design, ship(design, 100, Map.of()), ShipSolarEnvironment.DARK));
    }
    @Test void damagedAccelerationStopsImmediatelyRetainsFuelAndPaysDriftUpkeep() {
        for (double damage : List.of(50.0, 100.0)) {
            var design = design(false); var original = crossing(ship(design, damage, Map.of()), 100);
            var stopped = tick(state(design, original));
            assertEquals(Fleet.MODE_POWER_INTERRUPTED, stopped.interstellarMode());
            assertEquals(100, stopped.flightMotion().velocityMps(), 1e-9);
            assertEquals(5000 + 100 * 86400, stopped.flightMotion().positionMeters(), 1e-6);
            assertEquals(1000, stopped.ships().getFirst().currentFuelKg());
            assertEquals(1 - 48.0 / 6000, stopped.ships().getFirst().generatorFuelMassKg(), 1e-9);
            assertEquals(original.interstellarFuelBudgetKg(), stopped.interstellarFuelBudgetKg());
        }
    }

    @Test void disabledDriveCoastsUntilTheNextRequiredBrakeAndSupplyCannotRepairIt() {
        var design = design(false); var original = crossing(ship(design, 100, Map.of()), 490000);
        var world = state(design, original);
        assertEquals(10000.0 / 3600, PropulsionDamage.availableHours(world, original, 24), 1e-9);
        var stopped = tick(world);
        assertEquals(1000, stopped.flightMotion().velocityMps(), 1e-9);
        assertEquals(InterstellarTravel.progress(DISTANCE, 1, PEAK, 490000) * DISTANCE + 1000 * 86400,
                stopped.flightMotion().positionMeters(), 1e-6);
        assertEquals(1000, stopped.ships().getFirst().currentFuelKg());
        assertNull(FlightRecovery.plan(state(design, stopped), stopped));
        assertEquals(0, PropulsionDamage.thrust(design, stopped.ships().getFirst()));
    }

    @Test void damagedLocalVectorBurnPreservesActualPositionVelocityAndSpentFuel() {
        var design = design(false); var ship = ship(design, 50, Map.of());
        var zero = new LocalSiteGeometry.Point(0, 0, 0);
        var trajectory = new VectorFlight.Trajectory(zero, zero, new LocalSiteGeometry.Point(1, 0, 0),
                new LocalSiteGeometry.Point(-1, 0, 0), 1000, 498000);
        var vector = trajectory.at(100); var destination = trajectory.at(trajectory.totalSeconds()).position();
        var geometry = new LocalSiteGeometry.Leg(zero, destination, VectorFlight.norm(destination));
        var commitment = JourneyPropulsion.capture(ship, PropulsionCatalog.drive("mod_chemical_rocket"), null, 100);
        var flight = new LocalFlight(geometry, VectorFlight.motion(vector, geometry), Map.of(ship.id(), 100.0),
                Map.of(ship.id(), commitment), false, false, null, vector);
        var location = new FleetLocation(FleetLocation.Site.deepSpace(), FleetLocation.Site.orbit("unknown"),
                .01, trajectory.totalSeconds() / 86400, flight);
        var fleet = new Fleet("fleet", "Fleet", "owner", "a", "", 0, 0, 0, false, "PASSIVE", List.of(ship), location);
        var stopped = tick(state(design, fleet)); var motion = stopped.location().localFlight();
        assertTrue(motion.interrupted());
        assertEquals(VectorFlight.add(vector.position(), VectorFlight.scale(vector.velocity(), 86400)), motion.vectorFlight().position());
        assertEquals(vector.velocity(), motion.vectorFlight().velocity());
        assertEquals(ship.currentFuelKg(), stopped.ships().getFirst().currentFuelKg());
        assertEquals(1 - 48.0 / 6000, stopped.ships().getFirst().generatorFuelMassKg(), 1e-9);
        assertFalse(stopped.location().isAt(location.destination()));
    }

    @Test void paidOnboardRepairInMechanicalDriftRestoresThrustWithoutRestoringTheOldTrajectory() {
        var design = design(true); var original = crossing(ship(design, 100, Map.of("steel", 80.0, "refined_copper", 16.0)), 100);
        var stopped = tick(state(design, original)); var repaired = stopped.ships().getFirst();
        assertEquals(Fleet.MODE_POWER_INTERRUPTED, stopped.interstellarMode());
        assertEquals(80, repaired.moduleDamage().damageByMount().get(0));
        assertTrue(repaired.storedCargoKg().isEmpty());
        assertEquals(400, PropulsionDamage.thrust(design, repaired), 1e-9);
        assertNotNull(FlightRecovery.plan(state(design, stopped), stopped));
        assertEquals(1000, repaired.currentFuelKg());
        assertEquals(100, stopped.flightMotion().velocityMps());
        assertEquals(80, original.ships().getFirst().storedCargoKg().get("steel"));
    }

    @Test void engineTargetingDamagesOnlyDriveMountsAndGeneralHitsIncludeDrives() {
        var attacker = new ShipDesign("gun", "Gun", "owner", ShipRole.COMBAT_SHIP, "steel", List.of(CombatModuleCatalog.GUN),
                "steel", 0, 1000, 0, 1000, 100, 10, 0, 2000, false, false);
        var target = new ShipDesign("target", "Target", "enemy", ShipRole.COMBAT_SHIP, "steel",
                List.of("mod_fusion_drive", CombatModuleCatalog.GUN, SensorModuleCatalog.BASIC, ShipRepairModuleCatalog.WORKSHOP),
                "steel", 0, 1000, 0, 1000, 100, 10, 0, 2000, false, false);
        var first = new Fleet("a", "A", "owner", "a", "", 0, 0, 0, false, "PASSIVE",
                List.of(new ShipInstance("gun", "gun", "owner", 10000, 0, 1000, Map.of())));
        var last = new Fleet("d", "D", "enemy", "a", "", 0, 0, 0, false, TacticalCombatProcessor.STANCE_EVASIVE_RETREAT,
                List.of(new ShipInstance("target", "target", "enemy", 10000, 0, 1000, Map.of())));
        var result = new TacticalCombatProcessor(new Random(42)).resolveFleetEngagementWithCarrierWings(first, last,
                List.of(attacker, target), List.of(), List.of(), null, TacticalCombatProcessor.TARGET_SUBSYSTEM_ENGINES);
        var survivor = result.survivingDefenderFleet().ships().getFirst();
        assertEquals(Map.of(0, 100.0), survivor.moduleDamage().damageByMount());
        assertEquals(0, PropulsionDamage.thrust(target, survivor));
        var collateral = ShipModuleDamage.empty().hit(target, 80, null);
        assertEquals(.95, collateral.condition(0));
        assertEquals(.95, collateral.condition(1));
    }
}
