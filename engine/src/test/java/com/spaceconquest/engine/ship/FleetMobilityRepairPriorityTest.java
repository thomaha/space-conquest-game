package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FleetMobilityRepairPriorityTest {
    private ShipDesign design() {
        var modules = List.of(CombatModuleCatalog.GUN, SensorModuleCatalog.BASIC, "mod_chemical_rocket",
                ShipRepairModuleCatalog.WORKSHOP, ShipComponentCatalog.FISSION_REACTOR_ID);
        return new ShipDesign("design", "Design", "owner", ShipRole.COMBAT_SHIP, "steel", modules, "steel", 0,
                1000, 10000, 1000, 100, 10, 0, 2000, false, false, ShipManufacturingProfile.baseline(),
                ShipPowerProfile.capture(modules.stream().map(ShipComponentCatalog::module).toList()));
    }
    private ShipInstance ship(String id, Map<Integer, Double> damage, Map<String, Double> cargo) {
        return new ShipInstance(id, "design", "owner", 1000, 0, 1000, cargo)
                .withModuleDamage(new ShipModuleDamage(damage))
                .withPowerState(new ShipPowerState(Map.of("refined_uranium", 1.0), "rp1", "uranium", 0,
                        false, 1, 1, 0, 0, 0, 0, 0));
    }
    private Fleet drifting(List<ShipInstance> ships) {
        var fleet = new Fleet("fleet", "Fleet", "owner", "a", "b", 0, 0, 0, false, "PASSIVE", ships,
                FleetLocation.at(FleetLocation.Site.deepSpace()), Fleet.MODE_SUBLIGHT,
                InterstellarTravel.travelSeconds(500_000_000, 1, 1000) / 86400,
                500_000_000, 1, 100.0 / 86400, 1000, Map.of());
        return FlightRecovery.advance(fleet, 0);
    }
    private GameState state(Fleet fleet) {
        return GameState.builder().shipDesigns(List.of(design())).fleets(List.of(fleet)).solarSystems(List.of(
                new SolarSystem("a", "A", "", 0, 0, 0, 1, 1, "yellow", List.of(), List.of()),
                new SolarSystem("b", "B", "", 1, 0, 0, 1, 1, "yellow", List.of(), List.of()))).build();
    }

    @Test void driveRepairUnlocksFundedRecoveryAndPrecedesWeaponsWithoutRestartingMotion() {
        var target = ship("target", Map.of(0, 20.0, 1, 100.0, 2, 100.0), Map.of());
        var helper = ship("helper", Map.of(), Map.of("steel", 80.0, "refined_copper", 16.0));
        var fleet = drifting(List.of(target, helper)); var initial = state(fleet);
        assertFalse(FlightRecoveryReadiness.check(initial, fleet).ready());
        var context = ShipRepairPriority.context(initial, fleet);
        assertEquals(Set.of(2), context.mobility().get("target"));
        assertEquals(2, ShipRepairPriority.mounts(context, design(), target).getFirst());
        assertThrows(UnsupportedOperationException.class, () -> context.mobility().get("target").add(0));
        var repaired = ShipRepairProcessor.advanceDay(initial, initial.fleets()).getFirst();
        assertEquals(80, repaired.ships().getFirst().moduleDamage().damageByMount().get(2));
        assertEquals(20, repaired.ships().getFirst().moduleDamage().damageByMount().get(0));
        assertTrue(repaired.ships().getLast().storedCargoKg().isEmpty());
        assertTrue(FlightRecoveryReadiness.check(state(repaired), repaired).ready());
        assertEquals(fleet.flightMotion(), repaired.flightMotion());
        assertEquals(Fleet.MODE_POWER_INTERRUPTED, repaired.interstellarMode());
        assertEquals(1000, repaired.ships().getFirst().currentFuelKg());
        assertEquals(100, target.moduleDamage().damageByMount().get(2));
        assertEquals(1, target.generatorFuelMassKg());
    }

    @Test void emptyFuelOvershootAndAnotherDisabledFleetMemberDoNotPromiseRecovery() {
        var target = ship("target", Map.of(0, 20.0, 2, 100.0), Map.of());
        var fleet = drifting(List.of(target));
        var dry = fleet.withShips(List.of(new ShipInstance(target.id(), target.designId(), target.ownerEntityId(),
                target.currentHullHealth(), 0, 0, Map.of()).withPowerState(target.powerState()).withModuleDamage(target.moduleDamage())));
        assertTrue(ShipRepairPriority.context(state(dry), dry).mobility().isEmpty());
        var overshot = FleetPropulsionSupply.motion(fleet, Fleet.MODE_POWER_INTERRUPTED,
                new FlightMotion(600_000_000, 100, null, 0), Map.of(), Map.of());
        assertTrue(ShipRepairPriority.context(state(overshot), overshot).mobility().isEmpty());
        var blocked = drifting(List.of(target, ship("second", Map.of(2, 100.0), Map.of())));
        assertTrue(ShipRepairPriority.context(state(blocked), blocked).mobility().isEmpty());
    }

    @Test void usefulPowerRestorationPrecedesDriveWorkAndNoMaterialsMeansNoFreeMobility() {
        var target = ship("target", Map.of(0, 20.0, 2, 100.0, 4, 100.0), Map.of());
        var helper = ship("helper", Map.of(), Map.of("steel", 140.0, "refined_copper", 40.0));
        var fleet = drifting(List.of(target, helper)); var initial = state(fleet);
        assertEquals(4, ShipRepairPriority.mounts(ShipRepairPriority.context(initial, fleet), design(), target).getFirst());
        var powered = ShipPowerProcessor.advanceDay(initial).getFirst();
        assertEquals(80, powered.ships().getFirst().moduleDamage().damageByMount().get(4));
        assertEquals(100, powered.ships().getFirst().moduleDamage().damageByMount().get(2));
        var mobile = ShipPowerProcessor.advanceDay(state(powered)).getFirst();
        assertEquals(80, mobile.ships().getFirst().moduleDamage().damageByMount().get(2));
        assertEquals(20, mobile.ships().getFirst().moduleDamage().damageByMount().get(0));
        assertEquals(Fleet.MODE_POWER_INTERRUPTED, mobile.interstellarMode());
        var unfunded = drifting(List.of(ship("target", Map.of(0, 20.0, 2, 100.0), Map.of()),
                ship("helper", Map.of(), Map.of("steel", 20.0))));
        var repaired = ShipRepairProcessor.advanceDay(state(unfunded), List.of(unfunded)).getFirst();
        assertFalse(repaired.ships().getFirst().moduleDamage().damageByMount().containsKey(0));
        assertEquals(100, repaired.ships().getFirst().moduleDamage().damageByMount().get(2));
    }
    @Test void interruptedLocalRecoveryQuotesTheSavedPositionAndVelocityWithoutResettingFlight() {
        var target = ship("target", Map.of(0, 20.0, 2, 100.0), Map.of());
        var trajectory = new FlightMotion.Trajectory(0, 0, 1, 1000, 1000, 498000, 1000);
        var geometry = new LocalSiteGeometry.Leg(new LocalSiteGeometry.Point(0, 0, 0),
                new LocalSiteGeometry.Point(499_000_000, 0, 0), 499_000_000);
        var propulsion = JourneyPropulsion.capture(target, PropulsionCatalog.drive("mod_chemical_rocket"), null, 100);
        var flight = new LocalFlight(geometry, trajectory.at(100), Map.of(target.id(), 100.0),
                Map.of(target.id(), propulsion), false, false);
        var location = new FleetLocation(FleetLocation.Site.deepSpace(), FleetLocation.Site.orbit("saved-target"),
                .01, trajectory.totalSeconds() / 86400, flight);
        var fleet = new Fleet("fleet", "Fleet", "owner", "a", "", 0, 0, 0, false, "PASSIVE", List.of(target), location);
        fleet = LocalFlightProcessor.interrupt(fleet, 0); var initial = state(fleet);
        var actual = fleet.location().localFlight().motion();
        assertNull(PausedTravelRecovery.preview(initial, fleet));
        assertEquals(Set.of(2), ShipRepairPriority.context(initial, fleet).mobility().get(target.id()));
        var candidate = fleet.withShips(List.of(target.withModuleDamage(target.moduleDamage().repair(2, 100))));
        var preview = PausedTravelRecovery.preview(FleetReturnTravel.replace(initial, candidate), candidate);
        assertNotNull(preview); assertTrue(preview.ready());
        assertEquals(actual.positionMeters(), preview.physical().trajectory().startMeters(), 1e-9);
        assertEquals(actual.velocityMps(), preview.physical().trajectory().initialVelocityMps(), 1e-9);
        assertEquals(actual, fleet.location().localFlight().motion());
        assertEquals(1000, target.currentFuelKg());
        assertEquals(100, target.moduleDamage().damageByMount().get(2));
    }
}
