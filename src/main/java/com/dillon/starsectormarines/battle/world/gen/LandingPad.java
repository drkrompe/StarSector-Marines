package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

/**
 * One authored shuttle berth produced by map generation. Unlike the old
 * single-cell LZ marker, a berth carries the clear footprint the ship needs
 * and the side of the map its approach corridor faces.
 */
public final class LandingPad {

    /** Why the berth exists; setup uses this to add facility-specific traffic. */
    public enum Purpose {
        CIVIC_LANDING_ZONE,
        CIVILIAN_SPACEPORT,
        FALLBACK
    }

    /** Cardinal direction from the pad center toward open approach airspace. */
    public enum Approach {
        NORTH(0, 1), SOUTH(0, -1), EAST(1, 0), WEST(-1, 0);

        public final int dx;
        public final int dy;

        Approach(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }
    }

    public final int centerX;
    public final int centerY;
    public final int halfWidth;
    public final int halfHeight;
    public final Approach approach;
    public final Purpose purpose;

    public LandingPad(int centerX, int centerY, int halfWidth, int halfHeight,
                      Approach approach) {
        this(centerX, centerY, halfWidth, halfHeight, approach,
                Purpose.CIVIC_LANDING_ZONE);
    }

    public LandingPad(int centerX, int centerY, int halfWidth, int halfHeight,
                      Approach approach, Purpose purpose) {
        if (halfWidth < 0 || halfHeight < 0) {
            throw new IllegalArgumentException("landing-pad half extents must be >= 0");
        }
        this.centerX = centerX;
        this.centerY = centerY;
        this.halfWidth = halfWidth;
        this.halfHeight = halfHeight;
        this.approach = approach == null ? Approach.NORTH : approach;
        this.purpose = purpose == null ? Purpose.CIVIC_LANDING_ZONE : purpose;
    }

    /** Standard civilian berth: a clear 5x5 footprint around its marker. */
    public static LandingPad civilian(int centerX, int centerY, Approach approach) {
        return new LandingPad(centerX, centerY, 2, 2, approach,
                Purpose.CIVIC_LANDING_ZONE);
    }

    /** Standard berth inside a campaign-backed civilian spaceport district. */
    public static LandingPad spaceport(int centerX, int centerY, Approach approach) {
        return new LandingPad(centerX, centerY, 2, 2, approach,
                Purpose.CIVILIAN_SPACEPORT);
    }

    /** Backward-compatible one-cell berth for a dynamically selected LZ. */
    public static LandingPad fallback(int centerX, int centerY) {
        return new LandingPad(centerX, centerY, 0, 0, Approach.NORTH,
                Purpose.FALLBACK);
    }

    public int left()   { return centerX - halfWidth; }
    public int right()  { return centerX + halfWidth; }
    public int bottom() { return centerY - halfHeight; }
    public int top()    { return centerY + halfHeight; }

    public boolean contains(int x, int y) {
        return x >= left() && x <= right() && y >= bottom() && y <= top();
    }

    /**
     * True when every cell reserved for the berth remains walkable and outside
     * a building after setup-time vehicle/defense stamping.
     */
    public boolean isClear(NavigationGrid grid, CellTopology topology) {
        for (int y = bottom(); y <= top(); y++) {
            for (int x = left(); x <= right(); x++) {
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) return false;
                if (topology != null && topology.getBuildingId(x, y) != 0) return false;
            }
        }
        return true;
    }
}
