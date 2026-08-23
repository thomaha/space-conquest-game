package com.spaceconquest.engine.galaxy;

import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.espionage.PirateBase;
import com.spaceconquest.engine.ship.Fleet;
import com.spaceconquest.engine.ship.ShipDesign;
import com.spaceconquest.engine.ship.ShipInstance;
import com.spaceconquest.engine.ship.ShipRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SensorProcessorTest {

    @Test
    public void testSensorCoverageAndSystemExploration() {
        SensorProcessor processor = new SensorProcessor();

        Empire terran = new Empire(
                "terran_confederation", "Terran Confederation", "human", "Individualist",
                100000.0, 0.10, List.of("sol"), List.of(), Map.of(), List.of(), List.of()
        );

        Planet earth = new Planet(
                "earth", "Earth", "Homeworld", 5.97e24, 1.0, 1.496e8, 0.0, 12742.0,
                "terrestrial", "Breathable", true, 0.71, List.of("iron_ore"), List.of(), List.of()
        );

        SolarSystem sol = new SolarSystem(
                "sol", "Sol", "Home System", 0.0, 0.0, 0.0, 1.989e30, 1392700.0, "#FFF500",
                List.of(earth), List.of()
        );

        Planet alphaCentauriB = new Planet(
                "ac_prime", "Alpha Centauri Prime", "Colony World", 4.0e24, 0.9, 1.2e8, 0.0, 11000.0,
                "terrestrial", "Breathable", true, 0.50, List.of("copper_ore"), List.of(), List.of()
        );

        SolarSystem alphaCentauri = new SolarSystem(
                "alpha_centauri", "Alpha Centauri", "Neighbor System", 4.3, 0.0, 0.0, 2.0e30, 1400000.0, "#FFCC00",
                List.of(alphaCentauriB), List.of()
        );

        ShipDesign explorerDesign = new ShipDesign(
                "design_explorer_01", "Pathfinder Class Explorer", "terran_confederation",
                ShipRole.EXPLORER, "carbon_nanotubes", List.of("scanner_array_01"), "steel",
                10.0, 50000.0, 10000.0, 500.0, 10.0, 1000.0, 10000.0, true, false
        );

        Fleet explorerFleet = new Fleet(
                "fleet_exp_01", "1st Exploration Flotilla", "terran_confederation",
                "alpha_centauri", "", 0.0, 0.0, 0.0, false, "PATROL",
                List.of(new ShipInstance("ship_exp_01", "design_explorer_01", "terran_confederation", 100.0, 100.0, 100.0, Map.of()))
        );

        Anomaly derelict = new Anomaly(
                "anom_alien_wreck", "alpha_centauri", Anomaly.TYPE_DERELICT_STARSHIP,
                "Derelict Alien Starframe", "Ancient wreckage", 25.0, false,
                Anomaly.REWARD_SALVAGE_MATERIAL, 500.0, ""
        );

        PirateBase rogueBase = new PirateBase(
                "pirate_ac_01", "shadow_syndicate_sol", "alpha_centauri", "asteroid_ac_01",
                2500.0, 2, true
        );

        List<FogOfWarState> states = processor.updateSensorCoverage(
                List.of(terran),
                List.of(sol, alphaCentauri),
                List.of(explorerFleet),
                List.of(explorerDesign),
                List.of(),
                List.of(derelict),
                List.of(rogueBase),
                List.of()
        );

        assertNotNull(states);
        assertEquals(1, states.size());

        FogOfWarState terranFOW = states.get(0);
        assertTrue(terranFOW.isSystemExplored("sol"));
        assertTrue(terranFOW.isSystemExplored("alpha_centauri"));
        assertTrue(terranFOW.isPlanetScanned("earth"));
        assertTrue(terranFOW.isPlanetScanned("ac_prime"));
        assertTrue(terranFOW.discoveredAnomalyIds().contains("anom_alien_wreck"));
        assertTrue(terranFOW.discoveredPirateBaseIds().contains("pirate_ac_01"));
    }
}
