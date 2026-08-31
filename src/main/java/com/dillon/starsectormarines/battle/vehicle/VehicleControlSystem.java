package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.vehicle.components.VehicleControlComponent;

/**
 * Stateless per-vehicle motion driver — the behaviour half of the ground-vehicle
 * control split. Holds <b>no</b> per-vehicle state: every field it reads or writes
 * lives in the {@code VEHICLE_CONTROL} component ({@link VehicleControlComponent}),
 * resolved by entity id through {@link ConvoyService} at the top of each entry
 * point. {@link GroundSystem}'s state machine drives a vehicle by calling
 * {@link #tick(long, float, boolean)} each INCOMING / DEPARTING frame and reading
 * {@link #consumeArrived(long)} to fire the state transition. The tuning constants
 * and static geometry it references live on {@link VehicleController} (qualified,
 * shared with the unit tests).
 *
 * <p><b>Always kinematic, never on rails.</b> The body's own {@link BicycleBody}
 * kinematics govern the pose every tick — the driver only ever feeds it a carrot +
 * a target speed. There are two carrot sources:
 * <ol>
 *   <li><b>Rolling local trajectory</b> (primary) — {@link LocalTrajectoryPlanner}
 *       turns the corridor into a short feasible {@link Trajectory} the body
 *       pursues. Refreshed every {@link VehicleController#REPLAN_INTERVAL_SEC} /
 *       when consumed / on drift. This is what plans <em>through</em> corners so
 *       they read as continuous min-radius arcs.</li>
 *   <li><b>Coarse corridor</b> (boundary fallback) — only while the body is
 *       crossing the deliberate off-map entry/exit tail and therefore has no
 *       complete on-grid footprint pose. An on-grid {@code null} is a rejected
 *       route and triggers braking plus rerouting, never raw-corridor pursuit.</li>
 * </ol>
 * The old dead-reckon "playback along synthetic-heading rails" fork is gone — that
 * was the source of the 90° corner snaps. Reeds-Shepp docking is the one surviving
 * rails case, as a terminal LZ phase (see {@code convoy-nouns.md}).
 *
 * <p>Motion state (waypoint cursor, docking path, wall-stuck timers, {@code arrived})
 * lives in the {@link VehicleControlComponent}; the pose stays on the
 * {@link GroundBody} in {@code GROUND_KINEMATICS} and mission-level state (paths,
 * route inputs, LZ) on the {@link VehicleMission} — all reached by id through
 * {@link ConvoyService}.
 */
public final class VehicleControlSystem {

    private final ConvoyService convoy;
    private final NavigationService navigation;

    public VehicleControlSystem(ConvoyService convoy, NavigationService navigation) {
        this.convoy = convoy;
        this.navigation = navigation;
    }

    /**
     * Advance vehicle {@code id} one tick. {@code isInbound} selects the inbound
     * vs. outbound corridor; a change of direction since the previous tick rebuilds
     * the corridor and clears the rolling plan / docking state (this replaces the
     * old manual {@code waypointIndex = 1} reset in {@link GroundSystem}'s
     * LANDED).
     */
    public void tick(long id, float dt, boolean isInbound) {
        VehicleMission mission = convoy.mission(id);
        GroundBody body = convoy.body(id);
        VehicleType type = convoy.vehicleType(id);
        VehicleControlComponent s = convoy.control(id);

        float[] xs = isInbound ? mission.inboundX : mission.outboundX;
        float[] ys = isInbound ? mission.inboundY : mission.outboundY;

        if (s.lastInbound == null || s.lastInbound != isInbound) {
            initCorridor(s, xs, ys);
            s.lastInbound = isInbound;
        }

        advance(mission, body, type, s, xs, ys, dt, isInbound);
    }

    /**
     * Returns {@code true} (exactly once) if vehicle {@code id} reached its terminal
     * waypoint since the last call, then clears the flag. {@link GroundSystem} uses
     * this to drive INCOMING→LANDED / DEPARTING→GONE.
     */
    public boolean consumeArrived(long id) {
        VehicleControlComponent s = convoy.control(id);
        boolean a = s.arrived;
        s.arrived = false;
        return a;
    }

