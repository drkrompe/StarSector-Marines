package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Doodad;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.TileManifest;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.UnitType;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Standalone scoring tests for {@link TacticalScoring}. Each test builds the
 * smallest sim it can — a NavigationGrid, a CellTopology, and a handful of
 * Units / Doodads — and asserts a single scorer property. No combat, no
 * pathfinding, no GOAP planning.
 */
public class TacticalScoringTest {

    /**
     * A 20×20 open arena. All cells walkable, zero wall cover, no doodads
     * unless a test adds them. Used as the baseline for every test below.
     */
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

    private static Unit unit(BattleSimulation sim, Faction f, int x, int y) {
        Unit u = new Unit("u" + sim.getUnits().size(), f, UnitType.MARINE, x, y);
        sim.getUnits().add(u);
        return u;
    }

    // ---------------------------------------------------------------------
    // Part 1 — Cover model
    // ---------------------------------------------------------------------

    @Test
    public void doodadDefaultCoverFromTile() {
        // Crate doodad on row 1 col 8 — should report medium cover.
        Doodad crate = new Doodad(3, 3, new TileManifest.TileFrame(8, 1));
        assertEquals(Doodad.COVER_MED, crate.cover);

        // Shelf doodad on row 7 col 4 — should report heavy cover.
        Doodad shelf = new Doodad(4, 4, new TileManifest.TileFrame(4, 7));
        assertEquals(Doodad.COVER_HEAVY, shelf.cover);

        // Rubble decal — light cover.
        Doodad rubble = new Doodad(5, 5, new TileManifest.TileFrame(0, 7));
        assertEquals(Doodad.COVER_LIGHT, rubble.cover);

        // LZ pad — no cover.
        Doodad pad = new Doodad(6, 6, TileManifest.LZ_PAD, true);
        assertEquals(Doodad.COVER_NONE, pad.cover);
    }

    @Test
    public void simIndexReportsMaxCoverPerCell() {
        BattleSimulation sim = openArena(10, 10);
        sim.addDoodad(new Doodad(4, 4, new TileManifest.TileFrame(8, 1))); // crate, COVER_MED
        sim.addDoodad(new Doodad(4, 4, new TileManifest.TileFrame(4, 7))); // shelf, COVER_HEAVY
        sim.addDoodad(new Doodad(5, 5, new TileManifest.TileFrame(0, 7))); // rubble, COVER_LIGHT

        assertEquals(Doodad.COVER_HEAVY, sim.getDoodadCoverAt(4, 4),
                "stacked doodads on one cell collapse to the max cover");
        assertEquals(Doodad.COVER_LIGHT, sim.getDoodadCoverAt(5, 5));
        assertEquals(Doodad.COVER_NONE, sim.getDoodadCoverAt(0, 0));
        assertEquals(Doodad.COVER_NONE, sim.getDoodadCoverAt(-1, 0),
                "out-of-bounds cell returns 0");
    }

    // ---------------------------------------------------------------------
    // Part 2 — bestCoverCell
    // ---------------------------------------------------------------------

    @Test
    public void bestCoverCellPicksHighestCoverWalkableWithLos() {
        BattleSimulation sim = openArena(20, 20);
        // Threat at (15, 10). Anchor (marine) at (10, 10). Radius 4.
        // Several candidate doodad-cover cells in range:
        //   (12, 10) — heavy doodad cover (shelf)
        //   (11, 10) — medium doodad cover (crate)
        //   (10,  9) — light doodad cover (rubble)
        // All cells have LOS to (15, 10) in an open arena. The picker should
        // pick (12, 10).
        sim.addDoodad(new Doodad(12, 10, new TileManifest.TileFrame(4, 7))); // shelf, HEAVY
        sim.addDoodad(new Doodad(11, 10, new TileManifest.TileFrame(8, 1))); // crate, MED
        sim.addDoodad(new Doodad(10,  9, new TileManifest.TileFrame(0, 7))); // rubble, LIGHT

        int[] pick = TacticalScoring.bestCoverCell(15, 10, 10, 10, 4, sim);
        assertNotNull(pick, "open arena with LOS should always pick something");
        assertEquals(12, pick[0]);
        assertEquals(10, pick[1]);
    }

