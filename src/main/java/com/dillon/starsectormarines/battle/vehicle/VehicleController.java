package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vehicle.components.VehicleControlComponent;

/**
 * Per-vehicle motion for one ground vehicle, now split into a thin id handle plus
 * the shared math. The behaviour — corridor tracking, the rolling {@link Trajectory},
 * pure-pursuit + Reeds-Shepp docking, the reverse-recovery ladder — lives in the
 * stateless {@link VehicleControlSystem}, keyed by entity id over the
 * {@code VEHICLE_CONTROL} component ({@link VehicleControlComponent}). This class
 * carries two things:
 * <ul>
 *   <li>a <b>thin per-vehicle shim</b> ({@link #tick(float, boolean)} /
 *       {@link #consumeArrived()}) that {@link GroundSystem} still calls through
 *       {@code mission.controller}; it forwards to {@link VehicleControlSystem} by
 *       the vehicle's id. (Removed when the drive loop flips to the system directly.)</li>
 *   <li>the package-private <b>tuning constants + static geometry</b>
 *       ({@link #curvatureSpeedCap}, {@link #previewTurnDegrees},
 *       {@link #turnIsInfeasibleForward}, {@link #maxReverseDistance}, and the
 *       route/tail helpers) the system references qualified and the unit tests read.</li>
 * </ul>
 */
public final class VehicleController {

    /** Distance threshold (cells) for landing on the LZ — final waypoint. Tight so the snap-to-LZ at LANDED is invisible. */
    static final float LZ_ARRIVAL_DIST = 0.25f;
    /** Distance threshold (cells) at which a DEPARTING vehicle hits its final exit waypoint and is considered gone. */
    static final float EXIT_ARRIVAL_DIST = 1.0f;
    /**
     * Range from LZ (cells) at which an inbound truck attempts to switch from
     * pursuit to Reeds-Shepp docking. Sized to ~2× the truck's min turn radius
     * so the RS path fits in a comfortable window — long enough to be useful,
     * short enough that the path doesn't snake through walls beyond the local
     * LZ neighborhood.
     */
    static final float DOCKING_TRIGGER_CELLS = 6f;
    /** Constant forward speed (cells/sec) along the Reeds-Shepp docking path. Slower than cruise to read as a careful approach. */
    static final float DOCKING_SPEED = 2.0f;
    /** Sample step (cells) along the RS path when validating feasibility against {@link VehicleFootprint}. */
    private static final float DOCKING_FOOTPRINT_SAMPLE_CELLS = 0.5f;
    /** Sim-seconds a vehicle must be wall-blocked before it starts reversing. Brief pause reads as "realizing the turn won't fit." */
    static final float WALL_REVERSE_DELAY = 0.3f;
    /** Reverse speed when backing away from a wall, cells/sec. Slower than forward cruise — cautious backup. */
    static final float WALL_REVERSE_SPEED = 1.4f;
    /** Distance (cells) the vehicle must move from its stuck origin before wallStuckTime resets. Prevents oscillation from clearing the timer. */
    static final float STUCK_ESCAPE_DIST = 1.5f;

    /** Max sim-seconds a local {@link Trajectory} is tracked before a fresh plan is requested. Keeps the rolling goal marching down the corridor. */
    static final float REPLAN_INTERVAL_SEC = 0.25f;
    /** Off-corridor drift (cells) that forces an immediate replan rather than waiting out the interval. */
    static final float REPLAN_DRIFT_CELLS = 2.0f;
    /** Fraction of the current trajectory's length that may be consumed before a replan is forced (so we plan the next horizon before running out). */
    static final float REPLAN_CONSUMED_FRACTION = 0.5f;

