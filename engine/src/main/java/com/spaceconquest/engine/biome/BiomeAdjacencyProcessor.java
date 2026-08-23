package com.spaceconquest.engine.biome;

import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.industry.GeologicalDeposit;
import com.spaceconquest.engine.industry.IndustrialFacility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculates planetary biome bonuses, tile adjacency synergies and pollution penalties.
 */
public class BiomeAdjacencyProcessor {

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

    /**
     * Generates a procedural surface tile grid for a planet based on its physical properties.
     */
    public PlanetBiomeGrid generateDefaultGrid(Planet planet, List<GeologicalDeposit> deposits) {
        if (planet == null) {
            return new PlanetBiomeGrid("unknown_planet", 4, 4, List.of());
        }

        int rows = 4;
        int cols = 4;
        List<SurfaceTile> tiles = new ArrayList<>(rows * cols);
        String pType = planet.type() != null ? planet.type().toUpperCase() : "TERRESTRIAL";
        boolean hasWater = planet.hasLiquidWater();

        List<String> planetDepositIds = new ArrayList<>();
        if (deposits != null) {
            for (GeologicalDeposit d : deposits) {
                if (planet.id().equals(d.planetId())) {
                    planetDepositIds.add(d.id());
                }
            }
        }

        int depositCursor = 0;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int index = r * cols + c;
                String biome;
                boolean isWater = false;
                boolean isLocked = false;

                if (pType.contains("GAS")) {
                    biome = SurfaceTile.BIOME_BARREN_ROCK;
                    isLocked = true;
                } else if (pType.contains("VOLCANIC")) {
                    biome = (index % 2 == 0) ? SurfaceTile.BIOME_VOLCANIC_RIDGE : SurfaceTile.BIOME_RADIOACTIVE_CRATER;
                } else if (pType.contains("ICE") || pType.contains("FROZEN")) {
                    biome = SurfaceTile.BIOME_POLAR_ICE;
                } else if (pType.contains("DESERT")) {
                    biome = (r == 1 || r == 2) ? SurfaceTile.BIOME_EQUATORIAL_DESERT : SurfaceTile.BIOME_BARREN_ROCK;
                } else {
                    // Terrestrial / Oceanic / Earth-like
                    if (r == 0 || r == rows - 1) {
                        biome = SurfaceTile.BIOME_POLAR_ICE;
                    } else if (r == 1 && c < 2 && hasWater) {
                        biome = SurfaceTile.BIOME_OCEANIC_SHELF;
                        isWater = true;
                    } else if (r == 2 && c >= 2) {
                        biome = SurfaceTile.BIOME_EQUATORIAL_DESERT;
                    } else if (index % 3 == 0) {
                        biome = SurfaceTile.BIOME_MOUNTAIN_RANGE;
                    } else {
                        biome = SurfaceTile.BIOME_TEMPERATE_PLAINS;
                    }
                }

                String assignedDepositId = null;
                if (!isLocked && depositCursor < planetDepositIds.size() && (index % 3 == 1 || index == 5)) {
                    assignedDepositId = planetDepositIds.get(depositCursor++);
                }

                tiles.add(new SurfaceTile(index, r, c, biome, assignedDepositId, null, isWater, isLocked));
            }
        }

        return new PlanetBiomeGrid(planet.id(), rows, cols, tiles);
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

        // 2. Deposit on Current Tile
        if (tile.hasDeposit() && isHeavyIndustry) {
            throughputMult *= 1.20;
            synergies.add("Direct Vein Colocation (+20% Mining & Refining Throughput)");
        }

        // 3. Adjacency Bonuses & Penalties from Orthogonal Neighbors
        List<SurfaceTile> neighbors = grid.getOrthogonalNeighbors(tileIndex);
        for (SurfaceTile neighbor : neighbors) {
            if (neighbor.hasDeposit() && isHeavyIndustry) {
                throughputMult *= 1.10;
                synergies.add("Adjacent Mineral Deposit Logistics (+10% Throughput)");
            }

            if (neighbor.isOccupied() && allFacilitiesOnPlanet != null) {
                IndustrialFacility neighborFac = allFacilitiesOnPlanet.get(neighbor.facilityId());
                if (neighborFac != null) {
                    String nApp = neighborFac.applicationId() != null ? neighborFac.applicationId().toLowerCase() : "";
                    boolean nIsPower = nApp.contains("power") || nApp.contains("solar") || nApp.contains("fusion") || nApp.contains("geothermal");
                    boolean nIsHeavy = nApp.contains("foundry") || nApp.contains("metallurgy") || nApp.contains("refin") || nApp.contains("mine");

                    // Adjacency to Power Plant
                    if (nIsPower && isHeavyIndustry) {
                        throughputMult *= 1.15;
                        synergies.add("Adjacent High-Voltage Grid Coupling (+15% Heavy Industry Efficiency)");
                    }

                    // Pollution Degradation on Agriculture
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
