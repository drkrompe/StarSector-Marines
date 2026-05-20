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
        sim.addUnit(u);
        return u;
    }

    private static Unit unit(BattleSimulation sim, Faction f, UnitType type, int x, int y) {
        Unit u = new Unit("u" + sim.getUnits().size(), f, type, x, y);
        sim.addUnit(u);
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
        // Threat at (15, 10), anchor at (10, 10), radius 4. One heavy-cover
        // doodad at (13, 10) — its iso cell + the cell directly west of it
        // (12, 10) both read cover 3 in the E facing (threat direction). The
        // picker tie-breaks by proximity to anchor, so it should pick (12, 10)
        // — same protection as the doodad cell itself, one step closer to
        // where we are. This is the Story G directional-cover signal: a
        // marine doesn't need to stand <em>on</em> the crate to be behind it.
        sim.addDoodad(new Doodad(13, 10, new TileManifest.TileFrame(4, 7)));

        int[] pick = TacticalScoring.bestCoverCell(15, 10, 10, 10, 4, sim);
        assertNotNull(pick, "open arena with LOS should always pick something");
        assertEquals(12, pick[0],
                "(12, 10) wins: same E-facing cover as the doodad cell, closer to anchor");
        assertEquals(10, pick[1]);
    }

    @Test
    public void bestCoverCellRespectsDirectionalCover() {
        // When the threat shifts north instead of east, the same doodad at
        // (13, 10) covers a different set of cells: north-of-doodad (13, 9)
        // gets S-facing cover 3 against threats from the north. The picker
        // should reflect that. Same scene as the east-threat test, different
        // threat direction.
        BattleSimulation sim = openArena(20, 20);
        sim.addDoodad(new Doodad(13, 10, new TileManifest.TileFrame(4, 7)));
        // Threat at (13, 5) (north), anchor at (13, 12) (south of doodad),
        // radius 3. (13, 10) is the doodad cell — iso cover 3. (13, 11) is
        // south of doodad — N-facing cover 3 (doodad is north of it, but the
        // threat is FURTHER north, so doodad is between). (13, 9) is north of
        // doodad — but its S-facing applies to threats from south; threat is
        // north, so S-facing doesn't help. (13, 9) is exposed.
        int[] pick = TacticalScoring.bestCoverCell(13, 5, 13, 12, 3, sim);
        assertNotNull(pick);
        assertTrue(pick[1] == 10 || pick[1] == 11,
                "best cover cell against north threat should be south of or on the doodad, not north of it; got " + pick[0] + "," + pick[1]);
        assertEquals(13, pick[0]);
    }

    @Test
    public void bestCoverCellSkipsCellsWithoutLos() {
        BattleSimulation sim = openArena(20, 20);
        NavigationGrid grid = sim.getGrid();
        // Knock out a column of cells to block LOS to a high-cover doodad.
        // Threat at (15, 5). Anchor at (9, 5). Wall column at x=8 from y=4..6.
        // The heavy doodad at (6, 5) is on the wrong side of the wall —
        // no candidate near it has LOS to (15, 5). The medium doodad at
        // (12, 5) is on the visible side; under directional cover the cell
        // directly west of it, (11, 5), reads E-facing cover 2 (the doodad
        // covers from the east threat) AND is closer to the anchor than the
        // doodad cell itself, so it wins the tie-break.
        for (int y = 4; y <= 6; y++) grid.setWalkable(8, y, false);
        sim.addDoodad(new Doodad(6, 5, new TileManifest.TileFrame(4, 7)));   // heavy, blocked
        sim.addDoodad(new Doodad(12, 5, new TileManifest.TileFrame(8, 1)));  // medium, visible

        int[] pick = TacticalScoring.bestCoverCell(15, 5, 9, 5, 6, sim);
        assertNotNull(pick);
        assertEquals(11, pick[0],
                "directional cover: (11, 5) is behind the visible crate from east threats, closer to anchor than the crate cell itself");
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
    // Part 2b — AoE-radius spread (cell-claim hotfix)
    // ---------------------------------------------------------------------

    @Test
    public void aoeSpreadPushesSecondMarineOffDeskCell() {
        // Open field with one cover doodad — the "marines bunch on the
        // desk and one rocket wipes them" playtest failure. Two marines:
        // marine0 already pathing to the desk (its dest counted in occupancy
        // via setPath), marine1 picking a firing position now. Without
        // AoE spread + bumped occupancy cost, marine1 would pick the same
        // desk cell. With them in place, marine1 should pick an adjacent
        // cell.
        BattleSimulation sim = openArena(20, 20);
        // Heavy doodad on (10, 10).
        sim.addDoodad(new Doodad(10, 10, new TileManifest.TileFrame(4, 7)));

        Unit threat = unit(sim, Faction.DEFENDER, 18, 10);
        threat.attackRange = 30f;

        // marine0 already standing on the desk cell — counted as both
        // current-cell occupancy AND an AoE-spread ally near (10, 10).
        Unit m0 = unit(sim, Faction.MARINE, 10, 10);
        m0.attackRange = 30f;

        // marine1 picks a firing position now. Choose a setup where the
        // desk cell would otherwise win on cover alone.
        Unit m1 = unit(sim, Faction.MARINE, 5, 10);
        m1.attackRange = 30f;

        int[] pick = TacticalScoring.findFiringPosition(m1, threat, sim);
        assertNotNull(pick);
        boolean withinAoeRadius = (Math.abs(pick[0] - 10) <= TacticalScoring.FIRING_AOE_SPREAD_RADIUS
                && Math.abs(pick[1] - 10) <= TacticalScoring.FIRING_AOE_SPREAD_RADIUS);
        // The picker should choose a cell OUTSIDE the AoE-spread radius of
        // marine0, even though cover is concentrated at marine0's cell.
        // The exact cell depends on cover bleed-in from neighbors; the
        // contract is "not co-located AND not within AoE-radius."
        assertNotEquals(10, pick[0],
                "marine1 must not pick the same cell marine0 is on");
        if (withinAoeRadius) {
            // Acceptable fallback only if there literally is no cell outside
            // the radius with non-zero cover — in this open-arena setup with
            // a single doodad, there ARE such cells.
            org.junit.jupiter.api.Assertions.fail(
                    "marine1 picked " + pick[0] + "," + pick[1]
                            + " — within AoE radius of marine0; expected spread to push them outside");
        }
    }

    @Test
    public void alliesNearForSpreadCountsCurrentAndPathDest() {
        BattleSimulation sim = openArena(15, 15);
        Unit self = unit(sim, Faction.MARINE, 0, 0);

        // Ally currently at (5, 5) → within radius 2 of (4, 4)? distance sqrt(2) ≈ 1.4. Yes.
        Unit a1 = unit(sim, Faction.MARINE, 5, 5);
        // Ally currently far but path destination at (4, 5) → within radius of (4, 4).
        Unit a2 = unit(sim, Faction.MARINE, 12, 12);
        int[] path = new int[]{12, 12, 4, 5};
        sim.setPath(a2, path);

        int count = TacticalScoring.alliesNearForSpread(self, 4, 4, sim);
        assertEquals(2, count,
                "both the at-cell ally and the pathing-to-near-cell ally count toward AoE-spread");
    }

    @Test
    public void alliesNearForSpreadIgnoresEnemies() {
        BattleSimulation sim = openArena(15, 15);
        Unit self = unit(sim, Faction.MARINE, 0, 0);
        // Enemy literally on the candidate cell.
        unit(sim, Faction.DEFENDER, 4, 4);
        int count = TacticalScoring.alliesNearForSpread(self, 4, 4, sim);
        assertEquals(0, count, "enemies don't contribute to ally-spread");
    }

    // ---------------------------------------------------------------------
    // Part 3a — zone-mismatch target bias (Slice 3.5)
    // ---------------------------------------------------------------------

    /** 20×20 with a vertical wall at x=10 punched by a doorway at (10, 10). Two zones. */
    private static BattleSimulation twoZoneSim() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 20; x++) {
                if (x == 10) continue;
                grid.setWalkableFloor(x, y);
            }
        }
        grid.setWalkableFloor(10, 10);
        grid.setDoorway(10, 10, true);
        return new BattleSimulation(grid, new CellTopology(20, 20));
    }

    @Test
    public void inZoneTargetBeatsSlightlyCloserAcrossZoneTarget() {
        // Marine at (5, 10), in-zone enemy at (9, 10) (dist 4), across-zone
        // enemy at (11, 10) (dist 6). Old (distance-only) scoring would pick
        // the across-zone enemy because raw distance is irrelevant to the
        // wall geometry; with the +8f cross-zone bias the in-zone target
        // wins (adjusted: 4 vs 14).
        BattleSimulation sim = twoZoneSim();
        Unit marine = unit(sim, Faction.MARINE, 5, 10);
        marine.attackRange = 50f;
        Unit closer = unit(sim, Faction.DEFENDER, 9, 10);
        unit(sim, Faction.DEFENDER, 11, 10);

        Unit picked = TacticalScoring.findBestTarget(marine, sim);
        assertEquals(closer, picked,
                "in-zone enemy at dist 4 must beat across-zone enemy at dist 6 (bias > 2)");
    }

    @Test
    public void biasDoesNotFlipObviouslyCloserAcrossZoneTarget() {
        // The bias must not be a hard gate. A very close across-zone target
        // still wins against a far in-zone target. Marine at (5, 10),
        // across-zone enemy at (11, 10) (dist 6, adjusted 14), in-zone enemy
        // at (5, 19) (dist 9). Adjusted: 9 vs 14 → in-zone wins. To verify
        // the across-zone DOES win when far enough closer, we need:
        // distAcross + 8 < distIn. So put in-zone at (5, 4) (dist 6) and
        // remove it isn't enough... the doorway is at (10, 10) so put the
        // across-zone enemy literally on the doorway-adjacent cell (11, 10)
        // and the in-zone enemy as far as possible. With a 20×20 arena, the
        // max in-zone distance from (5, 10) is ~14 cells; adjusted across
        // is 6 + 8 = 14. Tied; in-zone wins by iteration order which is
        // implementation-dependent. We need a clearer-cut margin.
        //
        // Use a larger arena so the in-zone enemy can be definitively
        // farther. Build a 40-wide two-zone sim with the wall at x=20.
        NavigationGrid grid = new NavigationGrid(40, 20);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 40; x++) {
                if (x == 20) continue;
                grid.setWalkableFloor(x, y);
            }
        }
        grid.setWalkableFloor(20, 10);
        grid.setDoorway(20, 10, true);
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(40, 20));

        Unit marine = unit(sim, Faction.MARINE, 18, 10);
        marine.attackRange = 50f;
        Unit acrossClose = unit(sim, Faction.DEFENDER, 21, 10);   // dist 3, adjusted 11
        Unit inZoneFar  = unit(sim, Faction.DEFENDER, 2, 10);     // dist 16, adjusted 16

        Unit picked = TacticalScoring.findBestTarget(marine, sim);
        assertEquals(acrossClose, picked,
                "very close across-zone enemy (adjusted 11) must beat distant in-zone (16)");
    }

    @Test
    public void biasIgnoredWhenZonesIndistinguishable() {
        // Open arena with no zones distinguished — both targets in same zone.
        // The bias is 0; nearest wins by raw distance.
        BattleSimulation sim = openArena(20, 20);
        Unit marine = unit(sim, Faction.MARINE, 5, 5);
        marine.attackRange = 50f;
        Unit near = unit(sim, Faction.DEFENDER, 7, 5);
        Unit far = unit(sim, Faction.DEFENDER, 15, 5);

        Unit picked = TacticalScoring.findBestTarget(marine, sim);
        assertEquals(near, picked,
                "same-zone targets — pick nearest, bias is 0");
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

    // ---------------------------------------------------------------------
    // Part 4 — weapon-target affinity
    // ---------------------------------------------------------------------

    @Test
    public void rocketeerPrefersMechOverNearerInfantry() {
        // Two visible targets: a mech at distance 15, infantry at distance 5.
        // A marine carrying a rocket launcher (vsTurretMult 3.5) should pick
        // the mech — the affinity bonus overcomes the distance gap.
        BattleSimulation sim = openArena(30, 10);
        Unit rocketeer = unit(sim, Faction.MARINE, 5, 5);
        rocketeer.primaryWeapon = com.dillon.starsectormarines.battle.MarineWeapon.PULSE_RIFLE;
        rocketeer.secondaryWeapon = com.dillon.starsectormarines.battle.MarineSecondary.ROCKET_LAUNCHER;
        rocketeer.secondaryAmmo = 3;
        rocketeer.attackRange = 40f;

        Unit infantry = unit(sim, Faction.DEFENDER, 10, 5);
        Unit mech = unit(sim, Faction.DEFENDER, UnitType.HEAVY_MECH, 20, 5);

        Unit picked = TacticalScoring.findBestTarget(rocketeer, sim);
        assertEquals(mech, picked, "rocketeer must prefer the hardened mech over the soft infantry");
    }

    @Test
    public void smgMarinePrefersInfantryOverMech() {
        // Mirror case — an SMG marine (vsTurretMult 0.5) should pick the
        // infantry. No rocket, so suitability against the mech is poor.
        BattleSimulation sim = openArena(30, 10);
        Unit smg = unit(sim, Faction.MARINE, 5, 5);
        smg.primaryWeapon = com.dillon.starsectormarines.battle.MarineWeapon.SMG;
        smg.attackRange = 40f;

        Unit infantry = unit(sim, Faction.DEFENDER, 10, 5);
        Unit mech = unit(sim, Faction.DEFENDER, UnitType.HEAVY_MECH, 8, 5);
        // Mech is actually CLOSER than the infantry here — without affinity
        // the picker would lock the mech. Affinity must flip it.

        Unit picked = TacticalScoring.findBestTarget(smg, sim);
        assertEquals(infantry, picked, "SMG marine must prefer infantry even when mech is closer");
    }

    @Test
    public void rocketeerOutOfAmmoFallsBackToPrimaryAffinity() {
        // Rocket launcher with 0 ammo — affinity vs hardened drops to the
        // primary's mult only (PULSE_RIFLE 0.3, weak). Now the close
        // infantry wins on distance.
        BattleSimulation sim = openArena(30, 10);
        Unit dryRocketeer = unit(sim, Faction.MARINE, 5, 5);
        dryRocketeer.primaryWeapon = com.dillon.starsectormarines.battle.MarineWeapon.PULSE_RIFLE;
        dryRocketeer.secondaryWeapon = com.dillon.starsectormarines.battle.MarineSecondary.ROCKET_LAUNCHER;
        dryRocketeer.secondaryAmmo = 0;
        dryRocketeer.attackRange = 40f;

        Unit infantry = unit(sim, Faction.DEFENDER, 10, 5);
        Unit mech = unit(sim, Faction.DEFENDER, UnitType.HEAVY_MECH, 20, 5);

        Unit picked = TacticalScoring.findBestTarget(dryRocketeer, sim);
        assertEquals(infantry, picked, "no rockets left -> pick the closer infantry, not the distant mech");
    }

    @Test
    public void singleVisibleTargetPickedRegardlessOfAffinity() {
        // Only the mech is visible — even a rifle-armed marine picks it.
        // Affinity is a tiebreaker when multiple visible candidates compete,
        // not a hard filter.
        BattleSimulation sim = openArena(30, 10);
        Unit rifleMarine = unit(sim, Faction.MARINE, 5, 5);
        rifleMarine.primaryWeapon = com.dillon.starsectormarines.battle.MarineWeapon.PULSE_RIFLE;
        rifleMarine.attackRange = 40f;

        Unit mech = unit(sim, Faction.DEFENDER, UnitType.HEAVY_MECH, 15, 5);

        Unit picked = TacticalScoring.findBestTarget(rifleMarine, sim);
        assertEquals(mech, picked, "single visible target is picked regardless of weapon affinity");
    }

    // ---------------------------------------------------------------------
    // Part 3c — pursuit gate stickiness
    // ---------------------------------------------------------------------

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

    // ---------------------------------------------------------------------
    // Part 6 — findFallbackPosition reachability
    // ---------------------------------------------------------------------

    @Test
    public void findFallbackPositionExcludesCellsBlockedByImpassableEdges() {
        // Open arena with a sealed corridor: all cells flagged walkable, but
        // every edge between column 5 and column 6 is blocked. ZoneDetector
        // floods on cell-walkability alone, so cells on both sides of the
        // edge wall live in the same zone — yet GridPathfinder can't cross.
        // Picker must honor the edges and refuse to send self into the
        // unreachable side. (SQ-17 stuck-defender regression: ZoneGraph
        // reported "connected" but the unit had no path.)
        BattleSimulation sim = openArena(20, 20);
        NavigationGrid grid = sim.getGrid();
        for (int y = 0; y < 20; y++) {
            grid.setEdgePassable(5, y, com.dillon.starsectormarines.battle.nav.Direction.E, false);
            grid.setEdgePassable(6, y, com.dillon.starsectormarines.battle.nav.Direction.W, false);
        }

        Unit self = unit(sim, Faction.MARINE, 3, 10);
        // Enemy on self's side — gives findFallbackPosition a threat to hide
        // from. The picker would prefer cells far from the threat; cells past
        // column 6 are the obvious "far" choice, but they're not reachable.
        unit(sim, Faction.DEFENDER, 1, 10);

        int[] dest = TacticalScoring.findFallbackPosition(self, sim);
        assertTrue(dest[0] <= 5,
                "edge-sealed cells past column 5 must not be picked even though " +
                        "ZoneDetector groups them with self — got (" + dest[0] + "," + dest[1] + ")");
    }

    @Test
    public void findFallbackPositionStillPicksReachableHide() {
        // Sanity check: in a normal sim with no edge sealing, the picker
        // happily returns a cell behind partial cover. Guard against the
        // edge-honoring flood being so aggressive it strands every unit at
        // their start cell.
        BattleSimulation sim = openArena(20, 20);
        NavigationGrid grid = sim.getGrid();
        // Partial wall column at x=10, rows 4..15. Self at (5,10) on the
        // open side, enemy at (15,10) on the other.
        for (int y = 4; y <= 15; y++) {
            grid.setWalkable(10, y, false);
        }

        Unit self = unit(sim, Faction.MARINE, 5, 10);
        unit(sim, Faction.DEFENDER, 15, 10);

        int[] dest = TacticalScoring.findFallbackPosition(self, sim);
        // The picker should pick *something* reachable on self's side — not
        // the start cell, ideally a cell with cover. The minimum bar is
        // "the result is walkable from self's perspective."
        assertTrue(grid.isWalkable(dest[0], dest[1]),
                "picker returned a non-walkable cell (" + dest[0] + "," + dest[1] + ")");
        assertTrue(dest[0] < 10,
                "picker shouldn't have crossed the wall to land on the enemy's side — got (" + dest[0] + "," + dest[1] + ")");
    }
}
