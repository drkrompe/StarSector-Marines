package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.command.SquadDirectiveControl;
import com.dillon.starsectormarines.battle.unit.FactionUnitRoster;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.turret.TurretAim;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.turret.TurretFireSink;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretMountDef;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.world.MapEditor;

import java.util.ArrayDeque;
import java.util.HashSet;
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
 * VEHICLE_MISSION, HEALTH, ARMOR}} + optional {@code GROUND_TURRET}); the
 * {@link ConvoyService} owns the id backbone and this system resolves each
 * vehicle's mission / identity / kinematics / turret <b>by id</b> through it.
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
    private final Consumer<EntitySpec> addUnitSink;
    private final SquadDirectiveControl commandControl;
    /** Spawns each vehicle's world entity (identity + kinematics + mission + turret) at {@link #add} and reaps it at terminal GONE. */
    private final ConvoyService convoy;
    /** The stateless motion driver every vehicle's {@code mission.controller} shim forwards to — one instance shared across the whole convoy. */
    private final VehicleControlSystem controlSystem;

    /** The backbone: world entity ids of live convoy vehicles. The {@link VehicleMission} bags
     *  live in the {@code VEHICLE_MISSION} component (reached via {@link ConvoyService#mission},
     *  not a side list) — no separate mission storage. GONE ids are reaped each tick. */
    private final EffectsService effects;
    private final MapEditor mapEditor;

    public GroundSystem(NavigationService navigation, UnitRosterService roster,
                        com.dillon.starsectormarines.battle.decision.TacticalScoring tacticalScoring,
                        World world, TurretFireSink fireSink, Random rng,
                        Consumer<EntitySpec> addUnitSink, SquadDirectiveControl commandControl,
                        EffectsService effects, MapEditor mapEditor) {
        this.navigation = navigation;
        this.roster = roster;
        this.tacticalScoring = tacticalScoring;
        this.world = world;
        this.fireSink = fireSink;
        this.rng = rng;
        this.addUnitSink = addUnitSink;
        this.commandControl = commandControl;
        this.effects = effects;
        this.mapEditor = mapEditor;
        this.convoy = roster.convoy();
        this.controlSystem = new VehicleControlSystem(convoy, navigation);
    }

    /** Snapshot of the live convoy-vehicle entity ids — the id backbone the render / picking / debug
     *  passes walk, resolving each vehicle by id via {@link ConvoyService}. The ground twin of
     *  {@link com.dillon.starsectormarines.battle.air.AirSystem#airEntityIds}; N≈1–4 so the per-call array is negligible. */
    public long[] vehicleEntityIds() {
        return convoy.entityIds();
    }

    /**
     * Spawns {@code mission} as a world entity of the given variant / faction and
     * joins it to the system. The caller builds + configures the {@link VehicleMission}
     * (paths, route inputs, loadout) before handing it off; {@link ConvoyService#spawn}
     * builds the body + turret and seeds every column (including {@code VEHICLE_CONTROL}),
     * then the id joins this system's backbone and is driven by the shared
     * {@link VehicleControlSystem}.
     */
    public void add(VehicleType type, Faction faction, VehicleMission mission) {
        convoy.spawn(type, faction, mission);
    }

    /**
     * Advances every ground vehicle one tick by {@code dt} seconds. Same
     * fixed-tick contract as {@link com.dillon.starsectormarines.battle.air.AirSystem#tick}
     * — caller is responsible for matching {@code dt} to its tick cadence.
     */
    public void tick(float dt) {
        for (long id : convoy.entityIds()) {
            VehicleMission m = convoy.mission(id);
            VehicleType type = convoy.vehicleType(id);
            switch (m.state) {
                case PENDING:
                    m.pendingDelay -= dt;
                    if (m.pendingDelay <= 0f) m.state = VehicleState.INCOMING;
                    break;

                case INCOMING:
                    controlSystem.tick(id, dt, true);
                    if (controlSystem.consumeArrived(id)) {
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
                    controlSystem.tick(id, dt, false);
                    if (controlSystem.consumeArrived(id)) {
                        m.state = VehicleState.GONE;  // reaped end-of-tick by reapGoneVehicles()
                    }
                    break;

                case GONE:
                case WRECKED:
                default:
                    break;
            }
            if (m.isVisible()) {
                m.recordTick(convoy.body(id), convoy.control(id).wallStuckTime());
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
     * ownership contract in {@code ecs-nouns.md}.
     */
    private void reapGoneVehicles() {
        for (long id : convoy.entityIds()) {
            VehicleMission m = convoy.mission(id);
            // Resolve the mission BEFORE despawn (despawn destroys the entity → mission(id)
            // would then return null).
            if (m == null || m.state == VehicleState.GONE) {
                convoy.despawn(id);
            }
        }
    }

    /**
     * Once-only terminal transition invoked by the vehicle damage resolver.
     * The entity remains present as a darkened chassis/wreck obstacle, but its
     * mission, motion, turret, targetability, and onboard payload all stop here.
     */
    public void destroyVehicle(long id) {
        VehicleMission mission = convoy.mission(id);
        if (mission == null || mission.state == VehicleState.WRECKED
                || mission.state == VehicleState.GONE) return;
        GroundBody body = convoy.body(id);
        VehicleType type = convoy.vehicleType(id);
        mission.state = VehicleState.WRECKED;
        body.speed = 0f;
        GroundTurret turret = convoy.turret(id);
        if (turret != null) {
            turret.targetId = 0L;
            turret.burstTargetId = 0L;
            turret.burstRemaining = 0;
        }
        resolveOnboardPassengers(id, mission, type, body);
        mapEditor.placeVehicleWreck(body, type);
        refreshRouteClearance();
        effects.spawnSmokingWreck((int) Math.floor(body.x), (int) Math.floor(body.y));
    }

    /** Rebuild every active mission's immutable clearance snapshot after the wreck closes cells. */
    private void refreshRouteClearance() {
        NavigationGrid grid = navigation.getGrid();
        for (long vehicleId : convoy.entityIds()) {
            VehicleMission active = convoy.mission(vehicleId);
            if (active == null || active.routeClearance == null) continue;
            active.routeClearance = VehicleClearance.erode(
                    grid, active.routeClearance.radiusCells());
        }
    }

    private void resolveOnboardPassengers(long id, VehicleMission mission,
                                           VehicleType type, GroundBody body) {
        int onboard = mission.marinesRemaining;
        mission.marinesRemaining = 0;
        if (onboard <= 0) return;
        int survivors = Math.min(onboard, 1 + rng.nextInt(2));
        Set<Long> reserved = new HashSet<>();
        int originX = (int) Math.floor(body.x);
        int originY = (int) Math.floor(body.y);
        for (int i = 0; i < survivors; i++) {
            int[] cell = findDeboardCell(originX, originY, reserved);
            if (cell == null) break;
            reserved.add(((long) cell[0] << 32) | (cell[1] & 0xFFFFFFFFL));
            spawnPassengerAt(id, mission, type, cell, 0.25f);
        }
    }


    /**
     * Finds a free cell adjacent to the LZ and spawns a militia there as a
     * fresh {@code Entity}. Same BFS shape as the shuttle deboard — copied
     * rather than shared so the air/ground split stays clean and the BFS
     * is small enough that duplication isn't a real cost.
     */
    private boolean tryDeboardMarine(long id, VehicleMission m, VehicleType type) {
        int lzCellX = (int) Math.floor(m.lzX);
        int lzCellY = (int) Math.floor(m.lzY);
        int[] cell = findDeboardCell(lzCellX, lzCellY, java.util.Collections.emptySet());
        if (cell == null) return false;
        return spawnPassengerAt(id, m, type, cell, 1f);
    }

    private boolean spawnPassengerAt(long id, VehicleMission m, VehicleType type,
                                     int[] cell, float hpFraction) {
        Faction faction = convoy.faction(id);
        UnitType deboardType = (m.deboardUnitType != null)
                ? m.deboardUnitType
                : FactionUnitRoster.forFaction(faction).infantry();
        EntitySpec marine = new EntitySpec(roster.nextMarineId(), faction, deboardType, cell[0], cell[1]);
        int slot = type.capacity - m.marinesRemaining;
        MarineLoadout loadout = (m.marineLoadout != null && slot < m.marineLoadout.length)
                ? m.marineLoadout[slot] : null;
        if (loadout != null) loadout.seedInto(marine);
        if (hpFraction < 1f) marine.hp(Math.max(1f, marine.maxHp * hpFraction));
        if (m.squadId == Squad.NO_SQUAD) {
            m.squadId = roster.mintSquad(faction, deboardType);
            if (m.commandClaim != null) {
                ObjectiveAssignment initialAssignment = null;
                if (m.commandOwnsObjective && m.assignNode != null) {
                    initialAssignment = ObjectiveAssignment.holdNode(
                            m.squadId, m.assignNode);
                } else if (m.commandOwnsObjective
                        && m.assignZoneId != ObjectiveAssignment.UNSCOPED) {
                    initialAssignment = ObjectiveAssignment.clearZone(
                            m.squadId, m.assignZoneId);
                }
                if (initialAssignment != null) {
                    m.commandClaim.apply(commandControl, initialAssignment);
                } else {
                    m.commandClaim.apply(commandControl, m.squadId);
                }
            }
            if (m.assignNode != null) {
                Squad minted = roster.getSquad(m.squadId);
                if (minted != null) minted.assignedNode = m.assignNode;
            }
        }
        marine.squad(m.squadId);
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
        return findDeboardCell(lzX, lzY, java.util.Collections.emptySet());
    }

    private int[] findDeboardCell(int lzX, int lzY, Set<Long> excluded) {
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
                    && !navigation.isCellOccupied(p[0], p[1])
                    && !excluded.contains(((long) p[0] << 32) | (p[1] & 0xFFFFFFFFL))) {
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
        for (long id : convoy.entityIds()) {
            if (!convoy.isTargetable(id)) continue;
            VehicleType type = convoy.vehicleType(id);
            if (!type.hasTurretWeapon()) continue;
            // Armed ⟹ GROUND_TURRET present (seeded at spawn), so gt is non-null. Turret
            // state lives in the world's GROUND_TURRET component, read by id.
            GroundTurret gt = convoy.turret(id);
            if (gt.ammo <= 0) continue;

            GroundBody body = convoy.body(id);
            Faction faction = convoy.faction(id);
            StructureDef structure = type.turretStructure();
            TurretMountDef mount = structure.mount;
            WeaponDef weapon = mount.weapon;

            float chassisRad = (float) Math.toRadians(body.facingDegrees);
            float cc = (float) Math.cos(chassisRad);
            float cs = (float) Math.sin(chassisRad);
            float mountWorldX = body.x + type.turretMountX * cc - type.turretMountY * cs;
            float mountWorldY = body.y + type.turretMountX * cs + type.turretMountY * cc;

            long currentBurstTarget = roster.isAliveById(gt.burstTargetId) ? gt.burstTargetId : 0L;

            // Burst continuation fires ahead of fresh acquisition — the turret
            // commits to its salvo target, matching shuttle turret behavior.
            if (gt.burstRemaining > 0) {
                gt.burstTimer -= dt;
                if (gt.burstTimer <= 0f && currentBurstTarget != 0L && world.isAlive(gt.burstTargetId)) {
                    fireSink.fire(id, mountWorldX, mountWorldY, faction, structure,
                            currentBurstTarget, /*aerialShooter*/ false, /*hasLos*/ true);
                    gt.ammo--;
                    gt.burstRemaining--;
                    gt.burstTimer = weapon.burstSpacing;
                    if (gt.burstRemaining == 0) gt.burstTargetId = 0L;
                }
                if (currentBurstTarget == 0L || !world.isAlive(gt.burstTargetId)) {
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
            aim.turnRateDegPerSec = mount.turnRateDegPerSec;
            aim.attackRange = weapon.range;
            aim.minRange = weapon.minRange;
            aim.cooldownTimer = gt.cooldownTimer;
            aim.attackCooldown = weapon.cooldown;
            aim.target = roster.isAliveById(gt.targetId) ? gt.targetId : 0L;

            TurretAim.tick(aim, tacticalScoring, navigation.getGrid(), world, roster.vision(), dt);

            gt.facingDeg = aim.facingDegrees;
            gt.cooldownTimer = aim.cooldownTimer;
            gt.targetId = aim.target;

            if (aim.fireThisTick && aim.target != 0L) {
                fireSink.fire(id, mountWorldX, mountWorldY, faction, structure, aim.target,
                        /*aerialShooter*/ false, aim.lastFireHadLos);
                gt.ammo--;
                if (weapon.burstCount > 1 && world.isAlive(aim.target)) {
                    gt.burstRemaining = weapon.burstCount - 1;
                    gt.burstTimer = weapon.burstSpacing;
                    gt.burstTargetId = aim.target;
                }
            }
        }
    }
}