    @Test
    public void bestCoverCellSkipsCellsWithoutLos() {
        BattleSimulation sim = openArena(20, 20);
        NavigationGrid grid = sim.getGrid();
        // Knock out a column of cells to block LOS to a high-cover doodad.
        // Threat at (15, 5). Anchor at (5, 5). Wall column at x=8 from y=4..6.
        // Cell (6, 5) carries a heavy doodad but is now LOS-blocked.
        // Cell (12, 5) carries a medium doodad with clear LOS.
        for (int y = 4; y <= 6; y++) grid.setWalkable(8, y, false);
        sim.addDoodad(new Doodad(6, 5, new TileManifest.TileFrame(4, 7)));   // heavy, blocked
        sim.addDoodad(new Doodad(12, 5, new TileManifest.TileFrame(8, 1)));  // medium, visible

        int[] pick = TacticalScoring.bestCoverCell(15, 5, 9, 5, 6, sim);
        assertNotNull(pick);
        // The heavy-cover cell is on the wrong side of the wall — LOS-test
        // rejects it, so the medium-cover visible cell wins.
        assertEquals(12, pick[0]);
        assertEquals(5, pick[1]);
    }

    @Test
    public void bestCoverCellReturnsNullWhenNoLos() {
        BattleSimulation sim = openArena(20, 20);
        NavigationGrid grid = sim.getGrid();
        // Solid wall across x=10, y=0..19, isolating left from right.
        for (int y = 0; y < 20; y++) grid.setWalkable(10, y, false);
        // Threat on the right, candidates only on the left.
        int[] pick = TacticalScoring.bestCoverCell(15, 5, 5, 5, 3, sim);
        assertNull(pick, "no candidate has LOS through the wall");
    }

    // ---------------------------------------------------------------------
    // Part 3 — threat-density target picker (Story I)
    // ---------------------------------------------------------------------

    @Test
    public void threatDensityPickerAvoidsClusteredFleer() {
        BattleSimulation sim = openArena(40, 20);

        // Marine on the left side.
        Unit marine = unit(sim, Faction.MARINE, 5, 10);
        // No squad for the marine in this test — we isolate the density
        // penalty from the cohesion clamp.
        marine.attackRange = 30f;

        // Wounded fleer at (15, 10), surrounded by 3 squadmates: a tight
        // cluster well inside THREAT_DENSITY_RADIUS.
        Unit fleer = unit(sim, Faction.DEFENDER, 15, 10);
        unit(sim, Faction.DEFENDER, 14, 10);
        unit(sim, Faction.DEFENDER, 16, 10);
        unit(sim, Faction.DEFENDER, 15, 11);

        // Isolated lone enemy at (20, 10), no other enemies within radius.
        Unit lone = unit(sim, Faction.DEFENDER, 20, 10);

        // Both cells visible from the marine in an open arena. With Stage 1's
        // distance-only score, the picker would pick the fleer (distance 10
        // vs 15). With the density penalty (3 neighbors × 5 = 15) the fleer's
        // effective score becomes 25 vs the lone enemy's 15.
        Unit picked = TacticalScoring.findBestTarget(marine, sim);
        assertNotNull(picked);
        assertEquals(lone, picked,
                "picker should avoid the cluster and pick the isolated lone enemy");
        assertNotEquals(fleer, picked,
                "wounded fleer in a cluster must NOT be the lowest-cost target");
    }

    @Test
    public void targetPickerPicksFarTargetWhenItsTheOnlyOne() {
        // Cohesion is a movement constraint (handled in the action layer),
        // not a target-selection constraint. Marines deploying at the map
        // edge with the only enemies 35+ cells across the map must still
        // get a target — otherwise the planner has nothing to do and the
        // squad sits idle (the Conquest "stuck in Idle" failure mode).
        BattleSimulation sim = openArena(50, 50);
        int squadId = sim.mintSquad(Faction.MARINE, null);
        Squad squad = sim.getSquad(squadId);
        squad.aliveMembers = 4;
        squad.centroidX = 5f;
        squad.centroidY = 10f;

        Unit marine = unit(sim, Faction.MARINE, 5, 10);
        marine.squadId = squadId;
        marine.attackRange = 60f;

        Unit farEnemy = unit(sim, Faction.DEFENDER, 45, 10);

        Unit picked = TacticalScoring.findBestTarget(marine, sim);
        assertEquals(farEnemy, picked,
                "target selection must not be gated by squad centroid distance");
    }

