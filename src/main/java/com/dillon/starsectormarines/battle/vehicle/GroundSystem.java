package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.unit.FactionUnitRoster;
import com.dillon.starsectormarines.battle.unit.UnitRegistry;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.turret.TurretAim;
import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.turret.TurretFireSink;
import com.dillon.starsectormarines.battle.turret.TurretKind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Owns every ground vehicle in the battle and drives them each tick.
 * Handles convoy trucks (arrive, deboard, depart) and armored vehicles
 * like the APC (arrive, deboard, stay in overwatch with turret active).
 *
 * <p>Mirrors {@link com.dillon.starsectormarines.battle.air.AirSystem}'s
 * shape — constructor-injected services, per-tick state-machine pass, BFS
 * deboard scan, turret aim/fire loop. Kinematics differ: ground vehicles
 * use the {@link GroundBody} abstraction (currently {@link BicycleBody},
 * future tank/etc) driven by a pure-pursuit carrot along the polyline,
 * instead of the shuttle's "rotate-then-thrust" hover model.
 */
public class GroundSystem {

    /** Distance threshold (cells) for landing on the LZ — final waypoint. Tight so the snap-to-LZ at LANDED is invisible. */
    private static final float LZ_ARRIVAL_DIST = 0.25f;
    /** Distance threshold (cells) at which a DEPARTING vehicle hits its final exit waypoint and transitions to GONE. */
    private static final float EXIT_ARRIVAL_DIST = 1.0f;
    /** Max BFS radius from the LZ when looking for a free deboard cell. Past this we drop the deboard for this tick and retry. */
    private static final int DEBOARD_SCAN_RADIUS = 5;
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

    private final NavigationService navigation;
    private final UnitRosterService roster;
    private final UnitRegistry registry;
    private final com.dillon.starsectormarines.battle.decision.TacticalScoring tacticalScoring;
    private final TurretFireSink fireSink;
    private final Random rng;
    private final Consumer<Unit> addUnitSink;

    private final List<Vehicle> vehicles = new ArrayList<>();

    public GroundSystem(NavigationService navigation, UnitRosterService roster,
                        com.dillon.starsectormarines.battle.decision.TacticalScoring tacticalScoring,
                        TurretFireSink fireSink, Random rng, Consumer<Unit> addUnitSink) {
        this.navigation = navigation;
        this.roster = roster;
        this.registry = roster.getRegistry();
        this.tacticalScoring = tacticalScoring;
        this.fireSink = fireSink;
        this.rng = rng;
        this.addUnitSink = addUnitSink;
    }

    public List<Vehicle> getVehicles() { return vehicles; }
    public void add(Vehicle v) { vehicles.add(v); }

    /**
     * Advances every ground vehicle one tick by {@code dt} seconds. Same
     * fixed-tick contract as {@link com.dillon.starsectormarines.battle.air.AirSystem#tick}
     * — caller is responsible for matching {@code dt} to its tick cadence.
     */
    public void tick(float dt) {
        for (Vehicle v : vehicles) {
            switch (v.state) {
                case PENDING:
                    v.pendingDelay -= dt;
                    if (v.pendingDelay <= 0f) v.state = Vehicle.State.INCOMING;
                    break;

                case INCOMING:
                    advancePath(v, v.inboundX, v.inboundY, dt, true);
                    break;

                case LANDED:
                    v.deboardCountdown -= dt;
                    if (v.deboardCountdown <= 0f && v.marinesRemaining > 0) {
                        if (tryDeboardMarine(v)) {
                            v.marinesRemaining--;
                        }
                        v.deboardCountdown = v.type.deboardInterval;
                    }
                    if (v.marinesRemaining == 0) {
                        if (v.type.departsAfterDeboard) {
                            v.waypointIndex = 1;
                            v.playbackProgress = 0f;
                            v.state = Vehicle.State.DEPARTING;
                        } else {
                            v.overwatchCountdown = v.type.overwatchDurationSec;
                            v.state = Vehicle.State.OVERWATCH;
                        }
                    }
                    break;

                case OVERWATCH:
                    v.overwatchCountdown -= dt;
                    if (v.overwatchCountdown <= 0f) {
                        v.waypointIndex = 1;
                        v.playbackProgress = 0f;
                        v.state = Vehicle.State.DEPARTING;
                    }
                    break;

                case DEPARTING:
                    advancePath(v, v.outboundX, v.outboundY, dt, false);
                    break;

                case GONE:
                default:
                    break;
            }
            if (v.isVisible()) {
                v.recordTick();
            }
        }
        tickVehicleTurrets(dt);
    }

