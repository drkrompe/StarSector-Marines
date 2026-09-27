package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * Raw-cell negative proof shared by mech position pickers. This does not prove
 * chassis clearance: candidate fit and the eventual continuous route still do
 * that work. The normal path reuses the grid's revision-keyed component cache.
 */
final class MechReachability {
    private static final boolean REUSE_COMPONENTS = Boolean.parseBoolean(
            System.getProperty("battle.pathfinding.mechReachabilityComponents", "true"));

    private final NavigationGrid grid;
    private final int startX;
    private final int startY;
    private final boolean cardinalOnly;
    private final int[] legacyLabels;

    MechReachability(NavigationGrid grid, int startX, int startY) {
        this(grid, startX, startY, REUSE_COMPONENTS);
    }

    MechReachability(NavigationGrid grid, int startX, int startY,
                     boolean reuseComponents) {
        this.grid = grid;
        this.startX = startX;
        this.startY = startY;
        cardinalOnly = GridPathfinder.USE_CARDINAL_NAVIGATION;
        // The same-build control preserves the old eager, per-picker flood.
        legacyLabels = reuseComponents ? null : GridPathfinder.labelConnectedComponents(grid);
    }

    boolean contains(int x, int y) {
        if (legacyLabels == null) {
            return grid.arePathConnected(startX, startY, x, y, cardinalOnly);
        }
        if (!grid.inBounds(startX, startY) || !grid.inBounds(x, y)) return false;
        int startComponent = legacyLabels[grid.index(startX, startY)];
        return startComponent >= 0 && startComponent == legacyLabels[grid.index(x, y)];
    }
}
