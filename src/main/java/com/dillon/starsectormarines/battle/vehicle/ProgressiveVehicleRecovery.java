package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile.Bucket;
import com.dillon.starsectormarines.battle.vehicle.components.VehicleControlComponent;

import java.util.HashSet;
import java.util.Set;

/** One vehicle's resumable rescue enumeration; pending is never a failed-route answer. */
public final class ProgressiveVehicleRecovery {
    private final VehicleMission mission;
    private final VehicleLeg leg;
    private final NavigationGrid liveGrid;
    private final long liveRevision;
    private final ProgressiveVehicleField fields;
    private final VehicleType type;
    private final FailedVehicleRecovery key;
    private final int bodyCellX, bodyCellY;
    private final int startX, startY, goalX, goalY;
    private final float facing, radius;
    private final int ranking;
    private final boolean cardinalOnly;
    private final int triedMask;
    private final GridPathfinder.IndexedPassability available;
    private int directionIndex;
    private DrivableRouteSearch search;
    private RouteSearchBudget budget;
    private VehicleRoutePlanner.RescueRoute best;
    private float bestDot = -Float.MAX_VALUE;
    private boolean complete;

    ProgressiveVehicleRecovery(VehicleControlComponent state, VehicleMission mission,
                               GroundBody body, VehicleType type, NavigationGrid liveGrid,
                               ProgressiveVehicleField fields, int startX, int startY,
                               int goalX, int goalY, float radius) {
        this.mission = mission;
        this.leg = state.leg;
        this.liveGrid = liveGrid;
        this.liveRevision = liveGrid.topologyRevision();
        this.fields = fields;
        this.type = type;
        this.bodyCellX = (int) Math.floor(body.x);
        this.bodyCellY = (int) Math.floor(body.y);
        this.startX = startX;
        this.startY = startY;
        this.goalX = goalX;
        this.goalY = goalY;
        this.facing = body.facingDegrees;
        this.radius = radius;
        this.ranking = directionRanking(facing);
        this.cardinalOnly = GridPathfinder.USE_CARDINAL_NAVIGATION;
        this.triedMask = state.rescueFirstStepTriedMask;
        this.key = new FailedVehicleRecovery(state, fields, type, startX, startY,
                goalX, goalY, facing, radius);
        Set<Integer> avoided = new HashSet<>();
        int width = fields.width();
        int height = fields.height();
        for (int i = 0; i < state.rerouteAvoidCount; i++) {
            VehicleRoutePlanner.addDisc(avoided, width, height,
                    state.rerouteAvoidX[i], state.rerouteAvoidY[i], radius);
        }
        avoided.remove(startY * width + startX);
        available = index -> !avoided.contains(index) && fields.isPassable(index);
    }

    /** Sub-cell travel and heading jitter are harmless while cell and bearing preference stay unchanged. */
    boolean matches(VehicleControlComponent state, VehicleMission currentMission,
                    GroundBody body, VehicleType currentType, NavigationGrid currentGrid) {
        return mission == currentMission && leg == state.leg && liveGrid == currentGrid
                && liveRevision == currentGrid.topologyRevision() && fields == currentMission.routeFields
                && bodyCellX == (int) Math.floor(body.x) && bodyCellY == (int) Math.floor(body.y)
                && Float.isFinite(body.facingDegrees) && ranking == directionRanking(body.facingDegrees)
                && cardinalOnly == GridPathfinder.USE_CARDINAL_NAVIGATION
                && key.matches(state, fields, currentType, startX, startY, goalX, goalY, facing, radius);
    }