    /** (Re)build the corridor for a route array and clear all per-route tracking + recovery state. Used on direction flip and on a recovery re-route. */
    private void initCorridor(VehicleControlComponent s, float[] xs, float[] ys) {
        initCorridor(s, xs, ys, true);
    }

    /** Rebuilds the control state; a recovery re-route retains its tried rescue bearings. */
    private void initCorridor(VehicleControlComponent s, float[] xs, float[] ys,
                              boolean clearRescueFirstSteps) {
        s.corridor = new ReferenceCorridor(xs, ys, 1);
        s.trajectory = null;
        s.trajProgress = 0f;
        s.sinceReplan = 0f;
        s.trajCarrotAtEnd = false;
        s.localPlanFailureTime = 0f;
        s.localPlanFailureRerouteAttempted = false;
        s.dockingPath = null;
        s.dockingIsDeparture = false;
        s.turnaroundsUsed = 0;
        s.recovery = VehicleControlComponent.Recovery.NONE;
        s.recoveryAttempts = 0;
        s.recoveryBestRemaining = Float.MAX_VALUE;
        s.wallStuckTime = 0f;
        s.timeSinceProgress = 0f;
        if (clearRescueFirstSteps) {
            s.rescueFirstStepTriedMask = 0;
            s.rerouteAvoidCount = 0;
        }
    }

