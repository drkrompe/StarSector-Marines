package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.FightingPositionRole;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A resolved round names the environment feature that stopped it, and nothing for anything else. */
class BallisticStruckFeatureTest {

    private static final int W = 32;
    private static final int H = 16;
    private static final int ROW = 8;
    private static final int SX = 4;
    private static final int TX = 20;
    private static final float VEL = 40f;
    private static final float RANGE = 30f;
    private static final int SHOTS = 600;
    private static final DoodadDef TRENCH = new DoodadDef("doodad.test.trench", "sheet", 0, 0,
            DoodadCover.HEAVY, 0.3f, 1, 1, null, 0, null, FightingPositionRole.TRENCH);

    private final BattleSimulation sim;
    private final NavigationGrid grid;
    private final DoodadService doodads;
    private final long shooter;
    private final long target;

    BallisticStruckFeatureTest() {
        grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        sim = new BattleSimulation(grid, new CellTopology(W, H));
        doodads = new DoodadService(grid);
        target = sim.spawn(new EntitySpec("target", Faction.DEFENDER, UnitType.MARINE, TX, ROW));
        shooter = sim.spawn(new EntitySpec("shooter", Faction.MARINE, UnitType.MARINE, SX, ROW));
    }

    private BallisticResolver resolver() {
        return new BallisticResolver(grid, doodads, sim.getUnitIndex(), sim.getRoster());
    }

    /** Counts of each stop kind over a burst of perfectly aimed rounds, checking every attribution as it goes. */
    private int[] fire(Attribution check) {
        BallisticResolver resolver = resolver();
        Random rng = new Random(17L);
        int[] counts = new int[BallisticResolver.StopKind.values().length];
        for (int i = 0; i < SHOTS; i++) {
            BallisticResolver.Resolution res = resolver.resolve(shooter, target, 1f, 0f, VEL, RANGE, rng);
            counts[res.kind().ordinal()]++;
            check.verify(res);
        }
        return counts;
    }

    @FunctionalInterface
    private interface Attribution {
        void verify(BallisticResolver.Resolution res);
    }

    @Test
    void aRoundCaughtByACrateInTheLaneNamesTheCrate() {
        doodads.addDoodad(new Doodad(12, ROW, TileRegistry.installed().doodad("doodad.crate")));
        int[] counts = fire(res -> {
            if (res.kind() == BallisticResolver.StopKind.DOODAD_BLOCK) {
                assertEquals(StruckFeature.cell(12, ROW), res.struck());
            } else {
                assertNull(res.struck(), "a body hit names no feature");
            }
        });
        assertTrue(counts[BallisticResolver.StopKind.DOODAD_BLOCK.ordinal()] > 0, "the crate caught some rounds");
    }

    @Test
    void aRoundCaughtByEdgeCoverNamesTheBlockingCellBesideTheTarget() {
        // A see-through blocker west of the target: a sandbag or a low wall the shooter can see over.
        grid.setWalkable(TX - 1, ROW, false);
        grid.setSeeThrough(TX - 1, ROW, true);
        grid.recomputeCoverAt(TX, ROW);
        int[] counts = fire(res -> {
            if (res.kind() == BallisticResolver.StopKind.COVER_CLIP) {
                assertEquals(StruckFeature.cell(TX - 1, ROW), res.struck());
            }
        });
        assertTrue(counts[BallisticResolver.StopKind.COVER_CLIP.ordinal()] > 0, "the edge cover caught some rounds");
    }

    @Test
    void aRoundCaughtByAWindowNamesTheWindowsEdge() {
        assertNotNull(grid.tryPlaceEdgeBarrier(TX, ROW, Direction.W, SharedEdgeBarrier.Kind.WINDOW));
        int[] counts = fire(res -> {
            if (res.kind() == BallisticResolver.StopKind.COVER_CLIP) {
                assertEquals(StruckFeature.edge(TX, ROW, Direction.W), res.struck());
            }
        });
        assertTrue(counts[BallisticResolver.StopKind.COVER_CLIP.ordinal()] > 0, "the window caught some rounds");
    }

    @Test
    void aRoundCaughtByTheTrenchAManStandsInNamesNothing() {
        doodads.addDoodad(new Doodad(TX, ROW, TRENCH));
        int[] counts = fire(res -> assertNull(res.struck(), "a trench is ground: " + res.kind()));
        assertTrue(counts[BallisticResolver.StopKind.COVER_CLIP.ordinal()] > 0, "the trench caught some rounds");
    }

    @Test
    void oneCatchRollSplitsBetweenEdgeCoverAndTheTrench() {
        doodads.addDoodad(new Doodad(TX, ROW, TRENCH));
        grid.setWalkable(TX - 1, ROW, false);
        grid.setSeeThrough(TX - 1, ROW, true);
        grid.recomputeCoverAt(TX, ROW);
        int[] edge = {0};
        int[] ground = {0};
        fire(res -> {
            if (res.kind() != BallisticResolver.StopKind.COVER_CLIP) return;
            if (res.struck() == null) ground[0]++;
            else {
                assertEquals(StruckFeature.cell(TX - 1, ROW), res.struck());
                edge[0]++;
            }
        });
        assertTrue(edge[0] > 0 && ground[0] > 0, "edge " + edge[0] + ", trench " + ground[0]);
    }

    @Test
    void aRoundStoppedByAWallNamesTheWallCell() {
        grid.setWalkable(12, ROW, false);
        BallisticResolver.Resolution res = resolver().resolvePoint(shooter, TX + 0.5f, ROW + 0.5f,
                1f, 0f, VEL, RANGE, new Random(3L));
        assertEquals(BallisticResolver.StopKind.WALL, res.kind());
        assertEquals(StruckFeature.cell(12, ROW), res.struck());
    }

    @Test
    void anOvershootNamesNothing() {
        BallisticResolver.Resolution res = resolver().resolvePoint(shooter, SX + 0.5f, 0.5f,
                1f, 0f, VEL, RANGE, new Random(3L));
        assertEquals(BallisticResolver.StopKind.OVERSHOOT, res.kind());
        assertNull(res.struck());
    }
}
