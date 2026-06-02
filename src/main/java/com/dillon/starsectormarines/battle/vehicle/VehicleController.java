package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;

/**
 * Owns one ground vehicle's motion: the active {@link ReferenceCorridor}, the
 * playback / pure-pursuit / Reeds-Shepp-docking dispatch, and the reactive
 * recovery state. {@link GroundSystem}'s state machine drives a vehicle by
 * calling {@link #tick(float, boolean)} each INCOMING / DEPARTING frame and
 * reading {@link #consumeArrived()} to fire the state transition.
 *
 * <p>This is the seam the navigation rework plugs into (slice 0). The body of
 * {@link #tick} is, for now, a faithful relocation of {@code GroundSystem}'s
 * old {@code advancePath} / {@code advancePlayback} / {@code advanceDocking}
 * dispatch — same dead-reckon-vs-pursuit fork, same constants, byte-identical
 * motion. Slices 1–3 replace the body without touching {@code GroundSystem}.
 *
 * <p>Motion state that used to live as loose fields on {@link Vehicle}
 * (waypoint cursor, playback progress, docking path, wall-stuck timers) now
 * lives here, where it belongs. The pose itself stays on {@link Vehicle#body}
 * because the renderer and turret loop read it.
 */
public final class VehicleController {

    /** Distance threshold (cells) for landing on the LZ — final waypoint. Tight so the snap-to-LZ at LANDED is invisible. */
    private static final float LZ_ARRIVAL_DIST = 0.25f;
    /** Distance threshold (cells) at which a DEPARTING vehicle hits its final exit waypoint and is considered gone. */
    private static final float EXIT_ARRIVAL_DIST = 1.0f;
    /**
     * Range from LZ (cells) at which an inbound truck attempts to switch from
     * pure-pursuit-along-polyline to Reeds-Shepp docking. Sized to ~2× the
     * truck's min turn radius so the RS path fits in a comfortable window
     * — long enough to be useful, short enough that the path doesn't snake
     * through walls beyond the local LZ neighborhood.
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
    /** Sim-seconds of continuous wall-stuck before triggering a Hybrid A* re-plan from the current pose. */
    private static final float REPLAN_STUCK_THRESHOLD = 2.0f;
    /** Minimum sim-seconds between re-plan attempts so a failing planner doesn't run every tick. */
    private static final float REPLAN_COOLDOWN = 3.0f;
    /** Distance (cells) the vehicle must move from its stuck origin before wallStuckTime resets. Prevents oscillation from clearing the timer. */
    private static final float STUCK_ESCAPE_DIST = 1.5f;

    private final Vehicle vehicle;
    private final NavigationService navigation;

    /** Active corridor for the current direction; rebuilt when inbound flips to outbound (or on re-plan). */
    private ReferenceCorridor corridor;
    /** Last direction passed to {@link #tick}; a change rebuilds the corridor. {@code null} until the first tick. */
    private Boolean lastInbound;
    /** Distance (cells) travelled along the active refined polyline during pose playback. */
    private float playbackProgress;

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
    /** wallStuckTime value at which the last re-plan was attempted. Prevents calling the planner every tick. */
    private float lastReplanAtStuckTime = -1f;

    /** Set true the tick the vehicle reaches its terminal waypoint; cleared by {@link #consumeArrived()}. */
    private boolean arrived;

    public VehicleController(Vehicle vehicle, NavigationService navigation) {
        this.vehicle = vehicle;
        this.navigation = navigation;
    }

