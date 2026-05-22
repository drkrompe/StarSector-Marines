package com.dillon.starsectormarines.battle.map;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sprites.NatureTile;

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
        /** Indoor polished panel (urban-2 fl-2, single cell). Commercial-building floors — uniform across the interior, no autotile. */
        TILE,
        /** Brick-paver cluster (Floors_Tiles fl-tile-1..5). Plaza centers, building roofs (planned), large uniform paved areas. Five-variant pool for noise. Previously named {@code SIDEWALK} — renamed so the {@code SIDEWALK} slot could be repurposed for the urban-tileset-3 curb-side strip. */
        BRICK,
        /** Yellow-striped factory/safety floor (urban-2 fl-striped 3×3). Fortified posts, landing-zone aprons. */
        STRIPED,
        /** Landing-zone center marker (urban-2 grate, placeholder until real LZ art). Touchdown cell decal. */
        LZ_MARKER,
        /** Damaged floor (urban-1 damaged-floor 3×3). Cells that were walls and got knocked down. */
        RUBBLE,
        /** Curb-side sidewalk strip (urban-tileset-3 SIDEWALK / SIDEWALK_CORNER). Used for wide-road flanks where {@code STREET} cells aren't wall-adjacent and the render-time auto-detection can't pick them. Routes through the same urban-3 corner-aware picker the STREET-wall-adjacent path uses, so explicit {@code SIDEWALK} cells and implicit STREET-sidewalk cells join into one contiguous strip. */
        SIDEWALK,
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
        CROSSWALK_HORIZ,
        /** Roof above this building cell has caved in. Roof pass skips the cell; persistent (not driven by LOS), set when an adjacent wall collapses or a direct interior hit cracks the roof. Rubble decal is spawned at the moment of cave-in by the damage path. */
        ROOF_DESTROYED;

        public long mask() { return 1L << ordinal(); }
    }

    private static final GroundKind[] GROUND_KINDS = GroundKind.values();

    /**
     * Bits for the per-cell wall-direction mask. A wall cell whose
     * {@link #getWallDirMask} has e.g. {@link #WALL_DIR_N} set was placed
     * with "the exterior is on my north side." Walls are placed by building
     * stampers that know the building's geometry at gen time, so the mask
     * is set once at placement and stays constant under runtime mutations
     * (rubble appearing next door, etc.) — a wall doesn't reorient itself
     * just because its neighbor changed. Render code reads the mask as a
     * direct lookup, with no neighbor query.
     */
    public static final int WALL_DIR_N = 1;
    public static final int WALL_DIR_S = 2;
    public static final int WALL_DIR_E = 4;
    public static final int WALL_DIR_W = 8;

    private final int width;
    private final int height;
    private final byte[] ground;
    private final long[] flags;
    /**
     * Per-cell wall-direction mask. Bits are {@link #WALL_DIR_N} ... W.
     * Default 0 — meaningful only for cells tagged {@link Tag#WALL}; on
     * non-wall cells the render path never reads it. Set at gen time by
     * whatever code stamps walls (see {@link
     * com.dillon.starsectormarines.battle.mapgen.bsp.fill.BuildingShellCore}).
     */
    private final byte[] wallDir;
    /**
     * Per-cell building id, 0 = "not part of a building". Populated once at
     * gen time by {@link com.dillon.starsectormarines.battle.mapgen.bsp.BuildingFloodFill}
     * after all stamping has settled. Used by the roof-render and fog-of-war
     * visibility passes to find the cells of a given building cheaply.
     */
    private final short[] buildingId;
    /**
     * Per-cell building-kind hint, stored as {@code BuildingKind.ordinal() + 1}
     * so the implicit zero reads as "unset." Stampers (residential / commercial /
     * industrial / fortified shells) write their kind across their footprint;
     * the flood-fill votes the dominant hint per component to flavor the
     * resulting {@link com.dillon.starsectormarines.battle.map.Building}.
     */
    private final byte[] buildingKindHint;
    /**
     * Per-cell nature-tile overlay (plants, rocks). Stored as
     * {@code NatureTile.ordinal() + 1} so the implicit zero reads as "no
     * overlay." Set by nature-zone fillers (grassland / wetland / beach)
     * during gen; read by the renderer's nature-overlay pass after the
     * ground-tile flush so plant + rock sprites stack on top of the painted
     * surface. Only meaningful on walkable cells whose {@link GroundKind} is
     * a nature kind ({@link GroundKind#GRASS} / {@link GroundKind#DIRT} /
     * {@link GroundKind#SAND}) — wall + water cells ignore the slot.
     */
    private final byte[] natureOverlay;
    /**
     * Per-cell {@link RoomPurpose} label. Stored as {@code ordinal() + 1} so
     * the implicit zero reads as "no carver labeled this cell." Written by
     * carve-time partitioners that know which logical room a cell belongs to
     * (currently {@link com.dillon.starsectormarines.battle.mapgen.bsp.fill.BuildingShellCore}'s
     * partition step on opted-in {@link BuildingKind#FORTIFIED} sub-buildings).
     * Read by post-fill stampers and AI consumers that need to identify "which
     * chamber is this cell in?" without reverse-engineering via the zone graph.
     */
    private final byte[] roomPurpose;

    public CellTopology(int width, int height) {
        this.width = width;
        this.height = height;
        this.ground = new byte[width * height];
        this.flags  = new long[width * height];
        this.wallDir = new byte[width * height];
        this.buildingId = new short[width * height];
        this.buildingKindHint = new byte[width * height];
        this.natureOverlay = new byte[width * height];
        this.roomPurpose = new byte[width * height];
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
    public boolean isRoofDestroyed(int x, int y)                           { return hasTag(x, y, Tag.ROOF_DESTROYED); }
    public void    setRoofDestroyed(int x, int y, boolean v)               { setTag(x, y, Tag.ROOF_DESTROYED, v); }

    // ----- Wall direction mask -----

    /** Returns the wall-direction mask for this cell. 0 for non-wall cells. */
    public int getWallDirMask(int x, int y) {
        if (!inBounds(x, y)) return 0;
        return wallDir[index(x, y)] & 0xFF;
    }

    /** Replaces the wall-direction mask for this cell. Callers should pass a combination of {@link #WALL_DIR_N}/S/E/W. */
    public void setWallDirMask(int x, int y, int mask) {
        if (!inBounds(x, y)) return;
        wallDir[index(x, y)] = (byte) (mask & 0xFF);
    }

    /** Adds bits to this cell's wall-direction mask without disturbing existing bits. */
    public void orWallDirMask(int x, int y, int bits) {
        if (!inBounds(x, y)) return;
        int idx = index(x, y);
        wallDir[idx] = (byte) ((wallDir[idx] | bits) & 0xFF);
    }

    // ----- Building id -----

    /** Returns the building id for this cell, or 0 if not part of any building. */
    public int getBuildingId(int x, int y) {
        if (!inBounds(x, y)) return 0;
        return buildingId[index(x, y)] & 0xFFFF;
    }

    /** Sets the building id for this cell. Called by {@code BuildingFloodFill}. */
    public void setBuildingId(int x, int y, int id) {
        if (!inBounds(x, y)) return;
        buildingId[index(x, y)] = (short) id;
    }

    // ----- Building kind hint -----

    /**
     * Returns the building-kind hint for this cell, or {@code null} if no
     * stamper tagged it. Stored as {@code ordinal()+1} so the implicit zero
     * means "unset."
     */
    public BuildingKind getBuildingKindHint(int x, int y) {
        if (!inBounds(x, y)) return null;
        int raw = buildingKindHint[index(x, y)] & 0xFF;
        if (raw == 0) return null;
        BuildingKind[] vals = BuildingKind.values();
        int idx = raw - 1;
        return (idx >= 0 && idx < vals.length) ? vals[idx] : null;
    }

    /**
     * Stamps a building-kind hint across this cell. Idempotent — overwrites
     * any previous hint, so the most recent stamper wins (e.g. an industrial
     * yard re-stamping over a building footprint would supersede). Stampers
     * call this across their carved-interior footprint; the flood-fill reads
     * it back to assign {@link Building#kind}.
     */
    public void setBuildingKindHint(int x, int y, BuildingKind kind) {
        if (!inBounds(x, y)) return;
        buildingKindHint[index(x, y)] = (byte) (kind == null ? 0 : (kind.ordinal() + 1));
    }

    // ----- Nature overlay -----

    /**
     * Returns the nature overlay tile at this cell, or {@code null} if no
     * overlay is set. Stored as {@code ordinal()+1} so the implicit zero
     * means "unset."
     */
    public NatureTile getNatureOverlay(int x, int y) {
        if (!inBounds(x, y)) return null;
        int raw = natureOverlay[index(x, y)] & 0xFF;
        if (raw == 0) return null;
        NatureTile[] vals = NatureTile.values();
        int idx = raw - 1;
        return (idx >= 0 && idx < vals.length) ? vals[idx] : null;
    }

    /**
     * Stamps a nature overlay tile at this cell. Pass {@code null} to clear.
     * Caller is expected to have validated placement via
     * {@link NatureTile#canOverlay(NatureTile)} against the current ground
     * kind — the topology doesn't re-check here so a leaf-edge filler can
     * stamp atomically without per-cell predicate overhead.
     */
    public void setNatureOverlay(int x, int y, NatureTile tile) {
        if (!inBounds(x, y)) return;
        natureOverlay[index(x, y)] = (byte) (tile == null ? 0 : (tile.ordinal() + 1));
    }

    // ----- Room purpose -----

    /**
     * Returns the {@link RoomPurpose} label at this cell, or {@code null} if
     * no carver labeled it. Stored as {@code ordinal()+1} so the implicit zero
     * means "unset."
     */
    public RoomPurpose getRoomPurpose(int x, int y) {
        if (!inBounds(x, y)) return null;
        int raw = roomPurpose[index(x, y)] & 0xFF;
        if (raw == 0) return null;
        RoomPurpose[] vals = RoomPurpose.values();
        int idx = raw - 1;
        return (idx >= 0 && idx < vals.length) ? vals[idx] : null;
    }

    /**
     * Stamps a {@link RoomPurpose} label at this cell. Pass {@code null} to
     * clear. Stampers call this across a logical room's footprint (typically
     * every walkable non-doorway interior cell on one side of a partition);
     * post-fill consumers read it back to identify chambers without re-running
     * connectivity analysis.
     */
    public void setRoomPurpose(int x, int y, RoomPurpose purpose) {
        if (!inBounds(x, y)) return;
        roomPurpose[index(x, y)] = (byte) (purpose == null ? 0 : (purpose.ordinal() + 1));
    }

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
