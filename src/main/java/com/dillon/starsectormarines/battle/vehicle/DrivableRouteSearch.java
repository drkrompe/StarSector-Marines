package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.HashSet;
import java.util.Set;

/**
 * One endpoint pair's drivable-route search, carried across ticks.
 *
 * <p>{@link VehicleRoutePlanner#routeDrivable} is a loop, not a search: it finds
 * the minimum-cost path, asks the turn refinement whether the chassis can
 * actually drive its bends, and where it cannot it masks off the offending bend
 * and searches again. That loop is <em>stateful in the mask</em>, and the state
 * is the whole value of it — on the production Conquest fixture the best-ranked
 * drop routed its inbound leg on the first search and needed eighteen more
 * before its outbound leg was drivable, each one avoiding a little more of the
 * ground the last one failed on. Restarting it would not resume it; it would
 * begin it again.
 *
 * <p>So spreading a route proof across ticks cannot be done by re-entering the
 * planner with a smaller budget. It holds the endpoints, failed-turn exclusions,
 * and the current A* frontier. {@link #advance} bounds both new attempts and,
 * for on-demand routing, node expansions, reporting {@code PENDING} when it
 * has neither found a route nor refused one.
 *
 * <p>The planner stays a function of its inputs: this is where the resumable
 * state lives, and {@code routeDrivable} is now the special case that advances
 * one of these to completion in a single call.
 */
public final class DrivableRouteSearch {

    /** How a search stands after an {@link #advance}. */
    public enum Status {
        /** Neither found nor refused yet — call {@link #advance} again. */
        PENDING,
        /** A drivable polyline exists; read it from {@link #route}. */
        ROUTED,
        /** No drivable route between these endpoints. Terminal. */
        NO_ROUTE
    }

    private final int startX;
    private final int startY;
    private final int goalX;
    private final int goalY;
    private final NavigationGrid grid;
    private final TerrainCostField costField;
    /** Working mask — the clearance set minus every bend the refinement has refused so far. Mutated by {@link #advance}. */
    private final boolean[] mask;
    /** The unmodified clearance set, used to restore the endpoints after a disc clips them. */
    private final boolean[] basePassable;
    /** On-demand mode keeps only refused bends, not a copied map-sized mask. */
    private final Set<Integer> blocked;
    private final GridPathfinder.IndexedCost indexedCost;
    private final GridPathfinder.IndexedPassability indexedPassable;
    private final GridPathfinder.IndexedPassability workingPassable;
    private final int width;
    private final int height;
    private final VehicleType type;

    private Status status = Status.PENDING;
    private float[][] route;
    private GridPathfinder.OnDemandSearch pendingSearch;
    private GridPathfinder.OnDemandSearch exhaustedBaseSearch;
    private int attemptsStarted;
    private int expandedNodesThisAdvance;

    /**
     * A search over {@code clearance} for a body of {@code type}. The mask is
     * copied, so the caller's clearance snapshot is never written to and two
     * searches over the same snapshot cannot interfere.
     */
    public static DrivableRouteSearch over(int startX, int startY, int goalX, int goalY,
                                           NavigationGrid grid, TerrainCostField costField,
                                           VehicleClearance clearance, VehicleType type) {
        return new DrivableRouteSearch(startX, startY, goalX, goalY, grid, costField,
                clearance.passableArray().clone(), clearance.passableArray(),
                clearance.getWidth(), clearance.getHeight(), type);
    }

    /**
     * Resumable drivable search over stable, lazily derived fields. Failed turns
     * are recorded as a sparse overlay; neither the clearance view nor the
     * terrain-cost view is materialized or copied. The supplied grid must be
     * the same frozen topology used by both views and by turn refinement.
     */
    public static DrivableRouteSearch overOnDemand(int startX, int startY,
                                                    int goalX, int goalY,
                                                    NavigationGrid grid,
                                                    GridPathfinder.IndexedCost cost,
                                                    GridPathfinder.IndexedPassability passable,
                                                    VehicleType type) {
        return new DrivableRouteSearch(startX, startY, goalX, goalY,
                grid, cost, passable, type);
    }

    DrivableRouteSearch(int startX, int startY, int goalX, int goalY,
                        NavigationGrid grid, TerrainCostField costField,
                        boolean[] mask, boolean[] basePassable,
                        int width, int height, VehicleType type) {
        this.startX = startX;
        this.startY = startY;
        this.goalX = goalX;
        this.goalY = goalY;
        this.grid = grid;
        this.costField = costField;
        this.mask = mask;
        this.basePassable = basePassable;
        this.blocked = null;
        this.indexedCost = null;
        this.indexedPassable = null;
        this.workingPassable = null;
        this.width = width;
        this.height = height;
        this.type = type;
    }

    private DrivableRouteSearch(int startX, int startY, int goalX, int goalY,
                                NavigationGrid grid,
                                GridPathfinder.IndexedCost cost,
                                GridPathfinder.IndexedPassability passable,
                                VehicleType type) {
        this.startX = startX;
        this.startY = startY;
        this.goalX = goalX;
        this.goalY = goalY;
        this.grid = grid;
        this.costField = null;
        this.mask = null;
        this.basePassable = null;
        this.blocked = new HashSet<>();
        this.indexedCost = cost;
        this.indexedPassable = passable;
        this.workingPassable = index -> !blocked.contains(index)
                && indexedPassable.isPassable(index);
        this.width = grid.getWidth();
        this.height = grid.getHeight();
        this.type = type;
    }