    /**
     * Carrot look-ahead floor (cells) — look-ahead shrinks toward this as the
     * vehicle slows so tight, slow corners are tracked closely instead of cut
     * (a long fixed look-ahead chord cuts the inside of a sharp turn, so the body
     * steers tighter than the planned arc and wedges). Grows back to
     * {@link VehicleType#lookAheadCells} at cruise for smooth straights. ~one
     * wheelbase, below which pure pursuit oscillates.
     */
    static final float MIN_LOOKAHEAD_CELLS = 1.2f;
    /**
     * Slack (cells) a carrot must clear <em>inside</em> a min-turn circle before
     * the proactive reverse fires. A carrot tracked perfectly along a min-radius
     * corner rides exactly on that circle (distance == radius), so without a
     * margin float jitter would spam the trigger on legitimate tight corners; the
     * carrot has to be unambiguously unreachable-forward, not merely on the edge.
     */
    private static final float TURN_INFEASIBLE_MARGIN_CELLS = 0.25f;
    /** Path-preview window (cells) for the curvature speed governor — how far ahead the upcoming bend is measured. A couple cells past the cruise stopping distance so the truck is already slowed when it reaches the corner. */
    private static final float CURVE_PREVIEW_CELLS = 4.0f;
    /** Total heading change (deg) over the preview window below which speed isn't cut — ignores gentle bends and straight-line float wander. */
    private static final float CURVE_DEADBAND_DEG = 20f;
    /** Total heading change (deg) at/above which forward speed is cut all the way to {@link #CURVE_MIN_SPEED_FRAC} of cruise — a hard corner. */
    private static final float CURVE_FULL_DEG = 75f;
    /** Floor the curvature governor slows to, as a fraction of {@link VehicleType#maxSpeed} — keeps creeping through the corner, never a mid-route stop. */
    private static final float CURVE_MIN_SPEED_FRAC = 0.35f;

    /**
     * Target backup distance (cells) for a committed reverse recovery — enough
     * to open room for a fresh forward plan to swing the nose around, but capped
     * to whatever is actually clear behind ({@link #maxReverseDistance}).
     */
    static final float REVERSE_RECOVERY_CELLS = 3.5f;
    /** Below this achievable backup (cells) reversing can't gain useful room (walls close behind) — don't start a maneuver that goes nowhere. */
    static final float MIN_USEFUL_REVERSE_CELLS = 0.75f;
    /** Step (cells) for the backward footprint march that measures achievable backup distance — sub-cell so a 1-cell wall behind can't slip between samples. */
    private static final float REVERSE_MARCH_STEP = 0.25f;
    /**
     * Recoveries without net progress toward the LZ before the controller stops
     * retrying and holds position. The formal give-up rung — re-route / abandon /
     * deload-in-place — is slice 3; this just stops the visible thrash when a
     * route is kinematically impossible.
     */
    static final int MAX_RECOVERY_ATTEMPTS = 5;
    /**
     * Corridor remaining-length (cells) the vehicle must shave below its best-so-far
     * to count as real progress and reset {@link VehicleControlComponent#recoveryAttempts}. Above the
     * per-cycle drift of an oscillating box-in (which nets ~0) but below genuine
     * forward travel, so a true grind survives while a thrash caps out.
     */
    static final float RECOVERY_PROGRESS_MARGIN = 1.0f;
    /**
     * Seconds of no net progress toward the goal before escalating to a re-route
     * ("lap around"). Generous, so the fast local reverse recovery (rung 2) gets
     * to resolve a wall bump first and legitimate slow corners don't false-trip.
     */
    static final float STALL_SECONDS = 4.0f;
    /** Rings {@link VehicleRoutePlanner#snapToMask} searches to pull the re-route's current/goal cells onto the clearance mask. */
    static final int REROUTE_SNAP_RADIUS = 8;
    /** Radius (cells) of the impassable disc the re-route drops on the stuck spot, forcing a different corridor. ≥ a road width so it actually blocks the failing mouth. */
    static final float REROUTE_AVOID_RADIUS = 3.0f;

    /** Entity id of the vehicle this handle drives; forwarded to {@link VehicleControlSystem}. */
    private final long id;
    /** The stateless driver every call delegates to (shared across all vehicles). */
    private final VehicleControlSystem system;

    public VehicleController(long id, VehicleControlSystem system) {
        this.id = id;
        this.system = system;
    }