    /**
     * One tracking step. Priority: terminal RS docking (inbound) → exact arrival
     * → rolling local-trajectory tracking → aligned terminal-region landing when
     * no safe forward segment remains → off-map coarse-corridor crossing, with a
     * shared wall-stuck reverse stub wrapping the kinematic move.
     */
    private void advance(VehicleMission mission, GroundBody body, VehicleType type,
                         VehicleControlComponent s, float[] xs, float[] ys, float dt, boolean isInbound) {
        // Keep the advisory cursor abreast of the pose so remainingLength /
        // the rolling goal measure from the current segment.
        s.corridor.advance(body.x, body.y);

        // --- Committed reverse recovery ------------------------------------
        // A backup maneuver owns the pose until it finishes (or backs into
        // something). This runs before the forward track so the two don't
        // alternate tick-to-tick — the cancellation that produced the old
        // tiny-reverse oscillation — and before stall detection so the stall
        // clock is paused while a committed maneuver is in progress (a reverse
        // moves away from the goal, which must not itself read as "stalled").
        if (s.recovery == VehicleControlComponent.Recovery.REVERSING) {
            advanceReverse(body, type, s, dt);
            return;
        }

        // --- Non-convergence detection (runs every forward tick, so it catches
        // an open-space orbit as well as wall contact). Progress = corridor
        // remaining-length dropping a margin below the best reached. No progress
        // for STALL_SECONDS → the truck isn't converging (orbiting a turn it
        // can't make): escalate to a re-route that laps around the failing spot.
        float rem = s.corridor.remainingLength(body.x, body.y);
        if (rem < s.recoveryBestRemaining - VehicleController.RECOVERY_PROGRESS_MARGIN) {
            s.recoveryBestRemaining = rem;
            s.recoveryAttempts = 0;
            s.timeSinceProgress = 0f;
            s.rescueFirstStepTriedMask = 0;
        } else {
            s.timeSinceProgress += dt;
            if (s.timeSinceProgress > VehicleController.STALL_SECONDS) {
                boolean rerouted = attemptReroute(mission, body, type, s);
                s.timeSinceProgress = 0f; // rate-limit retries whether or not it took
                if (rerouted) return;   // next tick drives the fresh corridor cleanly
            }
        }

        // --- Reeds-Shepp maneuver phase ------------------------------------
        // A running maneuver owns the pose whichever direction it serves: an
        // arrival dock inbound, a departure turnaround outbound.
        if (s.dockingPath != null) { advanceDocking(mission, body, s, dt); return; }
        if (isInbound) {
            tryEngageDocking(mission, body, type, s, xs, ys);
            if (s.dockingPath != null) { advanceDocking(mission, body, s, dt); return; }
        }

        // --- Arrival -------------------------------------------------------
        // Preserve the exact endpoint and tiny snap when docking or ordinary
        // pursuit can actually reach it. A footprint-constrained terminal pose
        // farther out is recognized below only after planning finds no safe
        // forward segment.
        int lastIdx = xs.length - 1;
        float distToLast = body.distanceTo(xs[lastIdx], ys[lastIdx]);
        float threshold = VehicleController.arrivalDist(
                isInbound ? VehicleController.LZ_ARRIVAL_DIST : VehicleController.EXIT_ARRIVAL_DIST,
                body, dt);
        if (distToLast < threshold) {
            if (isInbound) body.teleport(xs[lastIdx], ys[lastIdx], body.facingDegrees);
            s.arrived = true;
            return;
        }

        // --- Rolling local-trajectory refresh ------------------------------
        s.sinceReplan += dt;
        if (needsReplan(body, s)) {
            Pose pose = new Pose(body.x, body.y, body.facingDegrees);
            s.trajectory = LocalTrajectoryPlanner.plan(pose, s.corridor, type, navigation.getGrid());
            s.trajProgress = 0f;
            s.sinceReplan = 0f;
            s.trajCarrotAtEnd = false;
        }

        // A null plan with on-grid route still to solve means the kinematic
        // planner rejected the route ahead. Do not feed that same sharp coarse
        // polyline to the bicycle controller: brake, then ask the macro router
        // for a genuinely different turn-aware corridor. Coarse pursuit remains
        // only for the deliberate off-map entry/exit crossing.
        if (s.trajectory == null && onGridRouteRemains(body, type, s)) {
            Pose here = new Pose(body.x, body.y, body.facingDegrees);
            // Hybrid A*'s soft goal is deliberately wider than the exact LZ
            // snap. If the truck has reached that terminal region and no safe
            // forward motion remains, this is the best footprint-valid landing
            // pose—not a failed bend that should hold the payload forever.
            if (isInbound && LocalTrajectoryPlanner.isInTerminalGoalRegion(
                    here, s.corridor, type)) {
                s.localPlanFailureTime = 0f;
                body.speed = 0f;
                s.arrived = true;
                return;
            }
            // A departing truck can be parked facing back down its own inbound
            // approach: only the docking maneuver lands it on the outbound
            // heading, so one that arrived through the plain distance gate keeps
            // whatever heading it came in on. Forward planning then has nothing
            // to find and never will, because turning a bicycle round needs more
            // lateral room than a road has. Back and fill onto the corridor
            // instead — the same Reeds-Shepp maneuver docking already uses.
            if (!isInbound && tryEngageDepartureTurnaround(body, type, s)) {
                s.localPlanFailureTime = 0f;
                advanceDocking(mission, body, s, dt);
                return;
            }
            s.localPlanFailureTime += dt;
            body.speed = 0f;
            if (!s.localPlanFailureRerouteAttempted
                    && s.localPlanFailureTime >= VehicleController.LOCAL_PLAN_FAILURE_REROUTE_SEC) {
                s.localPlanFailureRerouteAttempted = true;
                attemptReroute(mission, body, type, s);
                s.timeSinceProgress = 0f;
            }
            return;
        }
        if (s.trajectory != null) {
            s.localPlanFailureTime = 0f;
            s.localPlanFailureRerouteAttempted = false;
        }

        // --- Track (trajectory if we have one, else the coarse corridor) ---
        float prevX = body.x, prevY = body.y, prevFacing = body.facingDegrees;

        float[] px, py;
        int startIdx;
        if (s.trajectory != null) {
            px = s.trajectory.xs();
            py = s.trajectory.ys();
            startIdx = 1;   // poses[0] is the plan-time pose; scan forward from 1
        } else {
            px = xs;
            py = ys;
            startIdx = s.corridor.cursor();
        }

        // Speed-scaled look-ahead: pull the carrot toward the body as the truck
        // slows so tight corners track closely; let it stretch back to the cruise
        // look-ahead on straights for smoothness. Uses last tick's speed (the
        // one-tick lag is negligible) and the speed is already curvature-limited
        // below, so approaching a bend naturally tightens tracking.
        float speedFrac = type.maxSpeed > 0f ? Math.min(1f, Math.abs(body.speed) / type.maxSpeed) : 0f;
        float lookAhead = Math.min(type.lookAheadCells,
                VehicleController.MIN_LOOKAHEAD_CELLS + speedFrac * (type.lookAheadCells - VehicleController.MIN_LOOKAHEAD_CELLS));

        PurePursuit.Carrot carrot = PurePursuit.pick(body.x, body.y, px, py, startIdx, lookAhead);
        if (s.trajectory != null) s.trajCarrotAtEnd = carrot.atEnd;

        // --- Proactive turn-feasibility check (kinematic, not timer-based) ---
        // If the carrot sits inside one of the body's two minimum-turn circles,
        // no forward arc can reach it — the bicycle would orbit (the open-space
        // limit cycle that no wall-contact recovery catches). Read straight off
        // the instantaneous pose-vs-carrot geometry and commit to the reverse-to-
        // feasible maneuver the moment the turn is provably impossible, instead of
        // grinding through the oscillation until the stall timer trips. The reverse
        // backs up while aiming the nose at the corridor (a 3-point turn), then
        // replans forward from the roomier pose. On the boxed-in / capped case
        // beginReverseRecovery declines and we fall through to normal tracking
        // (the stall → re-route ladder stays the backstop).
        if (body instanceof BicycleBody
                && VehicleController.turnIsInfeasibleForward(body.x, body.y, body.facingDegrees,
                        carrot.x, carrot.y, ((BicycleBody) body).minTurnRadiusCells())
                && beginReverseRecovery(body, type, s)) {
            return; // committed; next tick drives the backup, then replans forward
        }

        // Forward speed is the min of two caps:
        //  - brake taper to the LZ end, so the truck stops cleanly at the corridor
        //    end regardless of where the local horizon currently ends;
        //  - a curvature governor that slows for the sharpest bend within
        //    CURVE_PREVIEW_CELLS ahead. The bicycle's min turn radius is fixed by
        //    geometry — speed can't tighten it — but entering a corner slow gives
        //    the bounded steering slew time to reach lock and keeps pursuit on the
        //    planned arc, instead of overshooting at cruise and reversing out (the
        //    "tiny reverses at a missed turn" failure).
        float remaining = s.corridor.remainingLength(body.x, body.y);
        float taper = (float) Math.sqrt(2f * type.brakingAccel * Math.max(0f, remaining));
        float curveCap = VehicleController.curvatureSpeedCap(px, py, startIdx, body.x, body.y, type.maxSpeed);
        float targetSpeed = Math.min(Math.min(type.maxSpeed, taper), curveCap);

        float cdx = carrot.x - body.x, cdy = carrot.y - body.y;
        float carrotBearing = AirBody.facingToward(cdx, cdy);
        float alpha = ((carrotBearing - body.facingDegrees + 540f) % 360f) - 180f;
        if (Math.abs(alpha) > 90f) {
            targetSpeed = -targetSpeed * 0.5f;
        }

        body.tick(carrot.x, carrot.y, targetSpeed, dt);
        if (s.trajectory != null) {
            s.trajProgress += (float) Math.hypot(body.x - prevX, body.y - prevY);
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
            wallStuckRecovery(body, type, s, prevX, prevY, prevFacing, dt);
        }
    }

