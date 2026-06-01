package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the hub side of the death-event seam: a
 * {@link DroneHubUnit} killed through the real damage path publishes a
 * {@code DeathEvent}, and {@link HubDemolitionSystem} (subscribed to the sim's
 * dispatcher) flips the hub cell to rubble, drops a smoking wreck, and
 * cascade-kills the hub's launched drones when the mailbox drains.
 *
 * <p>Also pins the buffering contract (demolition waits for the drain, not the
 * inline {@code applyDamage}) and the same-tick cascade→crash ordering: the
 * cascade-killed drones are picked up by the crash system on the very tick the
 * hub dies, because the demolition drain runs before the drone-crash phase.
 */
public class HubDemolitionSystemTest {

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
    public void deadHubIsDemolishedWhenTheMailboxDrains() {
        BattleSimulation sim = openArena(20, 20);
        DroneHubUnit hub = new DroneHubUnit("h0", Faction.DEFENDER, 10, 10);
        sim.addUnit(hub);
        int wrecksBefore = sim.getSmokingWrecks().size();

        sim.applyDamage(hub, 100_000f, 3.5f, 0f);

        assertFalse(hub.isAlive(), "the hub should be dead after a lethal hit");
        // Buffered: the handler has NOT run yet — death published, not drained.
        assertFalse(hub.demolished,
                "demolition must wait for the dispatcher drain, not fire inline at death");

        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(hub.demolished, "drain → hub-demolition handler flips the dead hub");
        assertEquals(CellTopology.GroundKind.RUBBLE, sim.getTopology().getGroundKind(10, 10),
                "the hub cell flips to walkable rubble");
        assertTrue(sim.getSmokingWrecks().size() > wrecksBefore,
                "a smoking wreck is dropped on the hub cell");
    }

    @Test
    public void hubDeathCascadeKillsItsOwnDronesAndStartsTheirCrashSameTick() {
        BattleSimulation sim = openArena(30, 30);
        // The hub that dies, with two drones it launched.
        DroneHubUnit deadHub = new DroneHubUnit("h0", Faction.DEFENDER, 10, 10);
        Drone d1 = new Drone("d1", Faction.DEFENDER, 10, 11, deadHub);
        Drone d2 = new Drone("d2", Faction.DEFENDER, 10, 9, deadHub);
        // A second, untouched hub with its own drone — the cascade must leave
        // a drone that calls a DIFFERENT hub home completely alone.
        DroneHubUnit liveHub = new DroneHubUnit("h1", Faction.DEFENDER, 20, 20);
        Drone control = new Drone("c0", Faction.DEFENDER, 20, 21, liveHub);
        sim.addUnit(deadHub);
        sim.addUnit(d1);
        sim.addUnit(d2);
        sim.addUnit(liveHub);
        sim.addUnit(control);

        sim.applyDamage(deadHub, 100_000f, 3.5f, 0f);
        sim.advance(BattleSimulation.TICK_DT);

        // The dead hub's drones are killed by the cascade and — because each
        // publishes a DeathEvent that the wave-drain fans out in the same drain,
        // before the drone-crash phase — already carry a Crashing component
        // (entered the crash sequence) on this same tick.
        assertFalse(d1.isAlive(), "cascade sets hp=0 on the dead hub's drones");
        assertFalse(d2.isAlive(), "cascade sets hp=0 on the dead hub's drones");
        assertTrue(sim.getCrashing().has(d1.entityId), "cascaded drone gets a Crashing component the same tick");
        assertTrue(sim.getCrashing().has(d2.entityId), "cascaded drone gets a Crashing component the same tick");

        // The other hub's drone is untouched.
        assertTrue(control.isAlive(), "a drone homed to a live hub is not part of the cascade");
        assertFalse(sim.getCrashing().has(control.entityId), "the untouched drone never starts crashing");
        assertFalse(liveHub.demolished, "the undamaged hub is not demolished");
    }

    @Test
    public void liveHubIsLeftAlone() {
        BattleSimulation sim = openArena(20, 20);
        DroneHubUnit hub = new DroneHubUnit("h0", Faction.DEFENDER, 10, 10);
        sim.addUnit(hub);

        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(hub.isAlive(), "no damage → still alive");
        assertFalse(hub.demolished, "a live hub is never demolished");
    }
}
