package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.HullSightings;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.DeployableEmplacementSpec;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Flies the tethered drones of every hull that carries a drone bay ({@link VehicleType#droneBay()}).
 *
 * <p>Each tick, in the serial part of the tick: a hull that has come onto the field gets a {@link DroneWing} and a
 * drone squad anchored to it; its bay launches one drone at a time in its role; the wing follows the hull, forgets
 * drones that have been shot down, and publishes its roster, its stations and the hostile nearest the hull. Flight,
 * formation and fire are the shared swarm action's ({@link DroneSwarmAction}).
 *
 * <p>What the roles add is ordinary machinery. A halo drone wears a carried point-defence mount
 * ({@code PointDefenseService}), so it engages incoming rounds and the mount ends when the drone falls. A sensor
 * drone is a sight source for its side ({@link #forEachSensor}, read by {@link HullSightings}). A strike drone is a
 * munition: the bay launches it at the hostile threatening the hull, it flies into it, and on arrival it detonates
 * as an ordinary blast credited to itself and is spent; the bay then rebuilds it.
 *
 * <p>When the hull is wrecked or leaves the field, the wing's anchor dies and every drone still out is destroyed where
 * it flies, so it falls through the ordinary drone crash ({@link DroneCrashSystem}).
 */
public final class DroneBaySystem {

    private static final float DESTROY = 1.0e6f;
    /**
     * A tethered drone is part of its hull's armament, not a hub's disposable screen: it flies on a composite airframe
     * with a light plate, so a lone rifle has to work at it. Hub and tender drones keep {@link Drone#DRONE_MAX_HP}.
     */
    static final float BAY_DRONE_HP = 30f;
    static final float BAY_DRONE_ARMOR = 20f;
    static final float BAY_DRONE_ARMOR_RATING = 4f;
    /** A strike drone is a warhead with a fan, lighter than the rest. */
    static final float STRIKE_DRONE_HP = 20f;
    /** Ticks between scans for the hostile nearest each hull. */
    static final int SCAN_TICKS = 6;
    /** How close a strike drone must come to its target to detonate, in cells. */
    public static final float STRIKE_FUSE_CELLS = 1.0f;
    public static final float STRIKE_BLAST_RADIUS = 1.6f;
    public static final float STRIKE_DAMAGE = 55f;
    public static final float STRIKE_PENETRATION = 12f;
    /** How far a sensor drone sees, in cells. */
    public static final float SENSOR_SIGHT_CELLS = 30f;
    /** The halo drone's carried laser: a mount riding the drone for the whole battle. */
    static final DeployableEmplacementSpec HALO_MOUNT = new DeployableEmplacementSpec(
            "structure.drone-pd-halo", 0f, 1.0e6f, 0f, 0f, true);
    private static final Color STRIKE_BLAST_TINT = new Color(0x8F, 0xE6, 0xFF);

    private final Map<Long, DroneWing> wings = new LinkedHashMap<>();
    private int scanCountdown;
    private int nextDrone;
    private int strikesDetonated;
    private float lastStrikeX = Float.NaN, lastStrikeY = Float.NaN;

    /** Strike drones that have reached their target and detonated this battle. */
    public int strikesDetonated() { return strikesDetonated; }

    /** Where the latest strike drone detonated, or NaN before the first; for evidence that measures the blast. */
    public float lastStrikeX() { return lastStrikeX; }

    /** See {@link #lastStrikeX()}. */
    public float lastStrikeY() { return lastStrikeY; }

    /** The wing tethered to {@code hullId}, or null when that hull carries none or has not come onto the field. */
    public DroneWing wing(long hullId) {
        return wings.get(hullId);
    }

    /** A sight source a side has in the air. */
    @FunctionalInterface
    public interface SensorSink {
        void sensor(Faction side, float x, float y, float rangeCells);
    }

    /** Every sensor drone flying, with its side, position and sight range. */
    public void forEachSensor(BattleSimulation sim, SensorSink sink) {
        for (DroneWing wing : wings.values()) {
            if (!wing.anchorAlive()) continue;
            Faction side = sim.convoy().faction(wing.hullId());
            if (side == null) continue;
            for (Long id : wing.drones()) {
                if (wing.role(id) != DroneRole.SENSOR || sim.resolveUnit(id) == 0L) continue;
                sink.sensor(side, sim.world().x(id), sim.world().y(id), SENSOR_SIGHT_CELLS);
            }
        }
    }

    public void tick(float dt, BattleSimulation sim) {
        ConvoyService convoy = sim.convoy();
        boolean scan = scanCountdown-- <= 0;
        if (scan) scanCountdown = SCAN_TICKS - 1;
        if (convoy.vehicleCount() > 0) {
            for (long hull : convoy.entityIds()) {
                VehicleType type = convoy.vehicleType(hull);
                VehicleMission mission = convoy.mission(hull);
                if (type == null || type.droneBay().isEmpty() || mission == null || !mission.isVisible()
                        || mission.state == VehicleState.WRECKED || wings.containsKey(hull)) continue;
                wings.put(hull, new DroneWing(hull, type.droneBay(), type.visualLengthCells));
            }
        }
        float seconds = sim.getSimTickIndex() * BattleSimulation.TICK_DT;
        for (Iterator<DroneWing> it = wings.values().iterator(); it.hasNext(); ) {
            DroneWing wing = it.next();
            VehicleMission mission = convoy.mission(wing.hullId());
            GroundBody body = convoy.body(wing.hullId());
            if (mission == null || body == null || mission.state == VehicleState.WRECKED
                    || mission.state == VehicleState.GONE) {
                collapse(wing, sim);
                it.remove();
                continue;
            }
            wing.follow(body.x, body.y, body.facingDegrees);
            for (Long id : wing.drones()) {
                if (sim.resolveUnit(id) == 0L) wing.lost(id);
            }
            Faction side = convoy.faction(wing.hullId());
            long focus = scan && side != null ? nearestHostile(side, wing, sim) : wing.focusTarget();
            if (side != null) {
                strike(wing, side, focus, sim);
                DroneRole role = wing.readyToLaunch(dt, focus != 0L);
                if (role != null) launch(wing, role, side, body, focus, sim);
            }
            publish(wing, focus, seconds, sim);
        }
    }

    /** The hull is lost: nothing more launches, and every drone still out comes down where it flies. */
    private static void collapse(DroneWing wing, BattleSimulation sim) {
        wing.hullLost();
        for (Long id : wing.drones()) {
            if (sim.resolveUnit(id) != 0L) sim.applyExternalDamage(id, DESTROY, DESTROY);
            wing.lost(id);
        }
    }

    /**
     * Steers the strike drone, if one is out: at its target while that lives, at the hull's threat when it does not,
     * home when there is none; and detonates it on arrival.
     */
    private void strike(DroneWing wing, Faction side, long focus, BattleSimulation sim) {
        long drone = wing.strikeDrone();
        if (drone == 0L) return;
        World world = sim.world();
        long target = wing.strikeTarget();
        if (target == 0L || sim.resolveUnit(target) == 0L) target = focus;
        if (target == 0L || sim.resolveUnit(target) == 0L) {
            wing.aimStrike(0L, 0f, 0f);
            return;
        }
        float tx = world.x(target), ty = world.y(target);
        wing.aimStrike(target, tx, ty);
        float dx = world.x(drone) - tx, dy = world.y(drone) - ty;
        if (dx * dx + dy * dy > STRIKE_FUSE_CELLS * STRIKE_FUSE_CELLS) return;
        float bx = world.x(drone), by = world.y(drone);
        sim.detonateNow(new PendingDetonation(drone, bx, by, 0f, STRIKE_BLAST_RADIUS, STRIKE_DAMAGE,
                STRIKE_PENETRATION, 0, side, /*aerialDelivery*/ false));
        sim.spawnHeavyImpact(bx, by, STRIKE_BLAST_RADIUS, STRIKE_BLAST_TINT);
        strikesDetonated++;
        lastStrikeX = bx;
        lastStrikeY = by;
        if (sim.resolveUnit(drone) != 0L) sim.applyExternalDamage(drone, DESTROY, DESTROY);
        wing.lost(drone);
    }

    private void launch(DroneWing wing, DroneRole role, Faction side, GroundBody body, long focus,
                        BattleSimulation sim) {
        if (wing.squadId < 0) {
            wing.squadId = sim.mintSquad(side, UnitType.DRONE);
            sim.getSquad(wing.squadId).droneAnchor = wing;
        }
        int[] cell = openCell(sim.getGrid(), body.x, body.y);
        EntitySpec spec = Drone.create("bay-drone-" + nextDrone++, side, cell[0], cell[1], 0L, role.look())
                .squad(wing.squadId)
                .health(role == DroneRole.STRIKE ? STRIKE_DRONE_HP : BAY_DRONE_HP)
                .armor(BAY_DRONE_ARMOR, BAY_DRONE_ARMOR_RATING);
        if (role == DroneRole.SENSOR || role == DroneRole.HALO) {
            spec.primaryWeapon(WeaponRegistry.require(WeaponRegistry.DRONE_ION_ID));
        }
        long id = sim.spawn(spec);
        if (id == 0L) return;
        AirBody air = sim.world().kinematics(id);
        if (air != null) {
            air.teleport(body.x, body.y, body.facingDegrees);
            sim.world().setPos(id, body.x, body.y);
        }
        wing.launched(id, role);
        if (role == DroneRole.HALO) sim.pointDefense().queuePlacement(id, side, cell[0], cell[1], HALO_MOUNT);
        if (role == DroneRole.STRIKE && focus != 0L) {
            wing.aimStrike(focus, sim.world().x(focus), sim.world().y(focus));
        }
        if (sim.resolveUnit(sim.getSquad(wing.squadId).leaderId) == 0L) sim.getSquad(wing.squadId).leaderId = id;
    }

    private static void publish(DroneWing wing, long focus, float seconds, BattleSimulation sim) {
        World world = sim.world();
        ArrayList<Long> drones = new ArrayList<>(wing.drones());
        long[] ids = new long[drones.size()];
        float[] at = new float[ids.length * 2];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = drones.get(i);
            at[2 * i] = world.x(ids[i]);
            at[2 * i + 1] = world.y(ids[i]);
        }
        wing.publish(ids, at, focus, seconds);
    }

    private static long nearestHostile(Faction side, DroneWing wing, BattleSimulation sim) {
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        long[] dense = roster.denseArray();
        long best = 0L;
        float bestD2 = DroneWing.REACH_CELLS * DroneWing.REACH_CELLS;
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long u = dense[i];
            if (roster.isRiding(u)) continue;
            if (!side.hostileTo(roster.identity().faction(u))) continue;
            float dx = world.x(u) - wing.anchorX();
            float dy = world.y(u) - wing.anchorY();
            float d2 = dx * dx + dy * dy;
            if (d2 < bestD2) {
                bestD2 = d2;
                best = u;
            }
        }
        return best;
    }

    /** The walkable cell nearest the bay, for the drone's roster cell; a hull over water launches from shore. */
    private static int[] openCell(NavigationGrid grid, float x, float y) {
        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        for (int r = 0; r <= 4; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    if (grid.inBounds(cx + dx, cy + dy) && grid.isWalkable(cx + dx, cy + dy)) {
                        return new int[]{cx + dx, cy + dy};
                    }
                }
            }
        }
        return new int[]{Math.max(0, Math.min(grid.getWidth() - 1, cx)),
                Math.max(0, Math.min(grid.getHeight() - 1, cy))};
    }
}