    /**
     * Decide whether to request a fresh local plan this tick: no current plan,
     * the replan interval elapsed, the current plan is consumed (carrot pinned
     * to its end or past the consume fraction), or the body has drifted off the
     * advisory corridor.
     */
    private boolean needsReplan(GroundBody body, VehicleControlComponent s) {
        if (s.trajectory == null) return true;
        if (s.sinceReplan >= VehicleController.REPLAN_INTERVAL_SEC) return true;
        if (s.trajCarrotAtEnd) return true;
        if (s.trajProgress >= s.trajectory.lengthCells() * VehicleController.REPLAN_CONSUMED_FRACTION) return true;
        if (s.corridor.offCorridorDistance(body.x, body.y) > VehicleController.REPLAN_DRIFT_CELLS) return true;
        return false;
    }

    /**
     * True while there is still on-grid route for the local planner to solve, so
     * a null trajectory is a genuine route failure rather than the off-map
     * crossing. Two ways to be on the tail: the footprint has physically left
     * the grid (the inbound spawn leg), or the rolling goal has (every outbound
     * exit, which pins its goal to a waypoint a fixed pad beyond the perimeter
     * long before the body reaches the edge).
     */
    private boolean onGridRouteRemains(GroundBody body, VehicleType type, VehicleControlComponent s) {
        NavigationGrid grid = navigation.getGrid();
        if (!VehicleFootprint.isPoseWithinGrid(body.x, body.y, body.facingDegrees,
                type.visualLengthCells, type.visualWidthCells, grid)) return false;
        return !LocalTrajectoryPlanner.isPlanningIntoOffMapTail(
                new Pose(body.x, body.y, body.facingDegrees), s.corridor, type, grid);
    }

