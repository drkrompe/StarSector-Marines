package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.DevConfig;
import com.dillon.starsectormarines.battle.unit.UnitDestinationSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.combat.DamageService;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.mesh.GreedyNavigationMesh;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the spatial state slice that {@code BattleSimulation} previously held
 * inline — the {@link NavigationGrid}, {@link CellTopology}, {@link ZoneGraph},
 * derived {@link GreedyNavigationMesh} + shared dirty lifecycle, the per-cell
 * {@link #occupancyMap}, the unit + destination
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
    private final GreedyNavigationMesh navigationMesh;
    private final HierarchicalPathfinder hierarchicalPathfinder;

    /** Per-cell unit count (current cell + path destination), rebuilt at the top of each tick and incrementally updated via {@link #applyOccupancyDeltaInline}. Read by the pathfinder so units route around ally-held cells. Saturates at 255. */
    private final byte[] occupancyMap;
    private final SharedGoalPathfinder sharedGoalPathfinder;
    /** Serial builder scratch; prepared fields retain only their settled corridor cells. */
    private final SquadRouteField.Builder squadRouteBuilder;
    /** Immutable batch published immediately before the parallel unit-update window. */
    private volatile Map<Integer, PreparedSquadRoute> preparedSquadRoutes = Map.of();
    private int lastSquadRouteBuilds;
    private int lastSquadRouteReuses;
    private int lastSquadRouteDeferred;
    private int lastSquadRouteCorridorCells;
    private int lastSquadRouteSettledCells;

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
     * rebuild ({@link #flushNavigationTopologyIfDirty}) since vantage geometry is
     * determined by walkability + LOS, which any breach / demolish event
     * invalidates.
     */
    private final Long2ObjectOpenHashMap<int[][]> vantagePointsByTargetCell = new Long2ObjectOpenHashMap<>();

    /**
     * Set whenever the walkability layout changes during a tick (wall breach,
     * turret demolish, hub demolish). Drained once at the end of the tick via
     * {@link #flushNavigationTopologyIfDirty()} so multiple breaches in the same tick
     * collapse into one update. AI queries that run mid-tick see the previous
     * tick's graph and mesh snapshot — fine in practice, since rubble stays walkable forever
     * (paths only ever gain shortcuts) and the new portal becomes visible
     * within 1/30s.
     *
     * <p>The opened cells are recorded ({@link #openedCells}) so the drain can take the
     * <b>incremental</b> {@link ZoneGraph#applyCellsOpened} path (zones only ever merge — see that
     * class) instead of an O(W×H) {@link ZoneGraph#rebuild()}. {@link #zoneForceFullRebuild} forces
     * the full path for a cell-less dirty mark or when {@link DevConfig#ZONE_INCREMENTAL_REBUILD}
     * is off (the kill-switch).
     */
    private boolean navigationTopologyDirty = false;
    private boolean zoneForceFullRebuild = false;
    private int[] openedCells = new int[8];
    private int openedCount = 0;

    /**
     * Roster/world owner used for by-id current-position reads in {@link #setPath}.
     * Setter-injected because the roster is constructed after this service; never
     * null once the simulation is wired.
     */
    private UnitRosterService roster;

    public NavigationService(NavigationGrid grid, CellTopology topology) {
        this.grid = grid;
        this.topology = topology;
        this.occupancyMap = new byte[grid.getWidth() * grid.getHeight()];
        this.unitIndex = new UnitSpatialIndex(grid.getWidth(), grid.getHeight());
        this.destIndex = new UnitDestinationSpatialIndex(grid.getWidth(), grid.getHeight());
        this.zoneGraph = new ZoneGraph(grid);
        this.zoneGraph.rebuild();
        this.navigationMesh = new GreedyNavigationMesh(grid);
        this.grid.preparePathComponents(GridPathfinder.USE_CARDINAL_NAVIGATION);
        this.hierarchicalPathfinder = new HierarchicalPathfinder(grid,
                navigationMesh);
        this.sharedGoalPathfinder = new SharedGoalPathfinder(grid,
                occupancyMap, hierarchicalPathfinder);
        this.squadRouteBuilder = new SquadRouteField.Builder(grid);
    }

    /** Injects the dense entity store once it's built (see {@link #roster}). Called once at sim construction. */
    public void setRoster(UnitRosterService roster) { this.roster = roster; }

    public NavigationGrid getGrid() { return grid; }
    /** Categorization tags (street / rubble / wall / vehicle / etc.) for renderer + placement filters. Sibling to {@link #grid}; the pathfinder doesn't touch this. */
    public CellTopology getTopology() { return topology; }
    /** Zone+portal graph layered on the {@link NavigationGrid}. Rebuilt on wall destruction so AI queries reflect the current map. */
    public ZoneGraph getZoneGraph() { return zoneGraph; }
    /**
     * Greedy rectangular acceleration layer derived from the same grid as the
     * zone graph. Its immutable snapshot advances at the topology flush
     * boundary, never during an in-tick destruction batch.
     */
    public GreedyNavigationMesh getNavigationMesh() { return navigationMesh; }
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

    /**
     * Records a just-opened cell (wall breach / structure→rubble) for the end-of-tick incremental
     * zone-graph update — called by {@code MapEditor}'s runtime map-modification ops. Preferred
     * over {@link #markNavigationTopologyDirty()}: it lets the drain take the O(smaller-zone)
     * {@link ZoneGraph#applyCellsOpened} path instead of a full O(W×H) rebuild.
     */
    public void markCellOpened(int x, int y) {
        navigationTopologyDirty = true;
        if (openedCount == openedCells.length) openedCells = Arrays.copyOf(openedCells, openedCount * 2);
        openedCells[openedCount++] = grid.index(x, y);
    }

    /** Marks every derived navigation layer dirty without a specific cell — forces a full rebuild on the next drain.
     *  Prefer {@link #markCellOpened} when the changed cell is known. */
    public void markNavigationTopologyDirty() {
        navigationTopologyDirty = true;
        zoneForceFullRebuild = true;
    }

    /** Compatibility name retained for existing mutation coordinators. */
    public void markZoneGraphDirty() { markNavigationTopologyDirty(); }

    public boolean isNavigationTopologyDirty() { return navigationTopologyDirty; }

    /** Compatibility name retained for existing diagnostics and tests. */
    public boolean isZoneGraphDirty() { return isNavigationTopologyDirty(); }

    /**
     * Runtime removal of a thin cardinal barrier. Edge topology only becomes
     * more permissive during battle, matching the cell-breach invariant: an
     * existing route remains legal and later searches gain the shortcut.
     * The full rebuild is intentional because opening an edge merges two
     * already-zoned walkable regions without adding a new cell for the
     * incremental cell-opening algorithm to fold.
     */
    public void openSharedEdge(int x, int y, Direction direction) {
        if (direction == null || direction.isDiagonal()) {
            throw new IllegalArgumentException(
                    "shared edge direction must be cardinal");
        }
        if (!grid.inBounds(x, y)
                || !grid.inBounds(x + direction.dx, y + direction.dy)) {
            return;
        }
        if (grid.isSharedEdgePassable(x, y, direction)) return;
        grid.openSharedEdge(x, y, direction);
        markNavigationTopologyDirty();
    }

    /**
     * Drains the derived-navigation dirty state at the end of a tick (collapsing multiple in-tick breaches
     * into one update), rebuilds the greedy navigation mesh, and clears the vantage-point cache in lockstep so the next
     * {@code findFiringPosition} stage-2 lookup recomputes against the new geometry. Retained
     * shared-goal fields are invalidated at the same boundary so their older topology view cannot
     * hide the new opening. No-op when clean. Takes the incremental {@link
     * ZoneGraph#applyCellsOpened} path when the changed cells are known and {@link
     * DevConfig#ZONE_INCREMENTAL_REBUILD} is on; otherwise a full {@link ZoneGraph#rebuild()}.
     */
    public void flushNavigationTopologyIfDirty() {
        if (flushZoneTopologyIfDirty()) rebuildDerivedNavigation();
    }

    /**
     * The zone-graph half of the flush, and the drain of the dirty state that
     * drove it. Returns {@code true} exactly when it did work — which is
     * exactly when the caller owes a {@link #rebuildDerivedNavigation()}.
     *
     * <p>Split from the second half so the tick profile can charge them
     * separately. The zone graph is the one derivation here that is already
     * incremental; the mesh re-covers only changed tiles but assembles a new
     * whole-region snapshot, so the two costs deserve separate laps.
     */
    public boolean flushZoneTopologyIfDirty() {
        if (!navigationTopologyDirty) return false;
        if (DevConfig.ZONE_INCREMENTAL_REBUILD && !zoneForceFullRebuild && openedCount > 0) {
            zoneGraph.applyCellsOpened(Arrays.copyOf(openedCells, openedCount));
        } else {
            zoneGraph.rebuild();
        }
        navigationTopologyDirty = false;
        zoneForceFullRebuild = false;
        openedCount = 0;
        return true;
    }

    /**
     * The derived-navigation half: the greedy mesh, the vantage-point cache and
     * every retained shared-goal field. Paired with a {@code true} from
     * {@link #flushZoneTopologyIfDirty()}; calling it unpaired is merely
     * wasteful rather than wrong.
     */
    public void rebuildDerivedNavigation() {
        navigationMesh.rebuild();
        grid.preparePathComponents(GridPathfinder.USE_CARDINAL_NAVIGATION);
        vantagePointsByTargetCell.clear();
        sharedGoalPathfinder.invalidateAll();
        preparedSquadRoutes = Map.of();
    }

    /** Compatibility name; prefer {@link #flushNavigationTopologyIfDirty()}. */
    public void flushZoneGraphIfDirty() { flushNavigationTopologyIfDirty(); }

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
     *
     * <p>Units carry a continuous (float) position; this method floors each
     * unit's position into its occupied grid cell before counting, so the
     * result is still a per-cell density field — semantics unchanged by the
     * continuous-position migration, only the source column did.
     */
    public void rebuildOccupancyMap(UnitRosterService roster) {
        EntityWorld world = roster.entityWorld();
        BattleComponents comps = roster.components();
        Arrays.fill(occupancyMap, (byte) 0);
        // Column-walk the live grid population instead of per-id reads off the
        // roster — the systems-half ECS idiom (grab the raw cell arrays once per
        // matched table, then a tight row loop; no per-unit location probe). The
        // archetype partitions movers from statics for free: a table carries
        // MOVEMENT iff its rows are movers, so the path-destination reservation is
        // gated by a per-table has(MOVEMENT), not a per-row probe. Static
        // emplacements (turrets, hubs) live in MOVEMENT-less tables and only claim
        // their current cell. Order-independent — occupancy is a saturating sum.
        for (ArchetypeTable t : world.matched(comps.gridOccupants)) {
            float[] posX = t.floats(comps.POSITION, BattleComponents.POSITION_X).array();
            float[] posY = t.floats(comps.POSITION, BattleComponents.POSITION_Y).array();
            boolean mover = t.has(comps.MOVEMENT);
            Object[] paths = mover
                    ? t.objects(comps.MOVEMENT, BattleComponents.MOVEMENT_PATH).array()
                    : null;
            for (int r = 0, n = t.rowCount(); r < n; r++) {
                // Bin at the floored grid cell — the occupancy map is cell-addressed.
                int curX = (int) Math.floor(posX[r]);
                int curY = (int) Math.floor(posY[r]);
                incrementOccupancy(curX, curY);
                if (!mover) continue;
                int[] path = (int[]) paths[r];
                int destX = Paths.destX(path);
                if (destX != Integer.MIN_VALUE) {
                    int destY = Paths.destY(path);
                    if (destX != curX || destY != curY) {
                        incrementOccupancy(destX, destY);
                    }
                }
            }
        }
    }

    /** Occupancy-aware coarse-to-fine path for ordinary one-off movement. */
    public int[] findPath(int startX, int startY, int goalX, int goalY) {
        return hierarchicalPathfinder.findPath(startX, startY, goalX, goalY,
                GridPathfinder.USE_CARDINAL_NAVIGATION, occupancyMap);
    }

    /** Geometry-only coarse-to-fine path for reachability and authored routes. */
    public int[] findGeometricPath(int startX, int startY,
                                   int goalX, int goalY) {
        return hierarchicalPathfinder.findPath(startX, startY, goalX, goalY,
                GridPathfinder.USE_CARDINAL_NAVIGATION, null);
    }

    /** Coarse-to-fine route with terrain cost and footprint clearance. */
    public int[] findPath(int startX, int startY, int goalX, int goalY,
                          float[] costField, boolean[] passable) {
        return hierarchicalPathfinder.findPath(startX, startY, goalX, goalY,
                GridPathfinder.USE_CARDINAL_NAVIGATION, null,
                costField, passable);
    }

    /**
     * Occupancy-aware path extraction from a reverse field shared by callers
     * pursuing the same goal during the frozen UPDATE_UNITS window. Outside
     * that explicitly bracketed phase the implementation falls back to A*.
     */
    public int[] findSharedPathToGoal(int startX, int startY,
                                      int goalX, int goalY) {
        return findSharedPathToGoal(startX, startY, goalX, goalY, null);
    }

    /**
     * As above, with a per-cell traversal cost folded into the shared field.
     * The costing is part of the field's identity, so two costings serve two
     * trees and a republished one retires its predecessor.
     */
    public int[] findSharedPathToGoal(int startX, int startY,
                                      int goalX, int goalY,
                                      RouteCostField cost) {
        return sharedGoalPathfinder.findPath(startX, startY, goalX, goalY,
                GridPathfinder.USE_CARDINAL_NAVIGATION, cost);
    }

    /**
     * Builds or retains one immutable local reverse field per requested squad
     * route. This method is serial-only and must run before UPDATE_UNITS. The
     * route seed is an exact, cost-aware cell path; its greedy-mesh regions
     * plus one neighboring region ring form the corridor. Compact fields are
     * then published as one immutable map for worker reads.
     */
    public void prepareSquadRoutes(List<SquadRouteRequest> requests) {
        Map<Integer, PreparedSquadRoute> previous = preparedSquadRoutes;
        Map<Integer, PreparedSquadRoute> next = new HashMap<>();
        GreedyNavigationMesh.Snapshot mesh = navigationMesh.snapshot();
        lastSquadRouteBuilds = 0;
        lastSquadRouteReuses = 0;
        lastSquadRouteDeferred = 0;
        lastSquadRouteCorridorCells = 0;
        lastSquadRouteSettledCells = 0;
        List<RouteCandidate> pending = new ArrayList<>();
        for (SquadRouteRequest request : requests) {
            PreparedSquadRoute retained = previous.get(request.squadId());
            if (retained != null && retained.isFresh(request, mesh.revision(),
                    grid.getWidth())) {
                next.put(request.squadId(), retained);
                lastSquadRouteReuses++;
                continue;
            }
            boolean compatible = retained != null
                    && retained.isCompatible(request, mesh.revision(),
                    grid.getWidth());
            pending.add(new RouteCandidate(request, retained, compatible));
        }
        int buildBudget = SharedGoalPolicy.maximumSquadRouteBuildsPerTick();
        // New/uncovered intents go first. Compatible older fields can serve
        // safely for another tick while their casualty-cost snapshot refreshes.
        for (int pass = 0; pass < 2; pass++) {
            boolean compatiblePass = pass == 1;
            for (RouteCandidate candidate : pending) {
                if (candidate.compatible != compatiblePass) continue;
                PreparedSquadRoute built = null;
                boolean attempted = false;
                if (buildBudget > 0) {
                    attempted = true;
                    long started = System.nanoTime();
                    built = buildSquadRoute(candidate.request, mesh);
                    TickInnerProfile profile = TickInnerProfile.currentIfBound();
                    if (profile != null) {
                        profile.record(TickInnerProfile.Bucket.SQUAD_PATH_FIELD_BUILD,
                                System.nanoTime() - started);
                    }
                    buildBudget--;
                }
                if (built != null) {
                    next.put(candidate.request.squadId(), built);
                    lastSquadRouteBuilds++;
                    lastSquadRouteCorridorCells += built.field.corridorCellCount();
                    lastSquadRouteSettledCells += built.field.settledCellCount();
                    TickInnerProfile profile = TickInnerProfile.currentIfBound();
                    if (profile != null) {
                        profile.recordSquadRouteFieldShape(
                                built.field.corridorCellCount(),
                                built.field.settledCellCount());
                    }
                } else if (candidate.compatible) {
                    next.put(candidate.request.squadId(),
                            candidate.retained.adopt(candidate.request));
                    lastSquadRouteDeferred++;
                } else if (attempted) {
                    // Remember the miss for this exact intent. Otherwise four
                    // permanently unreachable low-id squads would consume the
                    // whole budget on every tick and starve every later one.
                    next.put(candidate.request.squadId(),
                            PreparedSquadRoute.failure(candidate.request,
                                    mesh.revision(), grid.getWidth()));
                }
            }
        }
        preparedSquadRoutes = Map.copyOf(next);
    }

    private PreparedSquadRoute buildSquadRoute(
            SquadRouteRequest request, GreedyNavigationMesh.Snapshot mesh) {
        if (!grid.inBounds(request.goalX(), request.goalY())
                || !grid.isWalkable(request.goalX(), request.goalY())) {
            return null;
        }
        int[] starts = request.startCells();
        if (starts.length == 0 || mesh.regions().isEmpty()) return null;
        int goal = grid.index(request.goalX(), request.goalY());
        int goalRegion = mesh.regionIdAt(request.goalX(), request.goalY());
        if (goalRegion < 0) return null;
        boolean[] routeRegions = new boolean[mesh.regions().size()];
        boolean[] selectedRegions = new boolean[mesh.regions().size()];
        float[] costs = request.cost() == null ? null : request.cost().cells();
        boolean hasRoute = false;
        for (int start : starts) {
            if (start < 0 || start >= grid.getWidth() * grid.getHeight()) continue;
            int startX = start % grid.getWidth();
            int startY = start / grid.getWidth();
            int startRegion = mesh.regionIdAt(startX, startY);
            if (startRegion < 0) continue;
            if (hasRoute) {
                padRouteRegions(mesh, routeRegions, selectedRegions);
                if (selectedRegions[startRegion]) continue;
            }
            int[] seed = GridPathfinder.findPathUnprofiled(grid,
                    startX, startY, request.goalX(), request.goalY(),
                    GridPathfinder.USE_CARDINAL_NAVIGATION, null, costs, null);
            if (Paths.isEmpty(seed)) continue;
            for (int cell = 0; cell < Paths.cellCount(seed); cell++) {
                int region = mesh.regionIdAt(Paths.cellX(seed, cell),
                        Paths.cellY(seed, cell));
                if (region >= 0) routeRegions[region] = true;
            }
            hasRoute = true;
        }
        if (!hasRoute) return null;
        routeRegions[goalRegion] = true;
        padRouteRegions(mesh, routeRegions, selectedRegions);
        int corridorCount = 0;
        for (GreedyNavigationMesh.Region region : mesh.regions()) {
            if (selectedRegions[region.id()]) corridorCount += region.cellCount();
        }
        int[] corridor = new int[corridorCount];
        int offset = 0;
        int width = grid.getWidth();
        for (GreedyNavigationMesh.Region region : mesh.regions()) {
            if (!selectedRegions[region.id()]) continue;
            for (int y = region.y(); y < region.maxYExclusive(); y++) {
                for (int x = region.x(); x < region.maxXExclusive(); x++) {
                    corridor[offset++] = y * width + x;
                }
            }
        }
        Arrays.sort(corridor);
        SquadRouteField field = squadRouteBuilder.build(corridor, costs, goal,
                starts, GridPathfinder.USE_CARDINAL_NAVIGATION);
        return new PreparedSquadRoute(request.routingEpoch(),
                request.routeToken(), goal, mesh.revision(), request.cost(), field);
    }

    private static void padRouteRegions(
            GreedyNavigationMesh.Snapshot mesh, boolean[] routeRegions,
            boolean[] selectedRegions) {
        System.arraycopy(routeRegions, 0, selectedRegions, 0,
                routeRegions.length);
        for (GreedyNavigationMesh.Transition transition : mesh.transitions()) {
            if (routeRegions[transition.regionA()]) {
                selectedRegions[transition.regionB()] = true;
            }
            if (routeRegions[transition.regionB()]) {
                selectedRegions[transition.regionA()] = true;
            }
        }
    }

    /**
     * Reads a serially prepared squad field. A missing/mismatched intent or an
     * uncovered start is a cache miss, never an unreachable verdict: exact A*
     * remains the correctness fallback. Occupancy is intentionally excluded
     * from retained fields so ordinary crowd motion cannot invalidate them.
     */
    public int[] findSquadPathToGoal(
            int squadId, long routingEpoch, Object routeToken,
            int startX, int startY, int goalX, int goalY,
            RouteCostField currentCost) {
        if (!SharedGoalPolicy.squadRouteCorridorsEnabled()) {
            return findSharedPathToGoal(startX, startY, goalX, goalY,
                    currentCost);
        }
        long started = System.nanoTime();
        boolean extracted = false;
        try {
            PreparedSquadRoute prepared = preparedSquadRoutes.get(squadId);
            boolean matches = prepared != null
                    && prepared.routingEpoch == routingEpoch
                    && prepared.routeToken == routeToken
                    && grid.inBounds(goalX, goalY)
                    && prepared.goal == grid.index(goalX, goalY);
            if (matches && prepared.field != null) {
                int[] path = prepared.field.extract(startX, startY);
                if (!Paths.isEmpty(path)) {
                    extracted = true;
                    return path;
                }
            }
            RouteCostField fallbackCost = matches ? prepared.cost : currentCost;
            float[] costs = fallbackCost == null ? null : fallbackCost.cells();
            return GridPathfinder.findPathUnprofiled(grid, startX, startY,
                    goalX, goalY, GridPathfinder.USE_CARDINAL_NAVIGATION,
                    occupancyMap, costs, null);
        } finally {
            TickInnerProfile profile = TickInnerProfile.currentIfBound();
            if (profile != null) {
                long elapsed = System.nanoTime() - started;
                profile.record(TickInnerProfile.Bucket.PATHFIND, elapsed);
                profile.record(extracted
                                ? TickInnerProfile.Bucket.SQUAD_PATH_FIELD_EXTRACT
                                : TickInnerProfile.Bucket.SQUAD_PATH_FIELD_FALLBACK,
                        elapsed);
                if (GridPathfinder.profilePathRequests()) {
                    profile.recordPathfindRequest(startX, startY,
                            goalX, goalY, !extracted);
                }
            }
        }
    }

    public int preparedSquadRouteCount() { return preparedSquadRoutes.size(); }
    public int lastSquadRouteBuilds() { return lastSquadRouteBuilds; }
    public int lastSquadRouteReuses() { return lastSquadRouteReuses; }
    public int lastSquadRouteDeferred() { return lastSquadRouteDeferred; }
    public int lastSquadRouteCorridorCells() { return lastSquadRouteCorridorCells; }
    public int lastSquadRouteSettledCells() { return lastSquadRouteSettledCells; }

    private static final class PreparedSquadRoute {
        private final long routingEpoch;
        private final long builtEpoch;
        private final Object routeToken;
        private final int goal;
        private final long meshRevision;
        private final RouteCostField cost;
        private final SquadRouteField field;

        private PreparedSquadRoute(long routingEpoch, Object routeToken,
                                   int goal, long meshRevision,
                                   RouteCostField cost, SquadRouteField field) {
            this.routingEpoch = routingEpoch;
            this.builtEpoch = routingEpoch;
            this.routeToken = routeToken;
            this.goal = goal;
            this.meshRevision = meshRevision;
            this.cost = cost;
            this.field = field;
        }

        private static PreparedSquadRoute failure(
                SquadRouteRequest request, long meshRevision, int width) {
            return new PreparedSquadRoute(request.routingEpoch(),
                    request.routeToken(), request.goalY() * width
                    + request.goalX(), meshRevision, request.cost(), null);
        }

        private PreparedSquadRoute(long routingEpoch, long builtEpoch,
                                   Object routeToken, int goal,
                                   long meshRevision, RouteCostField cost,
                                   SquadRouteField field) {
            this.routingEpoch = routingEpoch;
            this.builtEpoch = builtEpoch;
            this.routeToken = routeToken;
            this.goal = goal;
            this.meshRevision = meshRevision;
            this.cost = cost;
            this.field = field;
        }

        private boolean isFresh(SquadRouteRequest request, long revision,
                                int width) {
            return routingEpoch == request.routingEpoch()
                    && builtEpoch == request.routingEpoch()
                    && routeToken == request.routeToken()
                    && goal == request.goalY() * width + request.goalX()
                    && meshRevision == revision;
        }

        private boolean isCompatible(SquadRouteRequest request, long revision,
                                     int width) {
            if (goal != request.goalY() * width + request.goalX()
                    || meshRevision != revision || field == null) return false;
            for (int start : request.startCells()) {
                if (!field.covers(start)) return false;
            }
            return true;
        }

        private PreparedSquadRoute adopt(SquadRouteRequest request) {
            return new PreparedSquadRoute(request.routingEpoch(), builtEpoch,
                    request.routeToken(), goal, meshRevision, cost, field);
        }
    }

    private record RouteCandidate(SquadRouteRequest request,
                                  PreparedSquadRoute retained,
                                  boolean compatible) { }

    public void beginSharedGoalPathSnapshot() {
        sharedGoalPathfinder.beginSnapshot();
    }

    public void endSharedGoalPathSnapshot() {
        sharedGoalPathfinder.endSnapshot();
    }

    /**
     * Rebuilds both spatial indices off the same tick-start snapshot of the
     * registry. Called right after {@link #rebuildOccupancyMap} so all
     * spatial state reflects the same frozen view. Both indices iterate
     * the registry's dense array directly (Phase 3 SoA consumer) — released
     * units are excluded by construction, and cellX/cellY reads stream
     * from the SoA arrays without per-unit indirection.
     */
    public void rebuildSpatialIndices(UnitRosterService roster) {
        unitIndex.rebuild(roster);
        destIndex.rebuild(roster);
    }

    /**
     * Inline occupancy + destIndex delta applier — wired into
     * {@link DamageService} at sim construction. Serial callers (off-tick,
     * post-UPDATE_UNITS) run this directly; parallel callers route through
     * the damage service's queue and the APPLY_OCCUPANCY drain runs the same
     * applier. {@code Integer.MIN_VALUE} for an old / new coord is the
     * "no-op" sentinel — that half of the delta is skipped.
     */
    public void applyOccupancyDeltaInline(long id, int oldDestX, int oldDestY, int newDestX, int newDestY) {
        if (oldDestX != Integer.MIN_VALUE) {
            decrementOccupancy(oldDestX, oldDestY);
            destIndex.removeDestination(id, oldDestX, oldDestY);
        }
        if (newDestX != Integer.MIN_VALUE) {
            incrementOccupancy(newDestX, newDestY);
            destIndex.addDestination(roster, id, newDestX, newDestY);
        }
    }

    /**
     * Replaces a unit's path and queues a deferred {@link #occupancyMap} +
     * {@link #destIndex} update. The path ref + cursor are the unit's own
     * MOVEMENT-component state, written by id here; the shared spatial-state
     * change goes through the queued {@link #occupancyDeltaSink} so the parallel
     * UPDATE_UNITS dispatch never races on the occupancy map or destIndex
     * (the delta drains in APPLY_OCCUPANCY at the end of the dispatch).
     * Pass {@link GridPathfinder#EMPTY_PATH} (or call {@link #clearPath})
     * to drop the current path.
     *
     * <p>Self-cell destinations don't claim occupancy, so both halves of the
     * delta skip them; if neither old nor new destination is occupancy-bearing
     * the sink call is elided entirely.
     */
    public void setPath(long id, int[] newPath) {
        World world = roster.world();
        // A unit riding in a vehicle has no MOVEMENT to hold a path, and
        // routing one is meaningless while it is not on the map. Giving it a
        // path is the caller's mistake; clearing the path of something that has
        // none is trivially already done, and callers do exactly that when an
        // order finishes by putting the squad aboard.
        if (roster.isRiding(id)) return;
        int[] oldPath = world.path(id);
        int oldDestX = Paths.destX(oldPath);
        int oldDestY = Paths.destY(oldPath);
        world.setPathRef(id, newPath);
        // Multi-cell paths start the carrot cursor at waypoint 1 (waypoint 0 is
        // the cell the unit already stands in). A one-cell path (findPath with
        // start == goal) must start at 0, or it is born exhausted and the mover
        // never pulls the unit onto the cell center — stranding it inside the
        // arrival band whenever a repath clobbers the in-flight path. Note a
        // hand-built one-cell path to a FOREIGN cell is walked straight-line
        // with no wall check — only findPath-produced paths are wall-legal.
        world.setPathIdx(id, newPath.length <= 2 ? 0 : 1);
        if (newPath.length > 0) roster.movement().markRepath(id);
        int newDestX;
        int newDestY;
        if (newPath.length > 0) {
            newDestX = newPath[newPath.length - 2];
            newDestY = newPath[newPath.length - 1];
        } else {
            newDestX = Integer.MIN_VALUE;
            newDestY = Integer.MIN_VALUE;
        }
        int curX = world.cellX(id);
        int curY = world.cellY(id);
        boolean hasOld = oldDestX != Integer.MIN_VALUE && (oldDestX != curX || oldDestY != curY);
        boolean hasNew = newDestX != Integer.MIN_VALUE && (newDestX != curX || newDestY != curY);
        if (!hasOld && !hasNew) return;
        occupancyDeltaSink.apply(id,
                hasOld ? oldDestX : Integer.MIN_VALUE,
                hasOld ? oldDestY : Integer.MIN_VALUE,
                hasNew ? newDestX : Integer.MIN_VALUE,
                hasNew ? newDestY : Integer.MIN_VALUE);
    }

    /** Convenience: drop the unit's path. Equivalent to {@code setPath(id, GridPathfinder.EMPTY_PATH)}. */
    public void clearPath(long id) {
        setPath(id, GridPathfinder.EMPTY_PATH);
    }

    /**
     * Begin-of-tick {@link LosCache} setup — sweeps every worker's slot on
     * <em>this grid</em> so cached pairs can't outlive a wall breach from the
     * prior tick's cleanup pass, then switches on auto-init for the duration
     * of the tick. Pairs with {@link #endTick()}. Another simulation's grid is
     * untouched, which is the whole reason the caches hang off the grid.
     */
    public void beginTick() {
        LosCaches caches = grid.losCaches();
        caches.clearAll();
        caches.enable();
    }

    /**
     * End-of-tick {@link LosCache} teardown — switches this grid's per-thread
     * caches off so off-tick callers (tests, mid-frame UI hooks) see
     * {@code null} and fall through to live Bresenham. Pairs with
     * {@link #beginTick()}.
     */
    public void endTick() {
        grid.losCaches().disable();
    }

    /**
     * Drops the calling thread's cache for this grid. Called at the ownership
     * edges — a battle-owned update worker terminating, and the simulation
     * closing on its host thread.
     */
    public void releaseCurrentThreadLosCache() {
        grid.losCaches().releaseCurrentThread();
    }

    /** X coordinate of {@code u}'s final path cell, or {@code Integer.MIN_VALUE} if empty. Reads the path off the MOVEMENT component by id. */
    public int pathDestX(long u) {
        return Paths.destX(roster.world().path(u));
    }
    /** Y coordinate of {@code u}'s final path cell, or {@code Integer.MIN_VALUE} if empty. Reads the path off the MOVEMENT component by id. */
    public int pathDestY(long u) {
        return Paths.destY(roster.world().path(u));
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
