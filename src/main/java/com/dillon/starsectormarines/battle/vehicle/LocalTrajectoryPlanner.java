package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile.Bucket;

/**
 * Rolling-horizon local planner — the heart of the navigation rework. Turns a
 * vehicle's current pose + an advisory {@link ReferenceCorridor} into a short,
 * kinematically-feasible {@link Trajectory} the controller can track (slice 2),
 * by aiming a bounded Hybrid A* search ({@link HybridAStarPlanner#planLocal})
 * at a soft goal a horizon down the corridor.
 *
 * <p>Why bounded + rolling rather than one-shot full-path: the search window is
 * sized to the start↔goal span plus a turn-radius margin, so the search stays
 * small and almost always succeeds — and because it replans every few ticks
 * against the <em>current</em> grid, it naturally handles dynamic obstacles
 * (wrecks, other trucks, marines) that a one-time refined path can't. See
 * {@code convoy-nouns.md}.
 *
 * <p>Pure and stateless: (pose, corridor, type, grid) → trajectory or
 * {@code null}. No {@link VehicleMission} / {@link GroundSystem} coupling, which is
 * what makes it unit-testable in isolation and reusable for future tanks /
 * player vehicles. A {@code null} return means "no forward trajectory"; the
 * controller distinguishes an aligned terminal-region pose from an ordinary
 * route failure before escalating through the recovery ladder.
 */
public final class LocalTrajectoryPlanner {

    /**
     * Rolling horizon as a multiple of the vehicle's min turn radius. Long
     * enough to plan <em>through</em> a corner (so the corner is solved, not
     * reacted to), short enough to stay cheap. Tuned in slice 4.
     */
    static final float HORIZON_TURN_RADIUS_FACTOR = 2.5f;
    /** Floor on the horizon (cells) so a very tight-turning vehicle still looks a few cells ahead. */
    static final float MIN_HORIZON_CELLS = 6f;
    /**
     * Soft goal acceptance radius as a multiple of turn radius. The corridor is
     * advisory, so arriving anywhere within this radius of the rolling goal —
     * with a feasible heading — counts as progress.
     */
    static final float GOAL_RADIUS_TURN_RADIUS_FACTOR = 0.75f;
    /** Floor on the goal radius (cells), so the soft region never collapses to a point. */
    static final float MIN_GOAL_RADIUS_CELLS = 1.5f;
    /**
     * Extra slack (cells) added to the search window beyond {@code turnRadius +
     * footprint}. The window must contain a full turn bulge between start and
     * goal plus the vehicle's footprint; this covers rounding and a little air.
     */
    static final float WINDOW_SLACK_CELLS = 2f;
    /**
     * Iteration cap for the bounded local search — far below
     * {@code HybridAStarPlanner.MAX_ITERATIONS} because the window keeps the
     * reachable state count small. A blocked window exhausts well under this.
     */
    static final int LOCAL_MAX_ITERATIONS = 4000;

    private LocalTrajectoryPlanner() {}

    /** Rolling horizon (cells) for a vehicle of this minimum turn radius. */
    private static float horizon(float turnRadiusCells) {
        return Math.max(MIN_HORIZON_CELLS, HORIZON_TURN_RADIUS_FACTOR * turnRadiusCells);
    }

    /**
     * Plan a feasible trajectory from {@code start} toward a goal a horizon
     * down {@code corridor}. Returns {@code null} if the bounded search finds
     * no forward trajectory in the window.
     */
    public static Trajectory plan(Pose start, ReferenceCorridor corridor,
                                  VehicleType type, NavigationGrid grid) {
        long started = VehicleWorkProfile.start();
        try {
            Trajectory result = planInner(start, corridor, type, grid);
            if (result == null) VehicleWorkProfile.count(Bucket.VEHICLE_LOCAL_NO_TRAJECTORY, 1);
            return result;
        } finally {
            VehicleWorkProfile.finish(Bucket.VEHICLE_LOCAL_PLAN, started);
        }
    }

    private static Trajectory planInner(Pose start, ReferenceCorridor corridor,
                                        VehicleType type, NavigationGrid grid) {
        GroundBody body = type.createBody();
        if (!(body instanceof BicycleBody)) return null;
        float turnRadius = ((BicycleBody) body).minTurnRadiusCells();

        Pose goal = corridor.targetAhead(start.x, start.y, horizon(turnRadius));
        float goalRadius = Math.max(MIN_GOAL_RADIUS_CELLS, GOAL_RADIUS_TURN_RADIUS_FACTOR * turnRadius);

        boolean routeEnd = samePoint(goal.x, goal.y, corridor.endX(), corridor.endY());
        Trajectory terminal = routeEnd
                ? directTerminalTrajectory(start, goal, goalRadius, type, grid)
                : null;
        if (terminal != null) return terminal;

        float margin = turnRadius
                + 0.5f * Math.max(type.visualLengthCells, type.visualWidthCells)
                + WINDOW_SLACK_CELLS;
        int minX = (int) Math.floor(Math.min(start.x, goal.x) - margin);
        int minY = (int) Math.floor(Math.min(start.y, goal.y) - margin);
        int maxX = (int) Math.ceil(Math.max(start.x, goal.x) + margin);
        int maxY = (int) Math.ceil(Math.max(start.y, goal.y) + margin);

        float[][] refined = HybridAStarPlanner.planLocal(
                start, goal, goalRadius, minX, minY, maxX, maxY,
                LOCAL_MAX_ITERATIONS, type, grid);
        if (refined == null) return null;
        return new Trajectory(refined[0], refined[1], refined[2]);
    }

