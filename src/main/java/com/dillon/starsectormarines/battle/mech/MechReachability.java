package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * Raw-cell negative proof shared by mech position pickers. This does not prove
 * chassis clearance: candidate fit and the eventual continuous route still do
 * that work. Membership reads the grid's revision-keyed component cache.
 */
final class MechReachability {
    private final NavigationGrid grid;
    private final int startX;
    private final int startY;
    private final boolean cardinalOnly;

    MechReachability(NavigationGrid grid, int startX, int startY) {
        this.grid = grid;
        this.startX = startX;
        this.startY = startY;
        cardinalOnly = GridPathfinder.USE_CARDINAL_NAVIGATION;
    }

    boolean contains(int x, int y) {
        return grid.arePathConnected(startX, startY, x, y, cardinalOnly);
    }
}