    /**
     * Forwards to {@link VehicleControlSystem#tick(long, float, boolean)} for this
     * vehicle. Thin per-vehicle shim so {@link GroundSystem}'s drive loop can keep
     * calling {@code mission.controller.tick(...)}; removed when the drive loop
     * flips to the system directly.
     */
    public void tick(float dt, boolean isInbound) {
        system.tick(id, dt, isInbound);
    }

    /** Forwards to {@link VehicleControlSystem#consumeArrived(long)} for this vehicle. */
    public boolean consumeArrived() {
        return system.consumeArrived(id);
    }

    /**
     * Forward-speed cap (cells/sec) for the bend in the tracked path within
     * {@link #CURVE_PREVIEW_CELLS} ahead: full {@code maxSpeed} up to
     * {@link #CURVE_DEADBAND_DEG} of total heading change, lerping down to
     * {@link #CURVE_MIN_SPEED_FRAC}·{@code maxSpeed} at {@link #CURVE_FULL_DEG}
     * and beyond. Package-private for {@code VehicleControllerCurvatureTest}.
     */
    static float curvatureSpeedCap(float[] xs, float[] ys, int startIdx,
                                   float bodyX, float bodyY, float maxSpeed) {
        float turnDeg = previewTurnDegrees(xs, ys, startIdx, bodyX, bodyY);
        float t = (turnDeg - CURVE_DEADBAND_DEG) / (CURVE_FULL_DEG - CURVE_DEADBAND_DEG);
        if (t <= 0f) return maxSpeed;
        if (t > 1f) t = 1f;
        return maxSpeed * (1f - t * (1f - CURVE_MIN_SPEED_FRAC));
    }

    /**
     * Total absolute heading change (degrees) along the tracked polyline within
     * {@link #CURVE_PREVIEW_CELLS} of the body — the curvature signal the speed
     * governor reads. Unified across the dense local trajectory (the sum of small
     * per-pose deltas approximates the arc sweep) and the sparse coarse corridor
     * (the vertex angle at the upcoming turn). The first segment runs from the
     * body to {@code xs[startIdx]}, so a body already mid-corner counts that bend.
     * Package-private for {@code VehicleControllerCurvatureTest}.
     */
    static float previewTurnDegrees(float[] xs, float[] ys, int startIdx,
                                    float bodyX, float bodyY) {
        int n = Math.min(xs.length, ys.length);
        if (n == 0) return 0f;
        int idx = Math.max(0, Math.min(startIdx, n - 1));
        float prevX = bodyX, prevY = bodyY;
        float prevBearing = Float.NaN;
        float dist = 0f, totalTurn = 0f;
        for (int i = idx; i < n && dist < CURVE_PREVIEW_CELLS; i++) {
            float dx = xs[i] - prevX, dy = ys[i] - prevY;
            float segLen = (float) Math.hypot(dx, dy);
            if (segLen < 1e-4f) continue;
            float bearing = AirBody.facingToward(dx, dy);
            if (!Float.isNaN(prevBearing)) {
                totalTurn += Math.abs(((bearing - prevBearing + 540f) % 360f) - 180f);
            }
            prevBearing = bearing;
            prevX = xs[i];
            prevY = ys[i];
            dist += segLen;
        }
        return totalTurn;
    }

    /**
     * True when {@code (tx,ty)} lies inside one of the body's two minimum-turn
     * circles — the geometric signature of a carrot no forward arc can reach
     * (the bicycle would orbit it). Both turn-circle centers sit a min-radius to
     * either side of the heading: with the facing convention here (0° = +Y,
     * heading {@code (-sinθ, cosθ)}), the heading-perpendicular is
     * {@code (∓cosθ, ∓sinθ)}, so the centers are {@code (x ∓ R·cosθ, y ∓ R·sinθ)}.
     * A target within {@code R − margin} of either center is unreachable forward.
     * Tested against both sides, so the result is independent of which way the
     * turn goes. Package-private + static for unit coverage.
     */
    static boolean turnIsInfeasibleForward(float x, float y, float facingDeg,
                                           float tx, float ty, float minRadius) {
        float inner = minRadius - TURN_INFEASIBLE_MARGIN_CELLS;
        if (inner <= 0f) return false;
        double rad = Math.toRadians(facingDeg);
        float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
        float lcx = x - minRadius * cos, lcy = y - minRadius * sin;
        float rcx = x + minRadius * cos, rcy = y + minRadius * sin;
        float dl2 = (tx - lcx) * (tx - lcx) + (ty - lcy) * (ty - lcy);
        float dr2 = (tx - rcx) * (tx - rcx) + (ty - rcy) * (ty - rcy);
        float inner2 = inner * inner;
        return dl2 < inner2 || dr2 < inner2;
    }

