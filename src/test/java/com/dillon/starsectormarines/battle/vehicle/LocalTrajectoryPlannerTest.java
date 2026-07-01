package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice-1 acceptance for {@link LocalTrajectoryPlanner}. Exercises the bounded
 * rolling-horizon search against hand-built {@link NavigationGrid} fixtures:
 *
 * <ul>
 *   <li>straight clear corridor → smooth feasible trajectory;</li>
 *   <li>90° corner with adequate width → feasible rounded trajectory (the case
 *       that produces the instant-90°-snap under the old playback);</li>
 *   <li>too-tight / blocked window → {@code null} (a clean "no trajectory" for
 *       slice 3 to escalate), never an infeasible-but-returned path;</li>
 *   <li>every returned trajectory is feasible pose-by-pose and never jumps
 *       heading faster than the bicycle model allows.</li>
 * </ul>
 *
 * The planner is pure (pose + corridor + grid → trajectory), so no
 * {@link VehicleMission} / {@link GroundSystem} is constructed here.
 */
public class LocalTrajectoryPlannerTest {

    private static final VehicleType TYPE = VehicleType.HEAVY_APC;
    /**
     * Max plausible heading change between adjacent dense poses. The bicycle
     * step over {@code STEP_CELLS} (2) cells caps at ~29°; the Reeds-Shepp tail
     * adds ~15°/cell. 40° leaves headroom while still being a fraction of the
     * ~90° instant pivot the old grid-aligned playback produced — this is the
     * assertion that the snap is gone.
     */
    private static final float MAX_HEADING_STEP_DEG = 40f;

    // ----- Fixtures --------------------------------------------------------

    /** Rectangular block of walkable floor [x0..x1] × [y0..y1] inclusive. */
    private static void carve(NavigationGrid grid, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
    }

