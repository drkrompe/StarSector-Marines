package com.dillon.starsectormarines.battle.world.gen.ship;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The footprint of a room, as a mask of cells rather than a width and a height.
 *
 * <p>A rectangle is the easy case, not the model. Real shipboard compartments
 * wrap around a hull flare, notch past a trunk, and turn a corner to reach a
 * bulkhead, so the deck packs shapes the way tetris packs pieces — and a packer
 * built around {@code w x h} could never place any of them. Building on a mask
 * from the start means an L-shaped bay is a data change, not a rewrite of the
 * placer.
 *
 * <p>A shape carries no orientation. {@link #orientations()} returns the
 * distinct ways it can be laid down — four rotations, deduplicated, so a square
 * yields one and a rectangle two — and the placer is free to choose among them.
 *
 * <p>Two derived sets matter as much as the mask itself. {@link #wall()} is the
 * eight-neighbour ring the room reserves as bulkhead, which is what keeps rooms
 * from opening into each other along a shared edge or leaking diagonally.
 * {@link #doorways()} is the subset of that ring where a door could actually be
 * cut: cells with exactly one room cell behind them, so the door has a room on
 * one side and open deck on the other.
 */
public final class RoomShape {

    private final boolean[][] cells;
    private final int width;
    private final int height;
    private final int area;
    private final int[][] filled;
    private final int[][] wall;
    private final int[][] doorways;

    private RoomShape(boolean[][] cells) {
        this.cells = cells;
        this.width = cells.length;
        this.height = cells[0].length;

        List<int[]> filledCells = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (cells[x][y]) filledCells.add(new int[]{ x, y });
            }
        }
        if (filledCells.isEmpty()) {
            throw new IllegalArgumentException("a room shape needs at least one cell");
        }
        this.filled = filledCells.toArray(new int[0][]);
        this.area = filled.length;

        List<int[]> wallCells = new ArrayList<>();
        List<int[]> doorCells = new ArrayList<>();
        for (int x = -1; x <= width; x++) {
            for (int y = -1; y <= height; y++) {
                if (contains(x, y)) continue;
                int orthogonal = 0;
                int backX = 0;
                int backY = 0;
                for (int[] step : STEPS) {
                    if (!contains(x + step[0], y + step[1])) continue;
                    orthogonal++;
                    backX = step[0];
                    backY = step[1];
                }
                boolean touching = orthogonal > 0;
                for (int dx = -1; dx <= 1 && !touching; dx++) {
                    for (int dy = -1; dy <= 1 && !touching; dy++) {
                        touching = contains(x + dx, y + dy);
                    }
                }
                if (!touching) continue;
                wallCells.add(new int[]{ x, y });
                // Exactly one room cell behind it, so the cell opposite is open
                // deck; a doorway with rooms on two sides is a shared bulkhead.
                if (orthogonal == 1) {
                    doorCells.add(new int[]{ x, y, x - backX, y - backY });
                }
            }
        }
        this.wall = wallCells.toArray(new int[0][]);
        this.doorways = doorCells.toArray(new int[0][]);
    }

    private static final int[][] STEPS = { { 0, -1 }, { 0, 1 }, { -1, 0 }, { 1, 0 } };

    /** A plain rectangular compartment — the common case, and the only one authored so far. */
    public static RoomShape rectangle(int width, int height) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("a room needs a positive footprint");
        }
        boolean[][] cells = new boolean[width][height];
        for (boolean[] column : cells) {
            java.util.Arrays.fill(column, true);
        }
        return new RoomShape(cells);
    }

    /**
     * A shape drawn as rows of {@code #} for floor and anything else for
     * outside, top row first — the readable way to author a compartment that
     * turns a corner.
     */
    public static RoomShape of(String... rows) {
        if (rows.length == 0) throw new IllegalArgumentException("a room shape needs at least one row");
        int width = 0;
        for (String row : rows) width = Math.max(width, row.length());
        boolean[][] cells = new boolean[width][rows.length];
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                cells[x][y] = rows[y].charAt(x) == '#';
            }
        }
        return new RoomShape(cells);
    }

    /** Extent along x, as this shape is written. Not a length: the placer may turn it. */
    public int width() {
        return width;
    }

    /** Extent along y, as this shape is written. */
    public int height() {
        return height;
    }

    /** Cells of floor the room claims, excluding its bulkheads. */
    public int area() {
        return area;
    }

    /** The shorter side of the bounding box — the deck depth this room needs in its easiest orientation. */
    public int minExtent() {
        return Math.min(width, height);
    }

    public boolean contains(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height && cells[x][y];
    }

    /** Offsets of every floor cell. */
    public int[][] filled() {
        return filled;
    }

    /** Offsets of the eight-neighbour ring this room reserves as bulkhead. */
    public int[][] wall() {
        return wall;
    }

    /**
     * Bulkhead cells a door could be cut through, as
     * {@code {doorX, doorY, outsideX, outsideY}} — the door itself and the cell
     * it opens onto.
     */
    public int[][] doorways() {
        return doorways;
    }

    /** The distinct ways this shape can be laid down: up to four rotations, deduplicated. */
    public List<RoomShape> orientations() {
        Set<String> seen = new LinkedHashSet<>();
        List<RoomShape> distinct = new ArrayList<>();
        RoomShape current = this;
        for (int turn = 0; turn < 4; turn++) {
            if (seen.add(current.signature())) distinct.add(current);
            current = current.rotated();
        }
        return List.copyOf(distinct);
    }

    /** This shape turned a quarter turn clockwise. */
    private RoomShape rotated() {
        boolean[][] turned = new boolean[height][width];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                turned[height - 1 - y][x] = cells[x][y];
            }
        }
        return new RoomShape(turned);
    }

    private String signature() {
        StringBuilder text = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                text.append(cells[x][y] ? '#' : '.');
            }
            text.append('/');
        }
        return text.toString();
    }

    @Override
    public String toString() {
        return "RoomShape[" + width + "x" + height + ", " + area + " cells]";
    }
}
