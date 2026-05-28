package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitDestinationSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.combat.DamageService;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.profile.LosCache;
import com.dillon.starsectormarines.battle.unit.UnitRegistry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Arrays;
import java.util.List;

/**
 * Owns the spatial state slice that {@code BattleSimulation} previously held
 * inline — the {@link NavigationGrid}, {@link CellTopology}, {@link ZoneGraph}
 * + dirty flag, the per-cell {@link #occupancyMap}, the unit + destination
 * spatial indices, the per-target vantage-point cache, and the per-tick
 * {@link LosCache} lifecycle. Sibling slice to
 * {@link com.dillon.starsectormarines.battle.combat.fx.EffectsService},
 * {@link com.dillon.starsectormarines.battle.combat.DamageService},
 * {@link com.dillon.starsectormarines.battle.unit.UnitRosterService} et al.
 *
 * <p>The sim aliases the grid / topology / zoneGraph / occupancyMap / indices
 * so its 100+ internal read sites stay direct (no per-call accessor hop) —
 * this service is still the canonical owner, the aliases are init-time
 * references to the same underlying instances.
 *
 * <p>{@link #applyOccupancyDeltaInline} is wired into the
 * {@link DamageService} occupancy-applier slot at sim construction. Serial
 * callers run it inline; parallel-dispatch callers route through the damage
 * service's queue and the drain runs the same applier — preserves the
 * "service owns inline-vs-defer" pattern from {@link DamageService}.
 */
public final class NavigationService {

    private final NavigationGrid grid;
    private final CellTopology topology;
    private final ZoneGraph zoneGraph;

    /** Per-cell unit count (current cell + path destination), rebuilt at the top of each tick and incrementally updated via {@link #applyOccupancyDeltaInline}. Read by the pathfinder so units route around ally-held cells. Saturates at 255. */
    private final byte[] occupancyMap;

    /** Bucketed spatial index over alive units. Rebuilt once per tick by {@link #rebuildSpatialIndices}. */
    private final UnitSpatialIndex unitIndex;
    /** Sister index keyed on each unit's path destination cell. Rebuilt alongside {@link #unitIndex} and incrementally maintained through {@link #applyOccupancyDeltaInline}. */
    private final UnitDestinationSpatialIndex destIndex;

    /**
     * Per-target-cell cache of walkable cells with line of sight to that
     * cell — the "vantage points" stage 2 of
     * {@link TacticalScoring#findFiringPosition} picks from when no in-range
     * LOS-bearing firing position exists.
     *
     * <p>Lifetime is per-battle; cleared in lockstep with the zone-graph
     * rebuild ({@link #flushZoneGraphIfDirty}) since vantage geometry is
     * determined by walkability + LOS, which any breach / demolish event
     * invalidates.
     */
    private final Long2ObjectOpenHashMap<int[][]> vantagePointsByTargetCell = new Long2ObjectOpenHashMap<>();

    /**
     * Set whenever the walkability layout changes during a tick (wall breach,
     * turret demolish, hub demolish). Drained to a single
     * {@link ZoneGraph#rebuild()} via {@link #flushZoneGraphIfDirty()} at the
     * end of the tick so multiple breaches in the same tick collapse into one
     * full graph rebuild. AI queries that run mid-tick see the previous
     * tick's graph — fine in practice, since rubble stays walkable forever
     * (paths only ever gain shortcuts) and the new portal becomes visible
     * within 1/30s.
     */
    private boolean zoneGraphDirty = false;

    public NavigationService(NavigationGrid grid, CellTopology topology) {
        this.grid = grid;
        this.topology = topology;
        this.occupancyMap = new byte[grid.getWidth() * grid.getHeight()];
        this.unitIndex = new UnitSpatialIndex(grid.getWidth(), grid.getHeight());
        this.destIndex = new UnitDestinationSpatialIndex(grid.getWidth(), grid.getHeight());
        this.zoneGraph = new ZoneGraph(grid);
        this.zoneGraph.rebuild();
    }