    /**
     * At most one new A* attempt and maxExpanded nodes. A finished attempt may
     * string-pull and refine its complete path; those stages are not expansion
     * units. Direction order and eight attempts per direction match the control.
     */
    void advance(int maxExpanded) {
        if (complete || maxExpanded <= 0) return;
        int clearanceBefore = fields.clearanceEvaluations();
        int costBefore = fields.costEvaluations();
        try {
            while (directionIndex < Direction.ALL.length) {
                Direction direction = Direction.ALL[directionIndex];
                if (search == null) {
                    if ((triedMask & (1 << direction.bit())) != 0
                            || cardinalOnly && direction.isDiagonal()
                            || !canStart(direction)) {
                        directionIndex++;
                        continue;
                    }
                    int viaX = startX + direction.dx;
                    int viaY = startY + direction.dy;
                    if (viaX == goalX && viaY == goalY) {
                        consider(direction, new float[][]{{startX + 0.5f, viaX + 0.5f},
                                {startY + 0.5f, viaY + 0.5f}});
                        directionIndex++;
                        continue;
                    }
                    search = DrivableRouteSearch.overOnDemand(viaX, viaY, goalX, goalY,
                            fields.grid(), fields, available, type);
                    budget = new RouteSearchBudget(VehicleRoutePlanner.MAX_KINEMATIC_ROUTE_ATTEMPTS);
                }
                int attemptsBefore = budget.spent();
                search.advanceRecovery(budget, maxExpanded);
                VehicleWorkProfile.count(Bucket.VEHICLE_RECOVERY_ATTEMPT, budget.spent() - attemptsBefore);
                VehicleWorkProfile.count(Bucket.VEHICLE_RECOVERY_EXPANDED, search.expandedNodesThisAdvance());
                if (search.status() == DrivableRouteSearch.Status.ROUTED) {
                    float[][] tail = search.route();
                    float[] xs = new float[tail[0].length + 1];
                    float[] ys = new float[tail[1].length + 1];
                    xs[0] = startX + 0.5f;
                    ys[0] = startY + 0.5f;
                    System.arraycopy(tail[0], 0, xs, 1, tail[0].length);
                    System.arraycopy(tail[1], 0, ys, 1, tail[1].length);
                    consider(direction, new float[][]{xs, ys});
                }
                if (search.status() != DrivableRouteSearch.Status.PENDING
                        || !search.hasActiveAttempt() && budget.isExhausted()) {
                    search = null;
                    directionIndex++;
                }
                // Even a zero-expansion refusal may have allocated whole-map
                // frontier storage. Never start another attempt in this call.
                complete = directionIndex == Direction.ALL.length;
                return;
            }
            complete = true;
        } finally {
            VehicleWorkProfile.count(Bucket.VEHICLE_RECOVERY_CLEARANCE_CELL,
                    fields.clearanceEvaluations() - clearanceBefore);
            VehicleWorkProfile.count(Bucket.VEHICLE_RECOVERY_COST_CELL,
                    fields.costEvaluations() - costBefore);
        }
    }

    private boolean canStart(Direction direction) {
        int x = startX + direction.dx;
        int y = startY + direction.dy;
        NavigationGrid grid = fields.grid();
        // The raw-grid method delegates to A*'s reciprocal-edge/corner rule.
        // Indexed A* additionally requires only the two endpoints' clearance.
        return grid.canTraverseCellStep(startX, startY, direction)
                && available.isPassable(startY * fields.width() + startX)
                && available.isPassable(y * fields.width() + x);
    }

    private void consider(Direction direction, float[][] route) {
        float dot = directionDot(direction, facing);
        if (dot > bestDot) {
            bestDot = dot;
            best = new VehicleRoutePlanner.RescueRoute(route, direction.bit());
        }
    }

    boolean complete() { return complete; }
    VehicleRoutePlanner.RescueRoute route() { return complete ? best : null; }
    FailedVehicleRecovery failedKey() { return FailedVehicleRecovery.cacheable(facing, radius) ? key : null; }
    int goalIndex(float[] xs, float[] ys) {
        return VehicleController.lastOnGridIndex(xs, ys, fields.grid());
    }

    private static float directionDot(Direction direction, float facing) {
        double radians = Math.toRadians(facing);
        float x = -(float) Math.sin(radians);
        float y = (float) Math.cos(radians);
        return (x * direction.dx + y * direction.dy)
                / (direction.isDiagonal() ? (float) Math.sqrt(2f) : 1f);
    }

    private static int directionRanking(float facing) {
        int key = 0;
        for (Direction direction : Direction.ALL) {
            float dot = directionDot(direction, facing);
            int rank = 0;
            for (Direction other : Direction.ALL) {
                float otherDot = directionDot(other, facing);
                if (otherDot > dot || otherDot == dot && other.ordinal() < direction.ordinal()) rank++;
            }
            key |= rank << (direction.ordinal() * 3);
        }
        return key;
    }
}
