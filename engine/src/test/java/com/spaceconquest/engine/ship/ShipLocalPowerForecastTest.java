package com.spaceconquest.engine.ship;

import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarRadiation;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ShipLocalPowerForecastTest {
    private ShipPowerProfile profile(double solar, double charge, boolean generators) {
        return new ShipPowerProfile(solar, generators ? 10 : 0, generators ? 20 : 0,
                500, charge, 200, 100, 100, 2, 20, 0, .65,
                Map.of("rp1", new ShipPowerProfile.Fuel("rp1_kerosene", "liquid_oxygen", .28, 100),
                        "uranium", new ShipPowerProfile.Fuel("refined_uranium", null, 1, 100)));
    }

    private GameState state(ShipPowerProfile profile, double mixture, double uranium, double battery) {
        var design = new ShipDesign("design", "Ship", "owner", ShipRole.EXPLORER, "steel",
                List.of("mod_chemical_rocket"), "steel", 0, 1000, 0, 1000, 0, 1, 0, 2000,
                false, false, ShipManufacturingProfile.baseline(), profile);
        var power = new ShipPowerState(Map.of("rp1_kerosene", mixture * .28, "liquid_oxygen", mixture * .72,
                "refined_uranium", uranium), "rp1", "uranium", battery, true, 1, 1, 0, 0, 0, 0, 0);
        var ship = new ShipInstance("ship", "design", "owner", 100, 0, 1000, Map.of()).withPowerState(power);
        var fleet = new Fleet("fleet", "Fleet", "owner", "a", "", 0, 0, 0, false, "PASSIVE", List.of(ship),
                FleetLocation.at(FleetLocation.Site.orbit("earth")));
        var earth = new Planet("earth", "Earth", "", 1, 9.81, SolarRadiation.AU_KM, 0, 12000,
                "terrestrial", "air", true, 0, List.of(), List.of(), List.of());
        var system = new SolarSystem("a", "A", "", 0, 0, 0, SolarRadiation.SOLAR_MASS_KG,
                1392000, "yellow", List.of(earth), List.of());
        return GameState.builder().solarSystems(List.of(system)).shipDesigns(List.of(design)).fleets(List.of(fleet)).build();
    }

    @Test void longSolarForecastMatchesActualTicksIncludingGeneratorExhaustionAndChargingLimits() {
        for (var profile : List.of(profile(120, 100, false), profile(40, 100, true), profile(24, 1, true))) {
            var state = state(profile, .1, profile.solarKw() == 24 ? 200 : .5, 500);
            var fleet = state.fleets().getFirst();
            var ship = fleet.ships().getFirst();
            fleet = fleet.withShips(List.of(ship.withPowerState(
                    ship.powerState().withChargeInputToday(profile.chargeKw() * 24))));
            state = state.withFleets(List.of(fleet));
            var destination = FleetLocation.Site.deepSpace("earth");
            var plan = new LocalTravel.Plan(120, Map.of(), Map.of());
            var check = ShipPowerForecast.departure(state, fleet, destination, plan, null).getFirst();
            assertTrue(check.ready(), check.explanation());
            var original = fleet.ships().getFirst().powerState();
            state = state.withFleets(List.of(LocalTravel.depart(fleet, destination, plan)));
            for (int day = 0; day < 120; day++) {
                state = state.withFleets(new FleetProcessor().processFleetMovements(
                        ShipPowerProcessor.advanceDay(state), List.of(), List.of()));
                assertNotEquals(Fleet.MODE_POWER_INTERRUPTED, state.fleets().getFirst().interstellarMode());
            }
            var actual = state.fleets().getFirst().ships().getFirst().powerState();
            assertEquals(check.remainingBatteryKwh(), actual.batteryChargeKwh(), 1e-6);
            assertEquals(check.generatorFuelUsedKg(), original.fuelMassKg() - actual.fuelMassKg(), 1e-6);
            assertTrue(state.fleets().getFirst().location().isAt(destination));
        }
    }

    @Test void aLateEclipseFailureRejectsAnOtherwiseSolarPoweredVoyage() {
        var state = state(profile(25, 100, false), 0, 0, 500);
        var fleet = state.fleets().getFirst();
        var destination = FleetLocation.Site.deepSpace("earth");
        var plan = new LocalTravel.Plan(120, Map.of(), Map.of());
        assertFalse(ShipPowerForecast.ready(ShipPowerForecast.departure(state, fleet, destination, plan, null)));
        state = state.withFleets(List.of(LocalTravel.depart(fleet, destination, plan)));
        for (int day = 0; day < 120 && !Fleet.MODE_POWER_INTERRUPTED.equals(state.fleets().getFirst().interstellarMode()); day++)
            state = state.withFleets(new FleetProcessor().processFleetMovements(
                    ShipPowerProcessor.advanceDay(state), List.of(), List.of()));
        assertEquals(Fleet.MODE_POWER_INTERRUPTED, state.fleets().getFirst().interstellarMode());
        assertTrue(state.fleets().getFirst().location().inTransit());
    }

    @Test void billionDaySolarForecastIsBoundedAndRetainsArrivalReserve() {
        var state = state(profile(120, 100, false), 0, 0, 500);
        var fleet = state.fleets().getFirst();
        assertTimeout(Duration.ofSeconds(2), () -> {
            var check = ShipPowerForecast.departure(state, fleet, FleetLocation.Site.deepSpace("earth"),
                    new LocalTravel.Plan(1_000_000_000, Map.of(), Map.of()), null).getFirst();
            assertTrue(check.ready());
            assertEquals(22 * 24.0 * 1_000_000_000, check.journeyKwh());
            assertEquals(48 * 2, check.arrivalReserveKwh());
        });
    }

    @Test void longerDarkJourneyMustFundItsArrivalReserveAsWellAsItsBurn() {
        var profile = new ShipPowerProfile(0, 0, 100, 500, 100, 200, 100, 100,
                2, 20, 0, .65, profile(0, 100, true).fuels());
        var state = state(profile, 0, 53.4, 0);
        var fleet = state.fleets().getFirst();
        var plan = new LocalTravel.Plan(10, Map.of(), Map.of());
        assertFalse(ShipPowerForecast.ready(ShipPowerForecast.departure(state, fleet,
                FleetLocation.Site.docked("unknown"), plan, null)));
        state = state(profile, 0, 54, 0);
        assertTrue(ShipPowerForecast.ready(ShipPowerForecast.departure(state, state.fleets().getFirst(),
                FleetLocation.Site.docked("unknown"), plan, null)));
    }

    @Test void automationBuysElectricityForTheActualLoadedManeuverInsteadOfTheSiteMinimum() {
        var profile = new ShipPowerProfile(0, 0, 100, 500, 100, 200, 100, 100,
                2, 20, 0, .65, profile(0, 100, true).fuels());
        var state = state(profile, 0, .01, 0);
        var design = new ShipDesign("design", "Slow rocket", "owner", ShipRole.EXPLORER,
                "steel", List.of("mod_chemical_rocket"), "steel", 0, 1000, 0, 1000,
                0, 1, 0, .1, false, false, ShipManufacturingProfile.baseline(), profile);
        var station = new OrbitalStation("port", "Port", "a", "earth", "owner", "PUBLIC_STATE",
                10, List.of(), Map.of(), 0, 0, 0, 0, 100, 100, "steel", 0, true);
        var empire = new Empire("owner", "Owner", "human", "Individualist", 1000, 0,
                List.of("a"), List.of(), Map.of(), List.of("electricity"), List.of());
        var hub = new CommercialHub("hub", "port", 0, 1000, 100, 10,
                Map.of("refined_uranium", new MarketOrder("refined_uranium", 100, 0, 2, 0)));
        var fleet = state.fleets().getFirst().withLocation(FleetLocation.at(FleetLocation.Site.docked("port")));
        state = state.toBuilder().shipDesigns(List.of(design)).fleets(List.of(fleet))
                .orbitalStations(List.of(station)).empires(List.of(empire)).commercialHubs(List.of(hub)).build();
        var destination = FleetLocation.Site.docked("other");
        assertEquals(3, LocalTravel.plan(state, fleet, destination).days());
        var supplied = ShipPowerResupply.prepareLocal(state, fleet, destination);
        var updated = supplied.fleets().getFirst();
        var plan = LocalTravel.plan(supplied, updated, destination);
        assertTrue(ShipPowerForecast.ready(ShipPowerForecast.departure(supplied, updated, destination, plan, null)));
        double bought = updated.ships().getFirst().generatorFuelMassKg() - .01;
        assertTrue(bought > 15, "A one-day purchase would leave the longer maneuver unsafe.");
        assertEquals(100 - bought, supplied.commercialHubs().getFirst().activeOrders().get("refined_uranium").supplyKg(), 1e-6);
        assertEquals(1000 - bought * 2, supplied.empires().getFirst().treasuryCredits(), 1e-6);
    }
}
