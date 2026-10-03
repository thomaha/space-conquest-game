package com.spaceconquest.engine;

import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.PowerGenerationProcessor;
import com.spaceconquest.engine.macrostructure.MacroStructureProcessor;
import com.spaceconquest.engine.macrostructure.OrbitalStation;
import com.spaceconquest.engine.macrostructure.StationModule;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.FleetLocation;
import com.spaceconquest.engine.ship.ShipPowerProcessor;
import com.spaceconquest.engine.ship.ShipPowerProfile;
import com.spaceconquest.engine.ship.ShipPowerState;
import com.spaceconquest.engine.ship.ShipSolarEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SolarRadiationTest {
    @TempDir Path directory;
    private SolarSystem system(double massRatio, double au, String atmosphere, String description) {
        var moon = new Moon("moon", "Moon", "", 1, 1, 384400, 3000, "none", false, 0, List.of(), List.of());
        var planet = new Planet("planet", "Planet", "", 1, 9.81, SolarRadiation.AU_KM * au, 0, 12000,
                "terrestrial", atmosphere, false, 0, List.of(), List.of(moon), List.of());
        return new SolarSystem("system", "Star", description, 0, 0, 0, SolarRadiation.SOLAR_MASS_KG * massRatio,
                1392700, "yellow", List.of(planet), List.of());
    }
    private Fleet fleet(FleetLocation.Site site) {
        return new Fleet("fleet", "Fleet", "owner", "system", "", 0, 0, 0, false, "PASSIVE", List.of(), FleetLocation.at(site));
    }
    private GameState state(SolarSystem system) {
        return GameState.builder().solarSystems(List.of(system)).build();
    }

    @Test void distanceStrengthAndStarTypeDetermineOneSharedFactorWithoutAFarDistanceFloor() {
        assertEquals(1, SolarRadiation.factor(system(1, 1, "none", ""), SolarRadiation.AU_KM), .000001);
        assertEquals(4, SolarRadiation.factor(system(1, .5, "none", ""), SolarRadiation.AU_KM * .5), .000001);
        assertEquals(.25, SolarRadiation.factor(system(1, 2, "none", ""), SolarRadiation.AU_KM * 2), .000001);
        assertEquals(.0001, SolarRadiation.factor(system(1, 100, "none", ""), SolarRadiation.AU_KM * 100), .000001);
        assertEquals(Math.pow(2, 3.5) / 4, SolarRadiation.factor(system(2, 2, "none", ""), SolarRadiation.AU_KM * 2), .000001);
        assertEquals(100, SolarRadiation.luminosity(system(1, 1, "none", "Red Giant")), .000001);
        assertEquals(.01, SolarRadiation.luminosity(system(1, 1, "none", "White Dwarf")), .000001);
        assertEquals(0, SolarRadiation.luminosity(system(1, 1, "none", "Black Hole")));
        assertEquals(0, SolarRadiation.factor(null, SolarRadiation.AU_KM));
        assertEquals(0, SolarRadiation.factor(system(1, 1, "none", ""), Double.NaN));
    }

    @Test void shipPanelsProduceMoreNearTheStarAndWorkThroughoutLocalSpaceTravel() {
        var system = system(1, .5, "none", "");
        var state = state(system);
        var fleet = fleet(FleetLocation.Site.orbit("planet"));
        var profile = new ShipPowerProfile(120, 0, 0, 500, 100, 200, 0, 0, 2, 20, 0, .65, Map.of());
        var environment = ShipSolarEnvironment.at(state, fleet, fleet.location().current());
        assertEquals(480, ShipPowerProcessor.solarKw(profile, ShipPowerState.empty(), environment), .000001);
        var crossing = ShipSolarEnvironment.journey(state, fleet, FleetLocation.Site.orbit("moon"));
        assertEquals(4, crossing.fluxRelativeToEarth(), .000001);
        var rendezvous = ShipSolarEnvironment.journey(state, fleet, FleetLocation.Site.deepSpace());
        assertEquals(1, rendezvous.fluxRelativeToEarth(), .000001);
        assertEquals(1, ShipSolarEnvironment.at(state, fleet, FleetLocation.Site.deepSpace("unmodeled_site")).fluxRelativeToEarth(), .000001);
        var interstellar = new Fleet("fleet", "Fleet", "owner", "system", "other", 0, 0, .1, true,
                "PASSIVE", List.of(), FleetLocation.at(FleetLocation.Site.deepSpace()));
        assertEquals(ShipSolarEnvironment.DARK, ShipSolarEnvironment.at(state, interstellar, FleetLocation.Site.deepSpace()));
    }

    @Test void atmosphericTravelBlocksShipPanelsWhileIdleSurfaceAndAirlessLaunchRemainUsable() {
        var state = state(system(1, 1, "air", ""));
        var landed = fleet(FleetLocation.Site.surface("planet"));
        assertEquals(1, ShipSolarEnvironment.at(state, landed, landed.location().current()).fluxRelativeToEarth());
        assertEquals(0, ShipSolarEnvironment.journey(state, landed, FleetLocation.Site.orbit("planet")).fluxRelativeToEarth());
        var orbit = fleet(FleetLocation.Site.orbit("planet"));
        assertEquals(0, ShipSolarEnvironment.journey(state, orbit, FleetLocation.Site.surface("planet")).fluxRelativeToEarth());
        assertEquals(1, ShipSolarEnvironment.journey(state, orbit, FleetLocation.Site.orbit("moon")).fluxRelativeToEarth());
        var airless = fleet(FleetLocation.Site.surface("moon"));
        assertEquals(1, ShipSolarEnvironment.journey(state, airless, FleetLocation.Site.orbit("moon")).fluxRelativeToEarth());
    }

    private OrbitalStation station(String body) {
        var control = new StationModule("control", "Control", StationModule.TYPE_CONTROL, 1, 100, 1, 0, Map.of(), "technician", 0, true);
        var solar = new StationModule("solar", "Solar", StationModule.TYPE_SOLAR_ARRAY, 4, 2500, 0, 500, Map.of(), "technician", 0, true);
        return new OrbitalStation("station", "Station", "system", body, "owner", OrbitalStation.OWNERSHIP_PUBLIC_STATE,
                20, List.of(control, solar), Map.of(), 0, 0, 0, 0, 100, 100, "steel", 1, true);
    }

    @Test void stationSolarUsesTheSameFactorAndKeepsItsReferenceRatingAcrossTicks() {
        var system = system(1, .5, "none", "");
        var processor = new MacroStructureProcessor();
        var first = processor.processOrbitalStation(station("moon"), .05, List.of(system)).updatedStation();
        assertEquals(2000, first.currentPowerGenerationKw(), .000001);
        var second = processor.processOrbitalStation(first, .05, List.of(system)).updatedStation();
        assertEquals(2000, second.currentPowerGenerationKw(), .000001);
        assertEquals(500, second.modules().get(1).powerOutputKw());
        assertEquals(0, processor.processOrbitalStation(station("planet"), .05,
                List.of(system(1, .5, "none", "Black Hole"))).updatedStation().currentPowerGenerationKw());
        assertEquals(1, SolarRadiation.stationFactor(List.of(system), station("")), .000001);
    }

    @Test void planetarySolarUsesStellarStrengthAndParentMoonDistanceIncludingVeryDistantOrbits() {
        var empire = new Empire("owner", "Owner", "human", "Individualist", 0, 0, List.of("system"), List.of(), Map.of(),
                List.of("electricity"), List.of());
        var plant = new IndustrialFacility("solar", "moon", "solar_power", "owner", IndustrialFacility.PUBLIC_STATE,
                1, 100, "technician", false, 0);
        for (double distance : new double[]{.5, 2, 100}) {
            var state = state(system(2, distance, "none", "")).toBuilder().empires(List.of(empire))
                    .industrialFacilities(List.of(plant)).build();
            double factor = Math.pow(2, 3.5) / (distance * distance);
            var generation = new PowerGenerationProcessor().process(state, Map.of("solar", 100), Map.of());
            assertEquals(6000 * factor, generation.generationKw().get("moon"), .000001);
        }
    }

    @Test void savedSolarStationsKeepTheirReferenceOutputAndLiveIlluminationAfterLoading() throws Exception {
        var state = state(system(2, 2, "none", "")).toBuilder().orbitalStations(List.of(station("moon"))).build();
        var manager = new SaveGameManager(directory);
        var file = directory.resolve("solar.scsave").toFile();
        manager.save(file, state, 1, "2027-01-01T08:00:00");
        var save = manager.load(file);
        assertEquals(com.spaceconquest.engine.SaveGame.CURRENT_VERSION, save.version());
        var loaded = save.toGameState(0, "RUNNING");
        assertEquals(state.orbitalStations(), loaded.orbitalStations());
        var updated = new MacroStructureProcessor().processOrbitalStation(loaded.orbitalStations().getFirst(),
                .05, loaded.solarSystems()).updatedStation();
        assertEquals(500 * Math.pow(2, 3.5) / 4, updated.currentPowerGenerationKw(), .000001);
        assertEquals(500, updated.modules().get(1).powerOutputKw());
    }
}