    /**
     * Forward-move feasibility guard + committed-backup trigger. When a forward
     * move carries a previously-feasible body into a wall, revert it; once
     * blocked past {@link VehicleController#WALL_REVERSE_DELAY} seconds, hand off
     * to a <em>committed</em> reverse maneuver ({@link #beginReverseRecovery})
     * instead of pulsing a single reverse tick. The old pulse alternated with the
     * next tick's forward move and cancelled out — the tiny-reverse oscillation. A
     * trajectory is feasible by construction, so this mostly guards the
     * coarse-corridor fallback (where the planner found no forward plan).
     */
    private void wallStuckRecovery(GroundBody body, VehicleType type, VehicleControlComponent s,
                                   float prevX, float prevY, float prevFacing, float dt) {
        NavigationGrid grid = navigation.getGrid();

        // (Net-progress tracking that resets recoveryAttempts now lives at the top
        // of advance(), so it runs every tick — including the open-space orbit
        // where this wall-contact path never fires.)

        boolean prevOnGrid = VehicleFootprint.isPoseFeasible(prevX, prevY, prevFacing,
                type.visualLengthCells, type.visualWidthCells, grid);
        boolean newFeasible = VehicleFootprint.isPoseFeasible(body.x, body.y, body.facingDegrees,
                type.visualLengthCells, type.visualWidthCells, grid);

        if (prevOnGrid && !newFeasible) {
            if (s.wallStuckTime == 0f) {
                s.stuckOriginX = prevX;
                s.stuckOriginY = prevY;
            }
            s.wallStuckTime += dt;
            body.x = prevX;
            body.y = prevY;
            body.facingDegrees = prevFacing;
            body.speed = 0f;
            if (s.wallStuckTime > VehicleController.WALL_REVERSE_DELAY) {
                beginReverseRecovery(body, type, s);
            }
            return;
        }

        if (s.wallStuckTime > 0f) {
            float dx = body.x - s.stuckOriginX;
            float dy = body.y - s.stuckOriginY;
            if (dx * dx + dy * dy > VehicleController.STUCK_ESCAPE_DIST * VehicleController.STUCK_ESCAPE_DIST) {
                s.wallStuckTime = 0f;
            }
        }
    }

    /**
     * Enter a committed reverse maneuver: back up to the achievable distance
     * (bounded by what's actually clear behind — {@link VehicleController#maxReverseDistance})
     * so a fresh forward plan has room to swing the nose around. Skips the maneuver
     * when nothing useful is reachable behind, and after
     * {@link VehicleController#MAX_RECOVERY_ATTEMPTS} fruitless tries holds position
     * rather than thrash (a kinematically impossible corridor — the give-up rung
     * re-routes or deloads in place).
     */
    private boolean beginReverseRecovery(GroundBody body, VehicleType type, VehicleControlComponent s) {
        if (s.recoveryAttempts >= VehicleController.MAX_RECOVERY_ATTEMPTS) return false;
        float achievable = VehicleController.maxReverseDistance(body.x, body.y, body.facingDegrees,
                type, navigation.getGrid());
        s.recoveryAttempts++;
        if (achievable < VehicleController.MIN_USEFUL_REVERSE_CELLS) return false; // boxed in — can't gain room
        s.reverseRemaining = achievable;
        s.recovery = VehicleControlComponent.Recovery.REVERSING;
        s.trajectory = null; // the forward plan is stale; replan after backing up
        return true;
    }

