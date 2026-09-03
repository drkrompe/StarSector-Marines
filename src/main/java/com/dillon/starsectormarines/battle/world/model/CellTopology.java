package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
import java.util.List;

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
     *       ground kind: WALL (non-walkable building), WINDOW (see-through
     *       firing aperture), VEHICLE (parked truck on top of ground),
     *       CROSSWALK (stripe decoration on STREET ground).
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
        /**
         * No deck here at all — outside the hull a ship encloses.
         *
         * <p>Distinct from a cell that is merely unwalkable. A wall is part of
         * the vessel and is drawn; this is the space she is not, and painting
         * it puts a floor outside the ship. Nothing draws it, which is what
         * lets a hull backdrop show through the shape she actually occupies.
         */
        VOID,
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
        ROOF_DESTROYED,
        /**
         * A non-walkable, non-structural physical feature such as a commercial
         * shelf run or large nature rock. Fixtures shape navigation but are not
         * walls: their own overlay/prop renders normally, and finalize does not
         * seed destructible wall HP.
         */
        FIXTURE,
        /**
         * A firing aperture in a structural wall. Window cells remain
         * non-walkable and destructible, but the nav grid marks them
         * see-through so sight and projectiles can cross the facade.
         */
        WINDOW;

        public long mask() { return 1L << ordinal(); }

        /**
         * Whether flipping this tag can change what the ground pass draws for
         * the cell, and therefore whether setting it enters the change log.
         *
         * <p>{@link #WALL} decides which of the two ground passes a cell is in
         * at all; the crosswalk pair paints stripes over a street. The rest are
         * read by other layers — a roof by the roof pass, a window by the
         * aperture pass, a fixture and a parked vehicle by navigation and by
         * their own props — and none of them moves a ground tile.
         */
        public boolean drawn() {
            return this == WALL || this == CROSSWALK || this == CROSSWALK_HORIZ;
        }
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
     * com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingShellCore}).
     */
    private final byte[] wallDir;
    /**
     * Per-cell building id, 0 = "not part of a building". Populated once at
     * gen time by {@link com.dillon.starsectormarines.battle.world.gen.bsp.BuildingFloodFill}
     * after all stamping has settled. Used by the roof-render and fog-of-war
     * visibility passes to find the cells of a given building cheaply.
     */
    private final short[] buildingId;
    /**
     * Per-cell building-kind hint, stored as {@code BuildingKind.ordinal() + 1}
     * so the implicit zero reads as "unset." Stampers (residential / commercial /
     * industrial / fortified shells) write their kind across their footprint;
     * the flood-fill votes the dominant hint per component to flavor the
     * resulting {@link com.dillon.starsectormarines.battle.world.model.Building}.
     */
    private final byte[] buildingKindHint;
    /**
     * Per-cell nature-tile overlay (plants, rocks). Stored as
     * {@code TileDef.index + 1} so the implicit zero reads as "no overlay."
     * Set by nature-zone fillers (grassland / wetland / beach) during gen;
     * read by the renderer's nature-overlay pass after the ground-tile flush
     * so plant + rock sprites stack on top of the painted surface. Only
     * meaningful on cells whose {@link GroundKind} is a nature kind
     * ({@link GroundKind#GRASS} / {@link GroundKind#DIRT} /
     * {@link GroundKind#SAND}) — wall + water cells ignore the slot.
     */
    private final short[] natureOverlay;
    /**
     * Per-cell {@link RoomPurpose} label. Stored as {@code ordinal() + 1} so
     * the implicit zero reads as "no carver labeled this cell." Written by
     * carve-time partitioners that know which logical room a cell belongs to
     * (currently {@link com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingShellCore}'s
     * partition step on opted-in {@link BuildingKind#FORTIFIED} sub-buildings).
     * Read by post-fill stampers and AI consumers that need to identify "which
     * chamber is this cell in?" without reverse-engineering via the zone graph.
     */
    private final byte[] roomPurpose;
    /**
     * Per-cell surface override, as an index into {@link #surfaces}. Zero — the
     * implicit default — means the cell draws the way its kind says: a wall as
     * {@link SurfaceRole#WALL}, a floor as its {@code GroundKind}'s block.
     *
     * <p>One array for walls and floors both, because a cell is one or the
     * other and never both. Which pass reads it decides what it means, so a
     * room asking for a vent floor and a room asking for a heavier bulkhead
     * spend the same byte.
     *
     * <p>A byte because this is one array over the whole grid and there are
     * never more than a handful of distinct bulkheads on a deck. Indices are
     * interned rather than assigned per room, so twelve berths asking for the
     * same wall share one.
     */
    private final byte[] surface;
    /**
     * Block ids the {@link #surface} indices name, index zero unused.
     *
     * <p>Held here rather than passed to the renderer separately because it is
     * a fact about these cells: a map carries its own bulkheads, and a renderer
     * handed the grid should not need a second argument to draw it.
     */
    private final List<String> surfaces = new ArrayList<>();

    /**
     * How many recent cell changes the log below remembers.
     *
     * <p>A reader that has fallen further behind than this is told to rebuild
     * from scratch instead, which is the right answer for it: the whole point of
     * the log is to make the handful of cells a breach touches cheap, and a
     * reader thousands of changes behind has been away long enough that walking
     * the map again costs less than replaying them.
     */
    private static final int CHANGE_LOG_CAPACITY = 4096;

    /** Ring of recently changed cell indices; see {@link #changedCellAt}. */
    private final int[] changeLog = new int[CHANGE_LOG_CAPACITY];

    /**
     * Cell changes recorded ever, and the cursor into {@link #changeLog}.
     *
     * <p>Monotonic and never reset, so a reader holds one {@code long} and can
     * always tell how far behind it is — including "further than the log goes".
     */
    private long changeCount;

    public CellTopology(int width, int height) {
        this.width = width;
        this.height = height;
        this.ground = new byte[width * height];
        this.flags  = new long[width * height];
        this.wallDir = new byte[width * height];
        this.buildingId = new short[width * height];
        this.buildingKindHint = new byte[width * height];
        this.natureOverlay = new short[width * height];
        this.roomPurpose = new byte[width * height];
        this.surface = new byte[width * height];
        // ground[i] == 0 == GroundKind.VOID.ordinal() — implicit default, so a
        // topology nobody has carved reads as outside the map rather than inside.
        // surface[i] == 0 — draws as SurfaceRole.WALL, the shared default.
    }

    public int getWidth()  { return width;  }
    public int getHeight() { return height; }

    public boolean inBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public int index(int x, int y) {
        return y * width + x;
    }

    // ----- Change log -----

    /**
     * Cell changes recorded since this topology was made.
     *
     * <p>A consumer that keeps a derived copy of the map — a resident render
     * mesh, most of all — holds the value it last caught up to and asks again
     * next frame. It exists because the alternative is rescanning every cell to
     * find the two that a breach moved, which on a 560x336 map is most of the
     * cost the derived copy was built to avoid.
     *
     * <p>What counts as a change is what a consumer could <em>draw</em>
     * differently: ground kind, wall tag, crosswalk tags, wall-direction mask,
     * nature overlay, and authored surface. Not building ids or room purposes,
     * which nothing paints from.
     */
    public long changeCount() {
        return changeCount;
    }

    /** How far behind {@link #changeCount} a reader may be and still catch up cell by cell. */
    public int changeLogCapacity() {
        return CHANGE_LOG_CAPACITY;
    }

    /**
     * The cell index recorded as the {@code sequence}-th change.
     *
     * <p>Valid for {@code sequence} in
     * {@code [changeCount() - changeLogCapacity(), changeCount())}; an older
     * sequence has been overwritten and the caller must rebuild rather than
     * catch up.
     */
    public int changedCellAt(long sequence) {
        return changeLog[(int) (sequence & (CHANGE_LOG_CAPACITY - 1))];
    }

    /**
     * Records that {@code idx} may now draw differently.
     *
     * <p>Unconditional rather than compare-and-record: generation writes these
     * arrays millions of times and a read-back to see whether the value actually
     * moved costs more than the two writes here. A duplicate entry makes a
     * consumer re-resolve one cell, which is cheap and correct.
     */
    private void markChanged(int idx) {
        changeLog[(int) (changeCount & (CHANGE_LOG_CAPACITY - 1))] = idx;
        changeCount++;
    }

    // ----- GroundKind -----

    public GroundKind getGroundKind(int x, int y) {
        if (!inBounds(x, y)) return GroundKind.INDOOR;
        return GROUND_KINDS[ground[index(x, y)]];
    }

    public void setGroundKind(int x, int y, GroundKind kind) {
        if (!inBounds(x, y)) return;
        int idx = index(x, y);
        ground[idx] = (byte) kind.ordinal();
        markChanged(idx);
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
        if (tag.drawn()) markChanged(idx);
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
    public boolean isFixture(int x, int y)                                 { return hasTag(x, y, Tag.FIXTURE); }
    public void    setFixture(int x, int y, boolean v)                     { setTag(x, y, Tag.FIXTURE, v); }
    public boolean isWindow(int x, int y)                                  { return hasTag(x, y, Tag.WINDOW); }
    public void    setWindow(int x, int y, boolean v)                      { setTag(x, y, Tag.WINDOW, v); }

    /** True iff the cell is part of a building and still has its intact roof — the aerial/indirect-fire shield discriminator. */
    public boolean isRoofIntact(int x, int y) {
        return getBuildingId(x, y) != 0 && !isRoofDestroyed(x, y);
    }

    // ----- Wall direction mask -----

    /** Returns the wall-direction mask for this cell. 0 for non-wall cells. */
    public int getWallDirMask(int x, int y) {
        if (!inBounds(x, y)) return 0;
        return wallDir[index(x, y)] & 0xFF;
    }

    /** Replaces the wall-direction mask for this cell. Callers should pass a combination of {@link #WALL_DIR_N}/S/E/W. */
    public void setWallDirMask(int x, int y, int mask) {
        if (!inBounds(x, y)) return;
        int idx = index(x, y);
        wallDir[idx] = (byte) (mask & 0xFF);
        markChanged(idx);
    }

    /** Adds bits to this cell's wall-direction mask without disturbing existing bits. */
    public void orWallDirMask(int x, int y, int bits) {
        if (!inBounds(x, y)) return;
        int idx = index(x, y);
        wallDir[idx] = (byte) ((wallDir[idx] | bits) & 0xFF);
        markChanged(idx);
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
     * Returns the dense registry index of the nature overlay tile at this cell,
     * or {@code -1} if no overlay is set. Stored as {@code TileDef.index + 1}
     * so the implicit zero means "unset." Resolve to a tile via
     * {@code TileRegistry.installed().byIndex(getNatureOverlayIndex(x, y))}.
     */
    public int getNatureOverlayIndex(int x, int y) {
        if (!inBounds(x, y)) return -1;
        int raw = natureOverlay[index(x, y)] & 0xFFFF;
        if (raw == 0) return -1;
        return raw - 1;
    }

    /**
     * Stamps a nature overlay tile at this cell by dense registry index.
     * Pass {@code -1} (or any negative value) to clear. Caller is expected
     * to have validated placement via {@link
     * com.dillon.starsectormarines.battle.world.tiles.TileDef#canOverlayOn}
     * against the current ground kind — the topology doesn't re-check here
     * so a leaf-edge filler can stamp atomically without per-cell predicate
     * overhead.
     */
    public void setNatureOverlayIndex(int x, int y, int tileIndex) {
        if (!inBounds(x, y)) return;
        int idx = index(x, y);
        natureOverlay[idx] = (short) (tileIndex < 0 ? 0 : tileIndex + 1);
        markChanged(idx);
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
     * this call so the wall pass can skip them via {@link #isWater}. Tagged
     * {@link Tag#FIXTURE} cells are also skipped: their own prop or nature
     * overlay supplies the visible feature even though navigation treats the
     * footprint as blocked.
     */
    public void tagDefaultWalls(NavigationGrid nav) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!nav.isWalkable(x, y) && !isWater(x, y) && !isFixture(x, y)) {
                    flags[index(x, y)] |= Tag.WALL.mask();
                }
            }
        }
    }

    // ----- surface override -----

    /**
     * The index for this bulkhead block, minting one if it is new.
     *
     * <p>Interned so that a deck whose twelve berths all ask for the same wall
     * spends one index on it. Returns zero for null, which is the shared
     * default and costs nothing.
     *
     * @throws IllegalStateException past 255 distinct overrides, which is far
     *     more than a deck can have and therefore a runaway rather than a limit
     */
    public int surfaceIndex(String blockId) {
        if (blockId == null || blockId.isEmpty()) return 0;
        int existing = surfaces.indexOf(blockId);
        if (existing >= 0) return existing + 1;
        if (surfaces.size() >= 255) {
            throw new IllegalStateException("more than 255 distinct surfaces on one map");
        }
        surfaces.add(blockId);
        return surfaces.size();
    }

    /** The block this cell draws from, or null when its kind decides. */
    public String getSurfaceId(int x, int y) {
        if (!inBounds(x, y)) return null;
        return surfaceId(surface[index(x, y)] & 0xFF);
    }

    /** The block an index names, or null for zero and for an index nobody minted. */
    public String surfaceId(int index) {
        return index <= 0 || index > surfaces.size() ? null : surfaces.get(index - 1);
    }

    /** How many distinct overrides this map carries, beyond the default. */
    public int surfaceCount() {
        return surfaces.size();
    }

    public int getSurface(int x, int y) {
        return inBounds(x, y) ? surface[index(x, y)] & 0xFF : 0;
    }

    /**
     * Draw this cell's bulkhead from another block.
     *
     * <p><b>A shared bulkhead is one wall.</b> Rooms are packed against each
     * other and the ring between two of them belongs to both, so the second
     * room to ask wins the cells they share. That is the honest outcome — there
     * is one wall there and it can only look like one thing — and it is why
     * this is a plain write rather than a merge.
     */
    public void setSurface(int x, int y, int index) {
        if (!inBounds(x, y)) return;
        int idx = index(x, y);
        surface[idx] = (byte) index;
        markChanged(idx);
    }
}
