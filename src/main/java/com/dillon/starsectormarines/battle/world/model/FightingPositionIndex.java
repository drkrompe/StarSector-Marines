package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.world.tiles.FightingPositionRole;

import java.util.Arrays;

/**
 * The battle's <b>fighting positions</b>: runs of prepared ground, large enough for a
 * full squad, that a marine can occupy. Immutable; {@link DoodadService} builds a fresh
 * one when the standing props change and hands out the current one.
 *
 * <p>Nothing is authored here. The index is read from the prepared-ground props actually
 * standing (those whose {@link FightingPositionRole} is not {@code NONE}), the way
 * frontage is read from apertures actually open, so every stamper that lays trench or
 * sandbag produces positions without being touched and a destroyed parapet shrinks or
 * dissolves the run it belonged to. A cell belongs to a position when it is
 * <ul>
 *   <li>a walkable cell occupied by a {@code TRENCH} prop,</li>
 *   <li>a walkable cell occupied by a {@code PARAPET} prop, or</li>
 *   <li>a walkable cardinal neighbour of a parapet cell, since a sandbag line can be
 *       manned from either side.</li>
 * </ul>
 * Cells are grouped into 4-connected <em>runs</em>. A run shorter than
 * {@link #MIN_RUN_CELLS} is still cover but is not a position and has no entry here.
 * The floor is a generation rule, not an occupancy rule: once a run qualifies any number
 * of marines may use it.
 *
 * <p>A position has no stored front. Trench cover is isotropic on its own cell and a
 * parapet already publishes facing cover onto both sides, so which side to man is the
 * threat bearing's choice, not the position's.
 *
 * <p>Run ids are dense ({@code 0..runCount()-1}) and numbered in row-major order of each
 * run's first cell, so the same props always yield the same ids. See
 * {@code fighting-positions.md}.
 */
public final class FightingPositionIndex {

    /** A run with fewer cells than a full squad is cover, not a fighting position. */
    public static final int MIN_RUN_CELLS = 12;

    /** Whether a cell can be stood on; supplied by the grid so the index never imports navigation. */
    @FunctionalInterface
    public interface Walkable {
        boolean test(int x, int y);
    }

    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DY = {0, 0, 1, -1};

    private final int width;
    private final int height;
    /** Run id per cell ({@code y * width + x}), or -1. */
    private final int[] runByCell;
    /** Cell indices per run, in discovery order. */
    private final int[][] runCells;

    private FightingPositionIndex(int width, int height, int[] runByCell, int[][] runCells) {
        this.width = width;
        this.height = height;
        this.runByCell = runByCell;
        this.runCells = runCells;
    }

    /** An index with no positions, for a map with no prepared ground. */
    public static FightingPositionIndex empty(int width, int height) {
        int[] none = new int[width * height];
        Arrays.fill(none, -1);
        return new FightingPositionIndex(width, height, none, new int[0][]);
    }

    /** Derives the positions from the props currently standing. */
    public static FightingPositionIndex build(int width, int height, Iterable<Doodad> props,
                                              Walkable walkable) {
        boolean[] member = new boolean[width * height];
        boolean any = false;
        for (Doodad d : props) {
            FightingPositionRole role = d.fightingPosition();
            if (role == FightingPositionRole.NONE) continue;
            for (int y = d.cellY; y < d.cellY + d.footprintCellsY; y++) {
                for (int x = d.cellX; x < d.cellX + d.footprintCellsX; x++) {
                    if (!inBounds(width, height, x, y)) continue;
                    if (walkable.test(x, y)) {
                        member[y * width + x] = true;
                        any = true;
                    }
                    if (role != FightingPositionRole.PARAPET) continue;
                    for (int k = 0; k < 4; k++) {
                        int nx = x + DX[k];
                        int ny = y + DY[k];
                        if (inBounds(width, height, nx, ny) && walkable.test(nx, ny)) {
                            member[ny * width + nx] = true;
                            any = true;
                        }
                    }
                }
            }
        }
        if (!any) return empty(width, height);
        return group(width, height, member);
    }

    private static FightingPositionIndex group(int width, int height, boolean[] member) {
        int[] runByCell = new int[width * height];
        Arrays.fill(runByCell, -1);
        int[][] runs = new int[8][];
        int runCount = 0;
        boolean[] seen = new boolean[member.length];
        int[] queue = new int[member.length];
        for (int start = 0; start < member.length; start++) {
            if (!member[start] || seen[start]) continue;
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            seen[start] = true;
            while (head < tail) {
                int cell = queue[head++];
                int cx = cell % width;
                int cy = cell / width;
                for (int k = 0; k < 4; k++) {
                    int nx = cx + DX[k];
                    int ny = cy + DY[k];
                    if (!inBounds(width, height, nx, ny)) continue;
                    int n = ny * width + nx;
                    if (!member[n] || seen[n]) continue;
                    seen[n] = true;
                    queue[tail++] = n;
                }
            }
            if (tail < MIN_RUN_CELLS) continue;
            if (runCount == runs.length) runs = Arrays.copyOf(runs, runCount * 2);
            int[] cells = Arrays.copyOf(queue, tail);
            for (int cell : cells) runByCell[cell] = runCount;
            runs[runCount++] = cells;
        }
        return new FightingPositionIndex(width, height, runByCell, Arrays.copyOf(runs, runCount));
    }

    private static boolean inBounds(int width, int height, int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    public boolean isFightingPositionCell(int x, int y) {
        return runIdAt(x, y) >= 0;
    }

    /** The run containing this cell, or -1 when it is not part of a fighting position. */
    public int runIdAt(int x, int y) {
        return inBounds(width, height, x, y) ? runByCell[y * width + x] : -1;
    }

    /** Number of qualifying runs. */
    public int runCount() {
        return runCells.length;
    }

    /** Cells in a run (always at least {@link #MIN_RUN_CELLS}). */
    public int runSize(int runId) {
        return runCells[runId].length;
    }

    public int runCellX(int runId, int i) {
        return runCells[runId][i] % width;
    }

    public int runCellY(int runId, int i) {
        return runCells[runId][i] / width;
    }
}