    /**
     * One tick of the committed backup. Reverses while aiming the nose at the
     * corridor ahead (BicycleBody negates steering in reverse, so the nose swings
     * <em>toward</em> the carrot as the body backs away — a 3-point-turn setup),
     * footprint-gated each tick. Stops when the owed distance is consumed or the
     * body backs into something, then forces a forward replan from the new pose.
     */
    private void advanceReverse(GroundBody body, VehicleType type, VehicleControlComponent s, float dt) {
        NavigationGrid grid = navigation.getGrid();

        float horizon = Math.max(VehicleController.MIN_LOOKAHEAD_CELLS, type.lookAheadCells);
        Pose ahead = s.corridor.targetAhead(body.x, body.y, horizon);

        float prevX = body.x, prevY = body.y, prevFacing = body.facingDegrees;
        body.tick(ahead.x, ahead.y, -VehicleController.WALL_REVERSE_SPEED, dt);

        if (!VehicleFootprint.isPoseFeasible(body.x, body.y, body.facingDegrees,
                type.visualLengthCells, type.visualWidthCells, grid)) {
            // Backed into something — this is as far as we get. (The march budget
            // is a straight-line estimate; the steered reverse can curve into a
            // wall the march didn't sample, so the per-tick gate is the backstop.)
            body.x = prevX;
            body.y = prevY;
            body.facingDegrees = prevFacing;
            body.speed = 0f;
            endReverseRecovery(s);
            return;
        }

        s.reverseRemaining -= (float) Math.hypot(body.x - prevX, body.y - prevY);
        if (s.reverseRemaining <= 0f) endReverseRecovery(s);
    }

    /** Exit the reverse maneuver and force a fresh forward plan from the new, roomier pose. */
    private void endReverseRecovery(VehicleControlComponent s) {
        s.recovery = VehicleControlComponent.Recovery.NONE;
        s.trajectory = null;
        s.sinceReplan = VehicleController.REPLAN_INTERVAL_SEC; // replan on the next forward tick
        s.wallStuckTime = 0f;
    }

