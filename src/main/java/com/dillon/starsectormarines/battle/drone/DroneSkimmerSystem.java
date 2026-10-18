package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Flies the drone skimmers' tethered drones ({@link VehicleType#droneComplement()}).
 *
 * <p>Each tick, in the serial part of the tick: a hull that has come onto the field gets a {@link SkimmerWing} and a
 * drone squad anchored to it; its bay launches one drone at a time until the complement is out; the wing follows the
 * hull, forgets drones that have been shot down and publishes its roster and the hostile nearest the hull. Flight,
 * formation and fire are the shared swarm action's ({@link DroneSwarmAction}) — ordinary drones with the
 * drone-pulse emitter, nothing new.
 *
 * <p>When the hull is wrecked or leaves the field the wing's anchor dies and every drone still out is destroyed where
 * it flies, so it falls through the ordinary drone crash ({@link DroneCrashSystem}). A drone lost stays lost; drones
 * never launched are simply gone with the hull.
 */
public final class DroneSkimmerSystem {

    private static final float DESTROY = 1.0e6f;
    /** Ticks between scans for the hostile nearest each hull. */
    static final int SCAN_TICKS = 6;

    private final Map<Long, SkimmerWing> wings = new LinkedHashMap<>();
    private int scanCountdown;
    private int nextDrone;

    /** The wing tethered to {@code hullId}, or null when that hull carries none or has not come onto the field. */
    public SkimmerWing wing(long hullId) {
        return wings.get(hullId);
    }

    public void tick(float dt, BattleSimulation sim) {
        ConvoyService convoy = sim.convoy();
        boolean scan = scanCountdown-- <= 0;
        if (scan) scanCountdown = SCAN_TICKS - 1;
        if (convoy.vehicleCount() > 0) {
            for (long hull : convoy.entityIds()) {
                VehicleType type = convoy.vehicleType(hull);
                VehicleMission mission = convoy.mission(hull);
                if (type == null || type.droneComplement() <= 0 || mission == null || !mission.isVisible()
                        || mission.state == VehicleState.WRECKED || wings.containsKey(hull)) continue;
                wings.put(hull, new SkimmerWing(hull, type.droneComplement(), type.visualLengthCells));
            }
        }
        for (Iterator<SkimmerWing> it = wings.values().iterator(); it.hasNext(); ) {
            SkimmerWing wing = it.next();
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
            if (side != null && wing.readyToLaunch(dt)) launch(wing, side, body, sim);
            publish(wing, side, scan, sim);
        }
    }

    /** The hull is lost: nothing more launches, and every drone still out comes down where it flies. */
    private static void collapse(SkimmerWing wing, BattleSimulation sim) {
        wing.hullLost();
        for (Long id : wing.drones()) {
            if (sim.resolveUnit(id) != 0L) sim.applyExternalDamage(id, DESTROY, DESTROY);
            wing.lost(id);
        }
    }

    private void launch(SkimmerWing wing, Faction side, GroundBody body, BattleSimulation sim) {
        if (wing.squadId < 0) {
            wing.squadId = sim.mintSquad(side, UnitType.DRONE);
            sim.getSquad(wing.squadId).droneAnchor = wing;
        }
        int[] cell = openCell(sim.getGrid(), body.x, body.y);
        EntitySpec spec = Drone.create("skimmer-drone-" + nextDrone++, side, cell[0], cell[1], 0L,
                DroneLook.STANDARD).squad(wing.squadId);
        long id = sim.spawn(spec);
        if (id == 0L) return;
        AirBody air = sim.world().kinematics(id);
        if (air != null) {
            air.teleport(body.x, body.y, body.facingDegrees);
            sim.world().setPos(id, body.x, body.y);
        }
        wing.launched(id);
        if (sim.resolveUnit(sim.getSquad(wing.squadId).leaderId) == 0L) sim.getSquad(wing.squadId).leaderId = id;
    }

    private static void publish(SkimmerWing wing, Faction side, boolean scan, BattleSimulation sim) {
        World world = sim.world();
        ArrayList<Long> drones = new ArrayList<>(wing.drones());
        long[] ids = new long[drones.size()];
        float[] at = new float[ids.length * 2];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = drones.get(i);
            at[2 * i] = world.x(ids[i]);
            at[2 * i + 1] = world.y(ids[i]);
        }
        long focus = scan && side != null ? nearestHostile(side, wing, sim) : wing.focusTarget();
        wing.publish(ids, at, focus);
    }

    private static long nearestHostile(Faction side, SkimmerWing wing, BattleSimulation sim) {
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        long[] dense = roster.denseArray();
        long best = 0L;
        float bestD2 = SkimmerWing.REACH_CELLS * SkimmerWing.REACH_CELLS;
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long u = dense[i];
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

    /** The walkable cell nearest the bay, for the drone's roster cell; a skimmer over water launches from shore. */
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
