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
        List<SurfaceTile> tiles,
        List<Integer> rowColumnCounts
) {
    public PlanetBiomeGrid(String planetId, int rows, int columns, List<SurfaceTile> tiles) {
        this(planetId, rows, columns, tiles, deriveRowColumns(rows, columns, tiles));
    }

    public PlanetBiomeGrid {
        if (tiles == null) tiles = List.of();
        if (rowColumnCounts == null || rowColumnCounts.isEmpty()) {
            rowColumnCounts = deriveRowColumns(rows, columns, tiles);
        }
    }

    private static List<Integer> deriveRowColumns(int rows, int columns, List<SurfaceTile> tiles) {
        if (rows <= 0 || tiles == null || tiles.isEmpty()) {
            return List.of();
        }
        int[] counts = new int[rows];
        for (SurfaceTile t : tiles) {
            if (t.row() >= 0 && t.row() < rows) {
                counts[t.row()]++;
            }
        }
        List<Integer> list = new ArrayList<>(rows);
        for (int c : counts) {
            list.add(c);
        }
        return List.copyOf(list);
    }

    public int totalTiles() {
        return tiles.size();
    }

    public int columnsInRow(int row) {
        if (row < 0 || row >= rows || rowColumnCounts == null || row >= rowColumnCounts.size()) {
            return 0;
        }
        return rowColumnCounts.get(row);
    }

    public SurfaceTile getTile(int index) {
        if (index >= 0 && index < tiles.size()) {
            return tiles.get(index);
        }
        return null;
    }

    public SurfaceTile getTile(int row, int col) {
        if (row < 0 || row >= rows || col < 0 || col >= columnsInRow(row)) {
            return null;
        }
        int offset = 0;
        for (int r = 0; r < row; r++) {
            offset += columnsInRow(r);
        }
        int index = offset + col;
        return getTile(index);
    }

    public List<SurfaceTile> getOrthogonalNeighbors(int index) {
        SurfaceTile center = getTile(index);
        if (center == null) return List.of();

        List<SurfaceTile> neighbors = new ArrayList<>();
        int r = center.row();
        int c = center.column();
        int currentCols = columnsInRow(r);

        // West neighbor in same row
        if (c - 1 >= 0) {
            SurfaceTile west = getTile(r, c - 1);
            if (west != null) neighbors.add(west);
        }

        // East neighbor in same row
        if (c + 1 < currentCols) {
            SurfaceTile east = getTile(r, c + 1);
            if (east != null) neighbors.add(east);
        }

        // North neighbor (spherical projection from row r to r-1)
        if (r - 1 >= 0) {
            int northCols = columnsInRow(r - 1);
            if (northCols > 0) {
                int northCol = (int) Math.floor(((c + 0.5) / Math.max(1, currentCols)) * northCols);
                northCol = Math.min(northCols - 1, Math.max(0, northCol));
                SurfaceTile north = getTile(r - 1, northCol);
                if (north != null) neighbors.add(north);
            }
        }

        // South neighbor (spherical projection from row r to r+1)
        if (r + 1 < rows) {
            int southCols = columnsInRow(r + 1);
            if (southCols > 0) {
                int southCol = (int) Math.floor(((c + 0.5) / Math.max(1, currentCols)) * southCols);
                southCol = Math.min(southCols - 1, Math.max(0, southCol));
                SurfaceTile south = getTile(r + 1, southCol);
                if (south != null) neighbors.add(south);
            }
        }

        return neighbors;
    }

    public PlanetBiomeGrid withUpdatedTile(SurfaceTile updatedTile) {
        if (updatedTile == null || updatedTile.tileIndex() < 0 || updatedTile.tileIndex() >= tiles.size()) {
            return this;
        }
        List<SurfaceTile> newTiles = new ArrayList<>(tiles);
        newTiles.set(updatedTile.tileIndex(), updatedTile);
        return new PlanetBiomeGrid(planetId, rows, columns, newTiles, rowColumnCounts);
    }
}
