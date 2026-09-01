package com.spaceconquest.engine.biome;

import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.industry.GeologicalDeposit;
import com.spaceconquest.engine.industry.IndustrialFacility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Procedural surface biome mapper, spherical adjacency graph builder and facility synergy resolver.
 */
public class BiomeAdjacencyProcessor {

    public record GridDimensions(int rows, int columns, List<Integer> rowColumns) {
        public GridDimensions(int rows, int columns) {
            this(rows, columns, List.of());
        }

        public int maxColumns() {
            return columns;
        }

        public int totalTiles() {
            if (rowColumns != null && !rowColumns.isEmpty()) {
                int sum = 0;
                for (int c : rowColumns) sum += c;
                return sum;
            }
            return rows * columns;
        }
    }

    public record BiomeSynergyResult(
            double throughputMultiplier,
            double powerGenerationMultiplier,
            double pollutionPenalty,
            List<String> activeSynergies
    ) {
        public static BiomeSynergyResult neutral() {
            return new BiomeSynergyResult(1.0, 1.0, 0.0, List.of());
        }
    }

    public GridDimensions calculateGridDimensions(double diameter, String bodyType) {
        if (bodyType != null && (bodyType.toUpperCase().contains("GAS") || bodyType.toUpperCase().contains("ICE_GIANT"))) {
            return new GridDimensions(0, 0, List.of());
        }
        if (diameter <= 0.0) {
            return new GridDimensions(5, 16, List.of(2, 10, 16, 10, 2));
        }
        if (diameter < 1000.0) {
            return new GridDimensions(2, 4, List.of(2, 4));
        }
        if (diameter < 4000.0) {
            return new GridDimensions(3, 8, List.of(2, 8, 2));
        }
        if (diameter < 9000.0) {
            return new GridDimensions(4, 10, List.of(2, 10, 10, 2));
        }
        if (diameter <= 16000.0) {
            return new GridDimensions(5, 16, List.of(2, 10, 16, 10, 2));
        }
        return new GridDimensions(6, 18, List.of(2, 10, 18, 18, 10, 2));
    }

    /**
     * Generates a procedural surface tile grid for a planet based on its physical properties.
     */
    public PlanetBiomeGrid generateDefaultGrid(Planet planet, List<GeologicalDeposit> deposits) {
        if (planet == null) {
            return new PlanetBiomeGrid("unknown_planet", 0, 0, List.of());
        }

        String pType = planet.type() != null ? planet.type().toUpperCase() : "TERRESTRIAL";
        if (pType.contains("GAS") || pType.contains("ICE_GIANT")) {
            return new PlanetBiomeGrid(planet.id(), 0, 0, List.of());
        }

        GridDimensions dims = calculateGridDimensions(planet.diameter(), pType);
        int rows = dims.rows();
        int maxCols = dims.columns();
        List<Integer> rowCols = dims.rowColumns();
        if (rows <= 0 || rowCols.isEmpty()) {
            return new PlanetBiomeGrid(planet.id(), 0, 0, List.of());
        }

        int totalCells = dims.totalTiles();
        List<SurfaceTile> tiles = new ArrayList<>(totalCells);
        boolean hasWater = planet.hasLiquidWater();
        String atmosphere = planet.atmosphere() != null ? planet.atmosphere().trim() : "";
        boolean isAirless = isAirlessBody(atmosphere, pType);

        List<String> planetDepositIds = extractDepositIds(planet.id(), deposits);

        int depositCursor = 0;
        int tileIndex = 0;

        for (int r = 0; r < rows; r++) {
            int colsInRow = rowCols.get(r);
            for (int c = 0; c < colsInRow; c++) {
                int index = tileIndex++;
                BiomeResolution res = resolveBiome(pType, isAirless, hasWater, r, c, rows, colsInRow, index);

                String assignedDepositId = null;
                if (!res.isLocked() && depositCursor < planetDepositIds.size()) {
                    if (index % 3 == 1 || index == 5 || index >= totalCells - (planetDepositIds.size() - depositCursor)) {
                        assignedDepositId = planetDepositIds.get(depositCursor++);
                    }
                }

                tiles.add(new SurfaceTile(index, r, c, res.biome(), assignedDepositId, null, res.isWater(), res.isLocked()));
            }
        }

        return new PlanetBiomeGrid(planet.id(), rows, maxCols, tiles, rowCols);
    }

    private boolean isAirlessBody(String atmosphere, String pType) {
        return atmosphere.isEmpty() || atmosphere.equalsIgnoreCase("None")
                || atmosphere.equalsIgnoreCase("Vacuum") || pType.contains("BARREN")
                || pType.contains("AIRLESS") || pType.contains("CRATER");
    }