    /**
     * Recovery rung 3 — "lap around". When the truck has stopped converging,
     * re-route from its current pose to the route's goal via the cost router,
     * dropping an impassable disc on the stuck spot so the search picks a
     * genuinely different corridor. Swaps the new polyline onto the vehicle and
     * rebuilds the corridor. Returns {@code false} (no change) when the vehicle
     * isn't cost-field-routed, the endpoints can't be snapped, or avoiding the
     * stuck spot disconnects the goal (genuinely boxed in → the truck holds; the
     * stall timer retries every {@link VehicleController#STALL_SECONDS} in case the
     * grid opens up).
     */
    private boolean attemptReroute(VehicleMission mission, GroundBody body, VehicleType type,
                                   VehicleControlComponent s) {
        TerrainCostField cost = mission.routeCostField;
        VehicleClearance clr = mission.routeClearance;
        if (cost == null || clr == null) return false; // not cost-routed — can't lap
        boolean inbound = s.lastInbound != null && s.lastInbound;
        float[] xs = inbound ? mission.inboundX : mission.outboundX;
        float[] ys = inbound ? mission.inboundY : mission.outboundY;
        NavigationGrid grid = navigation.getGrid();

        int goalIdx = VehicleController.lastOnGridIndex(xs, ys, grid);
        if (goalIdx < 1) return false;
        int[] goal = VehicleRoutePlanner.snapToMask(clr,
                (int) Math.floor(xs[goalIdx]), (int) Math.floor(ys[goalIdx]), VehicleController.REROUTE_SNAP_RADIUS);
        int[] cur = VehicleRoutePlanner.snapToMask(clr,
                (int) Math.floor(body.x), (int) Math.floor(body.y), VehicleController.REROUTE_SNAP_RADIUS);
        if (goal == null || cur == null) return false;

        // Avoid the failing spot AHEAD on the corridor (the turn / corridor mouth
        // it can't get through) — NOT the body's own cell. In an open-space orbit
        // the body sits on a passable cell, so an under-the-wheels disc would
        // blank the route's own start and the re-route could never fire. Aiming
        // the disc a little past the body keeps the start + its retreat clear.
        Pose ahead = s.corridor.targetAhead(body.x, body.y, VehicleController.REROUTE_AVOID_RADIUS + 1.5f);
        int avoidX = (int) Math.floor(ahead.x);
        int avoidY = (int) Math.floor(ahead.y);
        rememberFailedArea(s, avoidX, avoidY);
        VehicleRoutePlanner.RescueRoute rescue = VehicleRoutePlanner.routeAvoidingForwardFirst(
                cur[0], cur[1], goal[0], goal[1], body.facingDegrees,
                s.rescueFirstStepTriedMask, grid, cost, clr,
                s.rerouteAvoidX, s.rerouteAvoidY, s.rerouteAvoidCount,
                VehicleController.REROUTE_AVOID_RADIUS, type);
        if (rescue == null) return false; // boxed in or every first step already exhausted — hold (rung 4)
        s.rescueFirstStepTriedMask |= 1 << rescue.firstStepDirectionBit();
        float[][] re = rescue.points();

        float[][] full = VehicleController.appendTail(re, xs, ys, goalIdx);
        if (inbound) {
            mission.inboundX = full[0];
            mission.inboundY = full[1];
        } else {
            mission.outboundX = full[0];
            mission.outboundY = full[1];
        }
        initCorridor(s, full[0], full[1], false);
        // Installing a corridor is not physical progress. Seed the new route's
        // baseline here so its first forward tick cannot immediately clear the
        // tried-bearing history before the truck has actually escaped.
        s.recoveryBestRemaining = s.corridor.remainingLength(body.x, body.y);
        return true;
    }

    private static void rememberFailedArea(VehicleControlComponent s, int x, int y) {
        for (int i = 0; i < s.rerouteAvoidCount; i++) {
            int dx = s.rerouteAvoidX[i] - x;
            int dy = s.rerouteAvoidY[i] - y;
            if (dx * dx + dy * dy <= VehicleController.REROUTE_AVOID_RADIUS
                    * VehicleController.REROUTE_AVOID_RADIUS) return;
        }
        if (s.rerouteAvoidCount >= s.rerouteAvoidX.length) return;
        s.rerouteAvoidX[s.rerouteAvoidCount] = x;
        s.rerouteAvoidY[s.rerouteAvoidCount] = y;
        s.rerouteAvoidCount++;
    }

    /**
     * Try to switch the inbound truck from pursuit to a Reeds-Shepp docking
     * maneuver when within {@link VehicleController#DOCKING_TRIGGER_CELLS} of the
     * LZ. The candidate RS path is footprint-checked along its length; if any pose
     * is non-walkable, docking stays off this tick (pursuit then delivers the truck
     * to the LZ via the corridor).
     */
    private void tryEngageDocking(VehicleMission mission, GroundBody body, VehicleType type,
                                  VehicleControlComponent s,
                                  float[] xs, float[] ys) {
        if (!(body instanceof BicycleBody)) return;
        int lastIdx = xs.length - 1;
        float lzX = xs[lastIdx];
        float lzY = ys[lastIdx];
        float distToLz = body.distanceTo(lzX, lzY);
        if (distToLz > VehicleController.DOCKING_TRIGGER_CELLS) return;

        float lzFacingDeg = mission.lzDepartureFacingDeg;

        Pose start = new Pose(body.x, body.y, body.facingDegrees);
        Pose goal = new Pose(lzX, lzY, lzFacingDeg);
        float turnRadius = ((BicycleBody) body).minTurnRadiusCells();
        ReedsShepp.Path path = ReedsShepp.shortest(start, goal, turnRadius);
        if (path == null) return;
        if (!VehicleController.isPathFeasible(start, path, turnRadius, type, navigation.getGrid())) return;

        s.dockingPath = path;
        s.dockingStartPose = start;
        s.dockingTurnRadius = turnRadius;
        s.dockingProgressCells = 0f;
        s.dockingGoalFacingDeg = lzFacingDeg;
        s.trajectory = null;   // docking owns the pose now
    }

