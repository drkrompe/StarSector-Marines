package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the death-event seam: a {@link MapTurret} killed
 * through the real damage path publishes a {@code DeathEvent}, and
 * {@link TurretDemolitionSystem} (subscribed to the sim's dispatcher) flips the
 * mount cell to rubble + drops a smoking wreck when the mailbox drains.
 *
 * <p>Also pins the buffering contract — the demolition is NOT applied at the
 * moment of death (inline {@code applyDamage}), only when the per-tick
 * {@code DeathDispatcher.drain()} runs at the demolition phase.
 */
public class TurretDemolitionSystemTest {

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
    public void deadTurretIsDemolishedWhenTheMailboxDrains() {
        BattleSimulation sim = openArena(20, 20);
        MapTurret turret = new MapTurret("t0", Faction.DEFENDER, TurretKind.VULCAN, 10, 10);
        sim.addUnit(turret);
        int wrecksBefore = sim.getSmokingWrecks().size();

        // Lethal hit, routed through the production damage path so the death
        // cascade (and the DeathEvent publish) actually runs.
        sim.applyDamage(turret, 100_000f, 3.5f, 0f);

        assertFalse(turret.isAlive(), "the turret should be dead after a lethal hit");
        // Buffered: the handler has NOT run yet — death published, not drained.
        assertFalse(turret.demolished,
                "demolition must wait for the dispatcher drain, not fire inline at death");

        // One tick drains the mailbox at the demolition phase.
        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(turret.demolished, "drain → turret-demolition handler flips the dead turret");
        assertEquals(CellTopology.GroundKind.RUBBLE, sim.getTopology().getGroundKind(10, 10),
                "the mount cell flips to walkable rubble");
        assertTrue(sim.getSmokingWrecks().size() > wrecksBefore,
                "a smoking wreck is dropped on the mount cell");
    }

    @Test
    public void liveTurretIsLeftAlone() {
        BattleSimulation sim = openArena(20, 20);
        MapTurret turret = new MapTurret("t0", Faction.DEFENDER, TurretKind.VULCAN, 10, 10);
        sim.addUnit(turret);

        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(turret.isAlive(), "no damage → still alive");
        assertFalse(turret.demolished, "a live turret is never demolished");
    }
}
