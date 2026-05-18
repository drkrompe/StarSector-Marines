package com.dillon.starsectormarines.battle.nav;

import java.util.Arrays;

/**
 * 2D navigation grid with per-cell walkability and per-edge passability.
 *
 * <p>Ported (slim) from MoonLight Engine's {@code engine.navigation.NavigationGrid}.
 * Dropped from the original: stair/gate/tower-entrance cell flags, cell heights,
 * the clearance map (for >1-cell-radius agents), sparse {@code CellMetadata},
 * the on-demand walkable-index, and the fastutil dependencies. Kept the
 * pathfinder hot-path accessors that take pre-computed flat indices and
 * pre-shifted edge masks.
 *
 * <p>Cell flags byte layout:
 * <ul>
 *   <li>Bit 0: walkable (agent can stand here)</li>
 *   <li>Bit 1: floor (has a floor surface — currently informational only)</li>
 *   <li>Bit 2: rubble (cell used to be a wall; now walkable but renders + scores
 *       differently from a normal floor)</li>
 *   <li>Bit 3: doorway (cell is a zone-graph barrier — walkable, but the
 *       {@link com.dillon.starsectormarines.battle.nav.zone.ZoneDetector} treats it
 *       as a partition so it becomes its own 1-cell zone with portals on each side)</li>
 *   <li>Bit 4: street (outdoor walkable cell — renders with the road autotile;
 *       building interiors and doorways have this cleared so they render with
 *       the interior floor tileset)</li>
 *   <li>Bit 5: crosswalk (street cell painted with pedestrian stripes; tagged
 *       at gen time outside building doorways)</li>
 *   <li>Bit 6: crosswalk-stripes-horizontal (only meaningful when bit 5 is set;
 *       1 = stripes run east-west (pedestrian walking north-south), 0 = stripes
 *       run north-south (pedestrian walking east-west))</li>
 *   <li>Bit 7: courtyard (private interior pavement — outdoor walkable space
 *       absorbed into a super-block. Renders with the courtyard autotile from
 *       the road sheet so it reads distinct from both public road and indoor
 *       building floor)</li>
 * </ul>
 *
 * <p>Edge passability byte layout:
 * <ul>
 *   <li>Bits 0-3: cardinal (N, E, S, W)</li>
 *   <li>Bits 4-7: diagonal (NE, SE, SW, NW)</li>
 * </ul>
 *
 * <p>Default state: all zeros (not walkable, no edges passable). Map builders set
 * bits to 1. {@link #setWalkableFloor(int, int)} is the common-case convenience
 * that flags the cell walkable + floor and opens all eight edges.
 */
public class NavigationGrid {

    /**
     * Tags the pathfinder + zone graph care about. Strictly nav concerns —
     * rendering / categorization tags (FLOOR, STREET, RUBBLE, WALL, VEHICLE,
     * etc.) live on {@link com.dillon.starsectormarines.battle.map.CellTopology}
     * instead.
     *
     * <p>WALKABLE MUST stay at ordinal 0 — the pathfinder masks against
     * {@code 1L} on the hot path.
     */
    public enum CellTag {
        WALKABLE,
        /** Zone-graph partition cell. Treated as a portal between adjacent zones rather than collapsing them into one. Punched at gen time on building doorways and on wall breaches. */
        DOORWAY;

        public long mask() { return 1L << ordinal(); }
    }

    /**
     * Maximum cover level per facing. 0 = open in that direction, MAX = full
     * cover from that direction (a wall directly there). Cover is stored
     * per-facing (Story G) — a cell with a wall to its east has E-cover but
     * no S-cover, so a marine standing there is exposed to threats from the
     * south even though their east flank is "cozy."
     */
    public static final int MAX_COVER = 3;

    /** Facing N: threat is north (lower y) — cover comes from a wall at (x, y-1). */
    public static final int FACING_N = 0;
    /** Facing E: threat is east (higher x) — cover comes from a wall at (x+1, y). */
    public static final int FACING_E = 1;
    /** Facing S: threat is south (higher y) — cover comes from a wall at (x, y+1). */
    public static final int FACING_S = 2;
    /** Facing W: threat is west (lower x) — cover comes from a wall at (x-1, y). */
    public static final int FACING_W = 3;
    /** Number of distinct facings the cover model tracks. 4-way; diagonal threats snap to the dominant cardinal via {@link #facingFor(int, int)}. */
    public static final int FACING_COUNT = 4;

