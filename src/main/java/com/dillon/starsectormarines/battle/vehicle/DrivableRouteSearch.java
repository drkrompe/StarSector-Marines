package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

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
 * planner with a smaller budget. It is done by holding this: the endpoints, the
 * accumulating avoidance mask, and how far the loop has got. {@link #advance}
 * spends at most a stated number of searches and reports {@code PENDING} when it
 * has neither found a route nor refused one, so the caller can put it down and
 * pick it up on the next tick with nothing lost.
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
    private final int width;
    private final int height;
    private final VehicleType type;

    private Status status = Status.PENDING;
    private float[][] route;

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
        this.width = width;
        this.height = height;
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
        int spent = 0;
        while (status == Status.PENDING && spent < maxSearches) {
            if (!budget.claim()) return status;
            spent++;
            searchOnce();
        }
        return status;
    }

    /** How the search stands, without spending anything. */
    public Status status() { return status; }

    /** The drivable polyline, or {@code null} until {@link #status} is {@link Status#ROUTED}. */
    public float[][] route() { return route; }

    private void searchOnce() {
        float[][] macro = VehicleRoutePlanner.routeMasked(startX, startY, goalX, goalY,
                grid, costField, mask, width, height);
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
        VehicleRoutePlanner.blockDisc(mask, width, height, failedX, failedY,
                VehicleRoutePlanner.FAILED_TURN_AVOID_RADIUS);
        VehicleRoutePlanner.restoreEndpoint(mask, basePassable, width, height, startX, startY);
        VehicleRoutePlanner.restoreEndpoint(mask, basePassable, width, height, goalX, goalY);
    }
}