    /**
     * Plan a Reeds-Shepp turnaround from the current pose onto the outbound
     * corridor, for a departing vehicle that forward planning cannot move.
     *
     * <p>Reeds-Shepp is what makes this possible at all: it may reverse, and a
     * bicycle that must reverse to turn round is exactly the case a
     * forward-only trajectory search reports as "no route" forever. The goal is
     * a pose a lead distance down the corridor, on the corridor's own heading,
     * so completing the maneuver leaves the truck pointing where tracking wants
     * it and ordinary pursuit takes over on the next tick.
     *
     * @return whether a feasible maneuver was engaged
     */
    private boolean tryEngageDepartureTurnaround(GroundBody body, VehicleType type,
                                                 VehicleControlComponent s) {
        if (!(body instanceof BicycleBody)) return false;
        if (s.turnaroundsUsed >= VehicleController.MAX_DEPARTURE_TURNAROUNDS) return false;

        float turnRadius = ((BicycleBody) body).minTurnRadiusCells();
        Pose start = new Pose(body.x, body.y, body.facingDegrees);
        for (float factor : VehicleController.TURNAROUND_LEAD_FACTORS) {
            Pose goal = s.corridor.targetAhead(body.x, body.y,
                    VehicleController.turnaroundLead(turnRadius, factor));
            ReedsShepp.Path path = ReedsShepp.shortest(start, goal, turnRadius);
            if (path == null) continue;
            if (path.lengthCells(turnRadius) < VehicleController.MIN_TURNAROUND_CELLS) return false;
            if (!VehicleController.isPathFeasible(start, path, turnRadius, type,
                    navigation.getGrid())) continue;

            s.dockingPath = path;
            s.dockingStartPose = start;
            s.dockingTurnRadius = turnRadius;
            s.dockingProgressCells = 0f;
            s.dockingGoalFacingDeg = goal.facingDeg;
            s.dockingIsDeparture = true;
            s.turnaroundsUsed++;
            s.trajectory = null;   // the maneuver owns the pose now
            return true;
        }
        return false;
    }

    /**
     * Advance the docking truck by {@link VehicleController#DOCKING_SPEED} for one
     * tick along its Reeds-Shepp path, set the body's pose from the sampled point,
     * and flag arrival when the path's total length is consumed.
     */
    private void advanceDocking(VehicleMission mission, GroundBody body, VehicleControlComponent s, float dt) {
        s.dockingProgressCells += VehicleController.DOCKING_SPEED * dt;
        float totalCells = s.dockingPath.lengthCells(s.dockingTurnRadius);
        if (s.dockingProgressCells >= totalCells) {
            if (s.dockingIsDeparture) {
                // A turnaround ends wherever the path ends and hands the pose
                // straight back to corridor tracking. Nothing has arrived.
                Pose end = ReedsShepp.sample(s.dockingStartPose, s.dockingTurnRadius,
                        s.dockingPath, totalCells);
                body.x = end.x;
                body.y = end.y;
                body.facingDegrees = end.facingDeg;
                body.speed = 0f;
                s.dockingPath = null;
                s.dockingIsDeparture = false;
                return;
            }
            body.teleport(mission.lzX, mission.lzY, s.dockingGoalFacingDeg);
            s.dockingPath = null;
            s.arrived = true;
            return;
        }
        Pose p = ReedsShepp.sample(s.dockingStartPose, s.dockingTurnRadius,
                s.dockingPath, s.dockingProgressCells);
        body.x = p.x;
        body.y = p.y;
        body.facingDegrees = p.facingDeg;
    }
}