    /** Sim-seconds the vehicle has been continuously wall-blocked. Debug/history read. */
    public float wallStuckTime() { return wallStuckTime; }
    /** Distance travelled along the active playback polyline. Debug read. */
    public float playbackProgress() { return playbackProgress; }
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
     * Advance the vehicle one tick along its active path. {@code isInbound}
     * selects the inbound vs. outbound waypoint arrays; a change of direction
     * since the previous tick rebuilds the corridor and resets playback /
     * docking state (this replaces the old manual {@code waypointIndex = 1;
     * playbackProgress = 0} resets in {@code GroundSystem}'s LANDED/OVERWATCH).
     */
    public void tick(float dt, boolean isInbound) {
        float[] xs = isInbound ? vehicle.inboundX : vehicle.outboundX;
        float[] ys = isInbound ? vehicle.inboundY : vehicle.outboundY;
        float[] headings = isInbound ? vehicle.inboundHeading : vehicle.outboundHeading;

        if (lastInbound == null || lastInbound != isInbound) {
            corridor = new ReferenceCorridor(xs, ys, 1);
            playbackProgress = 0f;
            dockingPath = null;
            lastInbound = isInbound;
        }

        advance(xs, ys, headings, dt, isInbound);
    }

    /**
     * Path follower. Three modes (unchanged from the pre-rework
     * {@code GroundSystem.advancePath}):
     * <ol>
     *   <li><b>Direct pose playback</b> — when the path carries
     *       {@link HybridAStarPlanner} heading data, play the planned poses
     *       directly. No steering law, no reactive recovery.</li>
     *   <li><b>Pure pursuit</b> (coarse polylines without headings) — carrot
     *       along the corridor, brake taper to the LZ, wall-stuck recovery +
     *       stuck re-plan.</li>
     *   <li><b>Reeds-Shepp docking</b> — for inbound pursuit paths within
     *       {@link #DOCKING_TRIGGER_CELLS} of the LZ.</li>
     * </ol>
     */
    private void advance(float[] xs, float[] ys, float[] headings, float dt, boolean isInbound) {
        if (headings != null) {
            advancePlayback(xs, ys, headings, dt, isInbound);
            return;
        }

        if (dockingPath != null) {
            advanceDocking(dt);
            return;
        }

        if (isInbound) {
            tryEngageDocking(xs, ys);
            if (dockingPath != null) {
                advanceDocking(dt);
                return;
            }
        }

        GroundBody body = vehicle.body;
        VehicleType type = vehicle.type;
        float prevX = body.x, prevY = body.y;
        float prevFacing = body.facingDegrees;

        PurePursuit.Carrot carrot = corridor.carrot(body.x, body.y, type.lookAheadCells);
        float remaining = corridor.remainingLength(body.x, body.y);
        float taper = (float) Math.sqrt(2f * type.brakingAccel * Math.max(0f, remaining));
        float targetSpeed = Math.min(type.maxSpeed, taper);

        float cdx = carrot.x - body.x, cdy = carrot.y - body.y;
        float carrotBearing = AirBody.facingToward(cdx, cdy);
        float alpha = ((carrotBearing - body.facingDegrees + 540f) % 360f) - 180f;
        if (Math.abs(alpha) > 90f) {
            targetSpeed = -targetSpeed * 0.5f;
        }

        body.tick(carrot.x, carrot.y, targetSpeed, dt);

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
            if (wallStuckTime > REPLAN_STUCK_THRESHOLD
                    && wallStuckTime - lastReplanAtStuckTime >= REPLAN_COOLDOWN) {
                body.x = prevX;
                body.y = prevY;
                body.facingDegrees = prevFacing;
                body.speed = 0f;
                lastReplanAtStuckTime = wallStuckTime;
                if (tryReplan(xs, ys, isInbound)) {
                    wallStuckTime = 0f;
                    lastReplanAtStuckTime = -1f;
                    return;
                }
            }
            if (wallStuckTime > WALL_REVERSE_DELAY) {
                body.x = prevX;
                body.y = prevY;
                body.facingDegrees = prevFacing;
                body.speed = 0f;
                body.tick(carrot.x, carrot.y, -WALL_REVERSE_SPEED, dt);
                if (!VehicleFootprint.isPoseFeasible(body.x, body.y, body.facingDegrees,
                        type.visualLengthCells, type.visualWidthCells, grid)) {
                    body.x = prevX;
                    body.y = prevY;
                    body.facingDegrees = prevFacing;
                    body.speed = 0f;
                }
            } else {
                body.x = prevX;
                body.y = prevY;
                body.facingDegrees = prevFacing;
                body.speed = 0f;
            }
        } else if (wallStuckTime > 0f) {
            float dx = body.x - stuckOriginX;
            float dy = body.y - stuckOriginY;
            if (dx * dx + dy * dy > STUCK_ESCAPE_DIST * STUCK_ESCAPE_DIST) {
                wallStuckTime = 0f;
            }
        }

        int lastIdx = xs.length - 1;
        float distToLast = body.distanceTo(xs[lastIdx], ys[lastIdx]);
        float threshold = isInbound ? LZ_ARRIVAL_DIST : EXIT_ARRIVAL_DIST;
        if (distToLast < threshold) {
            if (isInbound) {
                body.teleport(xs[lastIdx], ys[lastIdx], body.facingDegrees);
            }
            arrived = true;
        }
    }

