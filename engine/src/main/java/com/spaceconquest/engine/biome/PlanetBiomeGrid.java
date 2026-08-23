package com.spaceconquest.engine.biome;

import java.util.ArrayList;
import java.util.List;

/**
 * Surface grid topography for a planet containing discrete tiles and facility positions.
 */
public record PlanetBiomeGrid(
        String planetId,
        int rows,
        int columns,
        List<SurfaceTile> tiles
) {
    public PlanetBiomeGrid {
        if (tiles == null) tiles = List.of();
    }

    public int totalTiles() {
        return rows * columns;
    }

    public SurfaceTile getTile(int index) {
        if (index >= 0 && index < tiles.size()) {
            return tiles.get(index);
        }
        return null;
    }

    public SurfaceTile getTile(int row, int col) {
        if (row < 0 || row >= rows || col < 0 || col >= columns) {
            return null;
        }
        int index = row * columns + col;
        return getTile(index);
    }

    public List<SurfaceTile> getOrthogonalNeighbors(int index) {
        SurfaceTile center = getTile(index);
        if (center == null) return List.of();

        List<SurfaceTile> neighbors = new ArrayList<>();
        int r = center.row();
        int c = center.column();

        SurfaceTile north = getTile(r - 1, c);
        SurfaceTile south = getTile(r + 1, c);
        SurfaceTile west = getTile(r, c - 1);
        SurfaceTile east = getTile(r, c + 1);

        if (north != null) neighbors.add(north);
        if (south != null) neighbors.add(south);
        if (west != null) neighbors.add(west);
        if (east != null) neighbors.add(east);

        return neighbors;
    }

    public PlanetBiomeGrid withUpdatedTile(SurfaceTile updatedTile) {
        if (updatedTile == null || updatedTile.tileIndex() < 0 || updatedTile.tileIndex() >= tiles.size()) {
            return this;
        }
        List<SurfaceTile> newTiles = new ArrayList<>(tiles);
        newTiles.set(updatedTile.tileIndex(), updatedTile);
        return new PlanetBiomeGrid(planetId, rows, columns, newTiles);
    }
}
