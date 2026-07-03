package com.dillon.starsectormarines.battle.drone;
import com.dillon.starsectormarines.battle.decision.UnitBehavior;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Entity;

/**
 * Per-tick driver for a drone hub ({@code UnitType.isDroneHub()}): counts down
 * the {@code HUB_STATE} spawn cooldown and asks {@link DroneSpawner} to launch
 * a drone whenever the hub is below its active-drone cap. The hub itself fires
 * nothing and moves nowhere — this behavior exists only to keep the spawn
 * cadence on the same tick dispatch every other unit's logic uses.
 */
public final class DroneHubBehavior implements UnitBehavior {

    public static final DroneHubBehavior INSTANCE = new DroneHubBehavior();

    private DroneHubBehavior() {}

    @Override
    public void update(long u, BattleSimulation sim) {
        if (!sim.identity().type(u).isDroneHub()) return;
        if (!sim.world().isAlive(u)) return;
        float cooldown = sim.hubState().spawnCooldown(u) - BattleSimulation.TICK_DT;
        if (cooldown > 0f) {
            sim.hubState().setSpawnCooldown(u, cooldown);
            return;
        }
        int active = countActiveDrones(sim, u);
        if (active < DroneHub.MAX_ACTIVE_DRONES) {
            DroneSpawner.tryLaunch(u, sim);
        }
        // Reset whether or not the launch placed a drone — a failed try (no
        // free cell within the search radius) waits the same interval before
        // re-attempting. Avoids a busy-loop scanning every tick when the area
        // around the hub is fully crowded.
        sim.hubState().setSpawnCooldown(u, DroneHub.SPAWN_INTERVAL_SEC);
    }

    private static int countActiveDrones(BattleView sim, long hub) {
        int n = 0;
        for (int i = 0, live = sim.liveUnitCount(); i < live; i++) {
            Entity u = sim.liveUnitAt(i);
            if (!u.type.isDrone()) continue;
            if (sim.droneState().homeHubId(u.entityId) == hub) n++;
        }
        return n;
    }
}
