package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SaveGameManager;
import com.spaceconquest.engine.SolarSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ShipPowerProcessorTest {
    @TempDir Path directory;

    private ShipPowerProfile profile(String powerId, String driveId) {
        return ShipPowerProfile.capture(ShipComponentCatalog.workbenchModules(driveId, false, powerId)
                .stream().map(ShipComponentCatalog::module).toList());
    }

    private ShipPowerState reserves(double mixtureKg, double charge) {
        return new ShipPowerState(Map.of("rp1_kerosene", mixtureKg * .28, "liquid_oxygen", mixtureKg * .72),
                "rp1", "uranium", charge, true, 1, 1, 0, 0, 0, 0, 0);
    }

    private ShipDesign design(ShipPowerProfile profile, String driveId) {
        return new ShipDesign("design", "Ship", "owner", ShipRole.EXPLORER, "steel",
                List.of(driveId, "mod_cargo_vault", "cryogenic_stasis_pod"), "steel", 0, 10_000,
                30_000, 15_000, 400, 1, 0, PropulsionCatalog.module(driveId).thrustOutputN(), false, false,
                new ShipManufacturingProfile(1, 2, 100), profile);
    }

    private ShipInstance ship(ShipPowerState power) {
        return new ShipInstance("ship", "design", "owner", 100, 0, 15_000, Map.of(), 0, "", "CONSCIOUS", power);
    }

    private GameState state(ShipDesign design, Fleet fleet) {
        var moon = new Moon("moon", "Moon", "", 1, 1, 384_400, 3000, "none", false, 0, List.of(), List.of());
        var earth = new Planet("earth", "Earth", "", 1, 9.81, ShipSolarEnvironment.AU_KM, 0, 12000,
                "terrestrial", "air", true, 0, List.of(), List.of(moon), List.of());
        var mars = new Planet("mars", "Mars", "", 1, 3, ShipSolarEnvironment.AU_KM * 1.5, 0, 6000,
                "terrestrial", "none", false, 0, List.of(), List.of(), List.of());
        var system = new SolarSystem("a", "A", "", 0, 0, 0, 1.989e30, 1_392_700, "yellow",
                List.of(earth, mars), List.of());
        return GameState.builder().solarSystems(List.of(system)).shipDesigns(List.of(design)).fleets(List.of(fleet)).build();
    }

    private Fleet fleet(ShipInstance ship) {
        return new Fleet("fleet", "Fleet", "owner", "a", "", 0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.orbit("earth")));
    }

    @Test void chemicalGenerationBurnsTheDraftMixtureAndIdleGeneratorsBurnNothing() {
        var profile = profile(ShipComponentCatalog.CHEMICAL_GENERATOR_ID, "mod_chemical_rocket");
        var stocked = reserves(15_000, 0);
        var result = ShipPowerProcessor.interval(profile, stocked, 24, 0, 50, 0, 0);
        assertTrue(result.supplied());
        assertEquals(1200, result.generatedKwh(), .000001);
        assertEquals(15_000 - 1200 / 1.008, result.state().fuelMassKg(), .000001);
        assertEquals(stocked.generatorMaterialsKg(),
                ShipPowerProcessor.interval(profile, stocked, 24, 0, 0, 0, 0).state().generatorMaterialsKg());
    }

    @Test void oxidizerIsALimitingIngredientAndFreightCannotFeedTheGenerator() {
        var profile = profile(ShipComponentCatalog.CHEMICAL_GENERATOR_ID, "mod_chemical_rocket");
        var stocked = new ShipPowerState(Map.of("rp1_kerosene", 28.0, "liquid_oxygen", .72),
                "rp1", "uranium", 0, true, 1, 1, 0, 0, 0, 0, 0);
        var result = ShipPowerProcessor.interval(profile, stocked, 1, 0, 50, 0, 0);
        assertFalse(result.supplied());
        assertEquals(1.008, result.generatedKwh(), .000001);
        assertEquals(27.72, result.state().generatorMaterialsKg().get("rp1_kerosene"), .000001);
        assertEquals(0, result.state().generatorMaterialsKg().get("liquid_oxygen"), .000001);
        var design = design(profile, "mod_chemical_rocket");
        var freight = new ShipInstance("ship", "design", "owner", 100, 0, 15_000,
                Map.of("rp1_kerosene", 1000.0, "liquid_oxygen", 2000.0));
        var after = ShipPowerProcessor.advanceDay(state(design, fleet(freight))).getFirst().ships().getFirst();
        assertEquals(1000, after.storedCargoKg().get("rp1_kerosene"));
        assertEquals(2000 * Math.pow(.98, .75), after.storedCargoKg().get("liquid_oxygen"), .000001);
        assertEquals(2000, after.storedCargoKg().get("liquid_oxygen")
                + after.powerState().cargoPreservation().lostKgToday().get("liquid_oxygen"), .000001);
        assertEquals(0, after.generatorFuelMassKg());
        assertTrue(after.powerState().lastUnmetEssentialKwh() > 0);
    }

    @Test void batteryCapacityCannotHideAPeakDischargeFailureAfterGeneratorDepletion() {
        var original = profile(ShipComponentCatalog.CHEMICAL_GENERATOR_ID, "mod_chemical_rocket");
        var limited = new ShipPowerProfile(0, 500, 0, 500, 100, 10, 15_000, 0, 2, 20, 30, .65, original.fuels());
        var result = ShipPowerProcessor.interval(limited, reserves(50 / 1.008, 500), 2, 0, 50, 0, 0);
        assertFalse(result.supplied());
        assertEquals(40, result.unmetEssentialKwh(), .000001);
        assertEquals(490, result.state().batteryChargeKwh(), .000001);
    }

    @Test void chargingHasLossesAndAnEclipseConsumesRealStoredEnergy() {
        var profile = profile(ShipComponentCatalog.SOLAR_ARRAY_ID, "mod_chemical_rocket");
        var sunlight = ShipPowerProcessor.interval(profile, ShipPowerState.empty(), 1, 120, 20, 0, 0);
        assertEquals(90, sunlight.state().batteryChargeKwh(), .000001);
        assertEquals(100, sunlight.state().chargedInputKwhToday(), .000001);
        assertEquals(120, sunlight.generatedKwh(), .000001);
        var dark = ShipPowerProcessor.interval(profile, sunlight.state(), 2, 0, 50, 0, 0);
        assertEquals(0, dark.state().batteryChargeKwh());
        assertEquals(10, dark.unmetEssentialKwh(), .000001);
    }

    @Test void solarFluxUsesParentPlanetDistanceForMoonsAndFallsWithDistance() {
        var profile = profile(ShipComponentCatalog.SOLAR_ARRAY_ID, "mod_chemical_rocket");
        var fleet = fleet(ship(ShipPowerState.empty()));
        var state = state(design(profile, "mod_chemical_rocket"), fleet);
        var earth = ShipSolarEnvironment.at(state, fleet, FleetLocation.Site.orbit("earth"));
        var moon = ShipSolarEnvironment.at(state, fleet, FleetLocation.Site.orbit("moon"));
        var mars = ShipSolarEnvironment.at(state, fleet, FleetLocation.Site.orbit("mars"));
        assertEquals(1, earth.fluxRelativeToEarth(), .000001);
        assertEquals(earth, moon);
        assertEquals(120 / 2.25, ShipPowerProcessor.solarKw(profile, ShipPowerState.empty(), mars), .000001);
        assertEquals(ShipSolarEnvironment.DARK, ShipSolarEnvironment.at(state, fleet, FleetLocation.Site.orbit("unknown")));
        assertEquals(0, ShipPowerProcessor.solarKw(profile, ShipPowerState.empty(), ShipSolarEnvironment.DARK));
    }

    @Test void solarLocalPreviewAndDailyProcessingAgreeAndRetainArrivalReserve() {
        var profile = profile(ShipComponentCatalog.SOLAR_ARRAY_ID, "mod_chemical_rocket");
        var design = design(profile, "mod_chemical_rocket");
        var fleet = fleet(ship(ShipPowerState.empty()));
        var state = state(design, fleet);
        var destination = FleetLocation.Site.orbit("moon");
        var plan = LocalTravel.plan(state, fleet, destination);
        var readiness = ShipPowerForecast.departure(state, fleet, destination, plan, null).getFirst();
        assertTrue(readiness.ready());
        assertEquals(96, readiness.arrivalReserveKwh());
        var departure = LocalTravel.depart(fleet, destination, plan);
        for (int day = 0; day < 2; day++) {
            var powered = ShipPowerProcessor.advanceDay(state.withFleets(List.of(departure)));
            departure = new FleetProcessor().processFleetMovements(powered, List.of(), List.of()).getFirst();
        }
        assertTrue(departure.location().isAt(destination));
        assertEquals(readiness.remainingBatteryKwh(), departure.ships().getFirst().powerState().batteryChargeKwh(), .000001);
        assertEquals(0, departure.ships().getFirst().powerState().lastUnmetEssentialKwh());
    }

    @Test void stowedOrEmptyEclipseStoragePreventsDepartureDespiteDailyAverageOutput() {
        var base = profile(ShipComponentCatalog.SOLAR_ARRAY_ID, "mod_chemical_rocket");
        var small = new ShipPowerProfile(120, 0, 0, 10, 100, 200, 0, 0, 2, 20, 30, .65, base.fuels());
        var fleet = fleet(ship(ShipPowerState.empty()));
        var state = state(design(small, "mod_chemical_rocket"), fleet);
        var destination = FleetLocation.Site.orbit("moon");
        assertFalse(ShipPowerForecast.ready(ShipPowerForecast.departure(state, fleet, destination,
                LocalTravel.plan(state, fleet, destination), null)));
        var stowed = new ShipPowerState(Map.of(), "rp1", "uranium", 0, false, 1, 1, 0, 0, 0, 0, 0);
        assertEquals(0, ShipPowerProcessor.solarKw(base, stowed,
                new ShipSolarEnvironment(1, 10, 2, "Test")));
    }

    @Test void ionThrustRequiresElectricityAndMatchesTheCapturedEfficiency() {
        var profile = profile(ShipComponentCatalog.CHEMICAL_GENERATOR_ID, "mod_ion_drive");
        var design = design(profile, "mod_ion_drive");
        assertEquals(3.9, ShipPowerProcessor.poweredThrust(design, ship(reserves(1000, 0)), ShipSolarEnvironment.DARK), .000001);
        assertEquals(0, ShipPowerProcessor.poweredThrust(design, ship(ShipPowerState.empty()), ShipSolarEnvironment.DARK));
    }

    @Test void interruptedTravelDoesNotClaimArrivalOrBurnUnpoweredPropellant() {
        var profile = profile(ShipComponentCatalog.CHEMICAL_GENERATOR_ID, "mod_chemical_rocket");
        var design = design(profile, "mod_chemical_rocket");
        var fleet = fleet(ship(ShipPowerState.empty())).withLocation(
                FleetLocation.at(FleetLocation.Site.orbit("earth")).depart(FleetLocation.Site.orbit("moon"), 1));
        var powered = ShipPowerProcessor.advanceDay(state(design, fleet));
        var failed = new FleetProcessor().processFleetMovements(powered, List.of(), List.of()).getFirst();
        assertEquals(Fleet.MODE_POWER_INTERRUPTED, failed.interstellarMode());
        assertTrue(failed.location().inTransit());
        assertEquals(0, failed.location().progress());
        assertEquals(15_000, failed.ships().getFirst().currentFuelKg());
        var next = ShipPowerProcessor.advanceDay(state(design, failed)).getFirst().ships().getFirst();
        assertEquals(0, next.powerState().unmetDriveKwh());
    }

    @Test void fractionalAccelerationAndBrakingDaysDoNotDrawDrivePowerDuringCoast() {
        var profile = profile(ShipComponentCatalog.CHEMICAL_GENERATOR_ID, "mod_chemical_rocket");
        var design = design(profile, "mod_chemical_rocket");
        var ordered = new Fleet("fleet", "Fleet", "owner", "a", "b", 0, 0, 0, false, "PASSIVE",
                List.of(ship(reserves(1000, 0))), FleetLocation.at(FleetLocation.Site.deepSpace()),
                Fleet.MODE_SUBLIGHT, 2, 100_000_000, 1, 0, 1000, Map.of("ship", 200.0));
        var state = state(design, ordered);
        var plan = new InterstellarTravel.Plan(Fleet.MODE_SUBLIGHT, 2, 100_000_000, 1, 1000, Map.of(), Map.of());
        var forecast = ShipPowerForecast.departure(state, ordered, FleetLocation.Site.deepSpace(), null, plan).getFirst();
        assertTrue(forecast.ready());
        double journeyKwh = 5 * 48 + 20 * 2000 / 3600.0;
        assertEquals(journeyKwh, forecast.journeyKwh(), .000001);
        Fleet moving = ordered;
        for (int day = 0; day < 2; day++) {
            var powered = ShipPowerProcessor.advanceDay(state.withFleets(List.of(moving)));
            moving = new FleetProcessor().processFleetMovements(powered, List.of(), List.of()).getFirst();
        }
        assertEquals("b", moving.currentSystemId());
        assertEquals(1000 - journeyKwh / 1.008, moving.ships().getFirst().generatorFuelMassKg(), .000001);
        assertEquals(forecast.generatorFuelUsedKg(), 1000 - moving.ships().getFirst().generatorFuelMassKg(), .000001);
    }

    @Test void stasisOutagesHaveAGracePeriodAndGradualLosses() {
        var profile = profile(ShipComponentCatalog.CHEMICAL_GENERATOR_ID, "mod_chemical_rocket");
        var design = design(profile, "mod_chemical_rocket");
        var ship = new ShipInstance("ship", "design", "owner", 100, 0, 0, Map.of(), 100, "human",
                ShipInstance.MODE_CRYOGENIC_STASIS, ShipPowerState.empty());
        assertEquals(82, profile.essentialKw(ship, design));
        var first = ShipPowerProcessor.interval(profile, ShipPowerState.empty(), 24, 0, 82, 0, 0).state();
        assertEquals(0, ShipPowerSurvival.casualties(ship.withPowerState(first), design));
        var second = ShipPowerProcessor.interval(profile, first, 24, 0, 82, 0, 0).state();
        assertTrue(ShipPowerSurvival.casualties(ship.withPowerState(second), design) > 0);
        assertTrue(ShipPowerSurvival.casualties(ship.withPowerState(second), design) < ship.passengerCount());
    }

    @Test void saveLoadPreservesProfilesReservesAndNoFreeRefuelOrChargeOccurs() throws Exception {
        var profile = profile(ShipComponentCatalog.CHEMICAL_GENERATOR_ID, "mod_chemical_rocket");
        var state = state(design(profile, "mod_chemical_rocket"), fleet(ship(reserves(500, 123))));
        var manager = new SaveGameManager(directory);
        var file = directory.resolve("power.scsave").toFile();
        manager.save(file, state, 1, "2027-01-01T08:00:00");
        var loaded = manager.load(file).toGameState(0, "RUNNING");
        assertEquals(state.shipDesigns(), loaded.shipDesigns());
        assertEquals(state.fleets(), loaded.fleets());
        assertEquals(ShipPowerProcessor.advanceDay(state), ShipPowerProcessor.advanceDay(loaded));
    }

    @Test void aVeryLongForecastUsesBoundedWorkAndRejectsFiniteSupplies() {
        var profile = profile(ShipComponentCatalog.CHEMICAL_GENERATOR_ID, "mod_chemical_rocket");
        var fleet = fleet(ship(reserves(15_000, 500)));
        var state = state(design(profile, "mod_chemical_rocket"), fleet);
        var crossing = new InterstellarTravel.Plan(Fleet.MODE_SUBLIGHT, 1_000_000_000, 1e17, .001,
                1000, Map.of(), Map.of());
        assertTimeout(Duration.ofSeconds(1), () -> assertFalse(ShipPowerForecast.ready(
                ShipPowerForecast.departure(state, fleet, FleetLocation.Site.deepSpace(), null, crossing))));
    }

    @Test void invalidNumericalReservesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> reserves(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> reserves(100, -1));
    }
}
