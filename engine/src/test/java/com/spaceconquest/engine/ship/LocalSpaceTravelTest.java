package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LocalSpaceTravelTest {
    @TempDir Path directory;

    private GameState world() {
        var profile = new ShipPowerProfile(0, 100, 0, 0, 0, 0, 100, 0, 1, 20, 0, .65,
                Map.of("rp1", new ShipPowerProfile.Fuel("rp1_kerosene", "liquid_oxygen", .28, 1000)));
        var design = new ShipDesign("design", "Rocket", "owner", ShipRole.EXPLORER, "steel",
                List.of("mod_chemical_rocket"), "steel", 0, 1000, 1000, 1000, 0, 1, 0, 2000,
                false, false, ShipManufacturingProfile.baseline(), profile);
        var power = new ShipPowerState(Map.of("rp1_kerosene", 2.8, "liquid_oxygen", 7.2),
                "rp1", "uranium", 0, true, 1, 1, 0, 0, 0, 0, 0);
        var ship = new ShipInstance("ship", "design", "owner", 100, 0, 1000, Map.of()).withPowerState(power);
        var fleet = new Fleet("fleet", "Fleet", "owner", "sol", "", 0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.orbit("earth")));
        var moon = new Moon("moon", "Moon", "", 1, 1, 384400, 3000, "none", false, 0, List.of(), List.of());
        var earth = new Planet("earth", "Earth", "", 1, 9.81, SolarRadiation.AU_KM, 15, 12000,
                "terrestrial", "air", true, 0, List.of(), List.of(moon), List.of());
        return GameState.builder().shipDesigns(List.of(design)).fleets(List.of(fleet))
                .solarSystems(List.of(new SolarSystem("sol", "Sol", "", 0, 0, 0, SolarRadiation.SOLAR_MASS_KG,
                        1392000, "yellow", List.of(earth), List.of()))).build();
    }

    private GameState depart(GameState state) {
        var fleet = state.fleets().getFirst();
        var target = FleetLocation.Site.orbit("moon");
        return state.withFleets(List.of(LocalTravel.depart(fleet, target, LocalTravel.plan(state, fleet, target))));
    }

    private GameState tick(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()));
    }

    @Test void geometryUsesMoonRelativeDistanceAndStableIds() {
        var state = world();
        var leg = LocalSiteGeometry.resolve(state, "sol", FleetLocation.Site.orbit("earth"), FleetLocation.Site.orbit("moon"));
        assertTrue(leg.distanceMeters() > 370e6 && leg.distanceMeters() < 400e6);
        assertEquals(leg, LocalSiteGeometry.resolve(state, "sol", FleetLocation.Site.orbit("earth"), FleetLocation.Site.orbit("moon")));
        assertNull(LocalSiteGeometry.resolve(state, "sol", FleetLocation.Site.orbit("unknown"), FleetLocation.Site.orbit("moon")));
        var far = LocalSpaceTravel.plan(state, state.fleets().getFirst(), FleetLocation.Site.deepSpace());
        assertTrue(far.days() > LocalSpaceTravel.plan(state, state.fleets().getFirst(), FleetLocation.Site.orbit("moon")).days());
    }

    @Test void forecastMatchesTickBurnsCoastAndControlledArrival() {
        var state = world(); var fleet = state.fleets().getFirst(); var target = FleetLocation.Site.orbit("moon");
        var plan = LocalTravel.plan(state, fleet, target);
        assertNotNull(plan.physical()); assertTrue(plan.physical().trajectory().coastSeconds() > 0);
        var readiness = ShipPowerForecast.departure(state, fleet, target, plan, null).getFirst();
        assertTrue(readiness.ready());
        state = depart(state);
        assertEquals(1000, state.fleets().getFirst().ships().getFirst().currentFuelKg());
        state = tick(state);
        double fuel = state.fleets().getFirst().ships().getFirst().currentFuelKg();
        assertTrue(fuel < 1000 && fuel > 0);
        state = tick(state);
        assertEquals(fuel, state.fleets().getFirst().ships().getFirst().currentFuelKg(), 1e-6);
        for (int day = 2; day < Math.ceil(plan.days()); day++) state = tick(state);
        var arrived = state.fleets().getFirst();
        assertTrue(arrived.location().isAt(target));
        assertEquals(1000 - plan.propellantKg().get("ship"), arrived.ships().getFirst().currentFuelKg(), 1e-6);
        assertEquals(readiness.generatorFuelUsedKg(), 10 - arrived.ships().getFirst().generatorFuelMassKg(), 1e-6);
    }

    @Test void economicalCruisePreservesEarliestArrivalDayAndRetainsFuel() {
        var state = world(); var fleet = state.fleets().getFirst();
        var plan = LocalSpaceTravel.plan(state, fleet, FleetLocation.Site.orbit("moon"));
        double acceleration = plan.trajectory().accelerationMps2();
        double distance = plan.geometry().distanceMeters();
        double mass = 2010;
        double fundedDeltaV = -3400 * Math.log1p(-(950 - .000001) / mass);
        double fastestPeak = Math.min(fundedDeltaV / 2, Math.sqrt(acceleration * distance));
        double fastestDays = (distance / fastestPeak + fastestPeak / acceleration) / 86400;
        assertEquals(Math.ceil(fastestDays), plan.scheduledDays());
        assertTrue(plan.trajectory().peakMps() < fastestPeak);
        double fastestFuel = mass * -Math.expm1(-2 * fastestPeak / 3400);
        assertTrue(plan.propellantKg().get("ship") < fastestFuel);

        var station = new com.spaceconquest.engine.macrostructure.OrbitalStation("port", "Port", "sol", null, "owner",
                "PUBLIC_STATE", 10, List.of(), Map.of(), 0, 0, 0, 0, 100, 100, "steel", 0, true);
        state = state.toBuilder().orbitalStations(List.of(station)).build();
        fleet = fleet.withLocation(FleetLocation.at(FleetLocation.Site.deepSpace()));
        var target = FleetLocation.Site.docked("port");
        var shortPlan = LocalTravel.plan(state, fleet, target);
        assertEquals(1, Math.ceil(shortPlan.days()));
        assertTrue(shortPlan.propellantKg().get("ship") < 100);
        state = tick(state.withFleets(List.of(LocalTravel.depart(fleet, target, shortPlan))));
        assertTrue(state.fleets().getFirst().location().isAt(target));
        assertEquals(1000 - shortPlan.propellantKg().get("ship"),
                state.fleets().getFirst().ships().getFirst().currentFuelKg(), 1e-6);
    }

    @Test void outageDriftsSaveLoadRetainsMotionAndRecoveryDoesNotRestartAtRest() throws Exception {
        var state = depart(world()); var fleet = state.fleets().getFirst(); var original = fleet.ships().getFirst();
        var empty = original.withPowerState(new ShipPowerState(Map.of("rp1_kerosene", .28e-4, "liquid_oxygen", .72e-4),
                "rp1", "uranium", 0, true, 1, 1, 0, 0, 0, 0, 0));
        state = tick(state.withFleets(List.of(fleet.withShips(List.of(empty)))));
        var failed = state.fleets().getFirst(); var motion = failed.location().localFlight().motion();
        assertTrue(failed.location().localFlight().interrupted()); assertTrue(motion.velocityMps() > 0);
        var later = tick(state).fleets().getFirst();
        assertEquals(motion.velocityMps(), later.location().localFlight().motion().velocityMps());
        assertEquals(motion.positionMeters() + motion.velocityMps() * 86400, later.location().localFlight().motion().positionMeters(), 1e-6);
        assertEquals(failed.ships().getFirst().currentFuelKg(), later.ships().getFirst().currentFuelKg());
        var manager = new SaveGameManager(); var path = directory.resolve("local.scsave");
        manager.save(path.toFile(), state, 1, "2026-10-04T00:00:00Z");
        assertEquals(failed.location(), manager.load(path.toFile()).fleets().getFirst().location());
        var funded = failed.withShips(List.of(failed.ships().getFirst().withPowerState(original.powerState())));
        state = state.withFleets(List.of(funded));
        var preview = PausedTravelRecovery.preview(state, funded);
        assertNotNull(preview); assertTrue(preview.ready());
        assertEquals(motion.velocityMps(), preview.physical().trajectory().initialVelocityMps());
        assertEquals(motion.positionMeters(), preview.physical().trajectory().startMeters());
        state = state.withFleets(List.of(PausedTravelRecovery.resume(funded, preview)));
        for (int day = 0; day < Math.ceil(preview.remainingDays()); day++) state = tick(state);
        assertTrue(state.fleets().getFirst().location().isAt(FleetLocation.Site.orbit("moon")));
    }

    @Test void splitAndMergeRetainMotionAndPartitionBudgets() {
        var state = world(); var fleet = state.fleets().getFirst(); var ship = fleet.ships().getFirst();
        var second = new ShipInstance("second", ship.designId(), ship.ownerEntityId(), 100, 0, 1000, Map.of()).withPowerState(ship.powerState());
        state = tick(depart(state.withFleets(List.of(fleet.withShips(List.of(ship, second))))));
        var motion = state.fleets().getFirst().location().localFlight().motion();
        state = FleetOrganization.split(state, "owner", "fleet", List.of("second"), "split", "Split");
        assertEquals(2, state.fleets().size());
        state.fleets().forEach(item -> { assertEquals(motion, item.location().localFlight().motion());
            assertEquals(1, item.location().localFlight().propellantKg().size()); });
        assertTrue(FleetOrganization.together(state.fleets().getFirst(), state.fleets().getLast()));
        state = FleetOrganization.transfer(state, "owner", "split", "fleet", List.of("second"));
        assertEquals(1, state.fleets().size());
        assertEquals(2, state.fleets().getFirst().location().localFlight().propellantKg().size());
    }

    @Test void loadedMixedFleetUsesLimitingAccelerationAndCommitsNuclearFeedOnce() {
        var state = world(); var fleet = state.fleets().getFirst(); var target = FleetLocation.Site.orbit("moon");
        var original = LocalSpaceTravel.plan(state, fleet, target);
        var nuclear = new ShipDesign("nuclear", "Thermal", "owner", ShipRole.CARGO_TRANSPORT, "steel",
                List.of("mod_fission_thruster"), "steel", 0, 1000, 1000, 1000, 0, 1, 0, 1000, false, false);
        var second = new ShipInstance("second", "nuclear", "owner", 100, 0, 1000,
                Map.of("steel", 500.0, "refined_uranium", 2.0));
        state = state.withShipDesigns(List.of(state.shipDesigns().getFirst(), nuclear));
        fleet = fleet.withShips(List.of(fleet.ships().getFirst(), second));
        var plan = LocalTravel.plan(state, fleet, target);
        assertTrue(plan.physical().trajectory().accelerationMps2() < original.trajectory().accelerationMps2());
        assertTrue(plan.physical().scheduledDays() >= original.scheduledDays());
        var departed = LocalTravel.depart(fleet, target, plan);
        var paid = departed.ships().getLast();
        assertEquals(1000, paid.currentFuelKg());
        assertEquals(2 - plan.reactorFuelKg().get("second").quantityKg(), paid.storedCargoKg().get("refined_uranium"), 1e-9);
        var moved = LocalFlightProcessor.advance(departed, 24, false);
        assertEquals(paid.storedCargoKg(), moved.ships().getLast().storedCargoKg());
        assertTrue(moved.ships().getLast().currentFuelKg() < 1000);
    }

    @Test void emptyBrakingTankRetainsVelocityAndOvershootCannotClaimArrival() {
        var state = tick(depart(world())); var fleet = state.fleets().getFirst(); var ship = fleet.ships().getFirst();
        var empty = new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(), 100, 0, 0, ship.storedCargoKg(),
                ship.passengerCount(), ship.passengerRaceId(), ship.transitMode(), ship.powerState(), ship.supplyState());
        state = state.withFleets(List.of(fleet.withShips(List.of(empty))));
        for (int day = 0; day < Math.ceil(fleet.location().travelDays()) + 2; day++) state = tick(state);
        fleet = state.fleets().getFirst();
        assertTrue(fleet.location().inTransit()); assertTrue(fleet.location().localFlight().interrupted());
        assertTrue(fleet.location().localFlight().motion().velocityMps() > 0);
        assertTrue(fleet.location().localFlight().motion().positionMeters() > fleet.location().localFlight().geometry().distanceMeters());
        assertEquals(0, fleet.ships().getFirst().currentFuelKg());
        assertNull(LocalSpaceTravel.recovery(state, fleet));
    }

    @Test void olderSavedLocalLocationFinishesWithoutAnotherPropellantCharge() throws Exception {
        var location = new com.fasterxml.jackson.databind.ObjectMapper().readValue("""
                {"current":{"kind":"ORBIT","entityId":"earth"},
                 "destination":{"kind":"ORBIT","entityId":"moon"},"progress":0.5,"travelDays":2}
                """, FleetLocation.class);
        assertNull(location.localFlight());
        var state = world();
        state = tick(state.withFleets(List.of(state.fleets().getFirst().withLocation(location))));
        assertTrue(state.fleets().getFirst().location().isAt(FleetLocation.Site.orbit("moon")));
        assertEquals(1000, state.fleets().getFirst().ships().getFirst().currentFuelKg());
    }

    @Test void solarCanPowerElectricDepartureFromAFreeStation() {
        var state = world(); var fleet = state.fleets().getFirst();
        var profile = new ShipPowerProfile(1000, 0, 0, 500, 100, 100, 0, 0, 1, 20, 0, .65, Map.of());
        var design = new ShipDesign("design", "Solar electric", "owner", ShipRole.EXPLORER, "steel",
                List.of("mod_ion_drive"), "steel", 0, 1000, 1000, 1000, 0, 1, 0, 2000,
                false, false, ShipManufacturingProfile.baseline(), profile);
        var station = new com.spaceconquest.engine.macrostructure.OrbitalStation("port", "Port", "sol", null, "owner",
                "PUBLIC_STATE", 10, List.of(), Map.of(), 0, 0, 0, 0, 100, 100, "steel", 0, true);
        fleet = fleet.withShips(List.of(fleet.ships().getFirst().withPowerState(ShipPowerState.empty())))
                .withLocation(FleetLocation.at(FleetLocation.Site.docked("port")));
        state = state.withShipDesigns(List.of(design)).toBuilder().orbitalStations(List.of(station)).fleets(List.of(fleet)).build();
        var target = FleetLocation.Site.deepSpace();
        assertTrue(ShipSolarEnvironment.journey(state, fleet, target).fluxRelativeToEarth() > 0);
        var plan = LocalTravel.plan(state, fleet, target);
        assertNotNull(plan.physical());
        assertTrue(ShipPowerForecast.ready(ShipPowerForecast.departure(state, fleet, target, plan, null)));
    }
}
