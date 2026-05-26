package com.dillon.starsectormarines.battle.ground;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.FactionUnitRoster;
import com.dillon.starsectormarines.battle.weapons.MarineLoadout;
import com.dillon.starsectormarines.battle.unit.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.ai.TurretAim;
import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
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

    private final NavigationService navigation;
    private final UnitRosterService roster;
    private final Random rng;
    private final Consumer<Unit> addUnitSink;

    private final List<Vehicle> vehicles = new ArrayList<>();

    public GroundSystem(NavigationService navigation, UnitRosterService roster,
                        Random rng, Consumer<Unit> addUnitSink) {
        this.navigation = navigation;
        this.roster = roster;
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
    public void tick(BattleSimulation sim, float dt) {
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
        }
        tickVehicleTurrets(sim, dt);
    }

    /**
     * Path follower. Two modes:
     * <ol>
     *   <li><b>Pure pursuit</b> (default) — pick a carrot along the polyline
     *       at {@code lookAheadCells}, derive target speed from remaining
     *       path length ({@code min(maxSpeed, sqrt(2·brake·remaining))}),
     *       tick the body. The bicycle's steering constraint handles
     *       cornering geometry.</li>
     *   <li><b>Reeds-Shepp docking</b> — when an inbound truck is within
     *       {@link #DOCKING_TRIGGER_CELLS} of the LZ on its last polyline
     *       leg, switch to playing the body's pose along a closed-form
     *       forward+reverse path from current pose to the LZ pose. The LZ
     *       facing is taken from the final polyline segment's direction so
     *       the truck arrives heading along the road. If the Reeds-Shepp
     *       path fails the {@link VehicleFootprint} feasibility check
     *       (sampled along its length), we fall back to pure pursuit for
     *       that tick and retry next tick.</li>
     * </ol>
     */
    private void advancePath(Vehicle v, float[] xs, float[] ys, float dt, boolean isInbound) {
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

        PurePursuit.Carrot carrot = PurePursuit.pick(
                v.body.x, v.body.y, xs, ys, v.waypointIndex, v.type.lookAheadCells);
        v.waypointIndex = carrot.nextIdx;

        float remaining = PurePursuit.remainingPathLength(
                v.body.x, v.body.y, xs, ys, v.waypointIndex);
        float taper = (float) Math.sqrt(2f * v.type.brakingAccel * Math.max(0f, remaining));
        float targetSpeed = Math.min(v.type.maxSpeed, taper);

        v.body.tick(carrot.x, carrot.y, targetSpeed, dt);

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
                marine.attackRange = loadout.primary.range;
                marine.attackDamage = loadout.primary.damage;
                marine.accuracy = loadout.primary.accuracy;
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

    /**
     * Per-tick aim + fire pass for every vehicle with a functional turret.
     * Mirrors {@code AirSystem.tickShuttleTurrets}: each armed vehicle uses
     * the shared {@link TurretAim} loop for acquisition / slew / fire-when-
     * aligned, with the turret's world-rotated mount position as origin and
     * {@link BattleSimulation#fireShotFrom} for shot emission.
     *
     * <p>Active whenever the vehicle is on-map and has ammo. OVERWATCH
     * vehicles fire indefinitely; INCOMING / LANDED / DEPARTING vehicles
     * also fire if armed (the APC's turret is live from the moment it
     * rolls onto the map).
     */
    private void tickVehicleTurrets(BattleSimulation sim, float dt) {
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
                    ? sim.resolveUnit(v.turretBurstTargetId) : null;

            // Burst continuation fires ahead of fresh acquisition — the turret
            // commits to its salvo target, matching shuttle turret behavior.
            if (v.turretBurstRemaining > 0) {
                v.turretBurstTimer -= dt;
                if (v.turretBurstTimer <= 0f && currentBurstTarget != null && currentBurstTarget.isAlive()) {
                    sim.fireShotFrom(mountWorldX, mountWorldY, v.faction, kind, currentBurstTarget, false);
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
            aim.target = (v.turretTargetId != 0L) ? sim.resolveUnit(v.turretTargetId) : null;

            TurretAim.tick(aim, sim, dt);

            v.turretFacingDeg = aim.facingDegrees;
            v.turretCooldownTimer = aim.cooldownTimer;
            v.turretTargetId = (aim.target != null) ? aim.target.entityId : 0L;

            if (aim.fireThisTick && aim.target != null) {
                sim.fireShotFrom(mountWorldX, mountWorldY, v.faction, kind, aim.target,
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