    private static ReferenceCorridor corridor(float... xy) {
        int n = xy.length / 2;
        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = xy[2 * i];
            ys[i] = xy[2 * i + 1];
        }
        return new ReferenceCorridor(xs, ys, 1);
    }

    // ----- Assertions ------------------------------------------------------

    private static void assertAllPosesFeasible(Trajectory t, NavigationGrid grid) {
        float[] xs = t.xs(), ys = t.ys(), hs = t.headings();
        for (int i = 0; i < xs.length; i++) {
            assertTrue(
                    VehicleFootprint.isPoseFeasible(xs[i], ys[i], hs[i],
                            TYPE.visualLengthCells, TYPE.visualWidthCells, grid),
                    "pose " + i + " (" + xs[i] + ", " + ys[i] + " @ " + hs[i] + "°) is infeasible");
        }
    }

    private static void assertHeadingsSmooth(Trajectory t) {
        float[] hs = t.headings();
        for (int i = 1; i < hs.length; i++) {
            float d = Math.abs(((hs[i] - hs[i - 1] + 540f) % 360f) - 180f);
            assertTrue(d <= MAX_HEADING_STEP_DEG,
                    "heading jump " + d + "° between pose " + (i - 1) + " and " + i
                            + " exceeds bicycle limit (" + MAX_HEADING_STEP_DEG + "°)");
        }
    }

    // ----- Tests -----------------------------------------------------------

    @Test
    public void straightClearCorridorIsSmoothAndFeasible() {
        NavigationGrid grid = new NavigationGrid(40, 40);
        carve(grid, 14, 0, 26, 39);   // wide vertical lane

        Pose start = new Pose(20.5f, 5f, 0f);   // facing +Y (north)
        ReferenceCorridor corr = corridor(20.5f, 5f, 20.5f, 20f, 20.5f, 35f);

        Trajectory t = LocalTrajectoryPlanner.plan(start, corr, TYPE, grid);

        assertNotNull(t, "straight clear corridor should yield a trajectory");
        assertTrue(t.lengthCells() > 3f, "trajectory should make real forward progress");
        assertAllPosesFeasible(t, grid);
        assertHeadingsSmooth(t);
        // Stays near the lane centerline (no wild lateral excursion).
        for (float x : t.xs()) {
            assertTrue(Math.abs(x - 20.5f) < 4f, "drifted off the straight lane: x=" + x);
        }
    }

    @Test
    public void offsetStartHeadingStillTracksSmoothly() {
        // Every other fixture starts aligned with the corridor, so the
        // start→first-lattice transition (poses[0]→poses[1]) is never stressed.
        // Start 30° off a due-north lane: the first feasible successor can only
        // bend ~29°, so the planner must ease the heading in rather than snap it.
        NavigationGrid grid = new NavigationGrid(40, 40);
        carve(grid, 14, 0, 26, 39);

        Pose start = new Pose(20.5f, 5f, 30f);
        ReferenceCorridor corr = corridor(20.5f, 5f, 20.5f, 20f, 20.5f, 35f);

        Trajectory t = LocalTrajectoryPlanner.plan(start, corr, TYPE, grid);

        assertNotNull(t, "a slightly mis-aligned start in a clear lane should still plan");
        assertAllPosesFeasible(t, grid);
        assertHeadingsSmooth(t);   // includes the start→first-lattice transition
    }

    @Test
    public void ninetyDegreeCornerWithWidthRoundsFeasibly() {
        NavigationGrid grid = new NavigationGrid(50, 50);
        carve(grid, 14, 0, 26, 26);    // vertical leg
        carve(grid, 14, 14, 45, 26);   // horizontal leg (wide overlap at the bend)

        // Start right at the approach so the rolling horizon goal lands well
        // down the east leg — forcing a single local plan to round the bend.
        Pose start = new Pose(20.5f, 17f, 0f);
        ReferenceCorridor corr = corridor(20.5f, 17f, 20.5f, 20f, 40f, 20f);

        Trajectory t = LocalTrajectoryPlanner.plan(start, corr, TYPE, grid);

        assertNotNull(t, "a wide 90° corner should be drivable");
        assertAllPosesFeasible(t, grid);
        assertHeadingsSmooth(t);   // the headline fix: no instant 90° pivot
        // It should actually turn toward the east leg — total heading change
        // from due-north toward east-ish is well past a few degrees.
        float[] hs = t.headings();
        float netTurn = Math.abs(((hs[hs.length - 1] - hs[0] + 540f) % 360f) - 180f);
        assertTrue(netTurn > 30f, "expected a real corner turn, got net " + netTurn + "°");
        // ...and it must turn the *right* way. The east leg runs +X, which is
        // 270° in this facing convention (0°=+Y, +CCW). Guard against a >30°
        // turn toward west (90°) passing the magnitude check.
        float finalH = hs[hs.length - 1];
        float towardEast = Math.abs(((finalH - 270f + 540f) % 360f) - 180f);
        float towardWest = Math.abs(((finalH - 90f + 540f) % 360f) - 180f);
        assertTrue(towardEast < towardWest,
                "corner turn should head east (270°), got final heading " + finalH + "°");
    }

    @Test
    public void blockedCorridorReturnsNoTrajectory() {
        NavigationGrid grid = new NavigationGrid(30, 30);
        carve(grid, 10, 5, 20, 14);   // walkable pocket; everything y>=15 is wall

        Pose start = new Pose(15.5f, 7f, 0f);
        // Corridor aims north straight into the wall band.
        ReferenceCorridor corr = corridor(15.5f, 7f, 15.5f, 25f);

        Trajectory t = LocalTrajectoryPlanner.plan(start, corr, TYPE, grid);

        assertNull(t, "goal walled off → no forward trajectory");
    }

    @Test
    public void tightCorridorNeverReturnsInfeasiblePath() {
        // A corridor only 3 cells wide — narrower than the truck can turn in.
        // The planner must either find a feasible (possibly reversing) path or
        // return null, but never hand back an infeasible one.
        NavigationGrid grid = new NavigationGrid(30, 30);
        carve(grid, 10, 0, 12, 20);    // 3-wide vertical
        carve(grid, 10, 10, 25, 12);   // 3-wide horizontal — tight elbow

        // Start near the bend so the rolling goal lands DEEP in the horizontal
        // leg (≈(18,11)), out of soft-goal-radius reach (2.97) from the vertical
        // lane. That defeats the trivial pass the critique flagged: a straight
        // northward stub can no longer satisfy the goal by leaking around the
        // corner — the planner must actually round the elbow, which a 3-wide
        // corridor can't fit (min turn radius ≈3.96), so the honest answer here
        // is null. If it ever does return a plan, it must have engaged the elbow.
        Pose start = new Pose(11.5f, 8f, 0f);
        ReferenceCorridor corr = corridor(11.5f, 3f, 11.5f, 11f, 24f, 11f);

        Trajectory t = LocalTrajectoryPlanner.plan(start, corr, TYPE, grid);

        // Whatever it returns, it must be feasible and smooth — that's the
        // invariant. (It may legitimately be null if no feasible plan exists.)
        if (t != null) {
            assertAllPosesFeasible(t, grid);
            assertHeadingsSmooth(t);
            float maxX = 0f;
            for (float x : t.xs()) maxX = Math.max(maxX, x);
            assertTrue(maxX > 12.5f,
                    "a non-null tight-corridor plan must engage the elbow, not stop short (maxX=" + maxX + ")");
        }
    }
}
