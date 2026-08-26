package com.dillon.starsectormarines.battle.command;

import java.util.ArrayList;
import java.util.List;

/** Stable two-dimensional public area partition shared by both Assault sides. */
public final class AssaultSectorLayout {
    private static final int MIN_SECTOR_DIM = 2;
    private static final int MAX_SECTOR_DIM = 3;
    private static final int TARGET_SECTOR_WIDTH = 30;
    private static final int TARGET_SECTOR_HEIGHT = 15;

    public record Sector(int index, int minX, int minY, int maxX, int maxY) {
        public int width() { return maxX - minX + 1; }
        public int height() { return maxY - minY + 1; }
        public int centerX() { return (minX + maxX) / 2; }
        public int centerY() { return (minY + maxY) / 2; }
    }

    private final int width;
    private final int height;
    private final int columns;
    private final int rows;
    private final List<Sector> sectors;

    private AssaultSectorLayout(int width, int height, int columns, int rows,
                                List<Sector> sectors) {
        this.width = width;
        this.height = height;
        this.columns = columns;
        this.rows = rows;
        this.sectors = List.copyOf(sectors);
    }

    public static AssaultSectorLayout create(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Assault layout requires positive dimensions");
        }
        int columns = Math.max(MIN_SECTOR_DIM, Math.min(MAX_SECTOR_DIM,
                width / TARGET_SECTOR_WIDTH));
        int rows = Math.max(MIN_SECTOR_DIM, Math.min(MAX_SECTOR_DIM,
                height / TARGET_SECTOR_HEIGHT));
        List<Sector> sectors = new ArrayList<>(columns * rows);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int minX = column * width / columns;
                int maxX = (column + 1) * width / columns - 1;
                int minY = row * height / rows;
                int maxY = (row + 1) * height / rows - 1;
                sectors.add(new Sector(row * columns + column,
                        minX, minY, maxX, maxY));
            }
        }
        return new AssaultSectorLayout(width, height, columns, rows, sectors);
    }

    public int width() { return width; }
    public int height() { return height; }
    public int columns() { return columns; }
    public int rows() { return rows; }
    public List<Sector> sectors() { return sectors; }

    public Sector sector(int index) {
        return index >= 0 && index < sectors.size() ? sectors.get(index) : null;
    }

    public int sectorForCell(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return -1;
        int column = Math.min(x * columns / width, columns - 1);
        int row = Math.min(y * rows / height, rows - 1);
        return row * columns + column;
    }
}
