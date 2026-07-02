package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cadence coverage for {@link DroneHubBehavior}: the {@code HUB_STATE}
 * spawn-cooldown ticks down by one {@code TICK_DT} per update, and resets to
 * {@link DroneHub#SPAWN_INTERVAL_SEC} once a launch attempt fires (regardless
 * of whether the attempt actually placed a drone).
 */
public class DroneHubBehaviorTest {

    private static BattleSimulation openArena(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, topology);
    }

    @Test
    public void spawnCooldownTicksDownByOneTickDtPerUpdate() {
        BattleSimulation sim = openArena(20, 20);
        Entity hub = DroneHub.create("h0", Faction.DEFENDER, 10, 10).toEntity();
        sim.addUnit(hub);

        DroneHubBehavior.INSTANCE.update(hub, sim);

        assertEquals(DroneHub.INITIAL_SPAWN_DELAY_SEC - BattleSimulation.TICK_DT,
                sim.hubState().spawnCooldown(hub.entityId), 1e-4f,
                "one update decrements the cooldown by exactly one TICK_DT");
    }

    @Test
    public void cooldownResetsToTheSteadyStateIntervalAfterALaunchAttempt() {
        BattleSimulation sim = openArena(20, 20);
        Entity hub = DroneHub.create("h0", Faction.DEFENDER, 10, 10).toEntity();
        sim.addUnit(hub);

        // Force the cooldown to the edge of expiry so the next update attempts
        // a launch — the reset must fire whether or not the attempt actually
        // found a free cell (a failed try still waits the full interval).
        sim.hubState().setSpawnCooldown(hub.entityId, 0.005f);
        DroneHubBehavior.INSTANCE.update(hub, sim);

        assertEquals(DroneHub.SPAWN_INTERVAL_SEC, sim.hubState().spawnCooldown(hub.entityId), 1e-4f,
                "a launch attempt resets the cooldown to SPAWN_INTERVAL_SEC");
    }
}
