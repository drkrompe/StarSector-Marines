package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.unit.FactionUnitRoster;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.turret.TurretAim;
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

    /** Max BFS radius from the LZ when looking for a free deboard cell. Past this we drop the deboard for this tick and retry. */
    private static final int DEBOARD_SCAN_RADIUS = 5;

    private final NavigationService navigation;
    private final UnitRosterService roster;
    private final com.dillon.starsectormarines.battle.decision.TacticalScoring tacticalScoring;
    private final World world;
    private final TurretFireSink fireSink;
    private final Random rng;
    private final Consumer<Entity> addUnitSink;

    private final List<Vehicle> vehicles = new ArrayList<>();

    public GroundSystem(NavigationService navigation, UnitRosterService roster,
                        com.dillon.starsectormarines.battle.decision.TacticalScoring tacticalScoring,
                        World world, TurretFireSink fireSink, Random rng, Consumer<Entity> addUnitSink) {
        this.navigation = navigation;
        this.roster = roster;
        this.tacticalScoring = tacticalScoring;
        this.world = world;
        this.fireSink = fireSink;
        this.rng = rng;
        this.addUnitSink = addUnitSink;
    }

    public List<Vehicle> getVehicles() { return vehicles; }

    public void add(Vehicle v) {
        v.controller = new VehicleController(v, navigation);
        vehicles.add(v);
    }

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
                    v.controller.tick(dt, true);
                    if (v.controller.consumeArrived()) {
                        v.state = Vehicle.State.LANDED;
                        v.deboardCountdown = v.type.deboardInterval;
                    }
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
                        v.state = Vehicle.State.DEPARTING;
                    }
                    break;

                case DEPARTING:
                    v.controller.tick(dt, false);
                    if (v.controller.consumeArrived()) {
                        v.state = Vehicle.State.GONE;
                    }
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
     * Finds a free cell adjacent to the LZ and spawns a militia there as a
     * fresh {@link Entity}. Same BFS shape as the shuttle deboard — copied
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
        Entity marine = new Entity(roster.nextMarineId(), v.faction, deboardType, cell[0], cell[1]);
        int slot = v.type.capacity - v.marinesRemaining;
        MarineLoadout loadout = (v.marineLoadout != null && slot < v.marineLoadout.length)
                ? v.marineLoadout[slot] : null;
        if (loadout != null) {
            marine.seedRole = loadout.role;
            marine.seedAssignedObjective = loadout.objective;
            if (loadout.primary != null) {
                // Pre-allocate seed (marine not yet added to the registry).
                marine.seedPrimaryWeapon = loadout.primary;
                marine.seedAttackRange = loadout.primary.range;
                marine.seedAttackDamage = loadout.primary.damage;
                marine.seedAccuracy = loadout.primary.accuracy;
                marine.seedAttackCooldown = loadout.primary.cooldown;
            }
            if (loadout.secondary != null && loadout.secondaryAmmo > 0) {
                // Pre-allocate seed — allocate adds the SECONDARY_WEAPON component.
                marine.seedSecondaryWeapon = loadout.secondary;
                marine.seedSecondaryAmmo = loadout.secondaryAmmo;
            }
        }
        if (v.squadId == Entity.NO_SQUAD) {
            v.squadId = roster.mintSquad(v.faction, marine);
        }
        marine.seedSquadId = v.squadId;
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

            Entity currentBurstTarget = (v.turretBurstTargetId != 0L)
                    ? roster.getOrNull(v.turretBurstTargetId) : null;

            // Burst continuation fires ahead of fresh acquisition — the turret
            // commits to its salvo target, matching shuttle turret behavior.
            if (v.turretBurstRemaining > 0) {
                v.turretBurstTimer -= dt;
                if (v.turretBurstTimer <= 0f && currentBurstTarget != null && world.isAlive(v.turretBurstTargetId)) {
                    fireSink.fire(mountWorldX, mountWorldY, v.faction, kind, currentBurstTarget, false);
                    v.turretAmmo--;
                    v.turretBurstRemaining--;
                    v.turretBurstTimer = kind.burstSpacing;
                    if (v.turretBurstRemaining == 0) v.turretBurstTargetId = 0L;
                }
                if (currentBurstTarget == null || !world.isAlive(v.turretBurstTargetId)) {
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
            aim.target = (v.turretTargetId != 0L) ? roster.getOrNull(v.turretTargetId) : null;

            TurretAim.tick(aim, tacticalScoring, navigation.getGrid(), world, roster.vision(), dt);

            v.turretFacingDeg = aim.facingDegrees;
            v.turretCooldownTimer = aim.cooldownTimer;
            v.turretTargetId = (aim.target != null) ? aim.target.entityId : 0L;

            if (aim.fireThisTick && aim.target != null) {
                fireSink.fire(mountWorldX, mountWorldY, v.faction, kind, aim.target,
                        /*aerialShooter*/ false, aim.lastFireHadLos);
                v.turretAmmo--;
                if (kind.burstCount > 1 && world.isAlive(aim.target.entityId)) {
                    v.turretBurstRemaining = kind.burstCount - 1;
                    v.turretBurstTimer = kind.burstSpacing;
                    v.turretBurstTargetId = aim.target.entityId;
                }
            }
        }
    }
}
