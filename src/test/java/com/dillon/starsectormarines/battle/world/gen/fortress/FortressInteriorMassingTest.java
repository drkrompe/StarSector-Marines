package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ward always leaves a way round what it builds.
 *
 * <p>Packing rewards wedging, which inside a hull is simply correct — a void
 * between two compartments is wasted displacement. On open ground the same
 * reward chains every building into one slab, and the packer cannot see the
 * cost: each room is still reachable, so nothing it checks fails. What fails is
 * crossing the place. At production proportions the chaining ran sixty-six
 * cells across a hundred-and-twenty-six-cell ward, and through the whole of its
 * twenty-eight-cell depth.
 *
 * <p>The bound is the ward's own size, not a tuning value: a run longer than
 * half the ground it stands on has no way round inside the ward at all. One
 * building may exceed that on its own — a vehicle shed is as long as the bays
 * in it — so a single building's extent is the floor under the bound. What is
 * ruled out is buildings adding up into a wall.
 */
class FortressInteriorMassingTest {

    /** The band a conquest ward gets: shallow along the approach, long across it. */
    private static final int DEPTH = 28;
    private static final int W;
    private static final int H = DEPTH + 4;

    static {
        W = FortressProgram.ward().envelopeArea() / DEPTH + 4;
    }

    @Test
    void noWallRunsLongEnoughToHaveNoWayRound() {
        int longest = 0;
        for (FortressBuilding building : FortressProgram.ward().buildings()) {
            longest = Math.max(longest,
                    Math.max(building.shape().width(), building.shape().height()));
        }
        // Plus the ring, which is wall a building carries with it.
        int single = longest + 2;

        for (long seed : new long[]{ 5L, 1234L }) {
            NavigationGrid grid = new NavigationGrid(W, H);
            GenContext ctx = new GenContext(
                    grid, new CellTopology(W, H), new Random(seed), W, H, seed);

            boolean[][] ground = new boolean[W][H];
            for (int x = 2; x < W - 2; x++) {
                for (int y = 2; y < H - 2; y++) ground[x][y] = true;
            }
            boolean[][] muster = new boolean[W][H];
            for (int y = 2; y < 10; y++) {
                muster[W / 2][y] = true;
                muster[W / 2 + 1][y] = true;
            }

            FortressInterior.pack(ctx, ground, muster,
                    TraversalAxis.SOUTH_TO_NORTH, FortressProgram.ward());

            assertRun(longestRun(grid, true), Math.max(W / 2, single), seed, "across");
            assertRun(longestRun(grid, false), Math.max(H / 2, single), seed, "through");
        }
    }

    private static void assertRun(int run, int bound, long seed, String axis) {
        assertTrue(run <= bound, "seed " + seed + ": " + run + " cells of unbroken wall "
                + axis + " the ward, against a bound of " + bound
                + " — buildings have chained into something to walk around rather "
                + "than something to fight through");
    }

    /** The longest unbroken run of ground nothing can walk through, on one axis. */
    private static int longestRun(NavigationGrid grid, boolean acrossX) {
        int longest = 0;
        int outer = acrossX ? H : W;
        int inner = acrossX ? W : H;
        for (int a = 2; a < outer - 2; a++) {
            int run = 0;
            for (int b = 2; b < inner - 2; b++) {
                int x = acrossX ? b : a;
                int y = acrossX ? a : b;
                run = grid.isWalkable(x, y) ? 0 : run + 1;
                longest = Math.max(longest, run);
            }
        }
        return longest;
    }
}
