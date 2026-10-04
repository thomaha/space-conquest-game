package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SaveGameManager;
import com.spaceconquest.engine.SolarSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PausedTravelRecoveryTest {
    @TempDir Path directory;

    private GameState state(boolean solar, double energy, double charge, boolean deployed) {
        var profile = new ShipPowerProfile(solar ? 120 : 0, solar ? 0 : 100, 0, solar ? 500 : 0,
                solar ? 100 : 0, solar ? 200 : 0, 15000, 0, 2, 20, 0, .65,
                Map.of("rp1", new ShipPowerProfile.Fuel("rp1_kerosene", "liquid_oxygen", .28, 1.008)));
        var design = new ShipDesign("design", "Ship", "owner", ShipRole.EXPLORER, "steel",
                List.of("mod_chemical_rocket"), "steel", 0, 1000, 0, 1000, 100, 1, 0, 2000,
                false, false, ShipManufacturingProfile.baseline(), profile);
        var power = power(energy, charge, deployed);
        var ship = new ShipInstance("ship", "design", "owner", 100, 0, 1000, Map.of(), 0, "", "CONSCIOUS", power);
        var fleet = new Fleet("fleet", "Fleet", "owner", "a", "", 0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.orbit("earth")));
        var moon = new Moon("moon", "Moon", "", 1, 1, 384400, 3000, "none", false, 0, List.of(), List.of());
        var earth = new Planet("earth", "Earth", "", 1, 9.81, ShipSolarEnvironment.AU_KM, 0, 12000,
                "terrestrial", "air", true, 0, List.of(), List.of(moon), List.of());
        var empire = new Empire("owner", "Owner", "human", "Individualist", 100000, 0,
                List.of("a"), List.of(), Map.of(), List.of("warp", "electricity", "solar_power"), List.of());
        return GameState.builder().shipDesigns(List.of(design)).fleets(List.of(fleet)).empires(List.of(empire))
                .solarSystems(List.of(new SolarSystem("a", "A", "", 0, 0, 0, 1.989e30, 1, "yellow", List.of(earth), List.of()),
                        new SolarSystem("b", "B", "", 1, 0, 0, 1.989e30, 1, "yellow", List.of(), List.of()))).build();
    }
    private ShipPowerState power(double energy, double charge, boolean deployed) {
        double kg = energy / 1.008;
        return new ShipPowerState(Map.of("rp1_kerosene", kg * .28, "liquid_oxygen", kg * .72),
                "rp1", "uranium", charge, deployed, 1, 1, 0, 0, 0, 0, 0);
    }
    private GameState tick(GameState state) {
        return state.withFleets(new FleetProcessor().processFleetMovements(ShipPowerProcessor.advanceDay(state), List.of(), List.of()));
    }
    private GameState replacePower(GameState state, ShipPowerState power) {
        var fleet = state.fleets().getFirst();
        return state.withFleets(List.of(fleet.withShips(List.of(fleet.ships().getFirst().withPowerState(power)))));
    }
    private GameState localDeparture(GameState state) {
        var fleet = state.fleets().getFirst();
        var destination = FleetLocation.Site.orbit("moon");
        return state.withFleets(List.of(LocalTravel.depart(fleet, destination, LocalTravel.plan(state, fleet, destination))));
    }

    @Test void localFailurePreservesPoweredProgressAndSolarRecoveryReusesPrepaidPropellant() {
        var initial = localDeparture(state(true, 0, 66, false));
        double paidFuel = initial.fleets().getFirst().ships().getFirst().currentFuelKg();
        var failed = tick(initial);
        var fleet = failed.fleets().getFirst();
        assertEquals(Fleet.MODE_POWER_INTERRUPTED, fleet.interstellarMode());
        assertEquals(3.0 / 48, fleet.location().progress(), .000001);
        assertEquals(paidFuel, fleet.ships().getFirst().currentFuelKg());
        assertEquals(fleet.location(), tick(failed).fleets().getFirst().location());
        assertFalse(PausedTravelRecovery.preview(failed, fleet).ready());
        var charged = tick(replacePower(failed, power(0, 0, true)));
        fleet = charged.fleets().getFirst();
        assertEquals(496, fleet.ships().getFirst().powerState().batteryChargeKwh(), .000001);
        var preview = PausedTravelRecovery.preview(charged, fleet);
        assertTrue(preview.ready());
        assertEquals(2, preview.remainingDays());
        var resumed = charged.withFleets(List.of(PausedTravelRecovery.resume(fleet, preview)));
        assertEquals(fleet.ships(), resumed.fleets().getFirst().ships());
        var halfway = tick(resumed);
        assertEquals(3.0 / 48 + .5, halfway.fleets().getFirst().location().progress(), .000001);
        var arrived = tick(halfway).fleets().getFirst();
        assertTrue(arrived.location().isAt(FleetLocation.Site.orbit("moon")));
        assertEquals("", arrived.interstellarMode());
        assertEquals(paidFuel, arrived.ships().getFirst().currentFuelKg());
    }

    private GameState warpDeparture(GameState state) {
        var fleet = state.fleets().getFirst();
        return state.withFleets(List.of(new Fleet(fleet.id(), fleet.name(), fleet.ownerEntityId(), "a", "b",
                0, 0, .25, true, "PASSIVE", fleet.ships(), FleetLocation.at(FleetLocation.Site.deepSpace()),
                Fleet.MODE_WARP, 4, 0, 0, 1, 0, Map.of())));
    }

    @Test void warpRecoveryRetainsPoweredCorridorProgressAndOnlyNeedsTheRemainingDays() {
        var failed = tick(warpDeparture(state(false, 2.2, 0, true)));
        var fleet = failed.fleets().getFirst();
        assertEquals(1 + .1 / 24, fleet.interstellarElapsedDays(), .000001);
        assertEquals((1 + .1 / 24) / 4, fleet.transitProgress(), .000001);
        assertEquals(fleet.transitProgress(), tick(failed).fleets().getFirst().transitProgress());
        assertFalse(PausedTravelRecovery.preview(failed, fleet).ready());
        var ready = replacePower(failed, power(5000, 0, true));
        fleet = ready.fleets().getFirst();
        var preview = PausedTravelRecovery.preview(ready, fleet);
        assertTrue(preview.ready());
        assertEquals(3, preview.remainingDays());
        var resumed = ready.withFleets(List.of(PausedTravelRecovery.resume(fleet, preview)));
        assertTrue(resumed.fleets().getFirst().isInWarp());
        var moving = tick(resumed);
        assertEquals(fleet.interstellarElapsedDays() + 1, moving.fleets().getFirst().interstellarElapsedDays(), .000001);
        moving = tick(tick(moving));
        assertEquals("b", moving.fleets().getFirst().currentSystemId());
        assertEquals(1000, moving.fleets().getFirst().ships().getFirst().currentFuelKg());
        assertEquals((5000 - 22 * 72) / 1.008, moving.fleets().getFirst().ships().getFirst().generatorFuelMassKg(), .000001);
    }

    @Test void aQueuedCrossingWaitsUntilTheResumedLocalDepartureFinishes() {
        var state = state(false, 5000, 0, true);
        var fleet = state.fleets().getFirst();
        var location = new FleetLocation(fleet.location().current(), FleetLocation.Site.deepSpace(), .25, 2);
        var paused = new Fleet(fleet.id(), fleet.name(), fleet.ownerEntityId(), "a", "b", 0, 0, 0,
                false, "PASSIVE", fleet.ships(), location, Fleet.MODE_POWER_INTERRUPTED, 4, 0, 0, 0, 0, Map.of());
        state = state.withFleets(List.of(paused));
        var preview = PausedTravelRecovery.preview(state, paused);
        assertTrue(preview.ready());
        assertEquals(6, preview.remainingDays());
        state = state.withFleets(List.of(PausedTravelRecovery.resume(paused, preview)));
        state = tick(state);
        assertTrue(state.fleets().getFirst().location().inTransit());
        state = tick(state);
        assertFalse(state.fleets().getFirst().location().inTransit());
        assertEquals(0, state.fleets().getFirst().interstellarElapsedDays());
        state = tick(state);
        assertEquals(1, state.fleets().getFirst().interstellarElapsedDays());
        assertEquals(1000, state.fleets().getFirst().ships().getFirst().currentFuelKg());
    }

    @Test void pausedJourneyProgressPersistsAcrossSaveLoadAndContinuesIdentically() throws Exception {
        for (var failed : List.of(tick(localDeparture(state(true, 0, 66, false))), tick(warpDeparture(state(false, 2.2, 0, true))))) {
            var manager = new SaveGameManager(directory);
            var file = directory.resolve("paused.scsave").toFile();
            manager.save(file, failed, 1, "2027-01-01T08:00:00");
            var loaded = manager.load(file).toGameState(0, "RUNNING");
            assertEquals(failed.fleets(), loaded.fleets());
            assertEquals(tick(failed).fleets(), tick(loaded).fleets());
        }
    }

    @Test void localArrivalBeforeALaterPowerFailureClearsTheInterruptedMode() {
        var state = state(false, 22 * 12, 0, true);
        var fleet = state.fleets().getFirst().withLocation(new FleetLocation(FleetLocation.Site.orbit("earth"),
                FleetLocation.Site.orbit("moon"), .8, 2));
        var arrived = tick(state.withFleets(List.of(fleet))).fleets().getFirst();
        assertTrue(arrived.location().isAt(FleetLocation.Site.orbit("moon")));
        assertEquals("", arrived.interstellarMode());
        assertTrue(arrived.ships().getFirst().powerState().lastUnmetEssentialKwh() > 0);
    }

    @Test void aLongPausedLocalJourneySurvivesSaveLoadAndResumesWithoutAnotherFuelCommitment() throws Exception {
        var state = state(false, 22 * 3, 0, true);
        var fleet = state.fleets().getFirst();
        var destination = FleetLocation.Site.orbit("moon");
        var plan = LocalTravel.plan(state, fleet, destination);
        state = state.withFleets(List.of(LocalTravel.depart(fleet, destination,
                new LocalTravel.Plan(10, plan.propellantKg(), plan.reactorFuelKg()))));
        double paidFuel = state.fleets().getFirst().ships().getFirst().currentFuelKg();
        var failed = tick(state);
        fleet = failed.fleets().getFirst();
        assertEquals(Fleet.MODE_POWER_INTERRUPTED, fleet.interstellarMode());
        assertEquals(3.0 / 240, fleet.location().progress(), 1e-6);
        var file = directory.resolve("long-local.scsave").toFile();
        var manager = new SaveGameManager(directory);
        manager.save(file, failed, 1, "2027-01-01T08:00:00");
        state = manager.load(file).toGameState(0, "RUNNING");
        assertEquals(failed.fleets(), state.fleets());
        state = replacePower(state, power(6000, 0, true));
        fleet = state.fleets().getFirst();
        var preview = PausedTravelRecovery.preview(state, fleet);
        assertNotNull(preview);
        assertTrue(preview.ready());
        assertEquals(10, preview.remainingDays());
        state = state.withFleets(List.of(PausedTravelRecovery.resume(fleet, preview)));
        for (int day = 0; day < 10; day++) state = tick(state);
        assertTrue(state.fleets().getFirst().location().isAt(destination));
        assertEquals(paidFuel, state.fleets().getFirst().ships().getFirst().currentFuelKg());
        assertEquals("", state.fleets().getFirst().interstellarMode());
    }
}
