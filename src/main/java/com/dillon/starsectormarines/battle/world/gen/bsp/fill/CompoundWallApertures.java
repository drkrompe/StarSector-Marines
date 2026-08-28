package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

/**
 * Adds paired firing apertures to already-authored compound perimeter walls.
 * Only straight runs with a walkable cell on both sides qualify, so windows
 * never invent circulation, occupy a gate, or fire from an unusable pocket.
 */
final class CompoundWallApertures {

    private static final int MIN_RUN_LENGTH = 6;
    private static final int CELLS_PER_PAIR = 8;

    private CompoundWallApertures() {}

    static void stamp(boolean[][] inCompound, NavigationGrid grid,
                      CellTopology topology) {
        for (Facing facing : Facing.values()) {
            if (facing.horizontalRun) {
                for (int y = 1; y < grid.getHeight() - 1; y++) {
                    stampRow(inCompound, grid, topology, facing, y);
                }
            } else {
                for (int x = 1; x < grid.getWidth() - 1; x++) {
                    stampColumn(inCompound, grid, topology, facing, x);
                }
            }
        }
    }

    private static void stampRow(boolean[][] inCompound, NavigationGrid grid,
                                 CellTopology topology, Facing facing, int y) {
        int runStart = -1;
        for (int x = 1; x < grid.getWidth(); x++) {
            boolean candidate = x < grid.getWidth() - 1
                    && facingAt(inCompound, grid, topology, x, y) == facing;
            if (candidate && runStart < 0) runStart = x;
            if (candidate) continue;
            if (runStart >= 0) stampRun(grid, topology, runStart, x - 1, y, true);
            runStart = -1;
        }
    }

    private static void stampColumn(boolean[][] inCompound, NavigationGrid grid,
                                    CellTopology topology, Facing facing, int x) {
        int runStart = -1;
        for (int y = 1; y < grid.getHeight(); y++) {
            boolean candidate = y < grid.getHeight() - 1
                    && facingAt(inCompound, grid, topology, x, y) == facing;
            if (candidate && runStart < 0) runStart = y;
            if (candidate) continue;
            if (runStart >= 0) stampRun(grid, topology, runStart, y - 1, x, false);
            runStart = -1;
        }
    }

    private static Facing facingAt(boolean[][] inCompound, NavigationGrid grid,
                                   CellTopology topology, int x, int y) {
        if (grid.isWalkable(x, y) || grid.isDoorway(x, y)
                || grid.getWallHp(x, y) <= 0 || topology.isVehicle(x, y)) return null;
        Facing match = null;
        for (Facing facing : Facing.values()) {
            int insideX = x + facing.insideDx;
            int insideY = y + facing.insideDy;
            int outsideX = x - facing.insideDx;
            int outsideY = y - facing.insideDy;
            if (!inCompound[insideX][insideY] || inCompound[outsideX][outsideY]) continue;
            if (!grid.isWalkable(insideX, insideY)
                    || !grid.isWalkable(outsideX, outsideY)) continue;
            if (match != null) return null;
            match = facing;
        }
        return match;
    }

    private static void stampRun(NavigationGrid grid, CellTopology topology,
                                 int start, int end, int fixed, boolean row) {
        int length = end - start + 1;
        if (length < MIN_RUN_LENGTH) return;
        int count = Math.max(1, length / CELLS_PER_PAIR);
        for (int i = 0; i < count; i++) {
            int segmentStart = start + (i * length) / count;
            int segmentEnd = start + ((i + 1) * length) / count - 1;
            int pairStart = (segmentStart + segmentEnd - 1) / 2;
            pairStart = Math.max(start + 1, Math.min(end - 2, pairStart));
            if (openingNearPair(grid, topology, pairStart, fixed, row)) continue;
            stampWindow(grid, topology, pairStart, fixed, row);
            stampWindow(grid, topology, pairStart + 1, fixed, row);
        }
    }

    private static boolean openingNearPair(NavigationGrid grid, CellTopology topology,
                                           int pairStart, int fixed, boolean row) {
        for (int along = pairStart - 1; along <= pairStart + 2; along++) {
            int x = row ? along : fixed;
            int y = row ? fixed : along;
            if (grid.isDoorway(x, y) || topology.isWindow(x, y)) return true;
        }
        return false;
    }

    private static void stampWindow(NavigationGrid grid, CellTopology topology,
                                    int along, int fixed, boolean row) {
        int x = row ? along : fixed;
        int y = row ? fixed : along;
        grid.setSeeThrough(x, y, true);
        topology.setWindow(x, y, true);
    }

    private enum Facing {
        NORTH(0, -1, true),
        SOUTH(0, 1, true),
        WEST(-1, 0, false),
        EAST(1, 0, false);

        final int insideDx;
        final int insideDy;
        final boolean horizontalRun;

        Facing(int insideDx, int insideDy, boolean horizontalRun) {
            this.insideDx = insideDx;
            this.insideDy = insideDy;
            this.horizontalRun = horizontalRun;
        }
    }
}
