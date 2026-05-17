package com.dillon.starsectormarines.battle.map;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * Per-cell rendering / categorization state — what kind of cell is this
 * visually, not "can I path through it". Parallel to {@link NavigationGrid},
 * indexed by the same {@code (x, y)} space and sharing dimensions; together
 * they describe a battle map. Pathfinder, LOS, cover, and zone graph read
 * the nav grid; the renderer and a handful of placement filters read the
 * topology.
 *
 * <p>Two axes of state per cell:
 * <ul>
 *   <li>{@link GroundKind} — exactly one per cell. The ground surface the
 *       renderer paints under everything else (asphalt, grass, water, indoor
 *       floor, rubble, etc.). Stored as a byte per cell.</li>
 *   <li>{@link Tag} bitmask — orthogonal overlays that can layer on any
 *       ground kind: WALL (non-walkable building), VEHICLE (parked truck on
 *       top of ground), CROSSWALK (stripe decoration on STREET ground).
 *       Stored as a long per cell.</li>
 * </ul>
 *
 * <p>Split rationale: a cell having ONE ground type and a SET of overlays
 * maps cleanly onto how the renderer paints layers and how generators reason
 * about placement. The previous "every concept is a separate boolean tag"
 * system meant a cell could nominally be both STREET and COURTYARD at once;
 * the new model makes that impossible by construction.
 *
 * <p>Walkability lives on {@link NavigationGrid}, not here. WATER cells are
 * non-walkable on the grid AND have {@code GroundKind.WATER} on the topology
 * — both sides set independently.
 */
public class CellTopology {

    /**
     * The ground surface the renderer paints at this cell under any overlays.
     * Exactly one value per cell. Default {@link #INDOOR} (zero ordinal); the
     * generator overrides per cell as it carves outdoor / wet / damaged
     * surfaces.
     */
    public enum GroundKind {
        /** Light beige indoor floor (urban-1 floor 3×3). Carved building interiors + doorways. Default for un-set cells. */
        INDOOR,
        /** Gray asphalt road (urban-2 road 3×3). Public outdoor pavement. */
        STREET,
        /** Dark navy stone (urban-2 courtyard 3×3). Private interior pavement inside a super-block. */
        COURTYARD,
        /** Green grass blob (Floors_Tiles). Parks, lawns, vegetation. */
        GRASS,
        /** Brown dirt blob (Floors_Tiles). Industrial yards, wastelands, unpaved ground. */
        DIRT,
        /** Gray stone blob (Floors_Tiles). Plaza paths, monuments, hardscape detail. */
        STONE,
        /** Beige sand blob (Floors_Tiles). Waterfront shore strips; reused for desert-biome ground later. */
        SAND,
        /** White snow blob (Floors_Tiles). Frozen-biome ground — defined now, generator wires later. */
        SNOW,
        /** Water (Water_tiles). Non-walkable on the nav grid; this kind tells the renderer to paint the surface. */
        WATER,
        /** Tiled floor cluster (Floors_Tiles fl-tile-*). Plaza centers, polished commercial interiors. */
        TILE,
        /** Yellow-striped factory/safety floor (urban-2 fl-striped 3×3). Fortified posts, landing-zone aprons. */
        STRIPED,
        /** Landing-zone center marker (urban-2 grate, placeholder until real LZ art). Touchdown cell decal. */
        LZ_MARKER,
        /** Damaged floor (urban-1 damaged-floor 3×3). Cells that were walls and got knocked down. */
        RUBBLE,
    }

