package com.dillon.starsectormarines.battle.map;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * Per-cell rendering / categorization tags — what kind of cell is this
 * visually, not "can I path through it". Parallel to {@link NavigationGrid},
 * indexed by the same {@code (x, y)} space and sharing dimensions; together
 * they describe a battle map. Pathfinder, LOS, cover, and zone graph read
 * the nav grid; the renderer and a handful of placement filters read the
 * topology.
 *
 * <p>Split rationale: the nav grid was accreting per-cell metadata
 * ({@code STREET}, {@code RUBBLE}, {@code CROSSWALK}, etc.) that the
 * pathfinder didn't care about, and adding new rendering layers meant
 * widening the nav grid's flag word. Keeping the two concerns in separate
 * classes lets navigation stay focused on walkability/edges/cover and the
 * topology accumulate as many visual categories as needed.
 *
 * <p>Tag storage is a {@code long[]} so we have 64-bit headroom for future
 * cell categories without another widening.
 */
public class CellTopology {

    /**
     * Categorization tags. Strictly visual / placement-filter use — anything
     * the pathfinder or zone graph reads lives on {@link NavigationGrid}.
     */
    public enum Tag {
        /** Set on every cell that's a structural floor (interior or carved out via gen). Currently informational. */
        FLOOR,
        /** Set on cells that used to be walls and were knocked down. Renders with damaged-floor autotile. */
        RUBBLE,
        /** Outdoor walkable cell — renders with the road autotile. Building interiors and doorways have this cleared. */
        STREET,
        /** Street cell painted with pedestrian stripes. */
        CROSSWALK,
        /** Only meaningful when {@link #CROSSWALK} is set. True = stripes run E-W (pedestrian crossing N-S). */
        CROSSWALK_HORIZ,
        /** Private interior pavement inside a super-block — renders with the courtyard autotile, distinct from public road and indoor floor. */
        COURTYARD,
        /** Non-walkable AND visually a building wall — renders with the wall autotile. Set by {@link #tagDefaultWalls(NavigationGrid)} after generation. */
        WALL,
        /** Cell sits under a parked vehicle. Non-walkable on the nav grid, but doesn't render as wall art — the renderer's vehicle pass draws a truck sprite over it. */
        VEHICLE;

        public long mask() { return 1L << ordinal(); }
    }

    private final int width;
    private final int height;
    private final long[] flags;

    public CellTopology(int width, int height) {
        this.width = width;
        this.height = height;
        this.flags = new long[width * height];
    }

    public int getWidth()  { return width;  }
    public int getHeight() { return height; }

    public boolean inBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public int index(int x, int y) {
        return y * width + x;
    }

    public boolean hasTag(int x, int y, Tag tag) {
        if (!inBounds(x, y)) return false;
        return (flags[index(x, y)] & tag.mask()) != 0L;
    }

    public void setTag(int x, int y, Tag tag, boolean on) {
        if (!inBounds(x, y)) return;
        int idx = index(x, y);
        if (on) flags[idx] |=  tag.mask();
        else    flags[idx] &= ~tag.mask();
    }

    // ----- Typed wrappers (one-liner getter/setter per tag) -----

    public boolean hasFloor(int x, int y)                                 { return hasTag(x, y, Tag.FLOOR); }
    public void    setFloor(int x, int y, boolean v)                      { setTag(x, y, Tag.FLOOR, v); }
    public boolean isRubble(int x, int y)                                 { return hasTag(x, y, Tag.RUBBLE); }
    public void    setRubble(int x, int y, boolean v)                     { setTag(x, y, Tag.RUBBLE, v); }
    public boolean isStreet(int x, int y)                                 { return hasTag(x, y, Tag.STREET); }
    public void    setStreet(int x, int y, boolean v)                     { setTag(x, y, Tag.STREET, v); }
    public boolean isCrosswalk(int x, int y)                              { return hasTag(x, y, Tag.CROSSWALK); }
    public void    setCrosswalk(int x, int y, boolean v)                  { setTag(x, y, Tag.CROSSWALK, v); }
    public boolean isCrosswalkStripesHorizontal(int x, int y)             { return hasTag(x, y, Tag.CROSSWALK_HORIZ); }
    public void    setCrosswalkStripesHorizontal(int x, int y, boolean v) { setTag(x, y, Tag.CROSSWALK_HORIZ, v); }
    public boolean isCourtyard(int x, int y)                              { return hasTag(x, y, Tag.COURTYARD); }
    public void    setCourtyard(int x, int y, boolean v)                  { setTag(x, y, Tag.COURTYARD, v); }
    public boolean isWall(int x, int y)                                   { return hasTag(x, y, Tag.WALL); }
    public void    setWall(int x, int y, boolean v)                       { setTag(x, y, Tag.WALL, v); }
    public boolean isVehicle(int x, int y)                                { return hasTag(x, y, Tag.VEHICLE); }
    public void    setVehicle(int x, int y, boolean v)                    { setTag(x, y, Tag.VEHICLE, v); }

    /**
     * Flags every cell that's non-walkable on the supplied nav grid as
     * {@link Tag#WALL} on this topology. Call once after a generator finishes
     * carving walkable space — vehicles and other "non-walkable but not a
     * wall" props stamp after this sweep so they keep WALL cleared.
     */
    public void tagDefaultWalls(NavigationGrid nav) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!nav.isWalkable(x, y)) {
                    flags[index(x, y)] |= Tag.WALL.mask();
                }
            }
        }
    }
}
