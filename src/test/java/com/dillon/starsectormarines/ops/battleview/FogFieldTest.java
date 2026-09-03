package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.battle.vision.RevealChangeLog;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The GL-free half of the resident fog field: what it derives, and how far a
 * change reaches.
 *
 * <p>{@code FogFieldGlEvidence} asks the other half — whether a driver draws the
 * patched field and the per-cell stream to the same pixels. Everything here is
 * arithmetic about which cells a change re-derives, and a test that needed a GPU
 * to ask about arithmetic would not be run.
 */
final class FogFieldTest {

    private static final int W = 80;
    private static final int H = 50;

    /** Three sim ticks per vision tick; six comfortably outruns the cadence. */
    private static final int SETTLE_TICKS = 6;

    @Test
    void aPatchedFieldHoldsWhatAFreshBakeOfTheSameWorldHolds() {
        try (BattleSimulation sim = world()) {
            FogOfWarService fog = sim.getFogOfWar();
            FogField field = new FogField();
            assertTrue(field.catchUp(fog), "the field has to bake before it can drift");

            byte[] baked = mirror(field);

            // A temporary source opens a blob, then goes away again. Both halves
            // reach the field through the change log.
            fog.addEphemeralSource(W - 8, H - 6, 6, 0f);
            settle(sim);
            assertTrue(field.catchUp(fog));
            byte[] patchedOpen = mirror(field);

            fog.clearEphemeralSources();
            settle(sim);
            assertTrue(field.catchUp(fog));
            byte[] patchedShut = mirror(field);

            assertNotEquals(java.util.Arrays.toString(baked),
                    java.util.Arrays.toString(patchedOpen),
                    "a source opening a blob has to change the picture, or nothing is measured");
            assertArrayEquals(freshBake(fog), patchedShut,
                    "a patched field must hold exactly what a fresh bake of the same world holds");
            assertArrayEquals(baked, patchedShut,
                    "and a source that released what it took leaves the picture where it was");
        }
    }

    /**
     * The ring around a change is re-derived, not only the change.
     *
     * <p>A revealed cell's shadow is feathered by how many of its four
     * neighbours are dark, so a cell going dark changes the picture of cells
     * whose own reveal state never moved. An extent that stopped at the changed
     * cells would leave those neighbours holding the alpha they had before.
     */
    @Test
    void aFeatheredNeighbourIsRederivedEvenThoughItsOwnRevealDidNotMove() {
        try (BattleSimulation sim = world()) {
            FogOfWarService fog = sim.getFogOfWar();
            FogField field = new FogField();
            assertTrue(field.catchUp(fog));

            // A source whose blob meets the contributor's own, so the cells on
            // the join stay revealed while their neighbours light up.
            fog.addEphemeralSource(W / 2, H / 2, 5, 0f);
            settle(sim);
            assertTrue(field.catchUp(fog));

            boolean[] revealed = fog.cellRevealedArray();
            int feathered = 0;
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    if (!revealed[y * W + x]) continue;
                    if (field.levelAt(x, y) != 0) feathered++;
                }
            }
            assertTrue(feathered > 0,
                    "a revealed cell beside a dark one carries a feather, and the field must hold it");
            assertArrayEquals(freshBake(fog), mirror(field),
                    "every feathered neighbour of the change has to have been re-derived");
        }
    }

    @Test
    void aPatchIsSmallerThanTheMap() {
        try (BattleSimulation sim = world()) {
            FogOfWarService fog = sim.getFogOfWar();
            FogField field = new FogField();
            assertTrue(field.catchUp(fog));
            assertArrayEquals(new int[]{0, 0, W - 1, H - 1}, field.lastDerivedRect(),
                    "a bake derives the whole map");

            fog.addEphemeralSource(W - 5, H - 4, 3, 0f);
            settle(sim);
            assertTrue(field.catchUp(fog));

            int[] rect = field.lastDerivedRect();
            assertTrue(rect[2] >= rect[0] && rect[3] >= rect[1], "the patch must cover something");
            long area = (long) (rect[2] - rect[0] + 1) * (rect[3] - rect[1] + 1);
            assertTrue(area < (long) W * H / 4,
                    "a corner source must not send the field over the whole map; covered " + area);

            assertTrue(field.catchUp(fog));
            assertTrue(field.lastDerivedRect()[2] < field.lastDerivedRect()[0],
                    "a caught-up field derives nothing at all");
        }
    }

    @Test
    void aFieldThatOutranTheLogRebuildsInsteadOfDrifting() {
        try (BattleSimulation sim = world()) {
            FogOfWarService fog = sim.getFogOfWar();
            FogField field = new FogField();
            assertTrue(field.catchUp(fog));

            // Push the log past its ring without the field looking.
            RevealChangeLog log = fog.revealChanges();
            long before = log.changeCount();
            for (int i = 0; i <= RevealChangeLog.capacity(); i++) {
                fog.addEphemeralSource(W / 2 + (i % 5), H / 2, 4, 0f);
                settle(sim);
                fog.clearEphemeralSources();
                settle(sim);
            }
            assertTrue(log.changeCount() - before > RevealChangeLog.capacity(),
                    "the log has to have been outrun for this to measure anything");

            assertTrue(field.catchUp(fog));
            assertArrayEquals(new int[]{0, 0, W - 1, H - 1}, field.lastDerivedRect(),
                    "a field that fell off the end of the log rebuilds from the bitmap");
            assertArrayEquals(freshBake(fog), mirror(field));
        }
    }

    @Test
    void adifferentBattleIsBakedRatherThanPatchedOntoTheOldOne() {
        try (BattleSimulation first = world(); BattleSimulation second = world()) {
            FogField field = new FogField();
            assertTrue(field.catchUp(first.getFogOfWar()));
            assertTrue(field.isServing(first.getFogOfWar()));

            assertTrue(field.catchUp(second.getFogOfWar()));
            assertFalse(field.isServing(first.getFogOfWar()),
                    "a field bound to a new battle must not claim to serve the old one");
            assertArrayEquals(freshBake(second.getFogOfWar()), mirror(field));
        }
    }

    // ---- the world -----------------------------------------------------------

    /** Open ground, one stationary contributor, one stationary hostile it cannot reach. */
    private static BattleSimulation world() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H), 20260902L);
        sim.setMissionCompletionEnabled(false);
        sim.spawn(new EntitySpec("eyes", Faction.MARINE, UnitType.MARINE, 5, 5).moveSpeed(0f));
        sim.spawn(new EntitySpec("them", Faction.DEFENDER, UnitType.MILITIA, W - 2, H - 2)
                .moveSpeed(0f));
        settle(sim);
        return sim;
    }

    private static void settle(BattleSimulation sim) {
        for (int i = 0; i < SETTLE_TICKS; i++) sim.advance(BattleSimulation.TICK_DT);
    }

    private static byte[] mirror(FogField field) {
        byte[] out = new byte[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) out[y * W + x] = field.levelAt(x, y);
        }
        return out;
    }

    private static byte[] freshBake(FogOfWarService fog) {
        FogField fresh = new FogField();
        assertTrue(fresh.catchUp(fog));
        return mirror(fresh);
    }
}