    private final int width;
    private final int height;
    private final long[] cellFlags;
    private final byte[] edgePassability;
    /**
     * Per-cell, per-facing cover level in {@code [0..{@link #MAX_COVER}]}.
     * Indexed as {@code (y * width + x) * FACING_COUNT + facing}. Initially
     * baked by the map generator from the wall layout via {@link
     * #recomputeCoverAt}; locally recomputed when {@link #damageCell} flips
     * a wall to rubble.
     */
    private final byte[] coverByFacing;
    /** Per-cell wall hit points. Non-zero only for non-walkable cells initialized as walls; ignored once a cell becomes walkable (rubble or floor). */
    private final int[] wallHp;

    public NavigationGrid(int width, int height) {
        this.width = width;
        this.height = height;
        int size = width * height;
        this.cellFlags = new long[size];
        this.edgePassability = new byte[size];
        this.coverByFacing = new byte[size * FACING_COUNT];
        this.wallHp = new int[size];
    }

    // ----- Tag access (generic) -----

    public boolean hasTag(int x, int y, CellTag tag) {
        if (!inBounds(x, y)) return false;
        return (cellFlags[index(x, y)] & tag.mask()) != 0L;
    }

    public boolean hasTagAt(int idx, CellTag tag) {
        return (cellFlags[idx] & tag.mask()) != 0L;
    }

    public void setTag(int x, int y, CellTag tag, boolean on) {
        if (!inBounds(x, y)) return;
        int idx = index(x, y);
        if (on) cellFlags[idx] |=  tag.mask();
        else    cellFlags[idx] &= ~tag.mask();
    }


    public int getWidth()  { return width;  }
    public int getHeight() { return height; }

    public boolean inBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    /** Flat array index. Public for hot-path callers that bypass bounds checks. */
    public int index(int x, int y) {
        return y * width + x;
    }

    // ----- Hot-path accessors (no bounds check — caller guarantees validity) -----

    public boolean isWalkableAt(int idx) {
        return (cellFlags[idx] & CellTag.WALKABLE.mask()) != 0L;
    }

    public boolean isEdgePassableAt(int idx, int mask) {
        return (edgePassability[idx] & mask) != 0;
    }

    // ----- Cell flags (bounds-checked typed wrappers around hasTag/setTag) -----

    public boolean isWalkable(int x, int y)            { return hasTag(x, y, CellTag.WALKABLE); }
    public void    setWalkable(int x, int y, boolean v){ setTag(x, y, CellTag.WALKABLE, v); }

    /** Marks the cell walkable and opens all eight edges. */
    public void setWalkableFloor(int x, int y) {
        setWalkable(x, y, true);
        openAllEdges(x, y);
    }

    // ----- Edge passability -----

    public boolean isEdgePassable(int x, int y, Direction dir) {
        if (!inBounds(x, y)) return false;
        return (edgePassability[index(x, y)] & (1 << dir.bit())) != 0;
    }

    public void setEdgePassable(int x, int y, Direction dir, boolean passable) {
        if (!inBounds(x, y)) return;
        int idx = index(x, y);
        if (passable) edgePassability[idx] |= (byte) (1 << dir.bit());
        else          edgePassability[idx] &= (byte) ~(1 << dir.bit());
    }

    public void openAllEdges(int x, int y) {
        if (!inBounds(x, y)) return;
        edgePassability[index(x, y)] = (byte) 0xFF;
    }

    /**
     * Blocks an edge on this cell only. The neighbor's reciprocal edge is NOT
     * modified — under the cell-local edge model the pathfinder checks both
     * sides at query time.
     */
    public void blockEdge(int x, int y, Direction dir) {
        setEdgePassable(x, y, dir, false);
    }

    public void openEdge(int x, int y, Direction dir) {
        setEdgePassable(x, y, dir, true);
    }