    /**
     * Path follower. Three modes:
     * <ol>
     *   <li><b>Direct pose playback</b> — when the path has heading data
     *       from {@link HybridAStarPlanner}, play the planned poses
     *       directly: accumulate distance, interpolate (x, y, heading)
     *       along the polyline, assign to body. No steering law, no
     *       reactive wall recovery — the plan IS the path.</li>
     *   <li><b>Pure pursuit</b> (coarse polylines without headings) — pick
     *       a carrot along the polyline at {@code lookAheadCells}, derive
     *       target speed from remaining path length, tick the body. Wall
     *       collision recovery + stuck re-plan for deviations.</li>
     *   <li><b>Reeds-Shepp docking</b> — for inbound PurePursuit paths
     *       within {@link #DOCKING_TRIGGER_CELLS} of the LZ, switch to RS
     *       pose playback. Not needed for Hybrid A* paths since they
     *       already terminate at the LZ with correct heading.</li>
     * </ol>
     */
    private void advancePath(Vehicle v, float[] xs, float[] ys, float dt, boolean isInbound) {
        float[] headings = isInbound ? v.inboundHeading : v.outboundHeading;
        if (headings != null) {
            advancePlayback(v, xs, ys, headings, dt, isInbound);
            return;
        }

        if (v.dockingPath != null) {
            advanceDocking(v, dt);
            return;
        }

        if (isInbound) {
            tryEngageDocking(v, xs, ys);
            if (v.dockingPath != null) {
                advanceDocking(v, dt);
                return;
            }
        }

        float prevX = v.body.x, prevY = v.body.y;
        float prevFacing = v.body.facingDegrees;

        PurePursuit.Carrot carrot = PurePursuit.pick(
                v.body.x, v.body.y, xs, ys, v.waypointIndex, v.type.lookAheadCells);
        v.waypointIndex = carrot.nextIdx;

        float remaining = PurePursuit.remainingPathLength(
                v.body.x, v.body.y, xs, ys, v.waypointIndex);
        float taper = (float) Math.sqrt(2f * v.type.brakingAccel * Math.max(0f, remaining));
        float targetSpeed = Math.min(v.type.maxSpeed, taper);

        float cdx = carrot.x - v.body.x, cdy = carrot.y - v.body.y;
        float carrotBearing = com.dillon.starsectormarines.battle.air.AirBody.facingToward(cdx, cdy);
        float alpha = ((carrotBearing - v.body.facingDegrees + 540f) % 360f) - 180f;
        if (Math.abs(alpha) > 90f) {
            targetSpeed = -targetSpeed * 0.5f;
        }

        v.body.tick(carrot.x, carrot.y, targetSpeed, dt);

        NavigationGrid grid = navigation.getGrid();
        boolean prevOnGrid = VehicleFootprint.isPoseFeasible(prevX, prevY, prevFacing,
                v.type.visualLengthCells, v.type.visualWidthCells, grid);
        boolean newFeasible = VehicleFootprint.isPoseFeasible(v.body.x, v.body.y, v.body.facingDegrees,
                v.type.visualLengthCells, v.type.visualWidthCells, grid);
        if (prevOnGrid && !newFeasible) {
            if (v.wallStuckTime == 0f) {
                v.stuckOriginX = prevX;
                v.stuckOriginY = prevY;
            }
            v.wallStuckTime += dt;
            if (v.wallStuckTime > REPLAN_STUCK_THRESHOLD
                    && v.wallStuckTime - v.lastReplanAtStuckTime >= REPLAN_COOLDOWN) {
                v.body.x = prevX;
                v.body.y = prevY;
                v.body.facingDegrees = prevFacing;
                v.body.speed = 0f;
                v.lastReplanAtStuckTime = v.wallStuckTime;
                if (tryReplan(v, xs, ys, isInbound)) {
                    v.wallStuckTime = 0f;
                    v.lastReplanAtStuckTime = -1f;
                    return;
                }
            }
            if (v.wallStuckTime > WALL_REVERSE_DELAY) {
                v.body.x = prevX;
                v.body.y = prevY;
                v.body.facingDegrees = prevFacing;
                v.body.speed = 0f;
                v.body.tick(carrot.x, carrot.y, -WALL_REVERSE_SPEED, dt);
                if (!VehicleFootprint.isPoseFeasible(v.body.x, v.body.y, v.body.facingDegrees,
                        v.type.visualLengthCells, v.type.visualWidthCells, grid)) {
                    v.body.x = prevX;
                    v.body.y = prevY;
                    v.body.facingDegrees = prevFacing;
                    v.body.speed = 0f;
                }
            } else {
                v.body.x = prevX;
                v.body.y = prevY;
                v.body.facingDegrees = prevFacing;
                v.body.speed = 0f;
            }
        } else if (v.wallStuckTime > 0f) {
            float dx = v.body.x - v.stuckOriginX;
            float dy = v.body.y - v.stuckOriginY;
            if ( dx * dx + dy * dy > STUCK_ESCAPE_DIST * STUCK_ESCAPE_DIST) {
                v.wallStuckTime = 0f;
            }
        }

        int lastIdx = xs.length - 1;
        float distToLast = v.body.distanceTo(xs[lastIdx], ys[lastIdx]);
        float threshold = isInbound ? LZ_ARRIVAL_DIST : EXIT_ARRIVAL_DIST;
        if (distToLast < threshold) {
            if (isInbound) {
                v.body.teleport(xs[lastIdx], ys[lastIdx], v.body.facingDegrees);
                v.state = Vehicle.State.LANDED;
                v.deboardCountdown = v.type.deboardInterval;
            } else {
                v.state = Vehicle.State.GONE;
            }
        }
    }

