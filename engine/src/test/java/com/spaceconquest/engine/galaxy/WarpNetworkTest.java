package com.spaceconquest.engine.galaxy;

import com.spaceconquest.engine.SolarSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WarpNetworkTest {

    private WarpNetwork network;
    private List<SolarSystem> systems;

    @BeforeEach
    void setUp() {
        network = new WarpNetwork();
        SolarSystem sol = new SolarSystem("sol", "Sol", "Home", 0.0, 0.0, 0.0, 1.0, 1.0, "Yellow", List.of(), List.of());
        SolarSystem alpha = new SolarSystem("alpha_centauri", "Alpha Centauri", "Neighbor", 4.3, 0.0, 0.0, 1.0, 1.0, "Yellow", List.of(), List.of());
        SolarSystem sirius = new SolarSystem("sirius", "Sirius", "Neighbor 2", 8.6, 0.0, 0.0, 2.0, 1.5, "White", List.of(), List.of());
        SolarSystem vega = new SolarSystem("vega", "Vega", "Far", 25.0, 0.0, 0.0, 2.1, 2.0, "Blue", List.of(), List.of());

        systems = List.of(sol, alpha, sirius, vega);
    }

    @Test
    void testNetworkGeneration() {
        List<WarpLane> lanes = network.generateNetwork(systems, 15.0);
        assertNotNull(lanes);
        assertFalse(lanes.isEmpty());

        // Check Sol connects to Alpha Centauri
        boolean solToAlpha = lanes.stream().anyMatch(l -> l.connects("sol") && l.connects("alpha_centauri"));
        assertTrue(solToAlpha);
    }

    @Test
    void testShortestPathDijkstra() {
        List<WarpLane> lanes = network.generateNetwork(systems, 30.0);
        List<String> path = network.findShortestPath("sol", "vega", lanes);

        assertNotNull(path);
        assertFalse(path.isEmpty());
        assertEquals("sol", path.get(0));
        assertEquals("vega", path.get(path.size() - 1));
    }
}
