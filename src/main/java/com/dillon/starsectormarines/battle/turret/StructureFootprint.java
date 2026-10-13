package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * The ground a {@link StructureDef} occupies, measured from the cell its body
 * stands on.
 *
 * <p>A structure's footprint is an odd number of cells on each axis, so the
 * anchor cell (the one its unit is spawned at) is the centre of the footprint
 * and the body's circle shares that centre. A one-cell structure is its anchor
 * cell alone, and every operation here reduces to exactly the single-cell
 * handling the emplacements always had.
 *
 * <p>Pure geometry plus the grid writes that follow from it; nothing here owns
 * state. {@code BattleSimulation.spawn} seals a multi-cell structure's footprint
 * as it is spawned and {@code TurretDemolitionSystem} opens it again on death.
 */
public final class StructureFootprint {

    private StructureFootprint() {}

    /** Lowest footprint column for a structure anchored at {@code anchorX}. */
    public static int minX(StructureDef def, int anchorX) {
        return anchorX - def.footprintCellsX / 2;
    }

    /** Lowest footprint row for a structure anchored at {@code anchorY}. */
    public static int minY(StructureDef def, int anchorY) {
        return anchorY - def.footprintCellsY / 2;
    }

    /** Whether {@code (x, y)} lies inside the footprint of a structure anchored at the given cell. */
    public static boolean contains(StructureDef def, int anchorX, int anchorY, int x, int y) {
        int x0 = minX(def, anchorX);
        int y0 = minY(def, anchorY);
        return x >= x0 && x < x0 + def.footprintCellsX
                && y >= y0 && y < y0 + def.footprintCellsY;
    }

    /**
     * Placement validity: every footprint cell is on the map and open ground.
     * A caller choosing where to put a structure asks this first; spawning does
     * not, because a stamped pad is deliberately sealed before its body arrives.
     */
    public static boolean canPlace(NavigationGrid grid, StructureDef def, int anchorX, int anchorY) {
        int x0 = minX(def, anchorX);
        int y0 = minY(def, anchorY);
        for (int dy = 0; dy < def.footprintCellsY; dy++) {
            for (int dx = 0; dx < def.footprintCellsX; dx++) {
                if (!grid.inBounds(x0 + dx, y0 + dy) || !grid.isWalkable(x0 + dx, y0 + dy)) return false;
            }
        }
        return true;
    }

    /**
     * Seals every footprint cell the way a one-cell emplacement seals its mount:
     * not walkable, so nobody paths through it, but see-through, because the live
     * body owns projectile interception through its radius. Cover is re-derived
     * on the ring around the footprint.
     */
    public static void seal(NavigationGrid grid, StructureDef def, int anchorX, int anchorY) {
        int x0 = minX(def, anchorX);
        int y0 = minY(def, anchorY);
        int x1 = x0 + def.footprintCellsX - 1;
        int y1 = y0 + def.footprintCellsY - 1;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                grid.setWalkable(x, y, false);
                grid.setSeeThrough(x, y, true);
            }
        }
        for (int y = y0; y <= y1; y++) grid.recomputeCoverAt(x1 + 1, y);
        for (int y = y0; y <= y1; y++) grid.recomputeCoverAt(x0 - 1, y);
        for (int x = x0; x <= x1; x++) grid.recomputeCoverAt(x, y1 + 1);
        for (int x = x0; x <= x1; x++) grid.recomputeCoverAt(x, y0 - 1);
    }
}
