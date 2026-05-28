package com.dillon.starsectormarines.battle.ai;
import com.dillon.starsectormarines.battle.decision.UnitBehavior;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.drone.Drone;
import com.dillon.starsectormarines.battle.drone.DroneHubUnit;
import com.dillon.starsectormarines.battle.drone.DroneSpawner;
import com.dillon.starsectormarines.battle.unit.Unit;

/**
 * Per-tick driver for a {@link DroneHubUnit}: counts down the spawn timer and
 * asks {@link DroneSpawner} to launch a drone whenever the hub is below its
 * active-drone cap. The hub itself fires nothing and moves nowhere — this
 * behavior exists only to keep the spawn cadence on the same tick dispatch
 * every other unit's logic uses.
 */
public final class DroneHubBehavior implements UnitBehavior {

    public static final DroneHubBehavior INSTANCE = new DroneHubBehavior();

    private DroneHubBehavior() {}

    @Override
    public void update(Unit u, BattleSimulation sim) {
        if (!(u instanceof DroneHubUnit)) return;
        DroneHubUnit hub = (DroneHubUnit) u;
        if (!hub.isAlive()) return;
        hub.spawnCooldown -= BattleSimulation.TICK_DT;
        if (hub.spawnCooldown > 0f) return;
        int active = countActiveDrones(sim, hub);
        if (active < DroneHubUnit.MAX_ACTIVE_DRONES) {
            DroneSpawner.tryLaunch(hub, sim);
        }
        // Reset whether or not the launch placed a drone — a failed try (no
        // free cell within the search radius) waits the same interval before
        // re-attempting. Avoids a busy-loop scanning every tick when the area
        // around the hub is fully crowded.
        hub.spawnCooldown = DroneHubUnit.SPAWN_INTERVAL_SEC;
    }

    private static int countActiveDrones(BattleSimulation sim, DroneHubUnit hub) {
        int n = 0;
        for (Unit u : sim.getUnits()) {
            if (!(u instanceof Drone)) continue;
            if (!u.isAlive()) continue;
            if (((Drone) u).homeHub == hub) n++;
        }
        return n;
    }
}
