package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

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
 * {@code navigation-rework/overview.md}.
 *
 * <p>Pure and stateless: (pose, corridor, type, grid) → trajectory or
 * {@code null}. No {@link VehicleMission} / {@link GroundSystem} coupling, which is
 * what makes it unit-testable in isolation and reusable for future tanks /
 * player vehicles. A {@code null} return means "no forward trajectory" — the
 * recovery ladder (slice 3) escalates from there.
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

    /**
     * Plan a feasible trajectory from {@code start} toward a goal a horizon
     * down {@code corridor}. Returns {@code null} if the bounded search finds
     * no forward trajectory in the window.
     */
    public static Trajectory plan(Pose start, ReferenceCorridor corridor,
                                  VehicleType type, NavigationGrid grid) {
        GroundBody body = type.createBody();
        if (!(body instanceof BicycleBody)) return null;
        float turnRadius = ((BicycleBody) body).minTurnRadiusCells();

        float horizon = Math.max(MIN_HORIZON_CELLS, HORIZON_TURN_RADIUS_FACTOR * turnRadius);
        Pose goal = corridor.targetAhead(start.x, start.y, horizon);
        float goalRadius = Math.max(MIN_GOAL_RADIUS_CELLS, GOAL_RADIUS_TURN_RADIUS_FACTOR * turnRadius);

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
}