    public NavigationGrid getGrid() { return grid; }
    /** Categorization tags (street / rubble / wall / vehicle / etc.) for renderer + placement filters. Sibling to {@link #grid}; the pathfinder doesn't touch this. */
    public CellTopology getTopology() { return topology; }
    /** Zone+portal graph layered on the {@link NavigationGrid}. Rebuilt on wall destruction so AI queries reflect the current map. */
    public ZoneGraph getZoneGraph() { return zoneGraph; }
    /** Per-cell unit count, indexed by {@link NavigationGrid#index(int, int)}. */
    public byte[] getOccupancyMap() { return occupancyMap; }
    public UnitSpatialIndex getUnitIndex() { return unitIndex; }
    public UnitDestinationSpatialIndex getDestIndex() { return destIndex; }

    /**
     * True if any alive ground unit currently occupies the given cell (current
     * position or path destination). Reads the precomputed {@link #occupancyMap}
     * — no unit scan.
     */
    public boolean isCellOccupied(int x, int y) {
        if (!grid.inBounds(x, y)) return false;
        return (occupancyMap[y * grid.getWidth() + x] & 0xFF) > 0;
    }

    @FunctionalInterface
    public interface CellCallback {
        void accept(int x, int y);
    }

    private CellCallback roofCollapseSink;

    public void setRoofCollapseSink(CellCallback sink) { this.roofCollapseSink = sink; }

    /**
     * Queued (parallel-safe) occupancy-delta sink that {@link #setPath} routes
     * through — bound to {@link DamageService#applyOccupancyDelta} at sim
     * construction. The queue itself stays in {@link DamageService} (the owner
     * of the parallel-dispatch safety queues); this is just the enqueue hook.
     * Setter-injected rather than constructor-injected because the sim builds
     * this service before {@link DamageService} exists (the inline applier the
     * damage service needs is one of <em>our</em> methods).
     */
    private DamageService.OccupancyApplier occupancyDeltaSink;

    public void setOccupancyDeltaSink(DamageService.OccupancyApplier sink) { this.occupancyDeltaSink = sink; }

    public boolean damageWall(int x, int y, int amount) {
        if (!grid.damageCell(x, y, amount)) return false;
        topology.setWall(x, y, false);
        topology.setGroundKind(x, y, CellTopology.GroundKind.RUBBLE);
        peelRoofAround(x, y);
        markZoneGraphDirty();
        return true;
    }

    private void peelRoofAround(int wallX, int wallY) {
        destroyRoof(wallX - 1, wallY);
        destroyRoof(wallX + 1, wallY);
        destroyRoof(wallX, wallY - 1);
        destroyRoof(wallX, wallY + 1);
    }

    public void destroyRoof(int x, int y) {
        if (!grid.inBounds(x, y)) return;
        if (topology.getBuildingId(x, y) == 0) return;
        if (topology.isRoofDestroyed(x, y)) return;
        topology.setRoofDestroyed(x, y, true);
        if (roofCollapseSink != null) roofCollapseSink.accept(x, y);
    }

    /** Flips the dirty flag — called by the sim's {@code damageCell} + the demolition passes when a walkability change happens mid-tick. */
    public void markZoneGraphDirty() { zoneGraphDirty = true; }
    public boolean isZoneGraphDirty() { return zoneGraphDirty; }

    /**
     * Drains the zone-graph dirty flag at the end of a tick: rebuilds the
     * graph once (collapsing multiple in-tick breaches into a single rebuild)
     * and clears the vantage-point cache in lockstep so the next
     * {@code findFiringPosition} stage-2 lookup recomputes against the new
     * geometry. No-op when the flag is clear.
     */
    public void flushZoneGraphIfDirty() {
        if (!zoneGraphDirty) return;
        zoneGraph.rebuild();
        vantagePointsByTargetCell.clear();
        zoneGraphDirty = false;
    }