    private List<String> extractDepositIds(String planetId, List<GeologicalDeposit> deposits) {
        List<String> planetDepositIds = new ArrayList<>();
        if (deposits != null) {
            for (GeologicalDeposit d : deposits) {
                if (planetId.equals(d.planetId())) {
                    planetDepositIds.add(d.id());
                }
            }
        }
        return planetDepositIds;
    }

    private record BiomeResolution(String biome, boolean isWater, boolean isLocked) {}

    private BiomeResolution resolveBiome(String pType, boolean isAirless, boolean hasWater,
                                         int r, int c, int rows, int colsInRow, int index) {
        if (pType.contains("VOLCANIC")) {
            String b = (index % 2 == 0) ? SurfaceTile.BIOME_VOLCANIC_RIDGE : SurfaceTile.BIOME_RADIOACTIVE_CRATER;
            return new BiomeResolution(b, false, false);
        }
        if (pType.contains("ICE") || pType.contains("FROZEN")) {
            String b = (index % 5 == 0) ? SurfaceTile.BIOME_MOUNTAIN_RANGE : SurfaceTile.BIOME_POLAR_ICE;
            return new BiomeResolution(b, false, false);
        }
        if (pType.contains("DESERT") || pType.contains("ARID")) {
            if (r == 0 || r == rows - 1) {
                String b = (c % 2 == 0) ? SurfaceTile.BIOME_BARREN_ROCK : SurfaceTile.BIOME_MOUNTAIN_RANGE;
                return new BiomeResolution(b, false, false);
            }
            String b = (index % 4 == 0) ? SurfaceTile.BIOME_BARREN_ROCK : SurfaceTile.BIOME_EQUATORIAL_DESERT;
            return new BiomeResolution(b, false, false);
        }
        if (isAirless) {
            String b = switch (index % 4) {
                case 0 -> SurfaceTile.BIOME_RADIOACTIVE_CRATER;
                case 1 -> SurfaceTile.BIOME_VOLCANIC_RIDGE;
                case 2 -> SurfaceTile.BIOME_MOUNTAIN_RANGE;
                default -> SurfaceTile.BIOME_BARREN_ROCK;
            };
            return new BiomeResolution(b, false, false);
        }

        return resolveTerrestrialBiome(hasWater, r, c, rows, colsInRow, index);
    }

    private BiomeResolution resolveTerrestrialBiome(boolean hasWater, int r, int c, int rows, int colsInRow, int index) {
        if (r == 0 || r == rows - 1) {
            if (c == 0) {
                return new BiomeResolution(SurfaceTile.BIOME_POLAR_ICE, false, false);
            } else if (hasWater) {
                return new BiomeResolution(SurfaceTile.BIOME_OCEANIC_SHELF, true, false);
            } else {
                return new BiomeResolution(SurfaceTile.BIOME_BARREN_ROCK, false, false);
            }
        }

        int midRow = rows / 2;
        boolean isEquator = (r == midRow);
        if (isEquator && (c == 5 || c == 10 || (colsInRow <= 4 && c == 0))) {
            return new BiomeResolution(SurfaceTile.BIOME_EQUATORIAL_DESERT, false, false);
        }
        if (hasWater) {
            return resolveWaterWorldTile(r, c, index);
        }
        return resolveDryTerrestrialTile(index);
    }

    private BiomeResolution resolveWaterWorldTile(int r, int c, int index) {
        boolean isLandCol = switch (r) {
            case 1 -> (c == 1 || c == 4 || c == 7);
            case 2 -> (c == 1 || c == 8 || c == 13);
            default -> (c == 2 || c == 6);
        };

        if (isLandCol && c != 5 && c != 10) {
            String biome;
            if (index % 5 == 0) {
                biome = SurfaceTile.BIOME_MOUNTAIN_RANGE;
            } else if (index % 7 == 0) {
                biome = SurfaceTile.BIOME_VOLCANIC_RIDGE;
            } else {
                biome = SurfaceTile.BIOME_TEMPERATE_PLAINS;
            }
            return new BiomeResolution(biome, false, false);
        }
        return new BiomeResolution(SurfaceTile.BIOME_OCEANIC_SHELF, true, false);
    }

    private BiomeResolution resolveDryTerrestrialTile(int index) {
        String biome;
        if (index % 3 == 0) {
            biome = SurfaceTile.BIOME_MOUNTAIN_RANGE;
        } else if (index % 5 == 0) {
            biome = SurfaceTile.BIOME_EQUATORIAL_DESERT;
        } else if (index % 7 == 0) {
            biome = SurfaceTile.BIOME_VOLCANIC_RIDGE;
        } else {
            biome = SurfaceTile.BIOME_TEMPERATE_PLAINS;
        }
        return new BiomeResolution(biome, false, false);
    }

