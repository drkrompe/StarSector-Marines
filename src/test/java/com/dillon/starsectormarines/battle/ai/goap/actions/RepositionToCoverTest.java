package com.dillon.starsectormarines.battle.ai.goap.actions;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Doodad;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.TileManifest;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.UnitType;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link RepositionToCover}'s cooldown gating + cover-preferred
 * shift. Story G's two load-bearing behaviors:
 * <ul>
 *   <li>A marine in better cover than nearby alternatives stays put
 *       (the MG-in-heavy-cover "cozy" case).</li>
 *   <li>A marine in open ground next to a doodad shifts to the doodad cell
 *       when their cooldown is ready, and only then.</li>
 * </ul>
 */
public class RepositionToCoverTest {

    private static BattleSimulation openArena(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, topology);
    }

    private static Unit marineAt(BattleSimulation sim, int x, int y) {
        Unit u = new Unit("m" + sim.getUnits().size(), Faction.MARINE, UnitType.MARINE, x, y);
        sim.getUnits().add(u);
        return u;
    }

    private static Unit enemyAt(BattleSimulation sim, int x, int y) {
        Unit u = new Unit("e" + sim.getUnits().size(), Faction.DEFENDER, UnitType.MARINE, x, y);
        sim.getUnits().add(u);
        return u;
    }

    @Test
    public void cooldownGatesReposition() {
        BattleSimulation sim = openArena(20, 20);
        Unit marine = marineAt(sim, 5, 5);
        Unit threat = enemyAt(sim, 15, 5);
        marine.target = threat;
        // Heavy cover right next to the marine.
        sim.addDoodad(new Doodad(6, 5, new TileManifest.TileFrame(4, 7)));
        // Cooldown set — the action must refuse to move.
        marine.repositionCooldown = 0.5f;

        boolean moved = RepositionToCover.tryReposition(marine, sim);
        assertFalse(moved, "cooldown > 0 must block reposition");
        assertTrue(marine.pathEmpty(), "no path queued when cooldown blocks the move");
    }

    @Test
    public void noTargetReturnsFalse() {
        BattleSimulation sim = openArena(20, 20);
        Unit marine = marineAt(sim, 5, 5);
        marine.target = null;
        marine.repositionCooldown = 0f;

        boolean moved = RepositionToCover.tryReposition(marine, sim);
        assertFalse(moved, "reposition needs a target to compute threat direction");
    }

    @Test
    public void alreadyInBestCoverDoesNotMove() {
        // Marine sits on the heavy crate cell — that's the best E-facing
        // cover available. findFiringPositionCoverPreferred won't pick
        // anywhere else (no candidate has higher cover). Story G's
        // MG-in-cover-stays-put property.
        BattleSimulation sim = openArena(20, 20);
        sim.addDoodad(new Doodad(5, 5, new TileManifest.TileFrame(4, 7)));
        Unit marine = marineAt(sim, 5, 5);
        marine.attackRange = 10f;
        Unit threat = enemyAt(sim, 12, 5);
        marine.target = threat;
        marine.repositionCooldown = 0f;

        boolean moved = RepositionToCover.tryReposition(marine, sim);
        // Either no move (best cell IS current) or the action stamps a
        // cooldown but doesn't queue a path. The contract is "didn't actually
        // move" so the marine sits in their cozy cover.
        assertFalse(moved,
                "marine already on best-cover cell must not reposition (Story G: heavy-cover MG stays put)");
    }

    @Test
    public void shiftsToBetterCoverWhenCooldownReady() {
        BattleSimulation sim = openArena(20, 20);
        // Heavy crate at (7, 5). Marine starts at (5, 5) — two cells west of
        // the crate, so the doodad isn't a direct cardinal neighbor of the
        // marine's cell; current E-facing cover = 0. (6, 5) IS a direct
        // west-of-crate neighbor and gets E-facing cover 3. Marine should
        // shift one cell east.
        sim.addDoodad(new Doodad(7, 5, new TileManifest.TileFrame(4, 7))); // heavy
        Unit marine = marineAt(sim, 5, 5);
        marine.attackRange = 10f;
        Unit threat = enemyAt(sim, 12, 5);
        marine.target = threat;
        marine.repositionCooldown = 0f;

        boolean moved = RepositionToCover.tryReposition(marine, sim);
        assertTrue(moved, "open-ground marine within reach of a heavy crate should shift");
        assertEquals(RepositionToCover.COOLDOWN_SECONDS, marine.repositionCooldown, 1e-6f,
                "successful reposition stamps the cooldown so the marine doesn't churn");
        assertFalse(marine.pathEmpty(), "successful reposition queues a path to the new cell");
    }
}
