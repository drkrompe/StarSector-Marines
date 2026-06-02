package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;

/**
 * Owns one ground vehicle's motion: the active {@link ReferenceCorridor}
 * (advisory coarse route), the rolling {@link Trajectory} it tracks, the
 * pure-pursuit + Reeds-Shepp-docking dispatch, and the reactive recovery stub.
 * {@link GroundSystem}'s state machine drives a vehicle by calling
 * {@link #tick(float, boolean)} each INCOMING / DEPARTING frame and reading
 * {@link #consumeArrived()} to fire the state transition.
 *
 * <p><b>Always kinematic, never on rails.</b> The body's own {@link BicycleBody}
 * kinematics govern the pose every tick — the controller only ever feeds it a
 * carrot + a target speed. There are two carrot sources:
 * <ol>
 *   <li><b>Rolling local trajectory</b> (primary) — {@link LocalTrajectoryPlanner}
 *       turns the corridor into a short feasible {@link Trajectory} the body
 *       pursues. Refreshed every {@link #REPLAN_INTERVAL_SEC} / when consumed /
 *       on drift. This is what plans <em>through</em> corners so they read as
 *       continuous min-radius arcs.</li>
 *   <li><b>Coarse corridor</b> (fallback) — when the local plan returns
 *       {@code null} (off-map approach before the truck reaches the grid, or a
 *       transient planner gap) the body pursues the advisory corridor polyline
 *       directly. Still kinematic, so still smooth; the slice-3 recovery ladder
 *       replaces this null-handling with a formal escalation.</li>
 * </ol>
 * The old dead-reckon "playback along synthetic-heading rails" fork is gone —
 * that was the source of the 90° corner snaps. Reeds-Shepp docking is the one
 * surviving rails case, as a terminal LZ phase (see {@code navigation-rework/
 * overview.md}).
 *
 * <p>Motion state that used to live as loose fields on {@link Vehicle}
 * (waypoint cursor, docking path, wall-stuck timers) lives here, where it
 * belongs. The pose itself stays on {@link Vehicle#body} because the renderer
 * and turret loop read it.
 */
public final class VehicleController {

    /** Distance threshold (cells) for landing on the LZ — final waypoint. Tight so the snap-to-LZ at LANDED is invisible. */
    private static final float LZ_ARRIVAL_DIST = 0.25f;
    /** Distance threshold (cells) at which a DEPARTING vehicle hits its final exit waypoint and is considered gone. */
    private static final float EXIT_ARRIVAL_DIST = 1.0f;
    /**
     * Range from LZ (cells) at which an inbound truck attempts to switch from
     * pursuit to Reeds-Shepp docking. Sized to ~2× the truck's min turn radius
     * so the RS path fits in a comfortable window — long enough to be useful,
     * short enough that the path doesn't snake through walls beyond the local
     * LZ neighborhood.
     */
    private static final float DOCKING_TRIGGER_CELLS = 6f;
    /** Constant forward speed (cells/sec) along the Reeds-Shepp docking path. Slower than cruise to read as a careful approach. */
    private static final float DOCKING_SPEED = 2.0f;
    /** Sample step (cells) along the RS path when validating feasibility against {@link VehicleFootprint}. */
    private static final float DOCKING_FOOTPRINT_SAMPLE_CELLS = 0.5f;
    /** Sim-seconds a vehicle must be wall-blocked before it starts reversing. Brief pause reads as "realizing the turn won't fit." */
    private static final float WALL_REVERSE_DELAY = 0.3f;
    /** Reverse speed when backing away from a wall, cells/sec. Slower than forward cruise — cautious backup. */
    private static final float WALL_REVERSE_SPEED = 1.4f;
    /** Distance (cells) the vehicle must move from its stuck origin before wallStuckTime resets. Prevents oscillation from clearing the timer. */
    private static final float STUCK_ESCAPE_DIST = 1.5f;

    /** Max sim-seconds a local {@link Trajectory} is tracked before a fresh plan is requested. Keeps the rolling goal marching down the corridor. */
    private static final float REPLAN_INTERVAL_SEC = 0.25f;
    /** Off-corridor drift (cells) that forces an immediate replan rather than waiting out the interval. */
    private static final float REPLAN_DRIFT_CELLS = 2.0f;
    /** Fraction of the current trajectory's length that may be consumed before a replan is forced (so we plan the next horizon before running out). */
    private static final float REPLAN_CONSUMED_FRACTION = 0.5f;