    /**
     * Orthogonal per-cell flags that may overlay any {@link GroundKind}.
     * WALL/VEHICLE render their own art on top of the ground; CROSSWALK is a
     * stripe overlay specifically meaningful on {@code STREET} ground.
     */
    public enum Tag {
        /** Non-walkable building wall. Renders with the wall autotile; ground underneath is normally hidden. */
        WALL,
        /** Cell sits under a parked vehicle. Non-walkable on the nav grid; doesn't render as wall art — vehicle sprite draws over the ground. */
        VEHICLE,
        /** Street cell painted with pedestrian stripes. Only meaningful when the ground kind is {@link GroundKind#STREET}. */
        CROSSWALK,
        /** Only meaningful when {@link #CROSSWALK} is set. True = stripes run E-W (pedestrian crossing N-S). */
        CROSSWALK_HORIZ;

        public long mask() { return 1L << ordinal(); }
    }

    private static final GroundKind[] GROUND_KINDS = GroundKind.values();

    private final int width;
    private final int height;
    private final byte[] ground;
    private final long[] flags;

    public CellTopology(int width, int height) {
        this.width = width;
        this.height = height;
        this.ground = new byte[width * height];
        this.flags  = new long[width * height];
        // ground[i] == 0 == GroundKind.INDOOR.ordinal() — implicit default.
    }

    public int getWidth()  { return width;  }
    public int getHeight() { return height; }

    public boolean inBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public int index(int x, int y) {
        return y * width + x;
    }

    // ----- GroundKind -----

    public GroundKind getGroundKind(int x, int y) {
        if (!inBounds(x, y)) return GroundKind.INDOOR;
        return GROUND_KINDS[ground[index(x, y)]];
    }

    public void setGroundKind(int x, int y, GroundKind kind) {
        if (!inBounds(x, y)) return;
        ground[index(x, y)] = (byte) kind.ordinal();
    }

    /** Predicate sugar so the migrated call sites read the same as before. */
    public boolean isStreet(int x, int y)    { return getGroundKind(x, y) == GroundKind.STREET; }
    /** Predicate sugar. */
    public boolean isCourtyard(int x, int y) { return getGroundKind(x, y) == GroundKind.COURTYARD; }
    /** Predicate sugar. */
    public boolean isRubble(int x, int y)    { return getGroundKind(x, y) == GroundKind.RUBBLE; }
    /** Predicate sugar — water cells (non-walkable per nav grid). */
    public boolean isWater(int x, int y)     { return getGroundKind(x, y) == GroundKind.WATER; }

    // ----- Tag flags -----

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

    // Typed wrappers — one-liner getter/setter per flag.

    public boolean isWall(int x, int y)                                    { return hasTag(x, y, Tag.WALL); }
    public void    setWall(int x, int y, boolean v)                        { setTag(x, y, Tag.WALL, v); }
    public boolean isVehicle(int x, int y)                                 { return hasTag(x, y, Tag.VEHICLE); }
    public void    setVehicle(int x, int y, boolean v)                     { setTag(x, y, Tag.VEHICLE, v); }
    public boolean isCrosswalk(int x, int y)                               { return hasTag(x, y, Tag.CROSSWALK); }
    public void    setCrosswalk(int x, int y, boolean v)                   { setTag(x, y, Tag.CROSSWALK, v); }
    public boolean isCrosswalkStripesHorizontal(int x, int y)              { return hasTag(x, y, Tag.CROSSWALK_HORIZ); }
    public void    setCrosswalkStripesHorizontal(int x, int y, boolean v)  { setTag(x, y, Tag.CROSSWALK_HORIZ, v); }

    /**
     * Flags every cell that's non-walkable on the supplied nav grid as
     * {@link Tag#WALL} on this topology. Call once after a generator finishes
     * carving walkable space — vehicles and other "non-walkable but not a
     * wall" props stamp after this sweep so they keep WALL cleared. Water
     * cells should ALSO have their {@code GroundKind} set to WATER before
     * this call so the wall pass can skip them via {@link #isWater}.
     */
    public void tagDefaultWalls(NavigationGrid nav) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!nav.isWalkable(x, y) && !isWater(x, y)) {
                    flags[index(x, y)] |= Tag.WALL.mask();
                }
            }
        }
    }
}