    // ----- Cover -----

    /**
     * Snaps an arbitrary direction vector {@code (dx, dy)} (cell offsets from
     * the covered cell toward the threat) to the dominant cardinal facing.
     * Pure helper — used by callers that have raw threat coords and want to
     * read directional cover. Ties on equal magnitude break toward N/E (the
     * positive-y / positive-x bias matches the FACING_* ordinal layout).
     */
    public static int facingFor(int dx, int dy) {
        int adx = Math.abs(dx);
        int ady = Math.abs(dy);
        if (adx >= ady) {
            return dx >= 0 ? FACING_E : FACING_W;
        }
        return dy >= 0 ? FACING_S : FACING_N;
    }

    /**
     * Per-facing cover level. {@code facing} is one of {@link #FACING_N},
     * {@link #FACING_E}, {@link #FACING_S}, {@link #FACING_W}. Returns 0 on
     * out-of-bounds or unknown facings.
     */
    public int getCoverAtFacing(int x, int y, int facing) {
        if (!inBounds(x, y)) return 0;
        if (facing < 0 || facing >= FACING_COUNT) return 0;
        return coverByFacing[index(x, y) * FACING_COUNT + facing] & 0xFF;
    }

    /**
     * Directional cover at (x, y) against a threat in the direction of the
     * raw vector {@code (fromDx, fromDy)} (offset from the covered cell to
     * the threat cell). Snaps to the dominant cardinal via {@link #facingFor}.
     */
    public int getCoverAt(int x, int y, int fromDx, int fromDy) {
        return getCoverAtFacing(x, y, facingFor(fromDx, fromDy));
    }

    /**
     * Scalar cover at (x, y) — the <em>sum</em> across all four facings.
     * Back-compat accessor for callers that don't carry a threat direction
     * ({@link com.dillon.starsectormarines.battle.ai.TacticalScoring#findFallbackPosition},
     * "is this cell hidden in general?" heuristics, debug overlays).
     * Equivalent numerically to the pre-Story-G "count of cardinal walls"
     * value when the grid is freshly baked (each wall contributes 1 to one
     * facing).
     */
    public int getCoverAt(int x, int y) {
        if (!inBounds(x, y)) return 0;
        int base = index(x, y) * FACING_COUNT;
        return (coverByFacing[base    ] & 0xFF)
             + (coverByFacing[base + 1] & 0xFF)
             + (coverByFacing[base + 2] & 0xFF)
             + (coverByFacing[base + 3] & 0xFF);
    }

    /** Sets the cover at (x, y) for one facing. Clamped to [0, {@link #MAX_COVER}]. */
    public void setCoverAtFacing(int x, int y, int facing, int level) {
        if (!inBounds(x, y)) return;
        if (facing < 0 || facing >= FACING_COUNT) return;
        int clamped = Math.max(0, Math.min(MAX_COVER, level));
        coverByFacing[index(x, y) * FACING_COUNT + facing] = (byte) clamped;
    }

    /**
     * Recomputes per-facing cover for the cell at (x, y) from its 4 cardinal
     * neighbors. Each facing gets 1 if a wall sits in that direction, else 0
     * — a marine on this cell is covered from threats in any direction that
     * has an adjacent wall. No-op for non-walkable cells (cover only applies
     * to standable cells).
     *
     * <p>This is the per-facing replacement for the old scalar bake: a cell
     * with walls north + east now reads {@code N=1, E=1, S=0, W=0} instead
     * of {@code total = 2}, exposing the directional pattern the planner
     * needs to make smart reposition / overwatch decisions (Story G/A/L).
     */
    public void recomputeCoverAt(int x, int y) {
        if (!inBounds(x, y) || !isWalkable(x, y)) return;
        setCoverAtFacing(x, y, FACING_N, isWalkable(x, y - 1) ? 0 : 1);
        setCoverAtFacing(x, y, FACING_E, isWalkable(x + 1, y) ? 0 : 1);
        setCoverAtFacing(x, y, FACING_S, isWalkable(x, y + 1) ? 0 : 1);
        setCoverAtFacing(x, y, FACING_W, isWalkable(x - 1, y) ? 0 : 1);
    }

