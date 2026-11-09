package com.dillon.starsectormarines.battle.door;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

/**
 * Walls nothing breaks: a vault's shell, so a heavy cannot be driven through it and a charge cannot open it
 * ({@code garrisonables.md}, Locked doors and hardened walls). A hardened cell carries
 * {@link NavigationGrid.CellTag#HARDENED}, which every way a wall comes down refuses: a blast's or a round's wall
 * damage ({@code NavigationGrid#damageCell}, {@code MapEditor#damageWall}), a shared edge's, a heavy hull's crush and
 * push ({@code WallCrush}), a ram ({@code BarricadeRam}) and a demolished emplacement's rubble. Movement, sight and cover
 * are unchanged. Opening a cell as passage ({@code MapEditor#openPassage}) clears the mark, which is how a
 * {@link LockedDoor} opens through a hardened wall.
 */
public final class HardenedWalls {

    private HardenedWalls() { }

    /**
     * Hardens every standing wall cell in the inclusive box: cells that are closed and walls in the topology, not the
     * open floor, props or fixtures inside it. Returns how many cells it hardened.
     */
    public static int harden(NavigationGrid grid, CellTopology topology, int left, int bottom, int right, int top) {
        int n = 0;
        for (int y = bottom; y <= top; y++) {
            for (int x = left; x <= right; x++) {
                if (!grid.inBounds(x, y) || grid.isWalkable(x, y) || !topology.isWall(x, y)) continue;
                grid.markHardened(x, y, true);
                n++;
            }
        }
        return n;
    }

    /** Whether the cell is hardened. */
    public static boolean hardened(NavigationGrid grid, int x, int y) {
        return grid.hasTag(x, y, NavigationGrid.CellTag.HARDENED);
    }
}