    /**
     * Calculates the biome bonus and adjacency synergies for a facility situated on a given tile.
     */
    public BiomeSynergyResult calculateTileSynergy(
            PlanetBiomeGrid grid,
            int tileIndex,
            IndustrialFacility facility,
            Map<String, IndustrialFacility> allFacilitiesOnPlanet
    ) {
        if (grid == null || facility == null) {
            return BiomeSynergyResult.neutral();
        }

        SurfaceTile tile = grid.getTile(tileIndex);
        if (tile == null) {
            return BiomeSynergyResult.neutral();
        }

        double throughputMult = 1.0;
        double powerGenMult = 1.0;
        double pollutionPenalty = 0.0;
        List<String> synergies = new ArrayList<>();

        String appId = facility.applicationId() != null ? facility.applicationId().toLowerCase() : "";
        boolean isPower = appId.contains("power") || appId.contains("solar") || appId.contains("fusion") || appId.contains("fission") || appId.contains("geothermal");
        boolean isAgri = appId.contains("agri") || appId.contains("hydro") || appId.contains("food") || appId.contains("bio");
        boolean isHeavyIndustry = appId.contains("foundry") || appId.contains("metallurgy") || appId.contains("mine") || appId.contains("refin");

        // 1. Biome Synergies
        switch (tile.biomeType()) {
            case SurfaceTile.BIOME_EQUATORIAL_DESERT -> {
                if (isPower || appId.contains("solar")) {
                    powerGenMult *= 2.0;
                    synergies.add("Equatorial Solar Alignment (+100% Power Output)");
                }
            }
            case SurfaceTile.BIOME_VOLCANIC_RIDGE -> {
                if (isPower || appId.contains("geothermal")) {
                    powerGenMult *= 1.5;
                    synergies.add("Volcanic Geothermal Tap (+50% Power Output)");
                }
            }
            case SurfaceTile.BIOME_TEMPERATE_PLAINS, SurfaceTile.BIOME_OCEANIC_SHELF -> {
                if (isAgri) {
                    throughputMult *= 1.25;
                    synergies.add("Fertile Biosphere Growth (+25% Biomass / Food Yield)");
                }
            }
            case SurfaceTile.BIOME_MOUNTAIN_RANGE, SurfaceTile.BIOME_RADIOACTIVE_CRATER -> {
                if (isHeavyIndustry) {
                    throughputMult *= 1.25;
                    synergies.add("Mineral-Rich Tectonic Crust (+25% Processing Throughput)");
                }
            }
        }

        // 2. Deposit on Same Tile
        if (tile.hasDeposit() && isHeavyIndustry) {
            throughputMult *= 1.20;
            synergies.add("Direct Vein Colocation (+20% Extraction Throughput)");
        }

        // 3. Adjacency Bonuses
        List<SurfaceTile> neighbors = grid.getOrthogonalNeighbors(tileIndex);
        for (SurfaceTile neighbor : neighbors) {
            if (neighbor.hasDeposit() && isHeavyIndustry) {
                throughputMult *= 1.05;
                synergies.add("Adjacent Mineral Vein Logistics (+5% Extraction Efficiency)");
            }

            if (neighbor.isOccupied() && allFacilitiesOnPlanet != null) {
                IndustrialFacility neighborFac = allFacilitiesOnPlanet.get(neighbor.facilityId());
                if (neighborFac != null) {
                    String nApp = neighborFac.applicationId() != null ? neighborFac.applicationId().toLowerCase() : "";
                    boolean nIsPower = nApp.contains("power") || nApp.contains("solar") || nApp.contains("fusion") || nApp.contains("geothermal");
                    boolean nIsHeavy = nApp.contains("foundry") || nApp.contains("metallurgy") || nApp.contains("refin") || nApp.contains("mine");

                    if (nIsPower && isHeavyIndustry) {
                        throughputMult *= 1.15;
                        synergies.add("Adjacent High-Voltage Grid Coupling (+15% Heavy Industry Efficiency)");
                    }
                    if (nIsHeavy && isAgri) {
                        pollutionPenalty += 0.25;
                        synergies.add("Heavy Industrial Runoff Degradation (-25% Agricultural Yield)");
                    }
                }
            }
        }

        double finalThroughput = Math.max(0.10, throughputMult * (1.0 - pollutionPenalty));

        return new BiomeSynergyResult(finalThroughput, powerGenMult, pollutionPenalty, synergies);
    }
}