    // ---------------------------------------------------------------------
    // Part 3b — pursuit gate
    // ---------------------------------------------------------------------

    @Test
    public void shouldKeepPursuingFalseOnDeadTarget() {
        BattleSimulation sim = openArena(20, 20);
        Unit marine = unit(sim, Faction.MARINE, 5, 5);
        Unit enemy = unit(sim, Faction.DEFENDER, 10, 5);
        enemy.hp = 0f;
        assertTrue(!TacticalScoring.shouldKeepPursuing(marine, enemy, sim));
    }

    @Test
    public void shouldKeepPursuingFalseLosLostIntoCluster() {
        BattleSimulation sim = openArena(20, 20);
        NavigationGrid grid = sim.getGrid();
        // Wall blocks LOS.
        for (int y = 4; y <= 6; y++) grid.setWalkable(8, y, false);

        Unit marine = unit(sim, Faction.MARINE, 5, 5);
        // Target hidden behind the wall...
        Unit fleer = unit(sim, Faction.DEFENDER, 12, 5);
        // ...with 2 squadmates around it (density count > 1).
        unit(sim, Faction.DEFENDER, 13, 5);
        unit(sim, Faction.DEFENDER, 12, 6);

        assertTrue(!TacticalScoring.shouldKeepPursuing(marine, fleer, sim),
                "LOS lost + target in cluster -> drop pursuit");
    }

    @Test
    public void shouldKeepPursuingTrueWhenVisible() {
        BattleSimulation sim = openArena(20, 20);
        Unit marine = unit(sim, Faction.MARINE, 5, 5);
        Unit enemy = unit(sim, Faction.DEFENDER, 10, 5);
        // Even surrounded, a *visible* target is still a fine target — the
        // gate only kicks in when LOS is lost. (And the nearby allies of the
        // enemy are within the RETARGET_DISTANCE_MARGIN of the current
        // target, so they don't beat the stickiness threshold.)
        unit(sim, Faction.DEFENDER, 9, 5);
        unit(sim, Faction.DEFENDER, 11, 5);
        unit(sim, Faction.DEFENDER, 10, 6);

        assertTrue(TacticalScoring.shouldKeepPursuing(marine, enemy, sim));
    }

    @Test
    public void shouldKeepPursuingFalseWhenCloserVisibleAppears() {
        // The user-reported case: a marine engaged on a turret at distance 15,
        // when a mech walks up to distance 2. shouldKeepPursuing must drop the
        // distant target so the marine re-picks the close threat.
        BattleSimulation sim = openArena(30, 10);
        Unit marine = unit(sim, Faction.MARINE, 5, 5);
        Unit distantTurret = unit(sim, Faction.DEFENDER, 20, 5);
        Unit closeMech = unit(sim, Faction.DEFENDER, 7, 5);

        assertTrue(!TacticalScoring.shouldKeepPursuing(marine, distantTurret, sim),
                "closer visible enemy must trigger re-target");
        assertTrue(TacticalScoring.shouldKeepPursuing(marine, closeMech, sim),
                "the close target itself is still a fine target to keep on");
    }

    @Test
    public void shouldKeepPursuingTrueWhenAlternativeNotMeaningfullyCloser() {
        // A second visible enemy that's only marginally closer than the
        // current target shouldn't cause thrashing. Margin is
        // RETARGET_DISTANCE_MARGIN (5 cells); a 2-cell-closer alternative
        // stays under that.
        BattleSimulation sim = openArena(20, 20);
        Unit marine = unit(sim, Faction.MARINE, 5, 5);
        Unit current = unit(sim, Faction.DEFENDER, 15, 5);  // distance 10
        unit(sim, Faction.DEFENDER, 13, 5);                  // distance 8

        assertTrue(TacticalScoring.shouldKeepPursuing(marine, current, sim),
                "alternative within the retarget margin must not trigger a switch");
    }
}
