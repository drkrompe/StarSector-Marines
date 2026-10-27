package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile.Bucket;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.vehicle.components.VehicleControlComponent;
import java.util.function.LongPredicate;

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

    private static final boolean REUSE_FAILED_RECOVERY = Boolean.parseBoolean(
            System.getProperty("battle.vehicle.reuseFailedRecovery", "true"));

    private final ConvoyService convoy;
    private final NavigationService navigation;
    private final boolean progressiveRecovery;
    private final int recoveryExpansionsPerTick;
    /** The dozer blade, or {@code null} where no map editor is wired (bare control tests). */
    private DozerBlade blade;
    /** Hulls that can't act this tick: a Brute's charge stalls them ({@code colony-breach-nouns.md}, Brute). */
    private LongPredicate stalled = id -> false;
    private HoverSkirt skirt;
    /** Heavy-lift hulls' tracks, or {@code null} where no map editor is wired: such a hull then drives the live grid. */
    private HeavyTracks tracks;
    /** Medium and heavy hulls' ramming of light barricades, or {@code null} where it is switched off. */
    private BarricadeRam ram;
    /** Soft ground a move order's hull may brush as its last rung, or {@code null} where it never does. */
    private SoftBrush brush;

    public VehicleControlSystem(ConvoyService convoy, NavigationService navigation) {
        this(convoy, navigation, Boolean.parseBoolean(System.getProperty(
                "battle.vehicle.progressiveRecovery", "true")),
                Math.max(1, Integer.getInteger("battle.vehicle.recoveryExpansionsPerTick", 2048)));
    }

    VehicleControlSystem(ConvoyService convoy, NavigationService navigation,
                         boolean progressiveRecovery, int recoveryExpansionsPerTick) {
        this.convoy = convoy;
        this.navigation = navigation;
        this.progressiveRecovery = progressiveRecovery;
        this.recoveryExpansionsPerTick = Math.max(1, recoveryExpansionsPerTick);
    }

    /** Gives bladed hulls their blade. Without one a dozer drives as the cannonless tank it looks like. */
    public void setBlade(DozerBlade blade) { this.blade = blade; }

    /** Gives heavy-lift hulls their tracks. Without them a tank stops at undergrowth as a lighter hull does. */
    public void setTracks(HeavyTracks tracks) { this.tracks = tracks; }

    /** Lets medium and heavy hulls ram light barricades. Without it they stop at a barricade as a light hull does. */
    public void setRam(BarricadeRam ram) { this.ram = ram; }

    private boolean rams(VehicleType type) { return ram != null && type.ramsBarricades(); }

    /** Lets a move order's last rung brush soft ground ({@link SoftBrush}). Without it the order is given up as before. */
    public void setBrush(SoftBrush brush) { this.brush = brush; }

    /** The soft ground a move order may brush, or {@code null} where none may. */
    SoftBrush brush() { return brush; }

    /** Hulls that hold still this tick, whatever their route says. */
    public void setStall(LongPredicate stalled) { this.stalled = stalled; }

    /**
     * The grid a hull's own planning reads: the blade's breach view for a bladed hull, a ramming hull's breach view
     * ({@link BarricadeRam}), else the grid it moves over.
     */
    NavigationGrid planningGrid(VehicleType type) {
        if (blade != null && type.hasBlade()) return blade.planningGrid();
        return rams(type) ? ram.planningGrid(type) : motionGrid(type);
    }

    /**
     * The grid a hull physically moves over: the skirt's hover view for a hover hull, which really does float over
     * the water it opens ({@link HoverSkirt}); the tracks' crush view for a heavy-lift hull, whose undergrowth gives
     * as it is driven into ({@link HeavyTracks}); else the live grid — a dozer's wall is there until it is pushed
     * down.
     */
    NavigationGrid motionGrid(VehicleType type) {
        if (type.isHover()) {
            if (skirt == null) skirt = new HoverSkirt(navigation);
            return skirt.motionGrid();
        }
        if (tracks != null && type.crushesUndergrowth()) return tracks.motionGrid();
        return navigation.getGrid();
    }

    /**
     * Whether {@code grid} is a planning view that keeps its identity as the map moves: the tracks' crush view or a
     * ramming hull's breach view.
     */
    boolean isKeptView(NavigationGrid grid) {
        return tracks != null && grid == tracks.currentView() || ram != null && ram.isView(grid);
    }

    /** The route cost a hull's own move-order proof pays. */
    TerrainCostField costField(VehicleType type) {
        if (type.isHover()) return HoverSkirt.costField(navigation.getTopology());
        TerrainCostField cost = TerrainCostField.from(navigation.getTopology());
        if (tracks != null && type.crushesUndergrowth()) cost = HeavyTracks.costField(navigation.getGrid(), cost);
        return rams(type) ? ram.costField(type, cost) : cost;
    }

    /**
     * Advance vehicle {@code id} one tick along the route ({@code xs},
     * {@code ys}) it has been handed, driving it as a {@code leg} of the given
     * kind. Handing over a different route, or the same route for a different
     * kind of leg, rebuilds the corridor and clears the rolling plan and
     * maneuver state.
     *
     * <p>The route is a parameter rather than something read off the mission,
     * so a vehicle can be sent somewhere that is not part of a delivery — see
     * {@code vehicle-as-commandable-unit.md}.
     */
    public void tick(long id, float dt, float[] xs, float[] ys, VehicleLeg leg) {
        tick(id, dt, xs, ys, leg, Float.NaN);
    }

    /**
     * {@link #tick(long, float, float[], float[], VehicleLeg)} for a route that
     * must finish on {@code goalFacingDeg}, as a player's dragged move order
     * does. A finite heading lets the terminal Reeds-Shepp docking maneuver
     * engage on any leg, landing the hull on that heading where the ground
     * allows it; {@code NaN} leaves the leg's own docking rule alone. Where no
     * feasible maneuver exists the vehicle arrives on whatever heading it drove
     * in on, as an undragged order does.
     */
    public void tick(long id, float dt, float[] xs, float[] ys, VehicleLeg leg, float goalFacingDeg) {
        VehicleMission mission = convoy.mission(id);
        GroundBody body = convoy.body(id);
        VehicleType type = convoy.vehicleType(id);
        VehicleControlComponent s = convoy.control(id);

        if (s.routeXs != xs || s.routeYs != ys || s.leg != leg) {
            initCorridor(s, xs, ys);
            s.leg = leg;
        }
        if (stalled.test(id)) {
            body.speed = 0f;
            return;
        }

        float previousX = body.x, previousY = body.y, previousFacing = body.facingDegrees;
        advance(mission, body, type, s, xs, ys, dt, leg, goalFacingDeg);
        if (type.hasAnimatedTreads()) {
            body.recordVisualTravel(previousX, previousY, previousFacing);
        }
    }

    /** Clear route/recovery state without changing the chassis pose, speed or wheel angle. */
    public void clearRoute(long id) {
        VehicleControlComponent s = convoy.control(id);
        resetTracking(s, true);
        if (convoy.body(id) instanceof BicycleBody bicycle) bicycle.endPivot();
        s.corridor = null;
        s.routeXs = null;
        s.routeYs = null;
        s.leg = null;
        s.arrived = false;
    }

    /** Manual wheel/throttle input shares bicycle integration and the live whole-body sweep. */
    public void tickManual(long id, float dt, float throttle, float steering) {
        convoy.control(id).failedRecovery = null;
        cancelPending(convoy.control(id));
        GroundBody body = convoy.body(id);
        if (!(body instanceof BicycleBody bicycle)) throw new IllegalStateException("Manual vehicle drive requires a bicycle chassis");
        VehicleType type = convoy.vehicleType(id);
        float prevX = body.x, prevY = body.y, prevFacing = body.facingDegrees;
        bicycle.tickManual(throttle, steering, dt);
        if (dt == 0f) return;
        boolean pushing = throttle > 0f && (bladeWorks(body, type, convoy.control(id), dt)
                || bladeGrinds(body, type, convoy.control(id), dt));
        boolean blocked = applySweep(body, type, prevX, prevY, prevFacing, false) && !pushing;
        if (type.hasAnimatedTreads()) {
            body.recordVisualTravel(prevX, prevY, prevFacing);
        }
        VehicleControlComponent s = convoy.control(id);
        s.wallStuckTime = blocked ? s.wallStuckTime + dt : 0f;
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
        s.routeXs = xs;
        s.routeYs = ys;
        resetTracking(s, clearRescueFirstSteps);
    }

    static void resetTracking(VehicleControlComponent s, boolean clearRescueFirstSteps) {
        s.failedRecovery = null;
        cancelPending(s);
        s.arrived = false;
        s.trajectory = null;
        s.trajProgress = 0f;
        s.sinceReplan = 0f;
        s.trajCarrotAtEnd = false;
        s.localPlanFailureTime = 0f;
        s.localPlanFailureRerouteAttempted = false;
        s.dockingPath = null;
        s.dockingStartPose = null;
        s.dockingProgressCells = 0f;
        s.dockingTurnRadius = 0f;
        s.dockingGoalFacingDeg = 0f;
        s.dockingIsDeparture = false;
        s.turnaroundsUsed = 0;
        s.recovery = VehicleControlComponent.Recovery.NONE;
        s.reverseRemaining = 0f;
        s.recoveryAttempts = 0;
        s.recoveryBestRemaining = Float.MAX_VALUE;
        s.wallStuckTime = 0f;
        s.timeSinceProgress = 0f;
        s.pivotTracking = false;
        s.pivotDirection = 0;
        s.brushing = false;
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
                         VehicleControlComponent s, float[] xs, float[] ys, float dt, VehicleLeg leg,
                         float goalFacingDeg) {
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
            cancelPending(s);
            advanceReverse(body, type, s, dt);
            return;
        }

        // A proof already admitted resumes every tick, not every stall retry.
        // Safe ordinary tracking may continue while it is pending. Changing its
        // start cell, bearing preference, terrain or ownership cancels it.
        boolean recoveryWorked = s.pendingRecovery != null;
        if (s.dockingPath != null) cancelPending(s);
        else if (recoveryWorked && resumeRecovery(mission, body, type, s)) return;

        // --- Non-convergence detection (runs every forward tick, so it catches
        // an open-space orbit as well as wall contact). Progress = corridor
        // remaining-length dropping a margin below the best reached. No progress
        // for STALL_SECONDS → the truck isn't converging (orbiting a turn it
        // can't make): escalate to a re-route that laps around the failing spot.
        float rem = s.corridor.remainingLength(body.x, body.y);
        if (mission.speedCap <= 0f) {
            // Held by its driver (a truck waiting for its escort, a column closing up): standing still on purpose is
            // not failing to converge, and must not spend the recovery ladder until the order is given up.
            s.timeSinceProgress = 0f;
        } else if (rem < s.recoveryBestRemaining - VehicleController.RECOVERY_PROGRESS_MARGIN) {
            s.recoveryBestRemaining = rem;
            s.recoveryAttempts = 0;
            s.timeSinceProgress = 0f;
            s.rescueFirstStepTriedMask = 0;
        } else {
            s.timeSinceProgress += dt;
            if (!recoveryWorked && s.pendingRecovery == null
                    && s.timeSinceProgress > VehicleController.STALL_SECONDS) {
                boolean rerouted = attemptReroute(mission, body, type, s);
                recoveryWorked = true;
                s.timeSinceProgress = 0f; // rate-limit retries whether or not it took
                if (rerouted) return;   // next tick drives the fresh corridor cleanly
            }
        }

        // --- Reeds-Shepp maneuver phase ------------------------------------
        // A running maneuver owns the pose whichever direction it serves: an
        // arrival dock inbound, a departure turnaround outbound.
        if (s.dockingPath != null) { advanceDocking(mission, body, type, s, dt); return; }
        boolean orderedFacing = !Float.isNaN(goalFacingDeg);
        if (leg.docksOnArrival() || orderedFacing) {
            tryEngageDocking(body, type, s, xs, ys,
                    orderedFacing ? goalFacingDeg : mission.lzDepartureFacingDeg);
            if (s.dockingPath != null) { advanceDocking(mission, body, type, s, dt); return; }
        }

        // --- Arrival -------------------------------------------------------
        // Preserve the exact endpoint and tiny snap when docking or ordinary
        // pursuit can actually reach it. A footprint-constrained terminal pose
        // farther out is recognized below only after planning finds no safe
        // forward segment.
        int lastIdx = xs.length - 1;
        float distToLast = body.distanceTo(xs[lastIdx], ys[lastIdx]);
        float threshold = VehicleController.arrivalDist(leg.arrivalFloorCells(), body, dt);
        if (distToLast < threshold) {
            // A brushed pose overlaps soft ground, so a strict snap from it would never move: it arrives as it stands.
            if (leg.snapsToEndpoint() && !s.brushing) {
                Pose from = new Pose(body.x, body.y, body.facingDegrees);
                var step = VehicleTerrainMotion.sweep(from, new Pose(xs[lastIdx], ys[lastIdx], body.facingDegrees),
                        type.visualLengthCells, type.visualWidthCells, motionGrid(type), false);
                applyPose(body, step.pose());
                body.speed = 0f;
                if (step.blocked()) {
                    s.trajectory = null;
                    s.localPlanFailureTime += dt;
                    return;
                }
            }
            if (orderedFacing && pivotToFacing(body, type, s, goalFacingDeg, dt)) return;
            cancelPending(s);
            s.arrived = true;
            return;
        }

        // --- Rolling local-trajectory refresh ------------------------------
        s.sinceReplan += dt;
        if (needsReplan(body, s)) {
            Pose pose = new Pose(body.x, body.y, body.facingDegrees);
            s.trajectory = LocalTrajectoryPlanner.plan(pose, s.corridor, type, planningGrid(type));
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
            if (leg.terminalRegionCountsAsArrival() && LocalTrajectoryPlanner.isInTerminalGoalRegion(
                    here, s.corridor, type)) {
                s.localPlanFailureTime = 0f;
                body.speed = 0f;
                cancelPending(s);
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
            // A hull that turns on the spot has a deeper answer than braking: follow the corridor itself, which
            // route proof drove at the chassis's turning circle wherever one fits and pivots it at the bends where
            // none does, stopping to pivot whenever the corridor turns further than it can steer. Tracks and skirts
            // do so at once, before any turnaround: a dozer set down in a narrow street spent half a minute driving
            // a Reeds-Shepp loop round its junction where it could have turned where it stood. Wheels pivot only once
            // braking, the turnaround and the reroute have all failed.
            boolean pivotsFirst = !s.brushing && NeutralPivot.onRoutes(type);
            if (pivotsFirst && pivotTracks(mission, body, type, s, xs, ys, dt)) return;
            if (leg.mayTurnAroundOntoRoute() && tryEngageDepartureTurnaround(body, type, s)) {
                s.localPlanFailureTime = 0f;
                advanceDocking(mission, body, type, s, dt);
                return;
            }
            boolean pivotDue = !s.brushing && !pivotsFirst && (s.pivotTracking
                    || s.localPlanFailureRerouteAttempted
                    && s.localPlanFailureTime >= NeutralPivot.LAST_RESORT_SECONDS);
            if (pivotDue && pivotTracks(mission, body, type, s, xs, ys, dt)) return;
            s.pivotTracking = false;
            // Every answer from this pose has failed (forward plans, the turnaround, the reroute): back off the way
            // the hull came and plan again from there. Without it a truck nosed into a building stood all battle,
            // since the committed reverse otherwise starts only from a followed trajectory's stall or wall contact.
            if (planlessReverseDue(mission, s) && beginReverseRecovery(body, type, s)) {
                countPlanlessReverse(body, s);
                s.localPlanFailureTime = 0f;
                s.localPlanFailureRerouteAttempted = false;
                return;
            }
            // The last rung of a move order, once every legal answer has failed: brush soft ground at a crawl
            // (SoftBrush). The failure clock holds while the hull brushes on, so an order that cannot brush either is
            // still given up on time.
            boolean brushDue = brush != null && leg == VehicleLeg.MOVE_ORDER && (s.brushing
                    || s.brushRouteXs == xs || s.localPlanFailureTime >= SoftBrush.START_SECONDS);
            if (brushDue && brushTrack(mission, body, type, s, xs, ys, dt)) {
                s.brushing = true;
                return;
            }
            if (s.brushing) endPivotTracking(body, s);
            s.localPlanFailureTime += dt;
            body.speed = 0f;
            if (!recoveryWorked && s.pendingRecovery == null && !s.localPlanFailureRerouteAttempted
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
            if (s.pivotTracking || s.pivotDirection != 0 || s.brushing) endPivotTracking(body, s);
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
        float terrainSpeed = type.maxSpeed * motionGrid(type).groundSpeedMultiplier(body.x, body.y);
        float targetSpeed = Math.min(Math.min(Math.min(terrainSpeed, taper), curveCap), Math.max(0f, mission.speedCap));
        body.speed = Math.copySign(Math.min(Math.abs(body.speed), terrainSpeed), body.speed);

        float cdx = carrot.x - body.x, cdy = carrot.y - body.y;
        float carrotBearing = AirBody.facingToward(cdx, cdy);
        float alpha = ((carrotBearing - body.facingDegrees + 540f) % 360f) - 180f;
        if (Math.abs(alpha) > 90f) {
            targetSpeed = -targetSpeed * 0.5f;
        }

        // A blade bearing on a wall is working, not stuck: pushing it down is this tick's progress.
        boolean pushing = targetSpeed > 0f && bladeWorks(body, type, s, dt);
        if (pushing) s.timeSinceProgress = 0f;
        body.tick(carrot.x, carrot.y, targetSpeed, dt);
        // Nor is a hull its turn has swung into what its blade could push: it grinds it down.
        if (!pushing && targetSpeed > 0f && bladeGrinds(body, type, s, dt)) {
            pushing = true;
            s.timeSinceProgress = 0f;
        }
        // Only delivery entry/exit tails may cross the map bounds; their
        // on-map terrain and edges still receive the same physical sweep.
        boolean deliveryTail = leg != VehicleLeg.MOVE_ORDER && (!VehicleFootprint.isPoseWithinGrid(
                prevX, prevY, prevFacing, type.visualLengthCells, type.visualWidthCells, motionGrid(type))
                || !motionGrid(type).inBounds((int) Math.floor(carrot.x), (int) Math.floor(carrot.y)));
        wallStuckRecovery(body, type, s, prevX, prevY, prevFacing, dt, deliveryTail, pushing);
        if (s.trajectory != null) s.trajProgress += (float) Math.hypot(body.x - prevX, body.y - prevY);

    }

    /**
     * Decide whether to request a fresh local plan this tick: no current plan,
     * the replan interval elapsed, the current plan is consumed (carrot pinned
     * to its end or past the consume fraction), or the body has drifted off the
     * advisory corridor.
     */
    private boolean needsReplan(GroundBody body, VehicleControlComponent s) {
        // Following the corridor by pivot or brushing is a plan of its own; asking the forward planner every tick
        // would only repeat its refusal.
        if (s.trajectory == null) {
            return !s.pivotTracking && !s.brushing || s.sinceReplan >= VehicleController.REPLAN_INTERVAL_SEC;
        }
        if (s.sinceReplan >= VehicleController.REPLAN_INTERVAL_SEC) return true;
        if (s.trajCarrotAtEnd) return true;
        if (s.trajProgress >= s.trajectory.lengthCells() * VehicleController.REPLAN_CONSUMED_FRACTION) return true;
        if (s.corridor.offCorridorDistance(body.x, body.y) > VehicleController.REPLAN_DRIFT_CELLS) return true;
        return false;
    }

    /**
     * One tick of following the corridor without a forward plan, by a hull that turns on the spot
     * ({@link NeutralPivot}). The carrot sits a short way down the corridor: while the hull points within
     * {@link NeutralPivot#ENGAGE_DEG} of it the hull drives at it, and past that it stops and pivots until it points
     * within {@link NeutralPivot#ALIGNED_DEG}, the way that keeps its footprint clear. Every move is swept like any
     * other, so the corridor's proof is a guide and the live ground the authority.
     *
     * <p>{@code brushing}, the sweeps and turns are judged by {@link SoftBrush} and the hull crawls.
     *
     * @return whether the hull moved or turned this tick; {@code false} leaves it to the ordinary brake and reroute
     */
    /** {@link #pivotTrack} as the planless rung: whether the hull follows its corridor by pivot this tick. */
    private boolean pivotTracks(VehicleMission mission, GroundBody body, VehicleType type,
                                VehicleControlComponent s, float[] xs, float[] ys, float dt) {
        if (!pivotTrack(mission, body, type, s, xs, ys, dt, false)) return false;
        s.pivotTracking = true;
        s.localPlanFailureTime = 0f;
        s.localPlanFailureRerouteAttempted = false;
        return true;
    }

    private boolean pivotTrack(VehicleMission mission, GroundBody body, VehicleType type,
                               VehicleControlComponent s, float[] xs, float[] ys, float dt, boolean brushing) {
        float rate = NeutralPivot.rateDegPerSec(type);
        if (rate <= 0f || !(body instanceof BicycleBody bicycle) || mission.speedCap <= 0f) return false;
        PurePursuit.Carrot carrot = PurePursuit.pick(body.x, body.y, xs, ys, s.corridor.cursor(),
                NeutralPivot.LOOKAHEAD_CELLS);
        float dx = carrot.x - body.x, dy = carrot.y - body.y;
        if (dx * dx + dy * dy < 1e-6f) return false;
        float bearing = AirBody.facingToward(dx, dy);
        float error = NeutralPivot.signedDegrees(bearing - body.facingDegrees);
        float threshold = s.pivotDirection != 0 ? NeutralPivot.ALIGNED_DEG : NeutralPivot.ENGAGE_DEG;
        float prevX = body.x, prevY = body.y, prevFacing = body.facingDegrees;
        if (Math.abs(error) > threshold) {
            if (Math.abs(body.speed) > NeutralPivot.STANDSTILL_SPEED) {
                // Stop first: tracks counter-rotate from a standstill.
                body.tick(carrot.x, carrot.y, 0f, dt);
                return !applySweep(body, type, prevX, prevY, prevFacing, false, brushing);
            }
            if (!pivotStep(bicycle, type, s, bearing, rate, dt, brushing)) return false;
            // Turning toward the corridor is progress, even though no ground is covered.
            s.timeSinceProgress = 0f;
            return true;
        }
        if (s.pivotDirection != 0) {
            s.pivotDirection = 0;
            bicycle.endPivot();
        }
        float remaining = s.corridor.remainingLength(body.x, body.y);
        float taper = (float) Math.sqrt(2f * type.brakingAccel * Math.max(0f, remaining));
        float terrainSpeed = type.maxSpeed * motionGrid(type).groundSpeedMultiplier(body.x, body.y);
        float fraction = brushing ? SoftBrush.CRAWL_FRACTION : NeutralPivot.TRACK_SPEED_FRACTION;
        float targetSpeed = Math.min(Math.min(type.maxSpeed * fraction, terrainSpeed),
                Math.min(taper, mission.speedCap));
        body.tick(carrot.x, carrot.y, targetSpeed, dt);
        if (!applySweep(body, type, prevX, prevY, prevFacing, false, brushing)) return true;
        // Driving at a carrot a few degrees off can still clip a bend's corner: square up exactly before driving on.
        s.pivotDirection = clearDirection(body, type, bearing, brushing);
        return false;
    }

    /**
     * One tick of a move order's last rung ({@link SoftBrush}): the hull follows its corridor brushing soft ground. A
     * hull that pivots on its routes follows it as {@link #pivotTrack} does; a wheeled one crawls it on its wheels,
     * steering as it would, since route proof laid the corridor at its turning circle and a wheeled hull spinning on
     * the spot reads worse than one shoving through a hedge.
     *
     * @return whether the hull moved or turned this tick
     */
    private boolean brushTrack(VehicleMission mission, GroundBody body, VehicleType type,
                               VehicleControlComponent s, float[] xs, float[] ys, float dt) {
        if (NeutralPivot.onRoutes(type)) return pivotTrack(mission, body, type, s, xs, ys, dt, true);
        if (mission.speedCap <= 0f) return false;
        PurePursuit.Carrot carrot = PurePursuit.pick(body.x, body.y, xs, ys, s.corridor.cursor(),
                VehicleController.MIN_LOOKAHEAD_CELLS);
        float dx = carrot.x - body.x, dy = carrot.y - body.y;
        if (dx * dx + dy * dy < 1e-6f) return false;
        float error = NeutralPivot.signedDegrees(AirBody.facingToward(dx, dy) - body.facingDegrees);
        if (Math.abs(error) > 90f) return false;
        float remaining = s.corridor.remainingLength(body.x, body.y);
        float taper = (float) Math.sqrt(2f * type.brakingAccel * Math.max(0f, remaining));
        float terrainSpeed = type.maxSpeed * motionGrid(type).groundSpeedMultiplier(body.x, body.y);
        float targetSpeed = Math.min(Math.min(type.maxSpeed * SoftBrush.CRAWL_FRACTION, terrainSpeed),
                Math.min(taper, mission.speedCap));
        float prevX = body.x, prevY = body.y, prevFacing = body.facingDegrees;
        body.tick(carrot.x, carrot.y, targetSpeed, dt);
        return !applySweep(body, type, prevX, prevY, prevFacing, false, true);
    }

    /** {@link NeutralPivot#clearDirection} for the hull as it stands, judged by {@link SoftBrush} when brushing. */
    private int clearDirection(GroundBody body, VehicleType type, float targetDeg, boolean brushing) {
        if (brushing) return brush.clearDirection(body.x, body.y, body.facingDegrees, targetDeg, type, motionGrid(type));
        // A bladed hull turns through what it grinds down, as it drives through it (pivotStep).
        return NeutralPivot.clearDirection(body.x, body.y, body.facingDegrees, targetDeg,
                type.visualLengthCells, type.visualWidthCells, planningGrid(type));
    }

    /**
     * Turn the hull on the spot toward {@code targetDeg} by up to one tick of {@code rate}, the way
     * {@link VehicleControlComponent#pivotDirection} holds or, at the start of a pivot, whichever way its footprint
     * clears the whole turn. Sweeps the turn like any other move.
     *
     * @return whether it turned; {@code false} when no way round is clear, which ends the pivot
     */
    private boolean pivotStep(BicycleBody body, VehicleType type, VehicleControlComponent s,
                              float targetDeg, float rate, float dt, boolean brushing) {
        // A target that has slid past the nose would read as nearly a whole turn the old way: choose afresh.
        if (s.pivotDirection == 0
                || NeutralPivot.positiveDegrees(s.pivotDirection * (targetDeg - body.facingDegrees)) > 180f) {
            s.pivotDirection = clearDirection(body, type, targetDeg, brushing);
            if (s.pivotDirection == 0) return false;
        }
        float prevX = body.x, prevY = body.y, prevFacing = body.facingDegrees;
        float owed = NeutralPivot.positiveDegrees(s.pivotDirection * (targetDeg - body.facingDegrees));
        body.pivot(s.pivotDirection * Math.min(rate * dt, owed), dt);
        if (!brushing && bladeGrinds(body, type, s, dt)) {
            // Swinging into what its blade could push: it holds the turn and grinds it down.
            applyPose(body, new Pose(prevX, prevY, prevFacing));
            return true;
        }
        if (applySweep(body, type, prevX, prevY, prevFacing, false, brushing)) {
            endPivotTracking(body, s);
            return false;
        }
        return true;
    }

    /**
     * At the destination of an order that names a heading, with no docking maneuver to land on it: a hull that turns
     * on the spot as a matter of course turns there, one tick at a time. A wheeled hull arrives on the heading it
     * drove in on; a heading is never worth its last resort.
     *
     * @return whether it is still turning, so arrival waits
     */
    private boolean pivotToFacing(GroundBody body, VehicleType type, VehicleControlComponent s,
                                  float goalFacingDeg, float dt) {
        float rate = NeutralPivot.rateDegPerSec(type);
        if (!NeutralPivot.onRoutes(type) || !(body instanceof BicycleBody bicycle)) return false;
        if (Math.abs(NeutralPivot.signedDegrees(goalFacingDeg - body.facingDegrees)) <= NeutralPivot.ALIGNED_DEG
                || !pivotStep(bicycle, type, s, goalFacingDeg, rate, dt, false)) {
            endPivotTracking(body, s);
            return false;
        }
        s.timeSinceProgress = 0f;
        return true;
    }

    /** Hand the hull back from following its corridor by pivot, or brushing, to the rolling forward plan. */
    private static void endPivotTracking(GroundBody body, VehicleControlComponent s) {
        s.pivotTracking = false;
        s.pivotDirection = 0;
        s.brushing = false;
        if (body instanceof BicycleBody bicycle) bicycle.endPivot();
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
        NavigationGrid grid = planningGrid(type);
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
                                   float prevX, float prevY, float prevFacing, float dt, boolean allowOffMap,
                                   boolean pushing) {

        // (Net-progress tracking that resets recoveryAttempts now lives at the top
        // of advance(), so it runs every tick — including the open-space orbit
        // where this wall-contact path never fires.)

        if (applySweep(body, type, prevX, prevY, prevFacing, allowOffMap)) {
            if (pushing) {
                s.wallStuckTime = 0f;
                return;
            }
            if (s.wallStuckTime == 0f) {
                s.stuckOriginX = prevX;
                s.stuckOriginY = prevY;
            }
            s.wallStuckTime += dt;
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
     * Whether a hull with no plan from where it stands should back off and replan: it is meant to be moving, its
     * reroute has been tried and is not still searching, and it has held planless for
     * {@link VehicleController#PLANLESS_REVERSE_SECONDS}. {@link #beginReverseRecovery} still declines when nothing
     * is clear behind, or after {@link VehicleController#MAX_RECOVERY_ATTEMPTS} tries without progress.
     */
    /**
     * Counts a planless back-off: the count starts again where a hull has got {@link VehicleController#PLANLESS_CLEAR}
     * cells clear of where the last run of them began, so only back-offs that get it nowhere add up.
     */
    static void countPlanlessReverse(GroundBody body, VehicleControlComponent s) {
        if (s.planlessReverses == 0 || Math.hypot(body.x - s.planlessAnchorX, body.y - s.planlessAnchorY)
                > VehicleController.PLANLESS_CLEAR) {
            s.planlessReverses = 0;
            s.planlessAnchorX = body.x;
            s.planlessAnchorY = body.y;
        }
        s.planlessReverses++;
    }

    static boolean planlessReverseDue(VehicleMission mission, VehicleControlComponent s) {
        return mission.speedCap > 0f && s.localPlanFailureRerouteAttempted && s.pendingRecovery == null
                && s.localPlanFailureTime >= VehicleController.PLANLESS_REVERSE_SECONDS;
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
                type, motionGrid(type));
        s.recoveryAttempts++;
        if (achievable < VehicleController.MIN_USEFUL_REVERSE_CELLS) return false; // boxed in — can't gain room
        cancelPending(s);
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
        NavigationGrid grid = motionGrid(type);

        float horizon = Math.max(VehicleController.MIN_LOOKAHEAD_CELLS, type.lookAheadCells);
        Pose ahead = s.corridor.targetAhead(body.x, body.y, horizon);

        float prevX = body.x, prevY = body.y, prevFacing = body.facingDegrees;
        body.tick(ahead.x, ahead.y, -VehicleController.WALL_REVERSE_SPEED
                * grid.groundSpeedMultiplier(body.x, body.y), dt);

        if (applySweep(body, type, prevX, prevY, prevFacing, false)) {
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
     * isn't cost-field-routed, the endpoints can't be snapped, or the
     * bounded rescue search finds no route. The truck holds and the stall timer
     * retries every {@link VehicleController#STALL_SECONDS}. Frozen-world rescue
     * reuses an identical failed request until its inputs change; only the legacy
     * live-grid branch can benefit directly from newly opened ground.
     */
    private boolean attemptReroute(VehicleMission mission, GroundBody body, VehicleType type,
                                   VehicleControlComponent s) {
        long started = VehicleWorkProfile.start();
        try {
            return attemptRerouteInner(mission, body, type, s);
        } finally {
            VehicleWorkProfile.finish(Bucket.VEHICLE_RECOVERY_SEARCH, started);
        }
    }

    private boolean attemptRerouteInner(VehicleMission mission, GroundBody body, VehicleType type,
                                        VehicleControlComponent s) {
        TerrainCostField cost = mission.routeCostField;
        VehicleClearance clr = mission.routeClearance;
        ProgressiveVehicleField fields = mission.routeFields;
        if (fields == null && (cost == null || clr == null)) return false;
        float[] xs = s.routeXs;
        float[] ys = s.routeYs;
        if (xs == null || ys == null) return false;
        NavigationGrid grid = fields != null ? fields.grid() : planningGrid(type);

        int goalIdx = VehicleController.lastOnGridIndex(xs, ys, grid);
        if (goalIdx < 1) return false;
        int[] goal = fields != null
                ? VehicleRoutePlanner.snapToMaskOnDemand(fields, fields.width(), fields.height(),
                (int) Math.floor(xs[goalIdx]), (int) Math.floor(ys[goalIdx]),
                VehicleController.REROUTE_SNAP_RADIUS)
                : VehicleRoutePlanner.snapToMask(clr,
                (int) Math.floor(xs[goalIdx]), (int) Math.floor(ys[goalIdx]),
                VehicleController.REROUTE_SNAP_RADIUS);
        int[] cur = fields != null
                ? VehicleRoutePlanner.snapToMaskOnDemand(fields, fields.width(), fields.height(),
                (int) Math.floor(body.x), (int) Math.floor(body.y),
                VehicleController.REROUTE_SNAP_RADIUS)
                : VehicleRoutePlanner.snapToMask(clr,
                (int) Math.floor(body.x), (int) Math.floor(body.y),
                VehicleController.REROUTE_SNAP_RADIUS);
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
        if (fields != null && progressiveRecovery) {
            if (REUSE_FAILED_RECOVERY && FailedVehicleRecovery.cacheable(body.facingDegrees,
                    VehicleController.REROUTE_AVOID_RADIUS) && s.failedRecovery != null
                    && s.failedRecovery.matches(s, fields, type, cur[0], cur[1], goal[0], goal[1],
                    body.facingDegrees, VehicleController.REROUTE_AVOID_RADIUS)) {
                VehicleWorkProfile.count(Bucket.VEHICLE_RECOVERY_FAILED_REUSE, 1);
                return false;
            }
            s.failedRecovery = null;
            s.pendingRecovery = new ProgressiveVehicleRecovery(s, mission, body, type,
                    navigation.getGrid(), fields, cur[0], cur[1], goal[0], goal[1],
                    VehicleController.REROUTE_AVOID_RADIUS);
            return advanceRecovery(mission, body, s);
        }
        VehicleRoutePlanner.RescueRoute rescue = fields != null
                ? frozenRescue(s, fields, type, cur[0], cur[1], goal[0], goal[1],
                body.facingDegrees, VehicleController.REROUTE_AVOID_RADIUS, REUSE_FAILED_RECOVERY)
                : VehicleRoutePlanner.routeAvoidingForwardFirst(
                cur[0], cur[1], goal[0], goal[1], body.facingDegrees,
                s.rescueFirstStepTriedMask, grid, cost, clr,
                s.rerouteAvoidX, s.rerouteAvoidY, s.rerouteAvoidCount,
                VehicleController.REROUTE_AVOID_RADIUS, type);
        if (rescue == null) return false; // boxed in or every first step already exhausted — hold (rung 4)
        return installRescue(mission, body, s, rescue, goalIdx);
    }

    private boolean installRescue(VehicleMission mission, GroundBody body, VehicleControlComponent s,
                                  VehicleRoutePlanner.RescueRoute rescue, int goalIdx) {
        float[] xs = s.routeXs;
        float[] ys = s.routeYs;
        s.rescueFirstStepTriedMask |= 1 << rescue.firstStepDirectionBit();
        float[][] re = rescue.points();

        float[][] full = VehicleController.appendTail(re, xs, ys, goalIdx);
        // Publish the replacement wherever the route came from, so the caller
        // keeps handing back the same arrays and the corridor is not rebuilt
        // twice. A route that belongs to neither delivery leg — a player move
        // order — simply has nothing to publish to.
        if (xs == mission.inboundX) {
            mission.inboundX = full[0];
            mission.inboundY = full[1];
        } else if (xs == mission.outboundX) {
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

    private boolean resumeRecovery(VehicleMission mission, GroundBody body, VehicleType type,
                                   VehicleControlComponent s) {
        if (!s.pendingRecovery.matches(s, mission, body, type, navigation.getGrid())) {
            cancelPending(s);
            return false;
        }
        long started = VehicleWorkProfile.start();
        try {
            return advanceRecovery(mission, body, s);
        } finally {
            VehicleWorkProfile.finish(Bucket.VEHICLE_RECOVERY_SEARCH, started);
        }
    }

    private boolean advanceRecovery(VehicleMission mission, GroundBody body, VehicleControlComponent s) {
        ProgressiveVehicleRecovery job = s.pendingRecovery;
        job.advance(recoveryExpansionsPerTick);
        if (!job.complete()) {
            VehicleWorkProfile.count(Bucket.VEHICLE_RECOVERY_PENDING, 1);
            return false;
        }
        s.pendingRecovery = null;
        VehicleRoutePlanner.RescueRoute rescue = job.route();
        if (rescue == null) {
            VehicleWorkProfile.count(Bucket.VEHICLE_RECOVERY_FAILED_RESULT, 1);
            if (REUSE_FAILED_RECOVERY && FailedVehicleRecovery.cacheable(body.facingDegrees,
                    VehicleController.REROUTE_AVOID_RADIUS)) s.failedRecovery = job.failedKey();
            return false;
        }
        return installRescue(mission, body, s, rescue, job.goalIndex(s.routeXs, s.routeYs));
    }

    private static void cancelPending(VehicleControlComponent s) {
        if (s.pendingRecovery == null) return;
        s.pendingRecovery = null;
        VehicleWorkProfile.count(Bucket.VEHICLE_RECOVERY_CANCEL, 1);
    }

    /** Only committed frozen route fields qualify; the eager/live-grid branch must always retry. */
    static VehicleRoutePlanner.RescueRoute frozenRescue(
            VehicleControlComponent s, ProgressiveVehicleField fields, VehicleType type,
            int startX, int startY, int goalX, int goalY, float facing, float radius,
            boolean reuseFailed) {
        boolean cacheable = reuseFailed && FailedVehicleRecovery.cacheable(facing, radius);
        if (cacheable && s.failedRecovery != null
                && s.failedRecovery.matches(s, fields, type, startX, startY, goalX, goalY, facing, radius)) {
            VehicleWorkProfile.count(Bucket.VEHICLE_RECOVERY_FAILED_REUSE, 1);
            return null;
        }
        s.failedRecovery = null;
        VehicleRoutePlanner.RescueRoute rescue = VehicleRoutePlanner.routeAvoidingForwardFirstOnDemand(
                startX, startY, goalX, goalY, facing, s.rescueFirstStepTriedMask,
                fields.grid(), fields, fields, s.rerouteAvoidX, s.rerouteAvoidY,
                s.rerouteAvoidCount, radius, type);
        if (rescue == null) {
            VehicleWorkProfile.count(Bucket.VEHICLE_RECOVERY_FAILED_RESULT, 1);
            if (cacheable) s.failedRecovery = new FailedVehicleRecovery(
                    s, fields, type, startX, startY, goalX, goalY, facing, radius);
        }
        return rescue;
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
     * maneuver onto {@code goalFacingDeg} when within
     * {@link VehicleController#DOCKING_TRIGGER_CELLS} of the LZ (or of an ordered
     * destination that names its heading). The candidate RS path is footprint-checked along its length; if any pose
     * is non-walkable, docking stays off this tick (pursuit then delivers the truck
     * to the LZ via the corridor).
     */
    private void tryEngageDocking(GroundBody body, VehicleType type,
                                  VehicleControlComponent s,
                                  float[] xs, float[] ys, float goalFacingDeg) {
        long started = VehicleWorkProfile.start();
        try {
            tryEngageDockingInner(body, type, s, xs, ys, goalFacingDeg);
        } finally {
            VehicleWorkProfile.finish(Bucket.VEHICLE_DOCKING_PROBE, started);
        }
    }

    private void tryEngageDockingInner(GroundBody body, VehicleType type,
                                      VehicleControlComponent s, float[] xs, float[] ys,
                                      float lzFacingDeg) {
        if (!(body instanceof BicycleBody)) return;
        int lastIdx = xs.length - 1;
        float lzX = xs[lastIdx];
        float lzY = ys[lastIdx];
        float distToLz = body.distanceTo(lzX, lzY);
        if (distToLz > VehicleController.DOCKING_TRIGGER_CELLS) return;

        Pose start = new Pose(body.x, body.y, body.facingDegrees);
        Pose goal = new Pose(lzX, lzY, lzFacingDeg);
        float turnRadius = ((BicycleBody) body).minTurnRadiusCells();
        ReedsShepp.Path path = ReedsShepp.shortest(start, goal, turnRadius);
        if (path == null) return;
        if (!VehicleController.isPathFeasible(start, path, turnRadius, type, motionGrid(type))) return;

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
        long started = VehicleWorkProfile.start();
        try {
            return tryEngageDepartureTurnaroundInner(body, type, s);
        } finally {
            VehicleWorkProfile.finish(Bucket.VEHICLE_TURNAROUND_PROBE, started);
        }
    }

    private boolean tryEngageDepartureTurnaroundInner(GroundBody body, VehicleType type,
                                                      VehicleControlComponent s) {
        if (!(body instanceof BicycleBody)) return false;
        if (s.turnaroundsUsed >= VehicleController.MAX_DEPARTURE_TURNAROUNDS) return false;

        float turnRadius = ((BicycleBody) body).minTurnRadiusCells();
        Pose start = new Pose(body.x, body.y, body.facingDegrees);
        VehicleController.Turnaround turn = VehicleController.turnaround(start, s.corridor, turnRadius, type,
                motionGrid(type));
        if (turn == null || turn.aligned(turnRadius)) return false;

        s.dockingPath = turn.path();
        s.dockingStartPose = start;
        s.dockingTurnRadius = turnRadius;
        s.dockingProgressCells = 0f;
        s.dockingGoalFacingDeg = turn.goal().facingDeg;
        s.dockingIsDeparture = true;
        s.turnaroundsUsed++;
        s.trajectory = null;   // the maneuver owns the pose now
        return true;
    }

    /**
     * Advance the docking truck by {@link VehicleController#DOCKING_SPEED} for one
     * tick along its Reeds-Shepp path, set the body's pose from the sampled point,
     * and flag arrival when the path's total length is consumed.
     */
    private void advanceDocking(VehicleMission mission, GroundBody body, VehicleType type,
                                VehicleControlComponent s, float dt) {
        cancelPending(s);
        float total = s.dockingPath.lengthCells(s.dockingTurnRadius);
        float next = Math.min(total, s.dockingProgressCells + VehicleController.DOCKING_SPEED * dt
                * motionGrid(type).groundSpeedMultiplier(body.x, body.y));
        var step = VehicleTerrainMotion.sweepReedsShepp(s.dockingStartPose, s.dockingPath,
                s.dockingTurnRadius, s.dockingProgressCells, next,
                type.visualLengthCells, type.visualWidthCells, motionGrid(type));
        applyPose(body, step.pose());
        s.dockingProgressCells += (next - s.dockingProgressCells) * step.fraction();
        if (step.blocked()) {
            body.speed = 0f;
            s.dockingPath = null;
            s.dockingIsDeparture = false;
            s.trajectory = null;
            s.sinceReplan = VehicleController.REPLAN_INTERVAL_SEC;
            s.wallStuckTime += dt;
            return;
        }
        if (next >= total) {
            body.speed = 0f;
            boolean departing = s.dockingIsDeparture;
            s.dockingPath = null;
            s.dockingIsDeparture = false;
            // The RS endpoint was physically reached; no unchecked final snap.
            if (!departing) s.arrived = true;
        }
    }

    private boolean bladeWorks(GroundBody body, VehicleType type, VehicleControlComponent s, float dt) {
        return blade != null && type.hasBlade() && blade.push(body, type, s, dt);
    }

    /**
     * Whether a bladed hull, at the pose its move would reach, is held only by ground it grinds down
     * ({@link DozerBlade#grind}), or a ramming hull only by barricades it shoves at ({@link BarricadeRam#shove}).
     */
    private boolean bladeGrinds(GroundBody body, VehicleType type, VehicleControlComponent s, float dt) {
        if (rams(type)) return ram.shove(body, type, s, dt);
        return blade != null && type.hasBlade() && blade.grind(body, type, s, dt);
    }

    private boolean applySweep(GroundBody body, VehicleType type, float x, float y, float facing,
                               boolean allowOffMap) {
        return applySweep(body, type, x, y, facing, allowOffMap, false);
    }

    /** {@code brushing}, the move is swept by {@link SoftBrush} rather than held to the ground strictly. */
    private boolean applySweep(GroundBody body, VehicleType type, float x, float y, float facing,
                               boolean allowOffMap, boolean brushing) {
        Pose from = new Pose(x, y, facing), to = new Pose(body.x, body.y, body.facingDegrees);
        // A ramming hull spends its way on the barricade it drives into before the sweep: what falls is open ground.
        if (rams(type)) ram.strike(body, type);
        var step = brushing ? brush.sweep(from, to, type, motionGrid(type), allowOffMap)
                : VehicleTerrainMotion.sweep(from, to, type.visualLengthCells, type.visualWidthCells,
                        motionGrid(type), allowOffMap);
        applyPose(body, step.pose());
        if (step.blocked()) {
            body.recordBlocked(body.speed);
            body.speed = 0f;
        }
        if (tracks != null && type.crushesUndergrowth()) tracks.crush(body, type);
        return step.blocked();
    }

    private static void applyPose(GroundBody body, Pose pose) {
        body.x = pose.x;
        body.y = pose.y;
        body.facingDegrees = pose.facingDeg;
    }
}
