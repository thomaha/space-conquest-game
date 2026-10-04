package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ShipArrivalReserveTest {
    private ShipPowerProfile profile(double battery, double charge, double discharge) {
        return new ShipPowerProfile(120, 0, 0, battery, charge, discharge, 0, 0, 2, 20, 30, .65, Map.of());
    }
    private ShipPowerState power(double charge, boolean deployed) {
        return new ShipPowerState(Map.of(), "rp1", "uranium", charge, deployed, 1, 1, 0, 0, 0, 0, 0);
    }
    private GameState state(double mass, double distance) {
        var moon = new Moon("moon", "Moon", "", 1, 1, 384400, 3000, "none", false, 0, List.of(), List.of());
        var planet = new Planet("earth", "Earth", "", 1, 9.81, distance * ShipSolarEnvironment.AU_KM, 0, 12000,
                "terrestrial", "air", false, 0, List.of(), List.of(moon), List.of());
        var fleet = new Fleet("fleet", "Fleet", "owner", "a", "", 0, 0, 0, false, "PASSIVE", List.of(),
                FleetLocation.at(FleetLocation.Site.orbit("earth")));
        return GameState.builder().fleets(List.of(fleet)).solarSystems(List.of(new SolarSystem("a", "A", "", 0, 0, 0,
                1.989e30 * mass, 1392000, "yellow", List.of(planet), List.of()))).build();
    }
    private ShipSolarEnvironment arrival(double mass, double distance, FleetLocation.Site site) {
        var state = state(mass, distance);
        return ShipArrivalReserve.environment(state, state.fleets().getFirst(), site, false);
    }
    @Test void localArrivalRequiresAnInitialEclipseBeforeAnySolarRecharge() {
        var env = arrival(1, 1, FleetLocation.Site.orbit("moon"));
        assertFalse(ShipArrivalReserve.check(profile(500, 100, 200), power(63, true), 2, 30, env).ready());
        var result = ShipArrivalReserve.check(profile(500, 100, 200), power(64, true), 2, 30, env);
        assertTrue(result.ready());
        assertEquals(1536, result.requiredKwh());
        assertFalse(ShipArrivalReserve.check(profile(500, 100, 31), power(500, true), 2, 30, env).ready());
    }
    @Test void weakStarsDistantOrbitsAndStowedPanelsCannotInventReservePower() {
        for (var env : List.of(arrival(.5, 1, FleetLocation.Site.orbit("moon")), arrival(1, 5, FleetLocation.Site.orbit("moon"))))
            assertFalse(ShipArrivalReserve.check(profile(500, 100, 200), power(500, true), 2, 30, env).ready());
        assertFalse(ShipArrivalReserve.check(profile(500, 100, 200), power(500, false), 2, 30,
                arrival(1, 1, FleetLocation.Site.orbit("moon"))).ready());
    }
    @Test void stationarySurfaceUsesItsNightAndAtmosphericArrivalStillHasSunlight() {
        var state = state(1, 1);
        var surface = FleetLocation.Site.surface("earth");
        assertEquals(0, ShipSolarEnvironment.journey(state, state.fleets().getFirst(), surface).fluxRelativeToEarth());
        var env = ShipArrivalReserve.environment(state, state.fleets().getFirst(), surface, false);
        assertEquals(1, env.fluxRelativeToEarth());
        assertTrue(ShipArrivalReserve.check(profile(500, 100, 200), power(384, true), 2, 30, env).ready());
        assertFalse(ShipArrivalReserve.check(profile(500, 100, 200), power(383, true), 2, 30, env).ready());
    }
    @Test void interstellarAndUnknownArrivalKeepDarknessAndCargoDemand() {
        var state = state(1, .5);
        var fleet = state.fleets().getFirst();
        assertEquals(ShipSolarEnvironment.DARK, ShipArrivalReserve.environment(state, fleet, FleetLocation.Site.orbit("moon"), true));
        for (var site : List.of(FleetLocation.Site.orbit("missing"), FleetLocation.Site.deepSpace("missing"), FleetLocation.Site.docked("missing")))
            assertEquals(ShipSolarEnvironment.DARK, ShipArrivalReserve.environment(state, fleet, site, false));
        assertFalse(ShipArrivalReserve.check(profile(500, 100, 200), power(500, true), 2, 30, ShipSolarEnvironment.DARK).ready());
        assertTrue(ShipArrivalReserve.check(profile(2000, 100, 200), power(1536, true), 2, 30, ShipSolarEnvironment.DARK).ready());
    }
    @Test void rechargeLimitsCanRejectAPositiveAverageSolarBudget() {
        var env = arrival(1, .5, FleetLocation.Site.orbit("moon"));
        assertFalse(ShipArrivalReserve.check(profile(500, .1, 200), power(64, true), 2, 30, env).ready());
        var original = power(500, true).withChargeInputToday(2400);
        var result = ShipArrivalReserve.check(profile(500, 100, 200), original, 2, 30, env);
        assertTrue(result.ready());
        assertEquals(2400, original.chargedInputKwhToday());
        assertEquals(0, result.remaining().chargedInputKwhToday());
    }
    @Test void occupancyAndMaterialLoadsScaleWithinCapturedCeilings() {
        var p = profile(500, 100, 200);
        var design = new ShipDesign("design", "Ship", "owner", ShipRole.CARGO_TRANSPORT, "steel",
                List.of("cryogenic_stasis_pod", "cryogenic_stasis_pod"), "steel", 0, 1000, 30000, 1000, 100, 1, 0, 2000,
                false, false, ShipManufacturingProfile.baseline(), p);
        for (int count : new int[]{0, 1, 10, 100, 101, 200}) {
            var ship = new ShipInstance("ship", "design", "owner", 100, 0, 0, Map.of(), count, "human", ShipInstance.MODE_CRYOGENIC_STASIS);
            assertEquals(2 + 2 * Math.ceil(count / 100.0) + .78 * count, p.essentialKw(ship, design), 1e-9);
        }
        for (String id : List.of("food_matrix", "agricultural_biomass", "liquid_hydrogen", "steel")) {
            var ship = new ShipInstance("ship", "design", "owner", 100, 0, 0, Map.of(id, 1000.0));
            double expected = switch (id) { case "food_matrix" -> 3.9; case "agricultural_biomass" -> 3.45;
                case "liquid_hydrogen" -> 4.8; default -> 3; };
            assertEquals(expected, ShipPowerProcessor.cargoKw(p, ship, design), 1e-9);
        }
        var full = new ShipInstance("ship", "design", "owner", 100, 0, 0, Map.of("liquid_hydrogen", 30000.0));
        assertEquals(30, ShipPowerProcessor.cargoKw(p, full, design));
        var higherCapacity = new ShipDesign("design", "Ship", "owner", design.role(), "steel", design.equippedModuleIds(),
                "steel", 0, 1000, 30000, 1000, 100, 1, 0, 2000, false, false,
                new ShipManufacturingProfile(1, 7, 400), p);
        var occupied = new ShipInstance("ship", "design", "owner", 100, 0, 0, Map.of(), 200, "human", ShipInstance.MODE_CRYOGENIC_STASIS);
        assertEquals(82, p.essentialKw(occupied, higherCapacity));
    }
}
