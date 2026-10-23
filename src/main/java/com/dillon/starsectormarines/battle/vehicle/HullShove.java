package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * Moves a hull sideways to its own drive, as a push from another body does ({@link HullContact},
 * {@link HullRam}), through the footprint sweep the drive itself uses, so a hull pushed along a wall slides
 * along it instead of being held off it by a circle the size of its length. The whole move is tried first,
 * then each axis alone. Facing is not changed.
 *
 * <p>With {@code margin} the sweep carries the route planner's clearance
 * ({@link HybridAStarPlanner#PLANNER_CLEARANCE}): a hull shoved flush against a wall is a pose its own planner
 * refuses to start from, so the shove stops where the planner's footprint would, and a hull already inside that
 * margin is not shoved by it at all. Without it the bare footprint is swept, for an overlap nothing else can
 * resolve.
 */
public final class HullShove {

    private HullShove() {}

    /**
     * Shoves {@code body} by up to {@code (dx, dy)}. Writes into {@code unmet} the part of the shove it could not
     * take, so whoever pushed can take it back.
     */
    public static void move(GroundBody body, VehicleType type, NavigationGrid grid, float dx, float dy,
                            boolean margin, float[] unmet) {
        float x0 = body.x, y0 = body.y;
        float pad = margin ? HybridAStarPlanner.PLANNER_CLEARANCE : 0f;
        float length = type.visualLengthCells + pad, width = type.visualWidthCells + pad;
        if (margin && !VehicleFootprint.isPoseFeasible(x0, y0, body.facingDegrees, length, width, grid)) {
            unmet[0] = dx;
            unmet[1] = dy;
            return;
        }
        if (!step(body, length, width, grid, dx, dy)) {
            step(body, length, width, grid, dx - (body.x - x0), 0f);
            step(body, length, width, grid, 0f, dy - (body.y - y0));
        }
        unmet[0] = dx - (body.x - x0);
        unmet[1] = dy - (body.y - y0);
    }

    /** One sweep; true when it went the whole way. */
    private static boolean step(GroundBody body, float length, float width, NavigationGrid grid, float dx, float dy) {
        if (Math.abs(dx) < 1e-6f && Math.abs(dy) < 1e-6f) return true;
        Pose from = new Pose(body.x, body.y, body.facingDegrees);
        Pose to = new Pose(body.x + dx, body.y + dy, body.facingDegrees);
        VehicleTerrainMotion.Result result = VehicleTerrainMotion.sweep(from, to, length, width, grid, false);
        body.x = result.pose().x;
        body.y = result.pose().y;
        return !result.blocked();
    }
}
