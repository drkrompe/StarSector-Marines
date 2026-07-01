package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.unit.FactionUnitRoster;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.Faction;
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
import java.util.Iterator;
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
 * shape — a stateless per-tick state-machine pass over an id backbone. Each
 * vehicle is a world entity ({@code {GROUND_IDENTITY, GROUND_KINEMATICS,
 * VEHICLE_MISSION}} + optional {@code GROUND_TURRET}); this system holds a
 * {@code List<Long>} of ids and resolves each vehicle's mission / identity /
 * kinematics / turret <b>by id</b> through {@link ConvoyService} (the data owner).
 * Kinematics differ from air: ground vehicles use the {@link GroundBody}
 * abstraction (currently {@link BicycleBody}) driven by a pure-pursuit carrot,
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
    /** Spawns each vehicle's world entity (identity + kinematics + mission + turret) at {@link #add} and reaps it at terminal GONE. */
    private final ConvoyService convoy;

    /** The backbone: world entity ids of live convoy vehicles. The {@link VehicleMission} bags
     *  live in the {@code VEHICLE_MISSION} component (reached via {@link ConvoyService#mission},
     *  not a side list) — no separate mission storage. GONE ids are reaped each tick. */
    private final List<Long> vehicleIds = new ArrayList<>();

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
        this.convoy = roster.convoy();
    }

    /** Snapshot of the live convoy-vehicle entity ids — the id backbone the render / picking / debug
     *  passes walk, resolving each vehicle by id via {@link ConvoyService}. The ground twin of
     *  {@link com.dillon.starsectormarines.battle.air.AirSystem#airEntityIds}; N≈1–4 so the per-call array is negligible. */
    public long[] vehicleEntityIds() {
        long[] ids = new long[vehicleIds.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = vehicleIds.get(i);
        return ids;
    }

    /**
     * Spawns {@code mission} as a world entity of the given variant / faction and
     * joins it to the system. The caller builds + configures the {@link VehicleMission}
     * (paths, route inputs, loadout) before handing it off; {@link ConvoyService#spawn}
     * builds the body + turret and seeds every column, then the controller is created
     * over the by-id-resolved body.
     */
    public void add(VehicleType type, Faction faction, VehicleMission mission) {
        long id = convoy.spawn(type, faction, mission);
        // The controller holds the mission + the by-id-resolved body / variant (one stable
        // instance each for the vehicle's life). See ConvoyService / VehicleController.
        mission.controller = new VehicleController(mission, convoy.body(id), type, navigation);
        vehicleIds.add(id);
    }

    /**
     * Advances every ground vehicle one tick by {@code dt} seconds. Same
     * fixed-tick contract as {@link com.dillon.starsectormarines.battle.air.AirSystem#tick}
     * — caller is responsible for matching {@code dt} to its tick cadence.
     */
    public void tick(float dt) {
        for (long id : vehicleIds) {
            VehicleMission m = convoy.mission(id);
            VehicleType type = convoy.vehicleType(id);
            switch (m.state) {
                case PENDING:
                    m.pendingDelay -= dt;
                    if (m.pendingDelay <= 0f) m.state = VehicleState.INCOMING;
                    break;

                case INCOMING:
                    m.controller.tick(dt, true);
                    if (m.controller.consumeArrived()) {
                        m.state = VehicleState.LANDED;
                        m.deboardCountdown = type.deboardInterval;
                    }
                    break;

                case LANDED:
                    m.deboardCountdown -= dt;
                    if (m.deboardCountdown <= 0f && m.marinesRemaining > 0) {
                        if (tryDeboardMarine(id, m, type)) {
                            m.marinesRemaining--;
                        }
                        m.deboardCountdown = type.deboardInterval;
                    }
                    if (m.marinesRemaining == 0) {
                        if (type.departsAfterDeboard) {
                            m.state = VehicleState.DEPARTING;
                        } else {
                            m.overwatchCountdown = type.overwatchDurationSec;
                            m.state = VehicleState.OVERWATCH;
                        }
                    }
                    break;

                case OVERWATCH:
                    m.overwatchCountdown -= dt;
                    if (m.overwatchCountdown <= 0f) {
                        m.state = VehicleState.DEPARTING;
                    }
                    break;

                case DEPARTING:
                    m.controller.tick(dt, false);
                    if (m.controller.consumeArrived()) {
                        m.state = VehicleState.GONE;  // reaped end-of-tick by reapGoneVehicles()
                    }
                    break;

                case GONE:
                default:
                    break;
            }
            if (m.isVisible()) {
                m.recordTick(convoy.body(id));
            }
        }
        tickVehicleTurrets(dt);
        reapGoneVehicles();
    }

    /**
     * End-of-tick reap: destroys the world entity of every {@link VehicleState#GONE}
     * vehicle and drops the id from the list — the air {@code reapGoneCraft} shape.
     * Gather-then-apply at the tick barrier (serial phase, so no {@code CommandBuffer};
     * {@code destroy} is idempotent). Bounds the list to live vehicles and covers any
     * terminal path in one place. Safe to remove from the list now that selection is
     * id-keyed (not a positional index), so removal shifts nothing — see the Phase-2
     * critique in {@code roadmap/ecs-migration/stories/vehicle-into-world.md}.
     */
    private void reapGoneVehicles() {
        for (Iterator<Long> it = vehicleIds.iterator(); it.hasNext(); ) {
            long id = it.next();
            VehicleMission m = convoy.mission(id);
            // Resolve the mission BEFORE despawn (despawn destroys the entity → mission(id)
            // would then return null).
            if (m == null || m.state == VehicleState.GONE) {
                convoy.despawn(id);
                it.remove();
            }
        }
    }


    /**
     * Finds a free cell adjacent to the LZ and spawns a militia there as a
     * fresh {@link Entity}. Same BFS shape as the shuttle deboard — copied
     * rather than shared so the air/ground split stays clean and the BFS
     * is small enough that duplication isn't a real cost.
     */
    private boolean tryDeboardMarine(long id, VehicleMission m, VehicleType type) {
        Faction faction = convoy.faction(id);
        int lzCellX = (int) Math.floor(m.lzX);
        int lzCellY = (int) Math.floor(m.lzY);
        int[] cell = findDeboardCell(lzCellX, lzCellY);
        if (cell == null) return false;
        UnitType deboardType = (m.deboardUnitType != null)
                ? m.deboardUnitType
                : FactionUnitRoster.forFaction(faction).infantry();
        Entity marine = new Entity(roster.nextMarineId(), faction, deboardType, cell[0], cell[1]);
        int slot = type.capacity - m.marinesRemaining;
        MarineLoadout loadout = (m.marineLoadout != null && slot < m.marineLoadout.length)
                ? m.marineLoadout[slot] : null;
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
        if (m.squadId == Entity.NO_SQUAD) {
            m.squadId = roster.mintSquad(faction, marine);
        }
        marine.seedSquadId = m.squadId;
        Squad squad = roster.getSquad(m.squadId);
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
        for (long id : vehicleIds) {
            VehicleMission m = convoy.mission(id);
            if (!m.isVisible()) continue;
            VehicleType type = convoy.vehicleType(id);
            if (!type.hasTurretWeapon()) continue;
            // Armed ⟹ GROUND_TURRET present (seeded at spawn), so gt is non-null. Turret
            // state lives in the world's GROUND_TURRET component, read by id.
            GroundTurret gt = convoy.turret(id);
            if (gt.ammo <= 0) continue;

            GroundBody body = convoy.body(id);
            Faction faction = convoy.faction(id);
            TurretKind kind = type.turretKind;

            float chassisRad = (float) Math.toRadians(body.facingDegrees);
            float cc = (float) Math.cos(chassisRad);
            float cs = (float) Math.sin(chassisRad);
            float mountWorldX = body.x + type.turretMountX * cc - type.turretMountY * cs;
            float mountWorldY = body.y + type.turretMountX * cs + type.turretMountY * cc;

            Entity currentBurstTarget = (gt.burstTargetId != 0L)
                    ? roster.getOrNull(gt.burstTargetId) : null;

            // Burst continuation fires ahead of fresh acquisition — the turret
            // commits to its salvo target, matching shuttle turret behavior.
            if (gt.burstRemaining > 0) {
                gt.burstTimer -= dt;
                if (gt.burstTimer <= 0f && currentBurstTarget != null && world.isAlive(gt.burstTargetId)) {
                    fireSink.fire(mountWorldX, mountWorldY, faction, kind, currentBurstTarget, false);
                    gt.ammo--;
                    gt.burstRemaining--;
                    gt.burstTimer = kind.burstSpacing;
                    if (gt.burstRemaining == 0) gt.burstTargetId = 0L;
                }
                if (currentBurstTarget == null || !world.isAlive(gt.burstTargetId)) {
                    gt.burstRemaining = 0;
                    gt.burstTargetId = 0L;
                }
                continue;
            }

            TurretAim.State aim = new TurretAim.State();
            aim.originCellX = (int) Math.floor(mountWorldX);
            aim.originCellY = (int) Math.floor(mountWorldY);
            aim.originX = mountWorldX;
            aim.originY = mountWorldY;
            aim.faction = faction;
            aim.facingDegrees = gt.facingDeg;
            aim.turnRateDegPerSec = kind.turnRateDegPerSec;
            aim.attackRange = kind.range;
            aim.minRange = kind.minRange;
            aim.cooldownTimer = gt.cooldownTimer;
            aim.attackCooldown = kind.cooldown;
            aim.target = (gt.targetId != 0L) ? roster.getOrNull(gt.targetId) : null;

            TurretAim.tick(aim, tacticalScoring, navigation.getGrid(), world, roster.vision(), dt);

            gt.facingDeg = aim.facingDegrees;
            gt.cooldownTimer = aim.cooldownTimer;
            gt.targetId = (aim.target != null) ? aim.target.entityId : 0L;

            if (aim.fireThisTick && aim.target != null) {
                fireSink.fire(mountWorldX, mountWorldY, faction, kind, aim.target,
                        /*aerialShooter*/ false, aim.lastFireHadLos);
                gt.ammo--;
                if (kind.burstCount > 1 && world.isAlive(aim.target.entityId)) {
                    gt.burstRemaining = kind.burstCount - 1;
                    gt.burstTimer = kind.burstSpacing;
                    gt.burstTargetId = aim.target.entityId;
                }
            }
        }
    }
}
