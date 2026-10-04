package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.SaveGameManager;
import com.spaceconquest.engine.SolarSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FlightRecoveryTest {
    @TempDir Path directory;
    private static final double DISTANCE = 500_000_000, PEAK = 1000, ACCELERATION = 1;
    private final ShipPowerProfile profile = new ShipPowerProfile(0, 100, 0, 0, 0, 0, 15000, 0,
            2, 20, 0, .65, Map.of("rp1", new ShipPowerProfile.Fuel("rp1_kerosene", "liquid_oxygen", .28, 1.008)));
    private final ShipDesign design = new ShipDesign("design", "Rocket", "owner", ShipRole.EXPLORER,
            "steel", List.of("mod_chemical_rocket"), "steel", 0, 1000, 0, 1000, 100, 1,
            0, 2000, false, false, new ShipManufacturingProfile(1, 1, 1), profile);

    private ShipPowerState reserves(double energyKwh) {
        double kg = energyKwh / 1.008;
        return new ShipPowerState(Map.of("rp1_kerosene", kg * .28, "liquid_oxygen", kg * .72),
                "rp1", "uranium", 0, true, 1, 1, 0, 0, 0, 0, 0);
    }
    private Fleet flight(double seconds, double energy) {
        var ship = new ShipInstance("ship", "design", "owner", 100, 0, 1000, Map.of(), 0, "", "CONSCIOUS", reserves(energy));
        return new Fleet("fleet", "Fleet", "owner", "a", "b", 0, 0,
                InterstellarTravel.progress(DISTANCE, ACCELERATION, PEAK, seconds), false, "PASSIVE",
                List.of(ship), FleetLocation.at(FleetLocation.Site.deepSpace()), Fleet.MODE_SUBLIGHT,
                InterstellarTravel.travelSeconds(DISTANCE, ACCELERATION, PEAK) / 86400,
                DISTANCE, ACCELERATION, seconds / 86400, PEAK, Map.of("ship", 100.0));
    }
    private GameState state(Fleet fleet) {
        return GameState.builder().shipDesigns(List.of(design)).fleets(List.of(fleet)).solarSystems(List.of(
                new SolarSystem("b", "B", "", 0, 0, 0, 1, 1, "yellow", List.of(), List.of()))).build();
    }
    private Fleet tick(Fleet fleet) {
        return new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state(fleet)), List.of(), List.of()).getFirst();
    }
    private Fleet restored(Fleet fleet) {
        return fleet.withShips(List.of(fleet.ships().getFirst().withPowerState(reserves(500))));
    }

    @ParameterizedTest @ValueSource(doubles = {100, 5000, 500500})
    void firstFailureStopsThrustAtTheActualTimeThenCoasts(double start) {
        double failureSeconds = start + 360;
        double energy = (start == 5000 ? 2 : 22) * .1;
        Fleet original = flight(start, energy), failed = tick(original);
        double velocity = InterstellarTravel.velocityMps(DISTANCE, ACCELERATION, PEAK, failureSeconds);
        double poweredPosition = InterstellarTravel.progress(DISTANCE, ACCELERATION, PEAK, failureSeconds) * DISTANCE;
        assertEquals(Fleet.MODE_POWER_INTERRUPTED, failed.interstellarMode());
        assertEquals(velocity, failed.flightMotion().velocityMps(), .00001);
        assertEquals(poweredPosition + velocity * (86400 - 360), failed.flightMotion().positionMeters(), .01);
        double fraction = InterstellarTravel.fuelBurnFraction(DISTANCE, ACCELERATION, PEAK, failureSeconds)
                - InterstellarTravel.fuelBurnFraction(DISTANCE, ACCELERATION, PEAK, start);
        assertEquals(1000 - 100 * fraction, failed.ships().getFirst().currentFuelKg(), .00001);
        assertEquals(0, failed.ships().getFirst().generatorFuelMassKg(), .00001);
        Fleet drifted = tick(failed);
        assertEquals(failed.flightMotion().positionMeters() + velocity * 86400, drifted.flightMotion().positionMeters(), .01);
        assertEquals(failed.ships().getFirst().currentFuelKg(), drifted.ships().getFirst().currentFuelKg());
        assertEquals("a", drifted.currentSystemId());
        assertEquals(0, drifted.ships().getFirst().powerState().unmetDriveKwh());
    }

    @ParameterizedTest @ValueSource(doubles = {100, 500500})
    void propellantExhaustionStopsAccelerationAndBrakingAtTheActualTime(double start) {
        var original = flight(start, 10000);
        var ship = original.ships().getFirst();
        var depleted = original.withShips(List.of(FleetPropulsionSupply.withFuel(ship, 5)));
        double seconds = start + 100;
        double velocity = InterstellarTravel.velocityMps(DISTANCE, ACCELERATION, PEAK, seconds);
        var failed = tick(depleted);
        assertEquals(Fleet.MODE_POWER_INTERRUPTED, failed.interstellarMode());
        assertEquals(velocity, failed.flightMotion().velocityMps(), 1e-6);
        assertEquals(InterstellarTravel.progress(DISTANCE, ACCELERATION, PEAK, seconds) * DISTANCE
                + velocity * (86400 - 100), failed.flightMotion().positionMeters(), .01);
        assertEquals(0, failed.ships().getFirst().currentFuelKg(), 1e-6);
        var drifted = tick(failed);
        assertEquals(velocity, drifted.flightMotion().velocityMps(), 1e-6);
        assertEquals(failed.flightMotion().positionMeters() + velocity * 86400, drifted.flightMotion().positionMeters(), .01);
        assertEquals("a", drifted.currentSystemId());
        var movementOnly = new FleetProcessor().processFleetMovements(List.of(depleted), List.of(), List.of()).getFirst();
        assertEquals(failed.flightMotion().positionMeters(), movementOnly.flightMotion().positionMeters(), .01);
    }

    @Test void emptyTankCoastsUntilBrakingIsRequiredThenCannotClaimArrival() {
        var original = flight(5000, 10000);
        var empty = original.withShips(List.of(FleetPropulsionSupply.withFuel(original.ships().getFirst(), 0)));
        var coasting = tick(empty);
        assertEquals(Fleet.MODE_SUBLIGHT, coasting.interstellarMode());
        assertEquals(0, coasting.ships().getFirst().currentFuelKg());
        for (int day = 0; day < 6; day++) coasting = tick(coasting);
        assertEquals(Fleet.MODE_POWER_INTERRUPTED, coasting.interstellarMode());
        assertEquals(PEAK, coasting.flightMotion().velocityMps(), 1e-6);
        assertEquals("a", coasting.currentSystemId());
    }

    @Test void replacementTrajectoryAlsoStopsWhenItsPropellantIsExhausted() {
        var original = flight(0, 10000);
        var trajectory = new FlightMotion.Trajectory(100, 10, 1, 20, 10, 100, 20);
        var recovering = FleetPropulsionSupply.motion(original.withShips(List.of(
                FleetPropulsionSupply.withFuel(original.ships().getFirst(), 1))), Fleet.MODE_RECOVERY,
                trajectory.at(0), Map.of("ship", 30.0), Map.of());
        var failed = tick(recovering);
        assertEquals(Fleet.MODE_POWER_INTERRUPTED, failed.interstellarMode());
        assertEquals(11, failed.flightMotion().velocityMps(), 1e-6);
        assertEquals(110.5 + 11 * (86400 - 1), failed.flightMotion().positionMeters(), .01);
        assertEquals(0, failed.ships().getFirst().currentFuelKg(), 1e-6);
    }

    @Test void recoveryUsesActualVelocityConsumesFuelAndBrakesBeforeArrival() {
        Fleet failed = restored(tick(flight(5000, .2)));
        var plan = FlightRecovery.plan(state(failed), failed);
        assertNotNull(plan);
        assertTrue(FlightRecovery.electricallyReady(state(failed), failed, plan));
        assertEquals(failed.flightMotion().velocityMps(), plan.trajectory().initialVelocityMps());
        assertEquals(DISTANCE, plan.trajectory().at(plan.trajectory().totalSeconds()).positionMeters(), .01);
        assertEquals(0, plan.trajectory().at(plan.trajectory().totalSeconds()).velocityMps(), .000001);
        Fleet recovering = FlightRecovery.depart(failed, plan);
        assertEquals(plan.propulsion(), recovering.journeyPropulsion());
        assertEquals(3400, recovering.journeyPropulsion().get("ship").exhaustVelocityMps());
        double originalFuel = recovering.ships().getFirst().currentFuelKg();
        for (int day = 0; day < 30 && recovering.hasInterstellarOrder(); day++) recovering = tick(recovering);
        assertEquals("b", recovering.currentSystemId());
        assertFalse(recovering.hasInterstellarOrder());
        assertNull(recovering.flightMotion());
        assertTrue(recovering.journeyPropulsion().isEmpty());
        assertEquals(originalFuel - plan.fuelKg().get("ship"), recovering.ships().getFirst().currentFuelKg(), .00001);
        assertTrue(recovering.ships().getFirst().generatorFuelMassKg() < failed.ships().getFirst().generatorFuelMassKg());
    }

    @Test void theFirstShipFailureStopsEveryShipAndSavesTheirUnusedDriveEnergy() {
        Fleet first = flight(100, 2.2);
        var originalShip = first.ships().getFirst();
        var second = new ShipInstance("second", originalShip.designId(), originalShip.ownerEntityId(),
                100, 0, 1000, Map.of(), 0, "", "CONSCIOUS", reserves(500));
        Fleet fleet = new Fleet(first.id(), first.name(), first.ownerEntityId(), "a", "b", 0, 0,
                first.transitProgress(), false, "PASSIVE", List.of(originalShip, second), first.location(),
                first.interstellarMode(), first.interstellarTravelDays(), DISTANCE, ACCELERATION,
                first.interstellarElapsedDays(), PEAK, Map.of("ship", 100.0, "second", 100.0));
        Fleet failed = tick(fleet);
        assertEquals(Fleet.MODE_POWER_INTERRUPTED, failed.interstellarMode());
        assertEquals(failed.ships().getFirst().currentFuelKg(), failed.ships().get(1).currentFuelKg());
        assertEquals((500 - 2 * 24 - 20 * .1) / 1.008,
                failed.ships().get(1).generatorFuelMassKg(), .000001);
    }

    @Test void depletedPowerInsufficientPropellantAndOvershootCannotGetFreeArrival() {
        Fleet failed = tick(flight(5000, .2));
        var plan = FlightRecovery.plan(state(failed), failed);
        assertNotNull(plan);
        assertFalse(FlightRecovery.electricallyReady(state(failed), failed, plan));
        var ship = failed.ships().getFirst();
        Fleet empty = failed.withShips(List.of(new ShipInstance(ship.id(), ship.designId(), ship.ownerEntityId(),
                100, 0, 1, Map.of(), 0, "", "CONSCIOUS", reserves(5000))));
        assertNull(FlightRecovery.plan(state(empty), empty));
        Fleet passed = restored(tick(flight(500500, 2.2)));
        assertTrue(passed.flightMotion().positionMeters() > DISTANCE);
        assertEquals("a", passed.currentSystemId());
        assertNull(FlightRecovery.plan(state(passed), passed));
    }

    @Test void aSecondPowerFailureRetainsTheRecoveryVelocityWithoutBrakingForFree() {
        Fleet failed = restored(tick(flight(5000, .2)));
        var plan = FlightRecovery.plan(state(failed), failed);
        Fleet recovering = FlightRecovery.depart(failed, plan);
        recovering = recovering.withShips(List.of(recovering.ships().getFirst().withPowerState(ShipPowerState.empty())));
        Fleet interrupted = tick(recovering);
        assertEquals(Fleet.MODE_POWER_INTERRUPTED, interrupted.interstellarMode());
        assertEquals(failed.flightMotion().velocityMps(), interrupted.flightMotion().velocityMps());
        assertEquals(recovering.ships().getFirst().currentFuelKg(), interrupted.ships().getFirst().currentFuelKg());
        assertEquals("a", interrupted.currentSystemId());
    }

    @Test void aLaterHotelOutageDoesNotUndoAnAlreadyCompletedPoweredBrake() {
        Fleet original = flight(0, .2);
        Fleet shortFlight = new Fleet(original.id(), original.name(), original.ownerEntityId(), "a", "b",
                0, 0, 0, false, "PASSIVE", original.ships(), original.location(), Fleet.MODE_SUBLIGHT,
                1, 1000, 1, 0, 10, Map.of("ship", 100.0));
        Fleet arrived = tick(shortFlight);
        assertEquals("b", arrived.currentSystemId());
        assertEquals(900, arrived.ships().getFirst().currentFuelKg(), .000001);
        assertTrue(arrived.ships().getFirst().powerState().lastUnmetEssentialKwh() > 0);
    }

    @Test void saveLoadContinuesBothDriftAndRecoveryWithoutResettingTheirMotion() throws Exception {
        Fleet failed = restored(tick(flight(5000, .2)));
        assertRoundTrip(failed);
        Fleet recovering = FlightRecovery.depart(failed, FlightRecovery.plan(state(failed), failed));
        assertRoundTrip(tick(recovering));
    }
    private void assertRoundTrip(Fleet fleet) throws Exception {
        var manager = new SaveGameManager(directory);
        var file = directory.resolve("recovery.scsave").toFile();
        manager.save(file, state(fleet), 1, "2027-01-01T08:00:00");
        var loaded = manager.load(file).toGameState(0, "RUNNING");
        assertEquals(fleet, loaded.fleets().getFirst());
        assertEquals(tick(fleet), tick(loaded.fleets().getFirst()));
    }
}