    // ----- Doorways (zone-graph barriers) -----

    public boolean isDoorway(int x, int y)             { return hasTag(x, y, CellTag.DOORWAY); }
    public boolean isDoorwayAt(int idx)                { return hasTagAt(idx, CellTag.DOORWAY); }
    public void    setDoorway(int x, int y, boolean v) { setTag(x, y, CellTag.DOORWAY, v); }

    // ----- Destructible walls (HP storage; "is this a wall" lives on CellTopology) -----

    /** Initial wall HP at (x, y). Caller sets this for non-walkable wall cells at map-gen time. */
    public void setWallHp(int x, int y, int hp) {
        if (!inBounds(x, y)) return;
        wallHp[index(x, y)] = Math.max(0, hp);
    }

    public int getWallHp(int x, int y) {
        if (!inBounds(x, y)) return 0;
        return wallHp[index(x, y)];
    }

    /**
     * Applies {@code amount} damage to a wall cell. Returns {@code true} the
     * call that knocks the wall down — flipping it to walkable + rubble,
     * opening its edges, and re-baking cover for the cell and its 4 cardinal
     * neighbors. Idempotent on already-walkable cells (returns false).
     *
     * <p>The grid only ever becomes <em>more</em> permissive — rubble stays
     * walkable forever. That sidesteps the "wall lands on a unit mid-path"
     * invalidation problem: existing paths only ever gain shortcuts, never
     * lose cells, so units pick up new routes on their next normal re-path.
     */
    public boolean damageCell(int x, int y, int amount) {
        if (!inBounds(x, y) || amount <= 0) return false;
        int idx = index(x, y);
        if ((cellFlags[idx] & CellTag.WALKABLE.mask()) != 0L) return false;
        int remaining = wallHp[idx] - amount;
        if (remaining > 0) {
            wallHp[idx] = remaining;
            return false;
        }
        wallHp[idx] = 0;
        // Flag the breach walkable + doorway so the zone graph treats it as a
        // portal cell — the two previously-separated zones gain a connection
        // without merging into one giant zone. The visual swap (clear WALL,
        // set RUBBLE, set FLOOR on the topology) is handled by the caller
        // (BattleSimulation.damageCell), since the rendering tag-bag isn't
        // visible from this class.
        cellFlags[idx] |= CellTag.WALKABLE.mask() | CellTag.DOORWAY.mask();
        edgePassability[idx] = (byte) 0xFF;
        recomputeCoverAt(x, y);
        recomputeCoverAt(x + 1, y);
        recomputeCoverAt(x - 1, y);
        recomputeCoverAt(x, y + 1);
        recomputeCoverAt(x, y - 1);
        return true;
    }

    // ----- Raw arrays (for the pathfinder's hot path) -----

    public long[] getCellFlagsArray()        { return cellFlags;       }
    public byte[] getEdgePassabilityArray()  { return edgePassability; }

    public void clear() {
        Arrays.fill(cellFlags, 0L);
        Arrays.fill(edgePassability, (byte) 0);
        Arrays.fill(coverByFacing, (byte) 0);
        Arrays.fill(wallHp, 0);
    }

    // ----- Line of sight -----

    /**
     * Bresenham trace from {@code (x0,y0)} to {@code (x1,y1)} — returns false
     * if any non-endpoint cell on the line is non-walkable. Endpoints are
     * exempt so a shooter can stand in a cell flagged non-walkable (they
     * normally don't) and a target's cell never blocks the shot at itself.
     *
     * <p>Basic Bresenham — doesn't visit every cell the geometric line
     * crosses, so a diagonal "tunnel" through corner-touching walls reads as
     * visible. Acceptable for an auto-battler; tighten with supercover
     * Bresenham if it becomes visually weird.
     */
    public boolean hasLineOfSight(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            boolean endpoint = (x == x0 && y == y0) || (x == x1 && y == y1);
            if (!endpoint && !isWalkable(x, y)) return false;
            if (x == x1 && y == y1) return true;
            int e2 = err << 1;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 <  dx) { err += dx; y += sy; }
        }
    }
}