    /**
     * Spends at most {@code maxSearches} of {@code budget} on this pair.
     *
     * <p>Stops early on a terminal answer, and stops without one when either
     * this step's allowance or the caller's whole budget runs out — the two are
     * told apart by asking the budget, because they mean different things: the
     * first is "come back next tick" and the second is "the proof is over".
     */
    public Status advance(RouteSearchBudget budget, int maxSearches) {
        return advance(budget, maxSearches, Integer.MAX_VALUE);
    }

    /**
     * Also limits on-demand A* work by node expansions. A partially expanded
     * search retains its own frontier and consumes its caller-owned search
     * budget only once, when that attempt starts. Eager mode retains its
     * historical whole-search behavior.
     */
    public Status advance(RouteSearchBudget budget, int maxSearches,
                          int maxExpandedNodes) {
        if (maxExpandedNodes < 0) {
            throw new IllegalArgumentException("maxExpandedNodes must be nonnegative");
        }
        expandedNodesThisAdvance = 0;
        if (blocked != null) {
            return advanceOnDemand(budget, maxSearches, maxExpandedNodes);
        }
        int spent = 0;
        while (status == Status.PENDING && spent < maxSearches) {
            if (!budget.claim()) return status;
            spent++;
            searchOnce();
        }
        return status;
    }

    private Status advanceOnDemand(RouteSearchBudget budget, int maxSearches,
                                   int maxExpandedNodes) {
        int attempted = 0;
        while (status == Status.PENDING && attempted < maxSearches
                && expandedNodesThisAdvance < maxExpandedNodes) {
            if (pendingSearch == null) {
                if (!budget.claim()) return status;
                pendingSearch = GridPathfinder.beginOnDemand(grid,
                        startX, startY, goalX, goalY,
                        indexedCost, workingPassable);
                attemptsStarted++;
            }
            attempted++;
            int before = pendingSearch.expandedNodes();
            GridPathfinder.OnDemandSearch.Status searchStatus = pendingSearch.advance(
                    maxExpandedNodes - expandedNodesThisAdvance);
            expandedNodesThisAdvance += pendingSearch.expandedNodes() - before;
            if (searchStatus == GridPathfinder.OnDemandSearch.Status.PENDING) return status;
            if (searchStatus == GridPathfinder.OnDemandSearch.Status.NO_ROUTE
                    && attemptsStarted == 1 && blocked.isEmpty()
                    && pendingSearch.expandedNodes() > 0) {
                exhaustedBaseSearch = pendingSearch;
            }
            float[][] macro = searchStatus == GridPathfinder.OnDemandSearch.Status.ROUTED
                    ? VehicleRoutePlanner.stringPullOnDemand(pendingSearch.path(),
                            workingPassable, width, height)
                    : null;
            pendingSearch = null;
            considerMacro(macro);
        }
        return status;
    }

    /** How the search stands, without spending anything. */
    public Status status() { return status; }

    /** The drivable polyline, or {@code null} until {@link #status} is {@link Status#ROUTED}. */
    public float[][] route() { return route; }

    /** Node expansions spent by the most recent {@link #advance} call. */
    public int expandedNodesThisAdvance() { return expandedNodesThisAdvance; }

    /**
     * True only for a cell proved reachable from this search's start by a
     * fully exhausted first A* over the unmasked base field. Failed-turn
     * exclusion attempts cannot establish this for another endpoint pair.
     */
    public boolean exhaustedBaseRegionContains(int x, int y) {
        return exhaustedBaseSearch != null && x >= 0 && x < width
                && y >= 0 && y < height
                && exhaustedBaseSearch.exhaustedReachable(y * width + x);
    }

    public boolean hasExhaustedBaseRegion() { return exhaustedBaseSearch != null; }

    private void searchOnce() {
        float[][] macro = VehicleRoutePlanner.routeMasked(startX, startY,
                goalX, goalY, grid, costField, mask, width, height);
        considerMacro(macro);
    }

    private void considerMacro(float[][] macro) {
        if (macro == null) {
            status = Status.NO_ROUTE;
            return;
        }
        TurnAwareCorridor.Result refined = TurnAwareCorridor.refine(macro, type, grid);
        if (refined.points() != null) {
            route = refined.points();
            status = Status.ROUTED;
            return;
        }
        int failedX = (int) Math.floor(refined.failedX());
        int failedY = (int) Math.floor(refined.failedY());
        // A bend the chassis cannot drive at an endpoint is not a corridor to
        // route around: masking it off would blank the very cell the search has
        // to start or finish on.
        if ((failedX == startX && failedY == startY)
                || (failedX == goalX && failedY == goalY)) {
            status = Status.NO_ROUTE;
            return;
        }
        if (blocked == null) {
            VehicleRoutePlanner.blockDisc(mask, width, height, failedX, failedY,
                    VehicleRoutePlanner.FAILED_TURN_AVOID_RADIUS);
            VehicleRoutePlanner.restoreEndpoint(mask, basePassable, width, height, startX, startY);
            VehicleRoutePlanner.restoreEndpoint(mask, basePassable, width, height, goalX, goalY);
        } else {
            blockDisc(failedX, failedY, VehicleRoutePlanner.FAILED_TURN_AVOID_RADIUS);
            blocked.remove(startY * width + startX);
            blocked.remove(goalY * width + goalX);
        }
    }

    private void blockDisc(int centerX, int centerY, float radius) {
        int r = (int) Math.ceil(radius);
        float radiusSq = radius * radius;
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dy * dy > radiusSq) continue;
                int x = centerX + dx, y = centerY + dy;
                if (x >= 0 && x < width && y >= 0 && y < height) {
                    blocked.add(y * width + x);
                }
            }
        }
    }
}