    /**
     * Carrot look-ahead floor (cells) — look-ahead shrinks toward this as the
     * vehicle slows so tight, slow corners are tracked closely instead of cut
     * (a long fixed look-ahead chord cuts the inside of a sharp turn, so the body
     * steers tighter than the planned arc and wedges). Grows back to
     * {@link VehicleType#lookAheadCells} at cruise for smooth straights. ~one
     * wheelbase, below which pure pursuit oscillates.
     */
    private static final float MIN_LOOKAHEAD_CELLS = 1.2f;
    /** Path-preview window (cells) for the curvature speed governor — how far ahead the upcoming bend is measured. A couple cells past the cruise stopping distance so the truck is already slowed when it reaches the corner. */
    private static final float CURVE_PREVIEW_CELLS = 4.0f;
    /** Total heading change (deg) over the preview window below which speed isn't cut — ignores gentle bends and straight-line float wander. */
    private static final float CURVE_DEADBAND_DEG = 20f;
    /** Total heading change (deg) at/above which forward speed is cut all the way to {@link #CURVE_MIN_SPEED_FRAC} of cruise — a hard corner. */
    private static final float CURVE_FULL_DEG = 75f;
    /** Floor the curvature governor slows to, as a fraction of {@link VehicleType#maxSpeed} — keeps creeping through the corner, never a mid-route stop. */
    private static final float CURVE_MIN_SPEED_FRAC = 0.35f;

    private final Vehicle vehicle;
    private final NavigationService navigation;

    /** Active corridor for the current direction; rebuilt when inbound flips to outbound. */
    private ReferenceCorridor corridor;
    /** Last direction passed to {@link #tick}; a change rebuilds the corridor. {@code null} until the first tick. */
    private Boolean lastInbound;

    /** Current rolling local plan the body is tracking, or {@code null} when on the coarse-corridor fallback. */
    private Trajectory trajectory;
    /** Arc-distance (cells) consumed along {@link #trajectory} since it was planned; resets on replan. */
    private float trajProgress;
    /** Sim-seconds since the last local plan; gates the replan cadence. */
    private float sinceReplan;
    /** True when last tick's carrot pinned to the trajectory end (consumed) — forces a replan. */
    private boolean trajCarrotAtEnd;

    /** Active Reeds-Shepp docking path, or {@code null} when not docking. */
    private ReedsShepp.Path dockingPath;
    private Pose dockingStartPose;
    private float dockingTurnRadius;
    private float dockingProgressCells;
    private float dockingGoalFacingDeg;

    /** Sim-seconds the vehicle has been continuously blocked by walls. Drives the reverse recovery. */
    private float wallStuckTime;
    /** Position where the vehicle first got stuck; wallStuckTime only resets once it moves meaningfully away. */
    private float stuckOriginX, stuckOriginY;

    /** Set true the tick the vehicle reaches its terminal waypoint; cleared by {@link #consumeArrived()}. */
    private boolean arrived;

    public VehicleController(Vehicle vehicle, NavigationService navigation) {
        this.vehicle = vehicle;
        this.navigation = navigation;
    }

    /** Sim-seconds the vehicle has been continuously wall-blocked. Debug/history read. */
    public float wallStuckTime() { return wallStuckTime; }
    /** Arc-distance consumed along the current local trajectory, or 0 on the corridor fallback. Debug read. */
    public float trajectoryProgress() { return trajProgress; }
    /** True when the controller is tracking a feasible local plan (vs. the coarse-corridor fallback). Debug read. */
    public boolean hasTrajectory() { return trajectory != null; }
    /** Active waypoint cursor into the coarse corridor. Debug read. */
    public int waypointIndex() { return corridor != null ? corridor.cursor() : 1; }
    /** Active Reeds-Shepp docking path, or {@code null} when not docking. Debug overlay read. */
    public ReedsShepp.Path dockingPath() { return dockingPath; }
    /** Start pose of the active docking path. Debug overlay read. */
    public Pose dockingStartPose() { return dockingStartPose; }
    /** Turn radius (cells) of the active docking path. Debug overlay read. */
    public float dockingTurnRadius() { return dockingTurnRadius; }