    /**
     * Returns the cached vantage-point set for target cell ({@code tx},
     * {@code ty}). Computes on cache miss and stores; subsequent hits return
     * the same {@code int[][]} reference.
     *
     * <p>Synchronized for the parallel UPDATE_UNITS path — fastutil's
     * {@link Long2ObjectOpenHashMap} isn't thread-safe; concurrent put can
     * rehash mid-get. Holds the lock across the
     * {@link TacticalScoring#computeVantagePoints} call (the expensive part)
     * because cache misses are rare and we want at-most-once compute per
     * target cell.
     */
    public int[][] getVantagePointsFor(int tx, int ty) {
        long key = (long) ty * grid.getWidth() + tx;
        synchronized (vantagePointsByTargetCell) {
            int[][] cached = vantagePointsByTargetCell.get(key);
            if (cached != null) return cached;
            int[][] computed = TacticalScoring.computeVantagePoints(grid, tx, ty);
            vantagePointsByTargetCell.put(key, computed);
            return computed;
        }
    }

    /**
     * Counts alive units per cell into {@link #occupancyMap}, including each
     * unit's path destination cell (if different from its current cell). This
     * makes destination cells visible to firing-position and fall-back scoring,
     * so units don't all converge on the same goal.
     *
     * <p>The map is also incrementally updated within a tick via
     * {@link #applyOccupancyDeltaInline} — when a unit re-paths in
     * {@code updateUnit}, the old destination is decremented and the new one
     * incremented — so units picking positions later in the same tick see
     * the freshest information.
     */
    public void rebuildOccupancyMap(List<Unit> units) {
        Arrays.fill(occupancyMap, (byte) 0);
        for (int i = 0, n = units.size(); i < n; i++) {
            Unit u = units.get(i);
            if (!u.isAlive()) continue;
            incrementOccupancy(u.getCellX(), u.getCellY());
            int destX = pathDestX(u);
            if (destX != Integer.MIN_VALUE) {
                int destY = pathDestY(u);
                if (destX != u.getCellX() || destY != u.getCellY()) {
                    incrementOccupancy(destX, destY);
                }
            }
        }
    }

    /**
     * Rebuilds both spatial indices off the same tick-start snapshot of the
     * registry. Called right after {@link #rebuildOccupancyMap} so all
     * spatial state reflects the same frozen view. Both indices iterate
     * the registry's dense array directly (Phase 3 SoA consumer) — released
     * units are excluded by construction, and cellX/cellY reads stream
     * from the SoA arrays without per-unit indirection.
     */
    public void rebuildSpatialIndices(UnitRegistry registry) {
        unitIndex.rebuild(registry);
        destIndex.rebuild(registry);
    }

    /**
     * Inline occupancy + destIndex delta applier — wired into
     * {@link DamageService} at sim construction. Serial callers (off-tick,
     * post-UPDATE_UNITS) run this directly; parallel callers route through
     * the damage service's queue and the APPLY_OCCUPANCY drain runs the same
     * applier. {@code Integer.MIN_VALUE} for an old / new coord is the
     * "no-op" sentinel — that half of the delta is skipped.
     */
    public void applyOccupancyDeltaInline(Unit u, int oldDestX, int oldDestY, int newDestX, int newDestY) {
        if (oldDestX != Integer.MIN_VALUE) {
            decrementOccupancy(oldDestX, oldDestY);
            destIndex.removeDestination(u, oldDestX, oldDestY);
        }
        if (newDestX != Integer.MIN_VALUE) {
            incrementOccupancy(newDestX, newDestY);
            destIndex.addDestination(u, newDestX, newDestY);
        }
    }

    /**
     * Replaces a unit's path and queues a deferred {@link #occupancyMap} +
     * {@link #destIndex} update. {@code u.path} / {@code u.pathIdx} are
     * unit-local and mutated inline; the shared spatial-state change goes
     * through the queued {@link #occupancyDeltaSink} so the parallel
     * UPDATE_UNITS dispatch never races on the occupancy map or destIndex
     * (the delta drains in APPLY_OCCUPANCY at the end of the dispatch).
     * Pass {@link GridPathfinder#EMPTY_PATH} (or call {@link #clearPath})
     * to drop the current path.
     *
     * <p>Self-cell destinations don't claim occupancy, so both halves of the
     * delta skip them; if neither old nor new destination is occupancy-bearing
     * the sink call is elided entirely.
     */
    public void setPath(Unit u, int[] newPath) {
        int oldDestX = pathDestX(u);
        int oldDestY = pathDestY(u);
        u.path = newPath;
        u.pathIdx = newPath.length == 0 ? 0 : 1;
        int newDestX;
        int newDestY;
        if (newPath.length > 0) {
            newDestX = newPath[newPath.length - 2];
            newDestY = newPath[newPath.length - 1];
        } else {
            newDestX = Integer.MIN_VALUE;
            newDestY = Integer.MIN_VALUE;
        }
        boolean hasOld = oldDestX != Integer.MIN_VALUE && (oldDestX != u.getCellX() || oldDestY != u.getCellY());
        boolean hasNew = newDestX != Integer.MIN_VALUE && (newDestX != u.getCellX() || newDestY != u.getCellY());
        if (!hasOld && !hasNew) return;
        occupancyDeltaSink.apply(u,
                hasOld ? oldDestX : Integer.MIN_VALUE,
                hasOld ? oldDestY : Integer.MIN_VALUE,
                hasNew ? newDestX : Integer.MIN_VALUE,
                hasNew ? newDestY : Integer.MIN_VALUE);
    }