    /**
     * Achievable straight-back distance (cells) before the footprint would hit a
     * wall or leave the grid — the "back up to where you can, accounting for
     * what's behind you" calculation. Marches the reverse axis
     * {@code (sin θ, −cos θ)} in {@link #REVERSE_MARCH_STEP} increments,
     * footprint-checking each, capped at {@link #REVERSE_RECOVERY_CELLS}. A
     * conservative budget for {@code advanceReverse} (which may steer and curve);
     * the per-tick gate there handles any deviation. Package-private + static for
     * {@code VehicleControllerRecoveryTest}.
     */
    static float maxReverseDistance(float x, float y, float facingDeg,
                                    VehicleType type, NavigationGrid grid) {
        float rad = (float) Math.toRadians(facingDeg);
        float bx =  (float) Math.sin(rad);   // reverse axis = −forward = −(−sinθ, cosθ)
        float by = -(float) Math.cos(rad);
        float dist = 0f;
        while (dist + REVERSE_MARCH_STEP <= REVERSE_RECOVERY_CELLS) {
            float nd = dist + REVERSE_MARCH_STEP;
            if (!VehicleFootprint.isPoseFeasible(x + bx * nd, y + by * nd, facingDeg,
                    type.visualLengthCells, type.visualWidthCells, grid)) {
                break;
            }
            dist = nd;
        }
        return dist;
    }

    /** Highest index whose cell is in-bounds — the route's last on-grid waypoint (outbound's true last is the off-map exit). {@code -1} if none. */
    static int lastOnGridIndex(float[] xs, float[] ys, NavigationGrid grid) {
        for (int i = xs.length - 1; i >= 0; i--) {
            if (grid.inBounds((int) Math.floor(xs[i]), (int) Math.floor(ys[i]))) return i;
        }
        return -1;
    }

    /** Append the original route's tail past {@code goalIdx} (the off-map exit waypoints, if any) onto the re-routed polyline. */
    static float[][] appendTail(float[][] re, float[] xs, float[] ys, int goalIdx) {
        int tail = xs.length - 1 - goalIdx;
        if (tail <= 0) return re;
        int rn = re[0].length;
        float[] nx = new float[rn + tail];
        float[] ny = new float[rn + tail];
        System.arraycopy(re[0], 0, nx, 0, rn);
        System.arraycopy(re[1], 0, ny, 0, rn);
        for (int i = 0; i < tail; i++) {
            nx[rn + i] = xs[goalIdx + 1 + i];
            ny[rn + i] = ys[goalIdx + 1 + i];
        }
        return new float[][]{nx, ny};
    }

    /**
     * Sample-based feasibility: walk the RS path at
     * {@link #DOCKING_FOOTPRINT_SAMPLE_CELLS} resolution and footprint-check
     * each pose. Conservative — false-positive rejection on a clear path is
     * fine because we just fall back to pursuit.
     */
    static boolean isPathFeasible(Pose start, ReedsShepp.Path path,
                                  float turnRadius, VehicleType type,
                                  NavigationGrid grid) {
        float total = path.lengthCells(turnRadius);
        for (float d = 0; d <= total; d += DOCKING_FOOTPRINT_SAMPLE_CELLS) {
            Pose p = ReedsShepp.sample(start, turnRadius, path, d);
            if (!VehicleFootprint.isPoseFeasible(p.x, p.y, p.facingDeg,
                    type.visualLengthCells, type.visualWidthCells, grid)) {
                return false;
            }
        }
        return true;
    }
}