    /**
     * Returns {@code true} (exactly once) if the vehicle reached its terminal
     * waypoint since the last call, then clears the flag. {@link GroundSystem}
     * uses this to drive INCOMING→LANDED / DEPARTING→GONE.
     */
    public boolean consumeArrived() {
        boolean a = arrived;
        arrived = false;
        return a;
    }

    /**
     * Advance the vehicle one tick. {@code isInbound} selects the inbound vs.
     * outbound corridor; a change of direction since the previous tick rebuilds
     * the corridor and clears the rolling plan / docking state (this replaces
     * the old manual {@code waypointIndex = 1} resets in {@code GroundSystem}'s
     * LANDED/OVERWATCH).
     */
    public void tick(float dt, boolean isInbound) {
        float[] xs = isInbound ? vehicle.inboundX : vehicle.outboundX;
        float[] ys = isInbound ? vehicle.inboundY : vehicle.outboundY;

        if (lastInbound == null || lastInbound != isInbound) {
            corridor = new ReferenceCorridor(xs, ys, 1);
            trajectory = null;
            trajProgress = 0f;
            sinceReplan = 0f;
            trajCarrotAtEnd = false;
            dockingPath = null;
            lastInbound = isInbound;
        }

        advance(xs, ys, dt, isInbound);
    }

