package com.dillon.starsectormarines.battle.nav;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
     * etc.) live on {@link com.dillon.starsectormarines.battle.world.model.CellTopology}
     * instead.
     *
     * <p>WALKABLE MUST stay at ordinal 0 — the pathfinder masks against
     * {@code 1L} on the hot path.
     */
    public enum CellTag {
        WALKABLE,
        /** Zone-graph partition cell. Treated as a portal between adjacent zones rather than collapsing them into one. Punched at gen time on building doorways and on wall breaches. */
        DOORWAY,
        /**
         * Non-walkable cell that bullets and sight still pass through. Meaningful
         * only when {@link #WALKABLE} is clear — walkable cells are LoS-passable
         * by default and never read this bit. Set on water (units can't wade in,
         * but a marine on the shore can shoot across the pond). Future
         * candidates: low fences, knee-high rubble, glass panes, smoke clouds.
         *
         * <p>The LoS / firing raycast asks {@link #blocksLineOfSightAt}: a cell
         * blocks iff it is non-walkable AND not see-through. This bit is the
         * positive opt-out for that rule.
         */
        SEE_THROUGH,

        /**
         * Non-walkable cell that must not create directional edge cover on
         * adjacent standable cells. Water uses this opt-out: it blocks
         * navigation without supplying a ballistic silhouette. Windows and
         * non-structural fixtures deliberately leave this clear because their
         * wall or authored doodad profile supplies edge cover.
         */
        NO_EDGE_COVER;

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
    /** Default symmetric Z catch band for directional wall-edge cover. */
    public static final float DEFAULT_COVER_CATCH_HALF_HEIGHT = 0.35f;

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
    /** Per-facing symmetric vertical catch half-height paired with {@link #coverByFacing}. */
    private final float[] coverCatchHalfHeightByFacing;
    /** Independent shared-edge contribution, revealed/cleared with the barrier identity. */
    private final byte[] edgeBarrierCoverByFacing;
    /** Catch height paired with {@link #edgeBarrierCoverByFacing}. */
    private final float[] edgeBarrierCoverCatchHalfHeightByFacing;
    /** Canonical east-facing barrier anchored on a cell, or null. */
    private final SharedEdgeBarrier[] eastEdgeBarriers;
    /** Canonical north-facing barrier anchored on a cell, or null. */
    private final SharedEdgeBarrier[] northEdgeBarriers;
    /** Deterministic authoring order, also used by the sparse render pass. */
    private final List<SharedEdgeBarrier> edgeBarriers = new ArrayList<>();
    private final List<SharedEdgeBarrier> edgeBarriersView =
            Collections.unmodifiableList(edgeBarriers);
    /** Per-cell wall hit points. Non-zero only for non-walkable cells initialized as walls; ignored once a cell becomes walkable (rubble or floor). */
    private final int[] wallHp;
    /** Reference-counted temporary opacity (smoke). Never affects walkability or ballistics. */
    private final short[] transientOpacity;
    private long opacityRevision;

    public NavigationGrid(int width, int height) {
        this.width = width;
        this.height = height;
        int size = width * height;
        this.cellFlags = new long[size];
        this.edgePassability = new byte[size];
        this.coverByFacing = new byte[size * FACING_COUNT];
        this.coverCatchHalfHeightByFacing = new float[size * FACING_COUNT];
        this.edgeBarrierCoverByFacing = new byte[size * FACING_COUNT];
        this.edgeBarrierCoverCatchHalfHeightByFacing =
                new float[size * FACING_COUNT];
        this.eastEdgeBarriers = new SharedEdgeBarrier[size];
        this.northEdgeBarriers = new SharedEdgeBarrier[size];
        this.wallHp = new int[size];
        this.transientOpacity = new short[size];
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

    /**
     * True if this cell stops a line of sight / projectile raycast. A cell
     * blocks iff it is non-walkable AND not flagged {@link CellTag#SEE_THROUGH}.
     * Walkable cells never block; non-walkable see-through cells (water today,
     * glass/fences/smoke later) let shots and sight pass.
     */
    public boolean blocksLineOfSightAt(int idx) {
        return transientOpacity[idx] > 0 || blocksStructuralLineOfSightAt(idx);
    }

    /** Permanent wall/fixture opacity, excluding smoke. */
    public boolean blocksStructuralLineOfSightAt(int idx) {
        long flags = cellFlags[idx];
        return (flags & CellTag.WALKABLE.mask())    == 0L
            && (flags & CellTag.SEE_THROUGH.mask()) == 0L;
    }

    public boolean hasTransientOpacityAt(int idx) { return transientOpacity[idx] > 0; }

    public boolean hasTransientOpacity(int x, int y) {
        return inBounds(x, y) && hasTransientOpacityAt(index(x, y));
    }

    public long opacityRevision() { return opacityRevision; }

    public void addTransientOpacityAt(int idx) {
        if (transientOpacity[idx] == Short.MAX_VALUE) return;
        transientOpacity[idx]++;
        opacityRevision++;
        LosCache.clearAll();
    }

    public void removeTransientOpacityAt(int idx) {
        if (transientOpacity[idx] <= 0) return;
        transientOpacity[idx]--;
        opacityRevision++;
        LosCache.clearAll();
    }

    /**
     * Bounds-checked variant of {@link #blocksLineOfSightAt(int)} for raycast
     * callers whose Bresenham stepping can briefly exit the grid — e.g., a
     * shuttle hovering at the map edge whose origin cell floors to (-1, y).
     * Out-of-bounds reads as "open sky" (non-blocking).
     */
    public boolean blocksLineOfSight(int x, int y) {
        if (!inBounds(x, y)) return false;
        return blocksLineOfSightAt(index(x, y));
    }

    // ----- Cell flags (bounds-checked typed wrappers around hasTag/setTag) -----

    public boolean isWalkable(int x, int y)            { return hasTag(x, y, CellTag.WALKABLE); }
    public void    setWalkable(int x, int y, boolean v){ setTag(x, y, CellTag.WALKABLE, v); }

    public boolean isSeeThrough(int x, int y)             { return hasTag(x, y, CellTag.SEE_THROUGH); }
    public void    setSeeThrough(int x, int y, boolean v) { setTag(x, y, CellTag.SEE_THROUGH, v); }

    public boolean isEdgeCoverSuppressed(int x, int y) {
        return hasTag(x, y, CellTag.NO_EDGE_COVER);
    }

    public void setEdgeCoverSuppressed(int x, int y, boolean v) {
        setTag(x, y, CellTag.NO_EDGE_COVER, v);
    }

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

    /**
     * True when the shared cardinal edge from {@code (x, y)} can be crossed
     * in both directions. A shared edge exists only between two in-bounds
     * cells; diagonal directions name corners rather than shared edges and
     * are rejected.
     *
     * <p>This is the topology query for a thin, cell-boundary barrier. The
     * two cells may both remain walkable while their shared transition is
     * closed. A* already applies the same dual-side rule on its hot path;
     * zone and authoring code use this named form so they cannot accidentally
     * treat cell walkability as connectivity.
     */
    public boolean isSharedEdgePassable(int x, int y, Direction dir) {
        requireCardinal(dir);
        int nx = x + dir.dx;
        int ny = y + dir.dy;
        if (!inBounds(x, y) || !inBounds(nx, ny)) return false;
        return isEdgePassable(x, y, dir)
                && isEdgePassable(nx, ny, dir.opposite());
    }

    /**
     * Authoritative one-cell movement check for continuous systems that may
     * cross a cell boundary without asking A* for a path. It applies the same
     * reciprocal-edge and diagonal corner constraints as the pathfinder.
     */
    public boolean canTraverseCellStep(int fromX, int fromY,
                                       int toX, int toY) {
        if (!inBounds(fromX, fromY) || !inBounds(toX, toY)) return false;
        if (!isWalkable(fromX, fromY) || !isWalkable(toX, toY)) return false;
        int dx = toX - fromX;
        int dy = toY - fromY;
        if (dx == 0 && dy == 0) return true;
        if (Math.abs(dx) > 1 || Math.abs(dy) > 1) return false;
        for (Direction direction : Direction.ALL) {
            if (direction.dx != dx || direction.dy != dy) continue;
            return GridPathfinder.canStep(index(fromX, fromY), fromX, fromY,
                    index(toX, toY), direction.ordinal(), width, height,
                    cellFlags, edgePassability, null);
        }
        return false;
    }

    /**
     * Atomically changes both cell-local halves of one shared cardinal edge.
     * No-op when either cell is out of bounds. Generation may use this method
     * directly; runtime opening goes through
     * {@link NavigationService#openSharedEdge} so zone and retained-path
     * caches are invalidated at the ordinary topology boundary.
     */
    public void setSharedEdgePassable(int x, int y, Direction dir,
                                      boolean passable) {
        requireCardinal(dir);
        int nx = x + dir.dx;
        int ny = y + dir.dy;
        if (!inBounds(x, y) || !inBounds(nx, ny)) return;
        setEdgePassable(x, y, dir, passable);
        setEdgePassable(nx, ny, dir.opposite(), passable);
    }

    /** Closes both halves of one shared cardinal transition. */
    public void blockSharedEdge(int x, int y, Direction dir) {
        setSharedEdgePassable(x, y, dir, false);
    }

    /** Opens both halves of one shared cardinal transition. */
    public void openSharedEdge(int x, int y, Direction dir) {
        setSharedEdgePassable(x, y, dir, true);
    }

    private static void requireCardinal(Direction dir) {
        if (dir == null || dir.isDiagonal()) {
            throw new IllegalArgumentException(
                    "shared edge direction must be cardinal");
        }
    }

    // ----- Authored shared-edge barriers -----

    /**
     * Authors one physical barrier and closes its shared transition. Both
     * adjacent cells must already be standable: a barrier divides usable
     * space rather than masquerading as a cell wall. Runtime construction is
     * intentionally unsupported; generators place barriers before play. The
     * authored {@code (x,y)} cell is retained as the structural-owner side of
     * the edge even when west/south placement is canonicalized onto its
     * neighbor. Presentation and later building-level consumers may use that
     * side without changing the zero-width navigation transition.
     */
    public SharedEdgeBarrier placeEdgeBarrier(
            int x, int y, Direction direction, SharedEdgeBarrier.Kind kind) {
        requireCardinal(direction);
        if (kind == null) throw new IllegalArgumentException("barrier kind is required");
        int canonicalX = direction == Direction.W ? x - 1 : x;
        int canonicalY = direction == Direction.S ? y - 1 : y;
        Direction canonicalDirection = direction == Direction.W
                ? Direction.E : direction == Direction.S ? Direction.N : direction;
        int otherX = canonicalX + canonicalDirection.dx;
        int otherY = canonicalY + canonicalDirection.dy;
        if (!inBounds(canonicalX, canonicalY) || !inBounds(otherX, otherY)) {
            throw new IllegalArgumentException("barrier edge must join two in-bounds cells");
        }
        if (!isWalkable(canonicalX, canonicalY) || !isWalkable(otherX, otherY)) {
            throw new IllegalArgumentException("barrier edge must join two walkable cells");
        }
        if (getEdgeBarrier(canonicalX, canonicalY, canonicalDirection) != null) {
            throw new IllegalArgumentException("barrier edge is already authored");
        }
        if (!isSharedEdgePassable(canonicalX, canonicalY, canonicalDirection)) {
            throw new IllegalArgumentException(
                    "barrier must own an initially passable shared edge");
        }

        SharedEdgeBarrier barrier = new SharedEdgeBarrier(
                canonicalX, canonicalY, canonicalDirection, kind, x, y);
        barrierArray(canonicalDirection)[index(canonicalX, canonicalY)] = barrier;
        edgeBarriers.add(barrier);
        blockSharedEdge(canonicalX, canonicalY, canonicalDirection);
        publishBarrierCover(barrier, true);
        if (kind.blocksSight()) LosCache.clearAll();
        return barrier;
    }

    /** Reciprocal lookup: either adjacent cell and facing names one identity. */
    public SharedEdgeBarrier getEdgeBarrier(int x, int y, Direction direction) {
        requireCardinal(direction);
        int canonicalX = direction == Direction.W ? x - 1 : x;
        int canonicalY = direction == Direction.S ? y - 1 : y;
        Direction canonicalDirection = direction == Direction.W
                ? Direction.E : direction == Direction.S ? Direction.N : direction;
        if (!inBounds(canonicalX, canonicalY)) return null;
        return barrierArray(canonicalDirection)[index(canonicalX, canonicalY)];
    }

    /** Stable read-only authoring order for sparse consumers such as rendering. */
    public List<SharedEdgeBarrier> getEdgeBarriers() { return edgeBarriersView; }

    /** Zero-allocation iteration seam for mutation systems. */
    public int edgeBarrierCount() { return edgeBarriers.size(); }

    /** Zero-allocation iteration seam for mutation systems. */
    public SharedEdgeBarrier edgeBarrierAt(int offset) {
        return edgeBarriers.get(offset);
    }

    /**
     * Applies structure damage and removes the identity and cover contribution
     * on destruction. The shared edge remains closed until the runtime
     * coordinator opens it and invalidates derived navigation exactly once.
     */
    public boolean damageEdgeBarrier(int x, int y, Direction direction,
                                     int amount) {
        SharedEdgeBarrier barrier = getEdgeBarrier(x, y, direction);
        if (barrier == null || !barrier.damage(amount)) return false;
        barrierArray(barrier.direction())[index(barrier.cellX(), barrier.cellY())] = null;
        edgeBarriers.remove(barrier);
        publishBarrierCover(barrier, false);
        if (barrier.kind().blocksSight()) LosCache.clearAll();
        return true;
    }

    /**
     * Un-author a barrier outright, opening the edge behind it.
     *
     * <p>For generation, where a stage demolishes what an earlier one built.
     * {@link #damageEdgeBarrier} is the runtime story and deliberately leaves
     * the edge closed for the coordinator to open exactly once; during
     * generation there is no coordinator, the ground is being reset anyway, and
     * a window left on an edge whose building no longer exists is both scenery
     * nobody can explain and an edge the next stage cannot author on — a single
     * authored identity per edge means the leftover blocks its successor.
     *
     * @return whether an identity was there to remove
     */
    public boolean removeEdgeBarrier(int x, int y, Direction direction) {
        SharedEdgeBarrier barrier = getEdgeBarrier(x, y, direction);
        if (barrier == null) return false;
        barrierArray(barrier.direction())[index(barrier.cellX(), barrier.cellY())] = null;
        edgeBarriers.remove(barrier);
        publishBarrierCover(barrier, false);
        openSharedEdge(barrier.cellX(), barrier.cellY(), barrier.direction());
        if (barrier.kind().blocksSight()) LosCache.clearAll();
        return true;
    }

    private SharedEdgeBarrier[] barrierArray(Direction canonicalDirection) {
        return canonicalDirection == Direction.E
                ? eastEdgeBarriers : northEdgeBarriers;
    }

    private void publishBarrierCover(SharedEdgeBarrier barrier, boolean present) {
        int x = barrier.cellX();
        int y = barrier.cellY();
        Direction direction = barrier.direction();
        int level = present ? barrier.kind().coverLevel() : 0;
        float height = present ? barrier.kind().coverCatchHalfHeight() : 0f;
        setEdgeBarrierCoverAtFacing(x, y,
                facingFor(direction.dx, direction.dy), level, height);
        setEdgeBarrierCoverAtFacing(x + direction.dx, y + direction.dy,
                facingFor(-direction.dx, -direction.dy), level, height);
    }

    private void setEdgeBarrierCoverAtFacing(int x, int y, int facing,
                                             int level, float catchHalfHeight) {
        int slot = index(x, y) * FACING_COUNT + facing;
        edgeBarrierCoverByFacing[slot] = (byte) Math.max(0,
                Math.min(MAX_COVER, level));
        edgeBarrierCoverCatchHalfHeightByFacing[slot] = level > 0
                ? Math.max(0f, catchHalfHeight) : 0f;
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
        int slot = index(x, y) * FACING_COUNT + facing;
        return Math.max(coverByFacing[slot] & 0xFF,
                edgeBarrierCoverByFacing[slot] & 0xFF);
    }

    /** Base cell/fixture contribution, excluding a feature on the shared edge. */
    public int getCellCoverAtFacing(int x, int y, int facing) {
        if (!inBounds(x, y)) return 0;
        if (facing < 0 || facing >= FACING_COUNT) return 0;
        return coverByFacing[index(x, y) * FACING_COUNT + facing] & 0xFF;
    }

    /** Base cell/fixture catch height, excluding a feature on the shared edge. */
    public float getCellCoverCatchHalfHeightAtFacing(
            int x, int y, int facing) {
        if (!inBounds(x, y)) return 0f;
        if (facing < 0 || facing >= FACING_COUNT) return 0f;
        return coverCatchHalfHeightByFacing[
                index(x, y) * FACING_COUNT + facing];
    }

    /**
     * Directional cover at (x, y) against a threat in the direction of the
     * raw vector {@code (fromDx, fromDy)} (offset from the covered cell to
     * the threat cell). Snaps to the dominant cardinal via {@link #facingFor}.
     */
    public int getCoverAt(int x, int y, int fromDx, int fromDy) {
        return getCoverAtFacing(x, y, facingFor(fromDx, fromDy));
    }

    /** Per-facing symmetric target-plane catch half-height, or zero for no cover. */
    public float getCoverCatchHalfHeightAtFacing(int x, int y, int facing) {
        if (!inBounds(x, y)) return 0f;
        if (facing < 0 || facing >= FACING_COUNT) return 0f;
        int slot = index(x, y) * FACING_COUNT + facing;
        int cellLevel = coverByFacing[slot] & 0xFF;
        int barrierLevel = edgeBarrierCoverByFacing[slot] & 0xFF;
        if (cellLevel > barrierLevel) return coverCatchHalfHeightByFacing[slot];
        if (barrierLevel > cellLevel) {
            return edgeBarrierCoverCatchHalfHeightByFacing[slot];
        }
        return Math.max(coverCatchHalfHeightByFacing[slot],
                edgeBarrierCoverCatchHalfHeightByFacing[slot]);
    }

    /** Directional catch half-height using the same facing snap as {@link #getCoverAt}. */
    public float getCoverCatchHalfHeight(int x, int y, int fromDx, int fromDy) {
        return getCoverCatchHalfHeightAtFacing(x, y, facingFor(fromDx, fromDy));
    }

    /**
     * Scalar cover at (x, y) — the <em>sum</em> across all four facings.
     * Back-compat accessor for callers that don't carry a threat direction
     * ({@link com.dillon.starsectormarines.battle.decision.TacticalScoring#findFallbackPosition},
     * "is this cell hidden in general?" heuristics, debug overlays).
     * Equivalent numerically to the pre-Story-G "count of cardinal walls"
     * value when the grid is freshly baked (each wall contributes 1 to one
     * facing).
     */
    public int getCoverAt(int x, int y) {
        if (!inBounds(x, y)) return 0;
        return getCoverAtFacing(x, y, FACING_N)
                + getCoverAtFacing(x, y, FACING_E)
                + getCoverAtFacing(x, y, FACING_S)
                + getCoverAtFacing(x, y, FACING_W);
    }

    /** Sets the cover at (x, y) for one facing. Clamped to [0, {@link #MAX_COVER}]. */
    public void setCoverAtFacing(int x, int y, int facing, int level) {
        setCoverAtFacing(x, y, facing, level,
                level > 0 ? DEFAULT_COVER_CATCH_HALF_HEIGHT : 0f);
    }

    /** Sets cover level and its symmetric target-plane catch half-height together. */
    public void setCoverAtFacing(int x, int y, int facing, int level,
                                 float catchHalfHeight) {
        if (!inBounds(x, y)) return;
        if (facing < 0 || facing >= FACING_COUNT) return;
        int clamped = Math.max(0, Math.min(MAX_COVER, level));
        int slot = index(x, y) * FACING_COUNT + facing;
        coverByFacing[slot] = (byte) clamped;
        coverCatchHalfHeightByFacing[slot] = clamped > 0 && Float.isFinite(catchHalfHeight)
                ? Math.max(0f, catchHalfHeight)
                : 0f;
    }

    /**
     * Recomputes per-facing cover for the cell at (x, y) from its 4 cardinal
     * neighbors. Each facing gets 1 if an edge-cover blocker sits in that
     * direction, else 0
     * — a marine on this cell is covered from threats in any direction that
     * has an adjacent wall, window, or fixture. Non-walkable cells such as
     * water may explicitly suppress this contribution. No-op for non-walkable
     * cells (cover only applies to standable cells).
     *
     * <p>This is the per-facing replacement for the old scalar bake: a cell
     * with walls north + east now reads {@code N=1, E=1, S=0, W=0} instead
     * of {@code total = 2}, exposing the directional pattern the planner
     * needs to make smart reposition / overwatch decisions (Story G/A/L).
     */
    public void recomputeCoverAt(int x, int y) {
        if (!inBounds(x, y) || !isWalkable(x, y)) return;
        setCoverAtFacing(x, y, FACING_N, providesEdgeCover(x, y - 1) ? 1 : 0);
        setCoverAtFacing(x, y, FACING_E, providesEdgeCover(x + 1, y) ? 1 : 0);
        setCoverAtFacing(x, y, FACING_S, providesEdgeCover(x, y + 1) ? 1 : 0);
        setCoverAtFacing(x, y, FACING_W, providesEdgeCover(x - 1, y) ? 1 : 0);
    }

    /** Map bounds remain hard cover; in-bounds blockers may explicitly opt out. */
    private boolean providesEdgeCover(int x, int y) {
        return !inBounds(x, y)
                || (!isWalkable(x, y) && !isEdgeCoverSuppressed(x, y));
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
        Arrays.fill(coverCatchHalfHeightByFacing, 0f);
        Arrays.fill(edgeBarrierCoverByFacing, (byte) 0);
        Arrays.fill(edgeBarrierCoverCatchHalfHeightByFacing, 0f);
        Arrays.fill(eastEdgeBarriers, null);
        Arrays.fill(northEdgeBarriers, null);
        edgeBarriers.clear();
        Arrays.fill(wallHp, 0);
        Arrays.fill(transientOpacity, (short) 0);
        opacityRevision++;
        LosCache.clearAll();
    }

    // ----- Line of sight -----

    /**
     * Bresenham trace from {@code (x0,y0)} to {@code (x1,y1)} — returns false
     * if any non-endpoint cell on the line blocks LoS per
     * {@link #blocksLineOfSightAt}. Endpoints are exempt so a shooter can
     * stand in a cell flagged non-walkable (they normally don't) and a
     * target's cell never blocks the shot at itself.
     *
     * <p>Basic Bresenham — doesn't visit every cell the geometric line
     * crosses, so a diagonal "tunnel" through corner-touching walls reads as
     * visible. Acceptable for an auto-battler; tighten with supercover
     * Bresenham if it becomes visually weird.
     */
    public boolean hasLineOfSight(int x0, int y0, int x1, int y1) {
        // Tick-scoped result cache. Off-tick (tests, mid-frame UI hooks) the
        // slot is null and we fall through to the Bresenham trace directly.
        LosCache cache = LosCache.current();
        if (cache != null) {
            int cached = cache.tryGet(x0, y0, x1, y1);
            if (cached >= 0) return cached == 1;
            boolean result = hasLineOfSightImpl(x0, y0, x1, y1);
            cache.put(x0, y0, x1, y1, result);
            return result;
        }
        return hasLineOfSightImpl(x0, y0, x1, y1);
    }

    private boolean hasLineOfSightImpl(int x0, int y0, int x1, int y1) {
        if (firstSightBlockingEdgeBarrierOnLine(
                x0 + 0.5f, y0 + 0.5f, x1 + 0.5f, y1 + 0.5f) != null) {
            return false;
        }
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            boolean endpoint = (x == x0 && y == y0) || (x == x1 && y == y1);
            if (!endpoint && blocksLineOfSight(x, y)) return false;
            if (x == x1 && y == y1) return true;
            int e2 = err << 1;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 <  dx) { err += dx; y += sy; }
        }
    }

    /**
     * Exact continuous-point firing trace through the grid. Unlike {@link
     * #hasLineOfSight(int, int, int, int)}, this follows the segment from the
     * source's real point to the target's real point and visits every cell
     * whose interior the segment crosses. The source and target cells are
     * exempt, matching the ordinary LoS endpoint contract.
     *
     * <p>This is intentionally uncached and more expensive than cell
     * Bresenham. Perception, fog, and topology remain cell projections; use
     * this only where a direct-fire decision needs to agree with the physical
     * ballistic ray.
     */
    public boolean hasLineOfFire(float x0, float y0, float x1, float y1) {
        return firstProjectileBlockingEdgeBarrierOnLine(x0, y0, x1, y1) == null
                && firstBlockOnLine(x0, y0, x1, y1,
                false, true) == noBlockPacked();
    }

    /** First authored edge feature physically crossed by an exact segment. */
    public SharedEdgeBarrier firstEdgeBarrierOnLine(
            float x0, float y0, float x1, float y1) {
        return firstEdgeBarrierOnLine(x0, y0, x1, y1, 0);
    }

    /** First exact crossing whose authored profile blocks sight. */
    public SharedEdgeBarrier firstSightBlockingEdgeBarrierOnLine(
            float x0, float y0, float x1, float y1) {
        return firstEdgeBarrierOnLine(x0, y0, x1, y1, 1);
    }

    /** First exact crossing whose authored profile blocks direct projectiles. */
    public SharedEdgeBarrier firstProjectileBlockingEdgeBarrierOnLine(
            float x0, float y0, float x1, float y1) {
        return firstEdgeBarrierOnLine(x0, y0, x1, y1, 2);
    }

    private SharedEdgeBarrier firstEdgeBarrierOnLine(
            float x0, float y0, float x1, float y1, int filter) {
        if (!Float.isFinite(x0) || !Float.isFinite(y0)
                || !Float.isFinite(x1) || !Float.isFinite(y1)) {
            throw new IllegalArgumentException("Ray endpoints must be finite");
        }
        float dx = x1 - x0;
        float dy = y1 - y0;
        float bestT = Float.POSITIVE_INFINITY;
        SharedEdgeBarrier best = null;
        for (SharedEdgeBarrier barrier : edgeBarriers) {
            if (filter == 1 && !barrier.kind().blocksSight()) continue;
            if (filter == 2 && !barrier.kind().blocksProjectiles()) continue;
            float t;
            float along;
            if (barrier.direction() == Direction.E) {
                if (Math.abs(dx) < 1e-7f) continue;
                t = (barrier.cellX() + 1f - x0) / dx;
                along = y0 + dy * t;
                if (along < barrier.cellY() - 1e-6f
                        || along > barrier.cellY() + 1f + 1e-6f) continue;
            } else {
                if (Math.abs(dy) < 1e-7f) continue;
                t = (barrier.cellY() + 1f - y0) / dy;
                along = x0 + dx * t;
                if (along < barrier.cellX() - 1e-6f
                        || along > barrier.cellX() + 1f + 1e-6f) continue;
            }
            if (t <= 1e-6f || t >= 1f - 1e-6f || t >= bestT) continue;
            bestT = t;
            best = barrier;
        }
        return best;
    }

    /**
     * Bounded {@link #hasLineOfSight}: returns false when the Euclidean cell
     * distance between {@code (x0,y0)} and {@code (x1,y1)} exceeds
     * {@code maxCells}, regardless of whether the line is geometrically clear.
     * Lets callers express "effective threat range" rather than pure geometry —
     * a militia at attackRange 18 cannot threaten a candidate cell 30 cells
     * away even on a perfectly open field.
     *
     * <p>Distance is checked once up front in squared form (no sqrt) and short-
     * circuits the Bresenham trace, so out-of-range pairs are O(1).
     *
     * <p>Used for perception-side callers ({@link com.dillon.starsectormarines.battle.decision.TacticalScoring#isHiddenFromAllEnemies}
     * and {@link com.dillon.starsectormarines.battle.decision.TacticalScoring#countEnemiesWithLos}).
     * Physics and presentation callers keep their geometric LOS contracts;
     * the AI perception layer decides when observed geometry becomes a
     * believed contact. See {@code ai-nouns.md}.
     */
    public boolean hasLineOfSightWithin(int x0, int y0, int x1, int y1, float maxCells) {
        if (maxCells <= 0f) return false;
        int dx = x1 - x0;
        int dy = y1 - y0;
        float distSq = (float) dx * dx + (float) dy * dy;
        if (distSq > maxCells * maxCells) return false;
        return hasLineOfSight(x0, y0, x1, y1);
    }

    /** True when a non-endpoint smoke cell lies on this Bresenham lane. */
    public boolean hasTransientOpacityOnLine(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            boolean endpoint = (x == x0 && y == y0) || (x == x1 && y == y1);
            if (!endpoint && hasTransientOpacity(x, y)) return true;
            if (x == x1 && y == y1) return false;
            int e2 = err << 1;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx) { err += dx; y += sy; }
        }
    }

    /**
     * Bresenham raycast from {@code (x0, y0)} to {@code (x1, y1)} that returns
     * the first LoS-blocking cell encountered (excluding the origin) per
     * {@link #blocksLineOfSightAt}, or {@code (-1, -1)} if the line is clear
     * all the way to the endpoint. See-through non-walkable cells (water,
     * future glass/smoke) are skipped — bullets pass through them.
     *
     * <p>Returned as a packed long: low 32 bits = x, next 32 bits = y. Caller
     * unpacks with {@code (int) (packed)} and {@code (int) (packed >>> 32)}.
     * Packed return avoids allocating a small array on every shot — turret
     * burst pumps and area-spread weapons exercise this hot path heavily.
     *
     * <p>Bresenham is the same stepping pass {@link #hasLineOfSight} uses;
     * the difference is this returns the BLOCKER's cell rather than a bool.
     * Used by {@link com.dillon.starsectormarines.battle.sim.BattleSimulation#fireShotFrom}
     * to snap scattered rounds to the first wall in their flight path so a
     * spread-fire turret can't pepper marines behind cover.
     */
    public long firstWallOnLine(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            if (!(x == x0 && y == y0) && inBounds(x, y)
                    && blocksStructuralLineOfSightAt(index(x, y))) {
                return (((long) y & 0xFFFFFFFFL) << 32) | ((long) x & 0xFFFFFFFFL);
            }
            if (x == x1 && y == y1) return (((long) -1) & 0xFFFFFFFFL) << 32 | (((long) -1) & 0xFFFFFFFFL);
            int e2 = err << 1;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 <  dx) { err += dx; y += sy; }
        }
    }

    /**
     * Continuous-point counterpart to {@link #firstWallOnLine(int, int, int,
     * int)}. Walks the actual segment rather than the Bresenham lane between
     * projected cells. Only structural blockers stop the ray; smoke remains a
     * visibility concern and bullets pass through it.
     */
    public long firstWallOnLine(float x0, float y0, float x1, float y1) {
        return firstBlockOnLine(x0, y0, x1, y1,
                true, false);
    }

    /**
     * Amanatides-Woo grid traversal. Returned format matches
     * {@link #firstWallOnLine(int, int, int, int)}. When {@code structuralOnly}
     * is false, transient opacity also blocks. When {@code excludeEnd} is true,
     * the target cell is exempt.
     */
    private long firstBlockOnLine(float x0, float y0, float x1, float y1,
                                  boolean structuralOnly, boolean excludeEnd) {
        if (!Float.isFinite(x0) || !Float.isFinite(y0)
                || !Float.isFinite(x1) || !Float.isFinite(y1)) {
            throw new IllegalArgumentException("Ray endpoints must be finite");
        }
        int x = (int) Math.floor(x0);
        int y = (int) Math.floor(y0);
        int endX = (int) Math.floor(x1);
        int endY = (int) Math.floor(y1);
        if (x == endX && y == endY) return noBlockPacked();

        float dx = x1 - x0;
        float dy = y1 - y0;
        int stepX = Float.compare(dx, 0f);
        int stepY = Float.compare(dy, 0f);
        float tDeltaX = stepX == 0 ? Float.POSITIVE_INFINITY : Math.abs(1f / dx);
        float tDeltaY = stepY == 0 ? Float.POSITIVE_INFINITY : Math.abs(1f / dy);
        float nextBoundaryX = stepX > 0 ? x + 1f : x;
        float nextBoundaryY = stepY > 0 ? y + 1f : y;
        float tMaxX = stepX == 0 ? Float.POSITIVE_INFINITY
                : (nextBoundaryX - x0) / dx;
        float tMaxY = stepY == 0 ? Float.POSITIVE_INFINITY
                : (nextBoundaryY - y0) / dy;

        while (x != endX || y != endY) {
            if (tMaxX < tMaxY) {
                x += stepX;
                tMaxX += tDeltaX;
                if (blocksRayCell(x, y, endX, endY,
                        structuralOnly, excludeEnd)) return packCell(x, y);
            } else if (tMaxY < tMaxX) {
                y += stepY;
                tMaxY += tDeltaY;
                if (blocksRayCell(x, y, endX, endY,
                        structuralOnly, excludeEnd)) return packCell(x, y);
            } else {
                // At an exact corner the zero-width segment enters only the
                // diagonal cell; the orthogonal neighbors are touched at one
                // boundary point but their interiors are not crossed. This
                // preserves diagonal fire through a one-cell doorway corner.
                x += stepX;
                y += stepY;
                tMaxX += tDeltaX;
                tMaxY += tDeltaY;
                if (blocksRayCell(x, y, endX, endY,
                        structuralOnly, excludeEnd)) return packCell(x, y);
            }
        }
        return noBlockPacked();
    }

    private boolean blocksRayCell(int x, int y, int endX, int endY,
                                  boolean structuralOnly, boolean excludeEnd) {
        if (excludeEnd && x == endX && y == endY) return false;
        if (!inBounds(x, y)) return false;
        int idx = index(x, y);
        return structuralOnly
                ? blocksStructuralLineOfSightAt(idx)
                : blocksLineOfSightAt(idx);
    }

    private static long packCell(int x, int y) {
        return (((long) y & 0xFFFFFFFFL) << 32)
                | ((long) x & 0xFFFFFFFFL);
    }

    private static long noBlockPacked() {
        return packCell(-1, -1);
    }
}