    /**
     * Direct pose playback along a Hybrid A* refined path. Accumulate distance,
     * interpolate (x, y, heading) between waypoints, assign to body. No steering
     * law, no reactive collision — the plan was validated at planning time and
     * the grid is static.
     */
    private void advancePlayback(float[] xs, float[] ys, float[] headings, float dt, boolean isInbound) {
        GroundBody body = vehicle.body;
        VehicleType type = vehicle.type;
        float totalLength = polylineLength(xs, ys);
        float remaining = totalLength - playbackProgress;
        float taper = (float) Math.sqrt(2f * type.brakingAccel * Math.max(0f, remaining));
        float speed = Math.min(type.maxSpeed, taper);
        playbackProgress += speed * dt;

        if (playbackProgress >= totalLength) {
            int last = xs.length - 1;
            if (isInbound) {
                body.teleport(xs[last], ys[last], headings[last]);
            }
            arrived = true;
            return;
        }

        float walked = 0f;
        for (int i = 0; i < xs.length - 1; i++) {
            float dx = xs[i + 1] - xs[i];
            float dy = ys[i + 1] - ys[i];
            float segLen = (float) Math.sqrt(dx * dx + dy * dy);
            if (walked + segLen >= playbackProgress) {
                float t = (segLen > 1e-6f) ? (playbackProgress - walked) / segLen : 0f;
                body.x = xs[i] + dx * t;
                body.y = ys[i] + dy * t;
                float dh = ((headings[i + 1] - headings[i] + 540f) % 360f) - 180f;
                body.facingDegrees = headings[i] + dh * t;
                body.speed = speed;
                return;
            }
            walked += segLen;
        }

        int last = xs.length - 1;
        body.x = xs[last];
        body.y = ys[last];
        body.facingDegrees = headings[last];
        body.speed = 0f;
    }

    private static float polylineLength(float[] xs, float[] ys) {
        float total = 0f;
        for (int i = 0; i < xs.length - 1; i++) {
            float dx = xs[i + 1] - xs[i];
            float dy = ys[i + 1] - ys[i];
            total += (float) Math.sqrt(dx * dx + dy * dy);
        }
        return total;
    }

    /**
     * Re-plan from the vehicle's current pose to its remaining goal when the
     * reactive recovery has failed for {@link #REPLAN_STUCK_THRESHOLD} seconds.
     * Runs Hybrid A* once; on success replaces the vehicle's waypoint arrays,
     * rebuilds the corridor, and resets the playback/docking cursors.
     */
    private boolean tryReplan(float[] xs, float[] ys, boolean isInbound) {
        GroundBody body = vehicle.body;
        int lastOnGrid = xs.length - 1;
        if (xs[lastOnGrid] < 0 || ys[lastOnGrid] < 0) lastOnGrid--;
        if (lastOnGrid < 1) return false;

        float goalX = xs[lastOnGrid], goalY = ys[lastOnGrid];
        float goalFacing = AirBody.facingToward(
                xs[lastOnGrid] - xs[lastOnGrid - 1],
                ys[lastOnGrid] - ys[lastOnGrid - 1]);

        float[] guideX = new float[]{body.x, goalX};
        float[] guideY = new float[]{body.y, goalY};
        float[][] refined = HybridAStarPlanner.refine(
                guideX, guideY, body.facingDegrees, goalFacing,
                vehicle.type, navigation.getGrid());
        if (refined == null) return false;

        if (isInbound) {
            vehicle.inboundX = refined[0];
            vehicle.inboundY = refined[1];
            vehicle.inboundHeading = refined[2];
        } else {
            vehicle.outboundX = refined[0];
            vehicle.outboundY = refined[1];
            vehicle.outboundHeading = refined[2];
        }
        corridor = new ReferenceCorridor(refined[0], refined[1], 1);
        playbackProgress = 0f;
        dockingPath = null;
        return true;
    }

    /**
     * Try to switch the inbound truck from pure pursuit to a Reeds-Shepp
     * docking maneuver when within {@link #DOCKING_TRIGGER_CELLS} of the LZ.
     * The candidate RS path is footprint-checked along its length; if any pose
     * is non-walkable, docking stays off this tick (pure pursuit then delivers
     * the truck to the LZ via the polyline).
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
     * fine because we just fall back to pure pursuit.
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