    /**
     * One tracking step. Priority: terminal RS docking (inbound) → arrival →
     * rolling local-trajectory tracking → coarse-corridor pursuit fallback,
     * with a shared wall-stuck reverse stub wrapping the kinematic move.
     */
    private void advance(float[] xs, float[] ys, float dt, boolean isInbound) {
        GroundBody body = vehicle.body;
        VehicleType type = vehicle.type;

        // Keep the advisory cursor abreast of the pose so remainingLength /
        // the rolling goal measure from the current segment.
        corridor.advance(body.x, body.y);

        // --- Terminal docking phase (inbound only) -------------------------
        if (isInbound) {
            if (dockingPath != null) { advanceDocking(dt); return; }
            tryEngageDocking(xs, ys);
            if (dockingPath != null) { advanceDocking(dt); return; }
        }

        // --- Arrival -------------------------------------------------------
        // Checked before planning: as the truck nears the corridor end the
        // rolling goal pins to the endpoint and the local plan legitimately
        // returns null (start already inside the soft goal radius). That is
        // "arrived / coasting in," NOT a stuck signal — handle it here so it
        // never escalates to recovery (slice-1 critique #1).
        int lastIdx = xs.length - 1;
        float distToLast = body.distanceTo(xs[lastIdx], ys[lastIdx]);
        float threshold = isInbound ? LZ_ARRIVAL_DIST : EXIT_ARRIVAL_DIST;
        if (distToLast < threshold) {
            if (isInbound) body.teleport(xs[lastIdx], ys[lastIdx], body.facingDegrees);
            arrived = true;
            return;
        }

        // --- Rolling local-trajectory refresh ------------------------------
        sinceReplan += dt;
        if (needsReplan(body)) {
            Pose pose = new Pose(body.x, body.y, body.facingDegrees);
            trajectory = LocalTrajectoryPlanner.plan(pose, corridor, type, navigation.getGrid());
            trajProgress = 0f;
            sinceReplan = 0f;
            trajCarrotAtEnd = false;
        }

        // --- Track (trajectory if we have one, else the coarse corridor) ---
        float prevX = body.x, prevY = body.y, prevFacing = body.facingDegrees;

        float[] px, py;
        int startIdx;
        if (trajectory != null) {
            px = trajectory.xs();
            py = trajectory.ys();
            startIdx = 1;   // poses[0] is the plan-time pose; scan forward from 1
        } else {
            px = xs;
            py = ys;
            startIdx = corridor.cursor();
        }

        // Speed-scaled look-ahead: pull the carrot toward the body as the truck
        // slows so tight corners track closely; let it stretch back to the cruise
        // look-ahead on straights for smoothness. Uses last tick's speed (the
        // one-tick lag is negligible) and the speed is already curvature-limited
        // below, so approaching a bend naturally tightens tracking.
        float speedFrac = type.maxSpeed > 0f ? Math.min(1f, Math.abs(body.speed) / type.maxSpeed) : 0f;
        float lookAhead = Math.min(type.lookAheadCells,
                MIN_LOOKAHEAD_CELLS + speedFrac * (type.lookAheadCells - MIN_LOOKAHEAD_CELLS));

        PurePursuit.Carrot carrot = PurePursuit.pick(body.x, body.y, px, py, startIdx, lookAhead);
        if (trajectory != null) trajCarrotAtEnd = carrot.atEnd;

        // Forward speed is the min of two caps:
        //  - brake taper to the LZ end, so the truck stops cleanly at the corridor
        //    end regardless of where the local horizon currently ends;
        //  - a curvature governor that slows for the sharpest bend within
        //    CURVE_PREVIEW_CELLS ahead. The bicycle's min turn radius is fixed by
        //    geometry — speed can't tighten it — but entering a corner slow gives
        //    the bounded steering slew time to reach lock and keeps pursuit on the
        //    planned arc, instead of overshooting at cruise and reversing out (the
        //    "tiny reverses at a missed turn" failure).
        float remaining = corridor.remainingLength(body.x, body.y);
        float taper = (float) Math.sqrt(2f * type.brakingAccel * Math.max(0f, remaining));
        float curveCap = curvatureSpeedCap(px, py, startIdx, body.x, body.y, type.maxSpeed);
        float targetSpeed = Math.min(Math.min(type.maxSpeed, taper), curveCap);

        float cdx = carrot.x - body.x, cdy = carrot.y - body.y;
        float carrotBearing = AirBody.facingToward(cdx, cdy);
        float alpha = ((carrotBearing - body.facingDegrees + 540f) % 360f) - 180f;
        if (Math.abs(alpha) > 90f) {
            targetSpeed = -targetSpeed * 0.5f;
        }

        body.tick(carrot.x, carrot.y, targetSpeed, dt);
        if (trajectory != null) {
            trajProgress += (float) Math.hypot(body.x - prevX, body.y - prevY);
        }

        // Skip the footprint gate while the carrot is pulling the body across
        // the map edge. The inbound staging waypoint (spawn) and the outbound
        // exit waypoint (GONE) are off-grid by design — there are no walls out
        // there. Without this, the gate reverts every move at the perimeter and
        // a departing truck oscillates at the edge instead of driving off (the
        // old playback fork drove off un-gated; this restores the exit, not the
        // rails).
        boolean exitingOffMap = !navigation.getGrid().inBounds(
                (int) Math.floor(carrot.x), (int) Math.floor(carrot.y));
        if (!exitingOffMap) {
            wallStuckRecovery(body, type, carrot, prevX, prevY, prevFacing, dt);
        }
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
     * Decide whether to request a fresh local plan this tick: no current plan,
     * the replan interval elapsed, the current plan is consumed (carrot pinned
     * to its end or past the consume fraction), or the body has drifted off the
     * advisory corridor.
     */
    private boolean needsReplan(GroundBody body) {
        if (trajectory == null) return true;
        if (sinceReplan >= REPLAN_INTERVAL_SEC) return true;
        if (trajCarrotAtEnd) return true;
        if (trajProgress >= trajectory.lengthCells() * REPLAN_CONSUMED_FRACTION) return true;
        if (corridor.offCorridorDistance(body.x, body.y) > REPLAN_DRIFT_CELLS) return true;
        return false;
    }

    /**
     * Wall-stuck reverse stub (shared by both carrot sources). When a move
     * carries a previously-feasible body into an infeasible pose, revert the
     * move; after {@link #WALL_REVERSE_DELAY} seconds blocked, pulse a reverse.
     * A trajectory is feasible by construction so this rarely fires while
     * tracking one — it mainly guards the coarse-corridor fallback. The formal
     * recovery ladder (drift → blocked → stuck → giveup) lands in slice 3.
     */
    private void wallStuckRecovery(GroundBody body, VehicleType type, PurePursuit.Carrot carrot,
                                   float prevX, float prevY, float prevFacing, float dt) {
        NavigationGrid grid = navigation.getGrid();
        boolean prevOnGrid = VehicleFootprint.isPoseFeasible(prevX, prevY, prevFacing,
                type.visualLengthCells, type.visualWidthCells, grid);
        boolean newFeasible = VehicleFootprint.isPoseFeasible(body.x, body.y, body.facingDegrees,
                type.visualLengthCells, type.visualWidthCells, grid);

        if (prevOnGrid && !newFeasible) {
            if (wallStuckTime == 0f) {
                stuckOriginX = prevX;
                stuckOriginY = prevY;
            }
            wallStuckTime += dt;
            body.x = prevX;
            body.y = prevY;
            body.facingDegrees = prevFacing;
            body.speed = 0f;
            if (wallStuckTime > WALL_REVERSE_DELAY) {
                body.tick(carrot.x, carrot.y, -WALL_REVERSE_SPEED, dt);
                if (!VehicleFootprint.isPoseFeasible(body.x, body.y, body.facingDegrees,
                        type.visualLengthCells, type.visualWidthCells, grid)) {
                    body.x = prevX;
                    body.y = prevY;
                    body.facingDegrees = prevFacing;
                    body.speed = 0f;
                }
            }
        } else if (wallStuckTime > 0f) {
            float dx = body.x - stuckOriginX;
            float dy = body.y - stuckOriginY;
            if (dx * dx + dy * dy > STUCK_ESCAPE_DIST * STUCK_ESCAPE_DIST) {
                wallStuckTime = 0f;
            }
        }
    }

    /**
     * Try to switch the inbound truck from pursuit to a Reeds-Shepp docking
     * maneuver when within {@link #DOCKING_TRIGGER_CELLS} of the LZ. The
     * candidate RS path is footprint-checked along its length; if any pose is
     * non-walkable, docking stays off this tick (pursuit then delivers the
     * truck to the LZ via the corridor).
     */
    private void tryEngageDocking(float[] xs, float[] ys) {
        GroundBody body = vehicle.body;
        if (!(body instanceof BicycleBody)) return;
        int lastIdx = xs.length - 1;
        float lzX = xs[lastIdx];
        float lzY = ys[lastIdx];
        float distToLz = body.distanceTo(lzX, lzY);
        if (distToLz > DOCKING_TRIGGER_CELLS) return;

        float prevX = xs[lastIdx - 1];
        float prevY = ys[lastIdx - 1];
        float lzFacingDeg = AirBody.facingToward(lzX - prevX, lzY - prevY);

        Pose start = new Pose(body.x, body.y, body.facingDegrees);
        Pose goal = new Pose(lzX, lzY, lzFacingDeg);
        float turnRadius = ((BicycleBody) body).minTurnRadiusCells();
        ReedsShepp.Path path = ReedsShepp.shortest(start, goal, turnRadius);
        if (path == null) return;
        if (!isPathFeasible(start, path, turnRadius, vehicle.type, navigation.getGrid())) return;

        dockingPath = path;
        dockingStartPose = start;
        dockingTurnRadius = turnRadius;
        dockingProgressCells = 0f;
        dockingGoalFacingDeg = lzFacingDeg;
        trajectory = null;   // docking owns the pose now
    }

    /**
     * Advance the docking truck by {@link #DOCKING_SPEED} for one tick along
     * its Reeds-Shepp path, set the body's pose from the sampled point, and
     * flag arrival when the path's total length is consumed.
     */
    private void advanceDocking(float dt) {
        GroundBody body = vehicle.body;
        dockingProgressCells += DOCKING_SPEED * dt;
        float totalCells = dockingPath.lengthCells(dockingTurnRadius);
        if (dockingProgressCells >= totalCells) {
            body.teleport(vehicle.lzX, vehicle.lzY, dockingGoalFacingDeg);
            dockingPath = null;
            arrived = true;
            return;
        }
        Pose p = ReedsShepp.sample(dockingStartPose, dockingTurnRadius,
                dockingPath, dockingProgressCells);
        body.x = p.x;
        body.y = p.y;
        body.facingDegrees = p.facingDeg;
    }

    /**
     * Sample-based feasibility: walk the RS path at
     * {@link #DOCKING_FOOTPRINT_SAMPLE_CELLS} resolution and footprint-check
     * each pose. Conservative — false-positive rejection on a clear path is
     * fine because we just fall back to pursuit.
     */
    private static boolean isPathFeasible(Pose start, ReedsShepp.Path path,
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
