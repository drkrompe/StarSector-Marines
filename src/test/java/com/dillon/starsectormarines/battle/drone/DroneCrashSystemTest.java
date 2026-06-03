package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the drone crash modelled as a component: a dead
 * {@link Drone} gets a {@code Crashing} component attached off its DeathEvent,
 * the crash system processes the component-set (not a units-list scan) to spin
 * + count down the fall, and on impact drops a smoking wreck and detaches the
 * component.
 *
 * <p>A live MARINE and a live (inert) DEFENDER hub are parked far apart so the
 * battle stays in progress across the ~0.7s crash window — otherwise the
 * win-check would complete once the target drone (the only other DEFENDER unit)
 * dies and {@code advance()} would no-op before the crash settles.
 */
public class DroneCrashSystemTest {

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

    /** A drone homed to a hub kept alive far away — so killing the drone doesn't end the battle. */
    private static Drone parkArenaWithDrone(BattleSimulation sim) {
        sim.addUnit(new Unit("m0", Faction.MARINE, UnitType.MARINE, 1, 1));
        DroneHubUnit keepAlive = new DroneHubUnit("hub", Faction.DEFENDER, 38, 38);
        sim.addUnit(keepAlive);
        Drone drone = new Drone("d0", Faction.DEFENDER, 20, 20, keepAlive);
        sim.addUnit(drone);
        return drone;
    }

    @Test
    public void shotDownDroneGetsACrashingComponentThenSettlesIntoAWreck() {
        BattleSimulation sim = openArena(40, 40);
        Drone drone = parkArenaWithDrone(sim);
        int wrecksBefore = sim.getSmokingWrecks().size();

        sim.applyDamage(drone, 100_000f, 20f, 20f);
        assertFalse(sim.world().isAlive(drone.entityId), "lethal hit kills the drone");
        // Buffered: no component until the death mailbox drains in the tick.
        assertFalse(sim.getCrashing().has(drone.entityId),
                "the Crashing component attaches on the death drain, not inline");

        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(sim.getCrashing().has(drone.entityId),
                "drain → the dead drone gets a Crashing component (the crash started)");
        assertEquals(wrecksBefore, sim.getSmokingWrecks().size(),
                "no wreck yet — the drone is still falling");

        // Run past the crash window: the fall settles into a wreck and the
        // component is detached.
        for (int i = 0; i < 30; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }
        assertFalse(sim.getCrashing().has(drone.entityId),
                "on impact the Crashing component is detached (crash done)");
        assertTrue(sim.getSmokingWrecks().size() > wrecksBefore,
                "a smoking wreck is dropped at the impact site");
    }

    @Test
    public void liveDroneNeverGetsACrashingComponent() {
        BattleSimulation sim = openArena(40, 40);
        Drone drone = parkArenaWithDrone(sim);

        for (int i = 0; i < 5; i++) {
            sim.advance(BattleSimulation.TICK_DT);
        }

        assertTrue(sim.world().isAlive(drone.entityId), "no damage → still flying");
        assertFalse(sim.getCrashing().has(drone.entityId), "a live drone is never crashing");
        assertTrue(sim.getCrashing().isEmpty(), "no crash components for an undamaged arena");
    }
}
