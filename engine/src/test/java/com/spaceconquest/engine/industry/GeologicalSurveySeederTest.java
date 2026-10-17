package com.spaceconquest.engine.industry;

import com.spaceconquest.engine.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeologicalSurveySeederTest {
    @Test void geologyIsFiniteDeterministicAndSeededOnlyOnceFromNaturalResources() {
        var moon = new Moon("moon", "Moon", "", 1, 1, 10000, 1000, "none", false, 0, List.of("silicates"), List.of());
        var planet = new Planet("planet", "Planet", "", 1, 1, 1, 0, 10000, "rock", "none", false, 0,
                List.of("iron_ore", "gold", "silicon", "nonexistent"), List.of(moon), List.of());
        var state = GameState.builder().solarSystems(List.of(new SolarSystem("system", "System", "", 0, 0, 0, 1, 1,
                "yellow", List.of(planet), List.of()))).build();
        var seeded = GeologicalSurveySeeder.seed(state);
        assertFalse(seeded.geologicalDeposits().isEmpty());
        assertTrue(seeded.geologicalDeposits().stream().noneMatch(GeologicalDeposit::isDiscovered));
        assertTrue(seeded.geologicalDeposits().stream().anyMatch(d -> d.planetId().equals("moon")));
        assertTrue(seeded.geologicalDeposits().stream().noneMatch(d -> d.materialId().equals("nonexistent")));
        assertEquals(seeded.geologicalDeposits(), GeologicalSurveySeeder.seed(state).geologicalDeposits());
        assertEquals(seeded.geologicalDeposits(), GeologicalSurveySeeder.seed(seeded).geologicalDeposits());
        var forced = new ProspectingProcessor(new Random() { @Override public double nextDouble() { return 0; } });
        var deposits = seeded.geologicalDeposits(); double volume = deposits.stream().mapToDouble(GeologicalDeposit::initialVolumeKg).sum();
        for (int pass = 0; pass < 100; pass++) deposits = forced.executeProspectingSurvey("planet", "owner", 1, 1, deposits);
        assertEquals(seeded.geologicalDeposits().size(), deposits.size());
        assertEquals(volume, deposits.stream().mapToDouble(GeologicalDeposit::initialVolumeKg).sum());
        assertTrue(deposits.stream().filter(d -> d.planetId().equals("planet")).allMatch(GeologicalDeposit::isDiscovered));
    }
}