    /** Convenience: drop the unit's path. Equivalent to {@code setPath(u, GridPathfinder.EMPTY_PATH)}. */
    public void clearPath(Unit u) {
        setPath(u, GridPathfinder.EMPTY_PATH);
    }

    /**
     * Flips a dead structure cell (destroyed turret mount, demolished drone
     * hub) to walkable rubble: opens the cell + all four edges, sets the
     * topology to {@link CellTopology.GroundKind#RUBBLE}, recomputes cover
     * on the cell and its four cardinal neighbors, and marks the zone graph
     * dirty so {@link #flushZoneGraphIfDirty} picks up the new portal at
     * tick end. Sibling to the wall-collapse path inside
     * {@link NavigationGrid#damageCell} — same intent ("obstacle removed,
     * stamp rubble, refresh navigation") for the non-wall obstacle kinds.
     */
    public void flipCellToRubble(int cellX, int cellY) {
        grid.setWalkable(cellX, cellY, true);
        grid.openAllEdges(cellX, cellY);
        topology.setGroundKind(cellX, cellY, CellTopology.GroundKind.RUBBLE);
        grid.recomputeCoverAt(cellX, cellY);
        grid.recomputeCoverAt(cellX + 1, cellY);
        grid.recomputeCoverAt(cellX - 1, cellY);
        grid.recomputeCoverAt(cellX, cellY + 1);
        grid.recomputeCoverAt(cellX, cellY - 1);
        markZoneGraphDirty();
    }

    /**
     * Begin-of-tick {@link LosCache} setup — sweeps every worker's slot so
     * cached pairs can't outlive a wall breach from the prior tick's cleanup
     * pass, then switches on auto-init for the duration of the tick. Pairs
     * with {@link #endTick()}.
     */
    public void beginTick() {
        LosCache.clearAll();
        LosCache.enable();
    }

    /**
     * End-of-tick {@link LosCache} teardown — switches the per-thread cache
     * off so off-tick callers (tests, mid-frame UI hooks) see {@code null}
     * and fall through to live Bresenham. Pairs with {@link #beginTick()}.
     */
    public void endTick() {
        LosCache.disable();
    }

    /** X coordinate of the unit's final path cell, or {@code Integer.MIN_VALUE} if the path is empty. */
    public static int pathDestX(Unit u) {
        return u.path.length == 0 ? Integer.MIN_VALUE : u.path[u.path.length - 2];
    }
    /** Y coordinate of the unit's final path cell, or {@code Integer.MIN_VALUE} if the path is empty. */
    public static int pathDestY(Unit u) {
        return u.path.length == 0 ? Integer.MIN_VALUE : u.path[u.path.length - 1];
    }

    private void incrementOccupancy(int x, int y) {
        if (!grid.inBounds(x, y)) return;
        int idx = y * grid.getWidth() + x;
        int cur = occupancyMap[idx] & 0xFF;
        if (cur < 255) occupancyMap[idx] = (byte) (cur + 1);
    }

    private void decrementOccupancy(int x, int y) {
        if (!grid.inBounds(x, y)) return;
        int idx = y * grid.getWidth() + x;
        int cur = occupancyMap[idx] & 0xFF;
        if (cur > 0) occupancyMap[idx] = (byte) (cur - 1);
    }
}