    /**
     * Direct pose playback along a Hybrid A* refined path. Same pattern as
     * {@link #advanceDocking} but for the full path: accumulate distance,
     * interpolate (x, y, heading) between waypoints, assign to body. No
     * steering law, no reactive collision — the plan was validated at
     * planning time and the grid is static.
     */
    private void advancePlayback(Vehicle v, float[] xs, float[] ys,
                                 float[] headings, float dt, boolean isInbound) {
        float totalLength = polylineLength(xs, ys);
        float remaining = totalLength - v.playbackProgress;
        float taper = (float) Math.sqrt(2f * v.type.brakingAccel * Math.max(0f, remaining));
        float speed = Math.min(v.type.maxSpeed, taper);
        v.playbackProgress += speed * dt;

        if (v.playbackProgress >= totalLength) {
            int last = xs.length - 1;
            if (isInbound) {
                v.body.teleport(xs[last], ys[last], headings[last]);
                v.state = Vehicle.State.LANDED;
                v.deboardCountdown = v.type.deboardInterval;
            } else {
                v.state = Vehicle.State.GONE;
            }
            return;
        }

        float walked = 0f;
        for (int i = 0; i < xs.length - 1; i++) {
            float dx = xs[i + 1] - xs[i];
            float dy = ys[i + 1] - ys[i];
            float segLen = (float) Math.sqrt(dx * dx + dy * dy);
            if (walked + segLen >= v.playbackProgress) {
                float t = (segLen > 1e-6f) ? (v.playbackProgress - walked) / segLen : 0f;
                v.body.x = xs[i] + dx * t;
                v.body.y = ys[i] + dy * t;
                float dh = ((headings[i + 1] - headings[i] + 540f) % 360f) - 180f;
                v.body.facingDegrees = headings[i] + dh * t;
                v.body.speed = speed;
                return;
            }
            walked += segLen;
        }

        int last = xs.length - 1;
        v.body.x = xs[last];
        v.body.y = ys[last];
        v.body.facingDegrees = headings[last];
        v.body.speed = 0f;
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
     * Re-plan from the vehicle's current pose to its remaining goal when
     * the reactive recovery has failed for {@link #REPLAN_STUCK_THRESHOLD}
     * seconds. Runs Hybrid A* once; if it finds a feasible path, replaces
     * the vehicle's waypoint arrays and resets the waypoint cursor.
     */
    private boolean tryReplan(Vehicle v, float[] xs, float[] ys, boolean isInbound) {
        int lastOnGrid = xs.length - 1;
        if (xs[lastOnGrid] < 0 || ys[lastOnGrid] < 0) lastOnGrid--;
        if (lastOnGrid < 1) return false;

        float goalX = xs[lastOnGrid], goalY = ys[lastOnGrid];
        float goalFacing;
        if (lastOnGrid >= 1) {
            goalFacing = AirBody.facingToward(
                    xs[lastOnGrid] - xs[lastOnGrid - 1],
                    ys[lastOnGrid] - ys[lastOnGrid - 1]);
        } else {
            goalFacing = v.body.facingDegrees;
        }

        float[] guideX = new float[]{v.body.x, goalX};
        float[] guideY = new float[]{v.body.y, goalY};
        float[][] refined = HybridAStarPlanner.refine(
                guideX, guideY, v.body.facingDegrees, goalFacing,
                v.type, navigation.getGrid());
        if (refined == null) return false;

        if (isInbound) {
            v.inboundX = refined[0];
            v.inboundY = refined[1];
            v.inboundHeading = refined[2];
        } else {
            v.outboundX = refined[0];
            v.outboundY = refined[1];
            v.outboundHeading = refined[2];
        }
        v.waypointIndex = 1;
        v.playbackProgress = 0f;
        v.dockingPath = null;
        return true;
    }

    /**
     * Try to switch the inbound truck from pure pursuit to a Reeds-Shepp
     * docking maneuver. Triggers when the truck is within
     * {@link #DOCKING_TRIGGER_CELLS} of the LZ. The LZ pose's facing is the
     * direction of the final polyline segment (so the truck arrives heading
     * along the road into the LZ). The candidate RS path is sampled along
     * its length and each pose footprint-checked against the navigation
     * grid; if any pose is non-walkable, we leave docking off this tick and
     * try again next tick (or never, if the geometry persists — pure
     * pursuit will then deliver the truck to the LZ via the polyline).
     */
    private void tryEngageDocking(Vehicle v, float[] xs, float[] ys) {
        if (!(v.body instanceof BicycleBody)) return;
        int lastIdx = xs.length - 1;
        float lzX = xs[lastIdx];
        float lzY = ys[lastIdx];
        float distToLz = v.body.distanceTo(lzX, lzY);
        if (distToLz > DOCKING_TRIGGER_CELLS) return;

        float prevX = xs[lastIdx - 1];
        float prevY = ys[lastIdx - 1];
        float lzFacingDeg = AirBody.facingToward(lzX - prevX, lzY - prevY);

        Pose start = new Pose(v.body.x, v.body.y, v.body.facingDegrees);
        Pose goal  = new Pose(lzX, lzY, lzFacingDeg);
        float turnRadius = ((BicycleBody) v.body).minTurnRadiusCells();
        ReedsShepp.Path path = ReedsShepp.shortest(start, goal, turnRadius);
        if (path == null) return;
        if (!isPathFeasible(start, path, turnRadius, v.type, navigation.getGrid())) return;

        v.dockingPath = path;
        v.dockingStartPose = start;
        v.dockingTurnRadius = turnRadius;
        v.dockingProgressCells = 0f;
        v.dockingGoalFacingDeg = lzFacingDeg;
    }

    /**
     * Advance the docking truck by {@link #DOCKING_SPEED} for one tick along
     * its Reeds-Shepp path, set the body's pose from the sampled point, and
     * transition to LANDED when the path's total length is consumed.
     */
    private void advanceDocking(Vehicle v, float dt) {
        v.dockingProgressCells += DOCKING_SPEED * dt;
        float totalCells = v.dockingPath.lengthCells(v.dockingTurnRadius);
        if (v.dockingProgressCells >= totalCells) {
            v.body.teleport(v.lzX, v.lzY, v.dockingGoalFacingDeg);
            v.state = Vehicle.State.LANDED;
            v.deboardCountdown = v.type.deboardInterval;
            v.dockingPath = null;
            return;
        }
        Pose p = ReedsShepp.sample(v.dockingStartPose, v.dockingTurnRadius,
                                    v.dockingPath, v.dockingProgressCells);
        v.body.x = p.x;
        v.body.y = p.y;
        v.body.facingDegrees = p.facingDeg;
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

    /**
     * Finds a free cell adjacent to the LZ and spawns a militia there as a
     * fresh {@link Unit}. Same BFS shape as the shuttle deboard — copied
     * rather than shared so the air/ground split stays clean and the BFS
     * is small enough that duplication isn't a real cost.
     */
    private boolean tryDeboardMarine(Vehicle v) {
        int lzCellX = (int) Math.floor(v.lzX);
        int lzCellY = (int) Math.floor(v.lzY);
        int[] cell = findDeboardCell(lzCellX, lzCellY);
        if (cell == null) return false;
        UnitType deboardType = (v.deboardUnitType != null)
                ? v.deboardUnitType
                : FactionUnitRoster.forFaction(v.faction).infantry();
        Unit marine = new Unit(roster.nextMarineId(), v.faction, deboardType, cell[0], cell[1]);
        int slot = v.type.capacity - v.marinesRemaining;
        MarineLoadout loadout = (v.marineLoadout != null && slot < v.marineLoadout.length)
                ? v.marineLoadout[slot] : null;
        if (loadout != null) {
            marine.role = loadout.role;
            marine.assignedObjective = loadout.objective;
            if (loadout.primary != null) {
                marine.primaryWeapon = loadout.primary;
                // Pre-allocate seed (marine not yet added to the registry).
                marine.seedAttackRange = loadout.primary.range;
                marine.seedAttackDamage = loadout.primary.damage;
                marine.seedAccuracy = loadout.primary.accuracy;
                marine.attackCooldown = loadout.primary.cooldown;
            }
            if (loadout.secondary != null && loadout.secondaryAmmo > 0) {
                marine.secondaryWeapon = loadout.secondary;
                marine.secondaryAmmo = loadout.secondaryAmmo;
            }
        }
        if (v.squadId == Unit.NO_SQUAD) {
            v.squadId = roster.mintSquad(v.faction, marine);
        }
        marine.squadId = v.squadId;
        Squad squad = roster.getSquad(v.squadId);
        if (squad != null) squad.originalSize++;
        addUnitSink.accept(marine);
        return true;
    }

    /**
     * BFS outward from the LZ cell for the first walkable, unoccupied cell
     * at distance ≥ 1. Distance 0 (the LZ itself) is skipped so the marine
     * sprite doesn't draw directly under the parked truck.
     */
    private int[] findDeboardCell(int lzX, int lzY) {
        NavigationGrid grid = navigation.getGrid();
        Set<Long> seen = new HashSet<>();
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{lzX, lzY, 0});
        seen.add(((long) lzX << 32) | (lzY & 0xFFFFFFFFL));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (p[2] > DEBOARD_SCAN_RADIUS) continue;
            if (p[2] > 0
                    && grid.inBounds(p[0], p[1])
                    && grid.isWalkable(p[0], p[1])
                    && !navigation.isCellOccupied(p[0], p[1])) {
                return new int[]{p[0], p[1]};
            }
            for (int[] d : dirs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny)) continue;
                long k = ((long) nx << 32) | (ny & 0xFFFFFFFFL);
                if (!seen.add(k)) continue;
                q.add(new int[]{nx, ny, p[2] + 1});
            }
        }
        return null;
    }

    private void tickVehicleTurrets(float dt) {
        for (Vehicle v : vehicles) {
            if (!v.isVisible()) continue;
            if (!v.type.hasTurretWeapon()) continue;
            if (v.turretAmmo <= 0) continue;

            TurretKind kind = v.type.turretKind;

            float chassisRad = (float) Math.toRadians(v.body.facingDegrees);
            float cc = (float) Math.cos(chassisRad);
            float cs = (float) Math.sin(chassisRad);
            float mountWorldX = v.body.x + v.type.turretMountX * cc - v.type.turretMountY * cs;
            float mountWorldY = v.body.y + v.type.turretMountX * cs + v.type.turretMountY * cc;

            Unit currentBurstTarget = (v.turretBurstTargetId != 0L)
                    ? registry.getOrNull(v.turretBurstTargetId) : null;

            // Burst continuation fires ahead of fresh acquisition — the turret
            // commits to its salvo target, matching shuttle turret behavior.
            if (v.turretBurstRemaining > 0) {
                v.turretBurstTimer -= dt;
                if (v.turretBurstTimer <= 0f && currentBurstTarget != null && currentBurstTarget.isAlive()) {
                    fireSink.fire(mountWorldX, mountWorldY, v.faction, kind, currentBurstTarget, false);
                    v.turretAmmo--;
                    v.turretBurstRemaining--;
                    v.turretBurstTimer = kind.burstSpacing;
                    if (v.turretBurstRemaining == 0) v.turretBurstTargetId = 0L;
                }
                if (currentBurstTarget == null || !currentBurstTarget.isAlive()) {
                    v.turretBurstRemaining = 0;
                    v.turretBurstTargetId = 0L;
                }
                continue;
            }

            TurretAim.State aim = new TurretAim.State();
            aim.originCellX = (int) Math.floor(mountWorldX);
            aim.originCellY = (int) Math.floor(mountWorldY);
            aim.originX = mountWorldX;
            aim.originY = mountWorldY;
            aim.faction = v.faction;
            aim.facingDegrees = v.turretFacingDeg;
            aim.turnRateDegPerSec = kind.turnRateDegPerSec;
            aim.attackRange = kind.range;
            aim.minRange = kind.minRange;
            aim.cooldownTimer = v.turretCooldownTimer;
            aim.attackCooldown = kind.cooldown;
            aim.target = (v.turretTargetId != 0L) ? registry.getOrNull(v.turretTargetId) : null;

            TurretAim.tick(aim, tacticalScoring, navigation.getGrid(), dt);

            v.turretFacingDeg = aim.facingDegrees;
            v.turretCooldownTimer = aim.cooldownTimer;
            v.turretTargetId = (aim.target != null) ? aim.target.entityId : 0L;

            if (aim.fireThisTick && aim.target != null) {
                fireSink.fire(mountWorldX, mountWorldY, v.faction, kind, aim.target,
                        /*aerialShooter*/ false, aim.lastFireHadLos);
                v.turretAmmo--;
                if (kind.burstCount > 1 && aim.target.isAlive()) {
                    v.turretBurstRemaining = kind.burstCount - 1;
                    v.turretBurstTimer = kind.burstSpacing;
                    v.turretBurstTargetId = aim.target.entityId;
                }
            }
        }
    }
}
