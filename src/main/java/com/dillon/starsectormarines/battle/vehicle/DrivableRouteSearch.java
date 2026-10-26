package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile.Bucket;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
 * <p>A disc on a failed bend can shut the only way through: the bend at the mouth of a narrow gap, met along the
 * wall it opens in, is one a wheeled hull cannot drive, and each retry's disc closes a little more of the mouth until
 * no route is left. So an order's search ({@link #departingFrom}) that would end there, with discs placed, tries
 * once more {@link #reapproach coming at its failed bends from further back}: the discs move back along the legs that
 * ran into those bends, leaving the bends themselves open. A search that routes without it never takes this path, and
 * dispatch, which ranks many candidate drops and moves on from a refused one, keeps its refusals.
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
    /** Where the hull an order is for stands, so the route's ends are judged as an order's; {@code null} for dispatch. */
    private Pose departure;

    private Status status = Status.PENDING;
    private float[][] route;
    private GridPathfinder.OnDemandSearch pendingSearch;
    private GridPathfinder.OnDemandSearch exhaustedBaseSearch;
    private int attemptsStarted;
    private int expandedNodesThisAdvance;
    private boolean profileRecoverySetup;
    /** Each failed bend so far, as {x, y, incoming leg's unit x, unit y}: where a reapproach puts its disc. */
    private final List<float[]> failedBends = new ArrayList<>();
    /** Whether the discs have been moved back along their incoming legs ({@link #reapproach}); once only. */
    private boolean reapproached;

    /** Recovery admits at most one fresh attempt per tick, including zero-expansion refusals. */
    Status advanceRecovery(RouteSearchBudget budget, int maxExpandedNodes) {
        profileRecoverySetup = true;
        try {
            return advance(budget, 1, maxExpandedNodes);
        } finally {
            profileRecoverySetup = false;
        }
    }

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
                long started = profileRecoverySetup ? VehicleWorkProfile.start() : 0L;
                try {
                    pendingSearch = GridPathfinder.beginOnDemand(grid,
                            startX, startY, goalX, goalY,
                            indexedCost, workingPassable);
                } finally {
                    if (profileRecoverySetup) VehicleWorkProfile.finish(Bucket.VEHICLE_RECOVERY_SETUP, started);
                }
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

    /**
     * Judges the route's ends as those of an order for a hull standing at {@code pose}
     * ({@link TurnAwareCorridor#refine(float[][], VehicleType, NavigationGrid, Pose)}). Set before the first advance.
     */
    DrivableRouteSearch departingFrom(Pose pose) {
        this.departure = pose;
        return this;
    }

    /** How the search stands, without spending anything. */
    public Status status() { return status; }

    /**
     * An already-paid A* frontier can resume even when the lifetime budget is
     * exhausted. A PENDING wrapper alone is insufficient: turn rejection can
     * leave it waiting to claim a new attempt with no frontier to advance.
     */
    public boolean hasActiveAttempt() { return pendingSearch != null; }

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
            if (departure != null && !reapproached && !failedBends.isEmpty()) {
                reapproach();
                return;
            }
            status = Status.NO_ROUTE;
            return;
        }
        TurnAwareCorridor.Result refined = TurnAwareCorridor.refine(macro, type, grid, departure);
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
        float[] bend = failedBend(macro, refined.failedX(), refined.failedY());
        failedBends.add(bend);
        if (reapproached) {
            avoid(behind(bend, 0), behind(bend, 1));
        } else {
            avoid(failedX, failedY);
        }
    }

    /** Puts the failed-turn disc on cell ({@code x}, {@code y}), keeping both endpoints open. */
    private void avoid(int x, int y) {
        if (blocked == null) {
            VehicleRoutePlanner.blockDisc(mask, width, height, x, y, VehicleRoutePlanner.FAILED_TURN_AVOID_RADIUS);
            VehicleRoutePlanner.restoreEndpoint(mask, basePassable, width, height, startX, startY);
            VehicleRoutePlanner.restoreEndpoint(mask, basePassable, width, height, goalX, goalY);
        } else {
            blockDisc(x, y, VehicleRoutePlanner.FAILED_TURN_AVOID_RADIUS);
            blocked.remove(startY * width + startX);
            blocked.remove(goalY * width + goalX);
        }
    }

    /**
     * The search's discs have cut its start off from its goal: lifts them all, and puts each back
     * {@link #REAPPROACH_BACK} cells behind its failed bend along the leg that ran into it, so the next route keeps
     * the bend and comes at it some other way.
     */
    private void reapproach() {
        reapproached = true;
        if (blocked == null) System.arraycopy(basePassable, 0, mask, 0, mask.length);
        else blocked.clear();
        for (float[] bend : failedBends) avoid(behind(bend, 0), behind(bend, 1));
    }

    /** Cells behind a failed bend, along its incoming leg, that a reapproach centres its disc on: clear of the bend. */
    static final float REAPPROACH_BACK = VehicleRoutePlanner.FAILED_TURN_AVOID_RADIUS + 1f;

    /** Axis {@code axis} (0 x, 1 y) of the cell {@link #REAPPROACH_BACK} behind {@code bend}. */
    private static int behind(float[] bend, int axis) {
        return (int) Math.floor(bend[axis] - bend[2 + axis] * REAPPROACH_BACK);
    }

    /**
     * The failed place on {@code macro} with the direction of the leg running into it, {x, y, ux, uy}: the leg
     * nearest the failure, the earlier where it sits on a vertex.
     */
    static float[] failedBend(float[][] macro, float x, float y) {
        int leg = 1;
        float best = Float.MAX_VALUE;
        for (int i = 1; i < macro[0].length; i++) {
            float d = distanceToSegment(x, y, macro[0][i - 1], macro[1][i - 1], macro[0][i], macro[1][i]);
            if (d < best - 1e-4f) {
                best = d;
                leg = i;
            }
        }
        float dx = macro[0][leg] - macro[0][leg - 1], dy = macro[1][leg] - macro[1][leg - 1];
        float length = (float) Math.hypot(dx, dy);
        return length < 1e-6f ? new float[]{x, y, 0f, 0f} : new float[]{x, y, dx / length, dy / length};
    }

    private static float distanceToSegment(float px, float py, float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay;
        float lengthSq = dx * dx + dy * dy;
        float t = lengthSq < 1e-12f ? 0f : Math.max(0f, Math.min(1f, ((px - ax) * dx + (py - ay) * dy) / lengthSq));
        return (float) Math.hypot(px - (ax + dx * t), py - (ay + dy * t));
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