    /**
     * True when the rolling goal this planner would aim at lies outside the
     * grid — the route ahead has left the map and there is nothing on-grid left
     * to solve. A convoy's exit waypoint sits a deliberate pad beyond the
     * perimeter, so every departing vehicle enters this state a full horizon
     * (~10 cells for a HEAVY_APC) before its own footprint leaves the grid.
     * Without it that whole stretch reads as an ordinary route failure and the
     * truck brakes to a permanent halt inside the map: the local search can
     * never reach an off-grid goal, and no re-route can move a goal that is off
     * the map by design.
     *
     * <p>Distinct from {@link VehicleFootprint#isPoseWithinGrid} on the body:
     * that says the crossing has physically begun, this says the planning
     * horizon has. The controller needs the earlier of the two to know it is on
     * the off-map tail rather than stuck.
     */
    static boolean isPlanningIntoOffMapTail(Pose pose, ReferenceCorridor corridor,
                                            VehicleType type, NavigationGrid grid) {
        GroundBody body = type.createBody();
        if (!(body instanceof BicycleBody)) return false;
        Pose goal = corridor.targetAhead(pose.x, pose.y,
                horizon(((BicycleBody) body).minTurnRadiusCells()));
        return !grid.inBounds((int) Math.floor(goal.x), (int) Math.floor(goal.y));
    }

    /**
     * True when {@code pose} occupies the same soft goal region Hybrid A* uses
     * after the rolling target has pinned to the route endpoint. A controller
     * calls this only after no executable forward trajectory remains: at that
     * point the null result means terminal-region success, not route failure.
     */
    static boolean isInTerminalGoalRegion(Pose pose, ReferenceCorridor corridor,
                                          VehicleType type) {
        GroundBody body = type.createBody();
        if (!(body instanceof BicycleBody)) return false;
        float turnRadius = ((BicycleBody) body).minTurnRadiusCells();
        Pose goal = corridor.targetAhead(pose.x, pose.y, horizon(turnRadius));
        if (!samePoint(goal.x, goal.y, corridor.endX(), corridor.endY())) return false;
        float goalRadius = Math.max(MIN_GOAL_RADIUS_CELLS,
                GOAL_RADIUS_TURN_RADIUS_FACTOR * turnRadius);
        return HybridAStarPlanner.isInLocalGoalRegion(
                pose.x, pose.y, pose.facingDeg, goal, goalRadius * goalRadius);
    }

    /**
     * Inside the soft goal radius the lattice would accept the start node and
     * extract a one-pose (therefore null) path. Preserve the distinction between
     * arrival and planning failure by returning the exact short straight finish
     * when the body can merge toward the route endpoint and the swept footprint
     * is clear. This fallback applies only after the rolling goal has pinned to
     * the route endpoint: intermediate rolling goals still require tangent
     * agreement in the lattice so proximity before a bend is not false success.
     */
    private static Trajectory directTerminalTrajectory(Pose start, Pose goal, float goalRadius,
                                                       VehicleType type, NavigationGrid grid) {
        float dx = goal.x - start.x, dy = goal.y - start.y;
        float distance = (float) Math.hypot(dx, dy);
        if (distance < 1e-4f || distance > goalRadius) return null;
        float bearing = AirBody.facingToward(dx, dy);
        if (headingError(start.facingDeg, bearing) > 20f) return null;

        float length = type.visualLengthCells + HybridAStarPlanner.PLANNER_CLEARANCE;
        float width = type.visualWidthCells + HybridAStarPlanner.PLANNER_CLEARANCE;
        int samples = Math.max(1, (int) Math.ceil(distance / 0.25f));
        for (int i = 0; i <= samples; i++) {
            float t = i / (float) samples;
            if (!VehicleFootprint.isPoseFeasible(start.x + dx * t, start.y + dy * t,
                    bearing, length, width, grid)) return null;
        }
        return new Trajectory(new float[]{start.x, goal.x}, new float[]{start.y, goal.y},
                new float[]{start.facingDeg, goal.facingDeg});
    }

    private static float headingError(float a, float b) {
        return Math.abs(((a - b + 540f) % 360f) - 180f);
    }

    private static boolean samePoint(float ax, float ay, float bx, float by) {
        float dx = ax - bx, dy = ay - by;
        return dx * dx + dy * dy < 1e-6f;
    }
}
