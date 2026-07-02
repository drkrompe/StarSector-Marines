package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the hub side of the death-event seam: a drone hub
 * ({@link DroneHub}-built entity) killed through the real damage path
 * publishes a {@code DeathEvent}, and {@link HubDemolitionSystem} (subscribed
 * to the sim's dispatcher) flips the hub cell to rubble, drops a smoking
 * wreck, and cascade-kills the hub's launched drones when the mailbox drains.
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

    /** Whether {@code id} carries the world's CRASHING component (the post-fold presence check). */
    private static boolean isCrashing(BattleSimulation sim, long id) {
        return sim.getEntityWorld().has(id, sim.getBattleComponents().CRASHING);
    }

    @Test
    public void deadHubIsDemolishedWhenTheMailboxDrains() {
        BattleSimulation sim = openArena(20, 20);
        Entity hub = DroneHub.create("h0", Faction.DEFENDER, 10, 10);
        sim.addUnit(hub);
        int wrecksBefore = sim.getSmokingWrecks().size();

        sim.applyDamage(hub, 100_000f, 3.5f, 0f);

        assertFalse(sim.world().isAlive(hub.entityId), "the hub should be dead after a lethal hit");
        // Buffered: the handler has NOT run yet — death published, not drained.
        assertFalse(sim.getHubDemolitionSystem().isDemolished(hub.entityId),
                "demolition must wait for the dispatcher drain, not fire inline at death");

        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(sim.getHubDemolitionSystem().isDemolished(hub.entityId),
                "drain → hub-demolition handler flips the dead hub");
        assertEquals(CellTopology.GroundKind.RUBBLE, sim.getTopology().getGroundKind(10, 10),
                "the hub cell flips to walkable rubble");
        assertTrue(sim.getSmokingWrecks().size() > wrecksBefore,
                "a smoking wreck is dropped on the hub cell");
    }

    @Test
    public void hubDeathCascadeKillsItsOwnDronesAndStartsTheirCrashSameTick() {
        BattleSimulation sim = openArena(30, 30);
        // The hub that dies, with two drones it launched.
        Entity deadHub = DroneHub.create("h0", Faction.DEFENDER, 10, 10);
        sim.addUnit(deadHub);
        Entity d1 = Drone.create("d1", Faction.DEFENDER, 10, 11, deadHub.entityId);
        Entity d2 = Drone.create("d2", Faction.DEFENDER, 10, 9, deadHub.entityId);
        // A second, untouched hub with its own drone — the cascade must leave
        // a drone that calls a DIFFERENT hub home completely alone.
        Entity liveHub = DroneHub.create("h1", Faction.DEFENDER, 20, 20);
        sim.addUnit(liveHub);
        Entity control = Drone.create("c0", Faction.DEFENDER, 20, 21, liveHub.entityId);
        sim.addUnit(d1);
        sim.addUnit(d2);
        sim.addUnit(control);

        sim.applyDamage(deadHub, 100_000f, 3.5f, 0f);
        sim.advance(BattleSimulation.TICK_DT);

        // The dead hub's drones are killed by the cascade and — because each
        // publishes a DeathEvent that the wave-drain fans out in the same drain,
        // before the drone-crash phase — already carry a CrashingComponent component
        // (entered the crash sequence) on this same tick.
        assertFalse(sim.world().isAlive(d1.entityId), "cascade sets hp=0 on the dead hub's drones");
        assertFalse(sim.world().isAlive(d2.entityId), "cascade sets hp=0 on the dead hub's drones");
        assertTrue(isCrashing(sim, d1.entityId), "cascaded drone gets a CRASHING component the same tick");
        assertTrue(isCrashing(sim, d2.entityId), "cascaded drone gets a CRASHING component the same tick");

        // The other hub's drone is untouched.
        assertTrue(sim.world().isAlive(control.entityId), "a drone homed to a live hub is not part of the cascade");
        assertFalse(isCrashing(sim, control.entityId), "the untouched drone never starts crashing");
        assertFalse(sim.getHubDemolitionSystem().isDemolished(liveHub.entityId), "the undamaged hub is not demolished");
    }

    @Test
    public void liveHubIsLeftAlone() {
        BattleSimulation sim = openArena(20, 20);
        Entity hub = DroneHub.create("h0", Faction.DEFENDER, 10, 10);
        sim.addUnit(hub);

        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(sim.world().isAlive(hub.entityId), "no damage → still alive");
        assertFalse(sim.getHubDemolitionSystem().isDemolished(hub.entityId), "a live hub is never demolished");
    }
}
