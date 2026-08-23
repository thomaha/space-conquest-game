package com.spaceconquest.engine.biome;

/**
 * Represents a discrete surface tile on a planetary body.
 *
 * @param tileIndex       linear index on the planet grid (0 to N-1)
 * @param row             row coordinate on the surface grid
 * @param column          column coordinate on the surface grid
 * @param biomeType       regional biome classification (POLAR_ICE, EQUATORIAL_DESERT, etc.)
 * @param depositId       optional geological mineral deposit on this tile
 * @param facilityId      optional industrial facility situated on this tile
 * @param isWaterCovered  whether the tile is submerged beneath oceanic water
 * @param isLocked        whether extreme tectonic or radiation conditions prevent building
 */
public record SurfaceTile(
        int tileIndex,
        int row,
        int column,
        String biomeType,
        String depositId,
        String facilityId,
        boolean isWaterCovered,
        boolean isLocked
) {
    public static final String BIOME_POLAR_ICE = "POLAR_ICE";
    public static final String BIOME_EQUATORIAL_DESERT = "EQUATORIAL_DESERT";
    public static final String BIOME_VOLCANIC_RIDGE = "VOLCANIC_RIDGE";
    public static final String BIOME_OCEANIC_SHELF = "OCEANIC_SHELF";
    public static final String BIOME_MOUNTAIN_RANGE = "MOUNTAIN_RANGE";
    public static final String BIOME_TEMPERATE_PLAINS = "TEMPERATE_PLAINS";
    public static final String BIOME_RADIOACTIVE_CRATER = "RADIOACTIVE_CRATER";
    public static final String BIOME_BARREN_ROCK = "BARREN_ROCK";

    public boolean isOccupied() {
        return facilityId != null && !facilityId.isEmpty();
    }

    public boolean hasDeposit() {
        return depositId != null && !depositId.isEmpty();
    }

    public SurfaceTile withFacility(String newFacilityId) {
        return new SurfaceTile(tileIndex, row, column, biomeType, depositId, newFacilityId, isWaterCovered, isLocked);
    }

    public SurfaceTile withDeposit(String newDepositId) {
        return new SurfaceTile(tileIndex, row, column, biomeType, newDepositId, facilityId, isWaterCovered, isLocked);
    }
}
