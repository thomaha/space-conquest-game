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
        assertEquals(5, grid.rows());
        assertEquals(16, grid.columns());
        assertEquals(40, grid.tiles().size());
        assertEquals(40, grid.totalTiles());
        assertEquals(List.of(2, 10, 16, 10, 2), grid.rowColumnCounts());

        SurfaceTile northPole = grid.getTile(0, 0);
        assertNotNull(northPole);
        assertEquals(SurfaceTile.BIOME_POLAR_ICE, northPole.biomeType());

        SurfaceTile interiorTile = grid.getTile(1, 1);
        assertNotNull(interiorTile);
        List<SurfaceTile> neighbors = grid.getOrthogonalNeighbors(interiorTile.tileIndex());
        assertEquals(4, neighbors.size());
    }

    @Test
    void testDynamicGridDimensionsByDiameter() {
        // Tiny bodies (< 1,000 km) -> 2 rows (2, 4 -> 6 sectors)
        BiomeAdjacencyProcessor.GridDimensions tinyDims = processor.calculateGridDimensions(12.4, "ASTEROID");
        assertEquals(2, tinyDims.rows());
        assertEquals(4, tinyDims.columns());
        assertEquals(6, tinyDims.totalTiles());
        assertEquals(List.of(2, 4), tinyDims.rowColumns());

        // Small moons and dwarf planets (1,000 - 4,000 km) -> 3 rows (2, 8, 2 -> 12 sectors)
        BiomeAdjacencyProcessor.GridDimensions smallDims = processor.calculateGridDimensions(3474.0, "MOON");
        assertEquals(3, smallDims.rows());
        assertEquals(8, smallDims.columns());
        assertEquals(12, smallDims.totalTiles());
        assertEquals(List.of(2, 8, 2), smallDims.rowColumns());

        // Medium terrestrial bodies (4,000 - 9,000 km) -> 4 rows (2, 10, 10, 2 -> 24 sectors)
        BiomeAdjacencyProcessor.GridDimensions medDims = processor.calculateGridDimensions(6779.0, "TERRESTRIAL");
        assertEquals(4, medDims.rows());
        assertEquals(10, medDims.columns());
        assertEquals(24, medDims.totalTiles());
        assertEquals(List.of(2, 10, 10, 2), medDims.rowColumns());

        // Standard terrestrial planets (9,000 - 16,000 km) -> 5 rows (2, 10, 16, 10, 2 -> 40 sectors)
        BiomeAdjacencyProcessor.GridDimensions stdDims = processor.calculateGridDimensions(12742.0, "TERRESTRIAL");
        assertEquals(5, stdDims.rows());
        assertEquals(16, stdDims.columns());
        assertEquals(40, stdDims.totalTiles());
        assertEquals(List.of(2, 10, 16, 10, 2), stdDims.rowColumns());

        // Massive terrestrial worlds (> 16,000 km) -> 6 rows (2, 10, 18, 18, 10, 2 -> 60 sectors)
        BiomeAdjacencyProcessor.GridDimensions massiveDims = processor.calculateGridDimensions(22000.0, "SUPER_EARTH");
        assertEquals(6, massiveDims.rows());
        assertEquals(18, massiveDims.columns());
        assertEquals(60, massiveDims.totalTiles());
        assertEquals(List.of(2, 10, 18, 18, 10, 2), massiveDims.rowColumns());

        // Gas giant -> 0x0
        BiomeAdjacencyProcessor.GridDimensions gasDims = processor.calculateGridDimensions(139820.0, "GAS_GIANT");
        assertEquals(0, gasDims.rows());
        assertEquals(0, gasDims.columns());
        assertEquals(0, gasDims.totalTiles());

        // Ice giant -> 0x0
        BiomeAdjacencyProcessor.GridDimensions iceGiantDims = processor.calculateGridDimensions(49244.0, "ICE_GIANT");
        assertEquals(0, iceGiantDims.rows());
        assertEquals(0, iceGiantDims.columns());
        assertEquals(0, iceGiantDims.totalTiles());
    }

    @Test
    void testGasGiantEmptyGridGeneration() {
        Planet jupiter = new Planet(
                "jupiter", "Jupiter", "Gas giant",
                1.898e27, 2.53, 5.2, 1.3, 139820, "GAS_GIANT", "Hydrogen-Helium",
                false, 0.0, List.of("hydrogen", "helium"), List.of(), List.of()
        );

        PlanetBiomeGrid grid = processor.generateDefaultGrid(jupiter, List.of());
        assertNotNull(grid);
        assertEquals(0, grid.rows());
        assertEquals(0, grid.columns());
        assertEquals(0, grid.tiles().size());
        assertEquals(0, grid.totalTiles());
    }

    @Test
    void testSphericalLatitudeBiomeDistributionOnEarth() {
        PlanetBiomeGrid grid = processor.generateDefaultGrid(earth, deposits);
        assertEquals(5, grid.rows());
        assertEquals(16, grid.columns());
        assertEquals(40, grid.totalTiles());

        // Polar rows (0 and 4) have only 2 places each, simulating spherical curvature
        assertEquals(2, grid.columnsInRow(0));
        assertEquals(10, grid.columnsInRow(1));
        assertEquals(16, grid.columnsInRow(2));
        assertEquals(10, grid.columnsInRow(3));
        assertEquals(2, grid.columnsInRow(4));

        // Exactly 5% of Earth is permanent polar ice (2 tiles out of 40)
        long polarCount = grid.tiles().stream()
                .filter(t -> SurfaceTile.BIOME_POLAR_ICE.equals(t.biomeType()))
                .count();
        assertEquals(2, polarCount, "Earth must have exactly 5% permanent polar ice (2 tiles out of 40)");

        // Exactly 5% of Earth is desert (2 tiles out of 40)
        long desertCount = grid.tiles().stream()
                .filter(t -> SurfaceTile.BIOME_EQUATORIAL_DESERT.equals(t.biomeType()))
                .count();
        assertEquals(2, desertCount, "Earth must have exactly 5% desert (2 tiles out of 40)");

        // Exactly 70% of Earth is water / oceanic shelf (28 tiles out of 40)
        long waterCount = grid.tiles().stream()
                .filter(SurfaceTile::isWaterCovered)
                .count();
        assertEquals(28, waterCount, "Earth must have exactly 70% water / oceans (28 tiles out of 40)");

        // Remaining 20% of Earth is other land (8 tiles out of 40: plains, mountains, volcanic ridges)
        long landCount = grid.tiles().stream()
                .filter(t -> !t.isWaterCovered() && !SurfaceTile.BIOME_POLAR_ICE.equals(t.biomeType()) && !SurfaceTile.BIOME_EQUATORIAL_DESERT.equals(t.biomeType()))
                .count();
        assertEquals(8, landCount, "Earth must have 20% temperate, mountain, and volcanic land (8 tiles out of 40)");

        // Intermediate rows (1, 2, 3) must NOT contain polar ice
        for (int r = 1; r <= 3; r++) {
            for (int c = 0; c < grid.columnsInRow(r); c++) {
                assertNotEquals(SurfaceTile.BIOME_POLAR_ICE, grid.getTile(r, c).biomeType(),
                        "Intermediate row " + r + " col " + c + " should not be polar ice");
            }
        }
    }

    @Test
    void testAirlessRockyBodyGeneratesNoPolarIce() {
        Planet moon = new Planet(
                "luna", "The Moon", "Airless rocky body",
                7.34e22, 0.166, 1.0, 5.14, 3474, "MOON", "None",
                false, 0.0, List.of("silicates", "titanium"), List.of(), List.of()
        );

        PlanetBiomeGrid grid = processor.generateDefaultGrid(moon, List.of());
        assertEquals(3, grid.rows());
        assertEquals(8, grid.columns());
        assertEquals(12, grid.tiles().size());
        assertEquals(List.of(2, 8, 2), grid.rowColumnCounts());

        // Airless body must have zero polar ice tiles
        long polarCount = grid.tiles().stream()
                .filter(t -> SurfaceTile.BIOME_POLAR_ICE.equals(t.biomeType()))
                .count();
        assertEquals(0, polarCount, "Airless body should not generate polar ice");

        // Should generate crater, volcanic, mountain or barren rock biomes
        boolean hasCraters = grid.tiles().stream().anyMatch(t -> SurfaceTile.BIOME_RADIOACTIVE_CRATER.equals(t.biomeType()));
        boolean hasVolcanic = grid.tiles().stream().anyMatch(t -> SurfaceTile.BIOME_VOLCANIC_RIDGE.equals(t.biomeType()));
        boolean hasBarren = grid.tiles().stream().anyMatch(t -> SurfaceTile.BIOME_BARREN_ROCK.equals(t.biomeType()));

        assertTrue(hasCraters || hasVolcanic || hasBarren, "Airless body should feature crater or barren rock biomes");
    }

    @Test
    void testEdgeCasesNullPlanetNegativeDiameterZeroDeposits() {
        PlanetBiomeGrid nullGrid = processor.generateDefaultGrid(null, deposits);
        assertNotNull(nullGrid);
        assertEquals(0, nullGrid.totalTiles());

        Planet negPlanet = new Planet(
                "neg", "Negative", "Test",
                1.0, 1.0, 1.0, 0, -100, "TERRESTRIAL", "Oxygen",
                false, 0.0, List.of(), List.of(), List.of()
        );
        PlanetBiomeGrid negGrid = processor.generateDefaultGrid(negPlanet, null);
        assertNotNull(negGrid);
        assertEquals(5, negGrid.rows());
        assertEquals(16, negGrid.columns());
        assertEquals(40, negGrid.totalTiles());
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

        // Place heavy industry on tile 9 (row 1, col 1)
        IndustrialFacility heavyIndustry = new IndustrialFacility(
                "fac_heavy_01", "earth", "heavy_metallurgy_foundry", "terran_confederation",
                IndustrialFacility.PUBLIC_STATE, 1, 100, "metallurgist", false, 0.0
        );

        // Place agricultural dome on neighbor tile 10 (row 1, col 2)
        IndustrialFacility agriDome = new IndustrialFacility(
                "fac_agri_01", "earth", "hydroponics_agricultural_dome", "terran_confederation",
                IndustrialFacility.PUBLIC_STATE, 1, 80, "botanist", false, 0.0
        );

        Map<String, IndustrialFacility> facilityMap = new HashMap<>();
        facilityMap.put(heavyIndustry.id(), heavyIndustry);
        facilityMap.put(agriDome.id(), agriDome);

        // Update grid with occupied tiles
        SurfaceTile t9 = grid.getTile(9).withFacility(heavyIndustry.id());
        SurfaceTile t10 = grid.getTile(10).withFacility(agriDome.id());
        PlanetBiomeGrid updatedGrid = grid.withUpdatedTile(t9).withUpdatedTile(t10);

        BiomeAdjacencyProcessor.BiomeSynergyResult agriResult = processor.calculateTileSynergy(
                updatedGrid, 10, agriDome, facilityMap
        );

        assertNotNull(agriResult);
        assertEquals(0.25, agriResult.pollutionPenalty(), 0.001);
        assertTrue(agriResult.activeSynergies().stream().anyMatch(s -> s.contains("Pollution") || s.contains("Runoff")));
    }
}
