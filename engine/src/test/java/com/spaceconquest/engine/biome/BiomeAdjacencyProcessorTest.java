package com.spaceconquest.engine.biome;

import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.industry.GeologicalDeposit;
import com.spaceconquest.engine.industry.IndustrialFacility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BiomeAdjacencyProcessorTest {

    private BiomeAdjacencyProcessor processor;
    private Planet earth;
    private List<GeologicalDeposit> deposits;

    @BeforeEach
    void setUp() {
        processor = new BiomeAdjacencyProcessor();
        earth = new Planet(
                "earth", "Earth", "Terrestrial world",
                5.97e24, 1.0, 1.0, 0, 12742, "TERRESTRIAL", "Oxygen-Nitrogen",
                true, 0.71, List.of("water", "silicates"), List.of(), List.of()
        );

        deposits = List.of(
                new GeologicalDeposit("dep_iron_01", "earth", "iron_ore", 100000.0, 100000.0, 1.5, true, "public_state"),
                new GeologicalDeposit("dep_cu_01", "earth", "copper_ore", 50000.0, 50000.0, 1.2, true, "public_state")
        );
    }

    @Test
    void testGridGenerationAndTileTopology() {
        PlanetBiomeGrid grid = processor.generateDefaultGrid(earth, deposits);
        assertNotNull(grid);
        assertEquals("earth", grid.planetId());
        assertEquals(4, grid.rows());
        assertEquals(4, grid.columns());
        assertEquals(16, grid.tiles().size());

        SurfaceTile northPole = grid.getTile(0, 0);
        assertNotNull(northPole);
        assertEquals(SurfaceTile.BIOME_POLAR_ICE, northPole.biomeType());

        List<SurfaceTile> neighbors = grid.getOrthogonalNeighbors(5); // row 1, col 1
        assertEquals(4, neighbors.size());
    }

    @Test
    void testEquatorialSolarBiomeSynergy() {
        PlanetBiomeGrid grid = processor.generateDefaultGrid(earth, deposits);

        // Find equatorial desert tile
        int desertTileIndex = -1;
        for (SurfaceTile t : grid.tiles()) {
            if (SurfaceTile.BIOME_EQUATORIAL_DESERT.equals(t.biomeType())) {
                desertTileIndex = t.tileIndex();
                break;
            }
        }
        assertTrue(desertTileIndex >= 0);

        IndustrialFacility solarPlant = new IndustrialFacility(
                "fac_solar_01", "earth", "solar_power_array", "terran_confederation",
                IndustrialFacility.PUBLIC_STATE, 1, 50, "technician", false, 0.0
        );

        BiomeAdjacencyProcessor.BiomeSynergyResult result = processor.calculateTileSynergy(
                grid, desertTileIndex, solarPlant, Map.of()
        );

        assertNotNull(result);
        assertEquals(2.0, result.powerGenerationMultiplier(), 0.001);
        assertTrue(result.activeSynergies().stream().anyMatch(s -> s.contains("Solar Alignment")));
    }

    @Test
    void testDirectDepositAndAdjacencyBonuses() {
        PlanetBiomeGrid grid = processor.generateDefaultGrid(earth, deposits);

        // Find a tile with a deposit
        int depositTileIdx = -1;
        for (SurfaceTile t : grid.tiles()) {
            if (t.hasDeposit()) {
                depositTileIdx = t.tileIndex();
                break;
            }
        }
        assertTrue(depositTileIdx >= 0);

        IndustrialFacility foundry = new IndustrialFacility(
                "fac_foundry_01", "earth", "heavy_metallurgy_foundry", "terran_confederation",
                IndustrialFacility.PUBLIC_STATE, 1, 100, "metallurgist", false, 0.0
        );

        BiomeAdjacencyProcessor.BiomeSynergyResult result = processor.calculateTileSynergy(
                grid, depositTileIdx, foundry, Map.of()
        );

        assertNotNull(result);
        assertTrue(result.throughputMultiplier() >= 1.20);
        assertTrue(result.activeSynergies().stream().anyMatch(s -> s.contains("Vein Colocation") || s.contains("Throughput")));
    }

    @Test
    void testIndustrialPollutionDegradationOnAdjacentAgriculture() {
        PlanetBiomeGrid grid = processor.generateDefaultGrid(earth, deposits);

        // Place heavy industry on tile 5 (row 1, col 1)
        IndustrialFacility heavyIndustry = new IndustrialFacility(
                "fac_heavy_01", "earth", "heavy_metallurgy_foundry", "terran_confederation",
                IndustrialFacility.PUBLIC_STATE, 1, 100, "metallurgist", false, 0.0
        );

        // Place agricultural dome on neighbor tile 6 (row 1, col 2)
        IndustrialFacility agriDome = new IndustrialFacility(
                "fac_agri_01", "earth", "hydroponics_agricultural_dome", "terran_confederation",
                IndustrialFacility.PUBLIC_STATE, 1, 80, "botanist", false, 0.0
        );

        Map<String, IndustrialFacility> facilityMap = new HashMap<>();
        facilityMap.put(heavyIndustry.id(), heavyIndustry);
        facilityMap.put(agriDome.id(), agriDome);

        // Update grid with occupied tiles
        SurfaceTile t5 = grid.getTile(5).withFacility(heavyIndustry.id());
        SurfaceTile t6 = grid.getTile(6).withFacility(agriDome.id());
        PlanetBiomeGrid updatedGrid = grid.withUpdatedTile(t5).withUpdatedTile(t6);

        BiomeAdjacencyProcessor.BiomeSynergyResult agriResult = processor.calculateTileSynergy(
                updatedGrid, 6, agriDome, facilityMap
        );

        assertNotNull(agriResult);
        assertEquals(0.25, agriResult.pollutionPenalty(), 0.001);
        assertTrue(agriResult.activeSynergies().stream().anyMatch(s -> s.contains("Pollution") || s.contains("Runoff")));
    }
}
