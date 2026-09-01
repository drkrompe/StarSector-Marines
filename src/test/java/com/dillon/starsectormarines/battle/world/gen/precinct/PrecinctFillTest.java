package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressInterior;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A programmed precinct packs into the shape it grew, not into a rectangle.
 *
 * <p>This is the load-bearing claim of the precinct model: that growing a
 * fortress district costs nothing at the packing tier, because
 * {@code FortressInterior.pack} takes a buildable mask and a circulation mask
 * and reads its extent off them. If it were secretly rectangular the whole
 * model would need a second packer, so it is asked directly rather than assumed
 * — the shape here is a real grown claim with arms through it, and nothing
 * about it is a rectangle.
 */
class PrecinctFillTest {

    private static final int W = 300;
    private static final int H = 200;

    private record Grown(Precinct precinct, int[][] claim, int[][] owner) { }

    private static Grown grownGarrison(long seed, FortressProgram program) {
        Precinct garrison = Precinct.garrison("garrison", 110, 100,
                GrownTrunkPlan.Profile.hamlet(), program);
        Precinct town = Precinct.settlement("town", 230, 70,
                GrownTrunkPlan.Profile.town());
        List<Precinct> places = List.of(garrison, town);
        GrownTrunkPlan.Grown grown = GrownTrunkPlan.grow(W, H, new Random(seed),
                places.stream()
                        .map(p -> new GrownTrunkPlan.Seed(p.seedX(), p.seedY(), p.growth()))
                        .toList());
        int[] allowance = PrecinctAllowance.derive(places, grown.owner(), W, H);
        // Compact, not budgeted: a programmed precinct needs its ground pooled
        // rather than spread along its arms. See PrecinctClaim.compact.
        int[][] claim = PrecinctClaim.compact(
                new int[]{garrison.seedX(), town.seedX()},
                new int[]{garrison.seedY(), town.seedY()})
                .assign(grown.owner(), W, H, allowance);
        return new Grown(garrison, claim, grown.owner());
    }

    private static GenContext context(long seed) {
        return new GenContext(new NavigationGrid(W, H), new CellTopology(W, H),
                new Random(seed), W, H, seed);
    }

    /** The claim a precinct packs into is genuinely not a rectangle. */
    @Test
    void theShapeBeingPackedIsNotARectangle() {
        Grown grown = grownGarrison(42L, FortressProgram.garrison());
        PrecinctFill.Masks masks = PrecinctFill.masks(grown.claim(), grown.owner(), 0, W, H);

        int claimed = 0;
        int minX = W, minY = H, maxX = -1, maxY = -1;
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                if (grown.claim()[x][y] != 0) continue;
                claimed++;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        int boundingBox = (maxX - minX + 1) * (maxY - minY + 1);
        assertTrue(claimed < boundingBox * 0.9, "the claim fills " + claimed + " of a "
                + boundingBox + "-cell bounding box, which is near enough a rectangle that "
                + "this test proves nothing about packing an irregular shape");
        assertTrue(hasAny(masks.circulation()),
                "no road runs through the claim, so the circulation mask is empty and the "
                        + "packer is not being asked the question this test is about");
    }

    /**
     * And the program packs into it — including the building that could not be
     * placed before.
     *
     * <p>Named rather than counted. The vehicle shed is 31x16 and is what a
     * ribbon-shaped claim cannot hold at any count, so "most of the program
     * placed" would go green on a claim that had quietly dropped the one
     * building the shape argument is about.
     */
    @Test
    void aGarrisonPacksIntoTheShapeItGrewIncludingItsShed() {
        for (long seed : new long[]{42L, 777L}) {
            Grown grown = grownGarrison(seed, FortressProgram.garrison());
            PrecinctFill.Masks masks =
                    PrecinctFill.masks(grown.claim(), grown.owner(), 0, W, H);
            FortressInterior.Result result = PrecinctFill.pack(
                    context(seed), grown.precinct(), masks, TraversalAxis.SOUTH_TO_NORTH);

            assertTrue(result.unplaced().stream()
                            .noneMatch(b -> b.purpose() == RoomPurpose.VEHICLE_BAY),
                    "seed " + seed + ": the 31x16 vehicle shed went unplaced, which is the "
                            + "ribbon-claim failure this shape is supposed to have fixed");
            int owed = grown.precinct().program().expanded().size();
            assertTrue(result.placed().size() >= owed - 2, "seed " + seed + ": placed only "
                    + result.placed().size() + " of " + owed + " buildings in the grown claim");
        }
    }

    /** Buildable and circulation partition the claim, and neither leaks outside it. */
    @Test
    void theMasksArePreciselyTheClaim() {
        Grown grown = grownGarrison(42L, FortressProgram.garrison());
        PrecinctFill.Masks masks = PrecinctFill.masks(grown.claim(), grown.owner(), 0, W, H);
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                boolean mine = grown.claim()[x][y] == 0;
                boolean covered = masks.buildable()[x][y] || masks.circulation()[x][y];
                assertEquals(mine, covered, "cell " + x + "," + y
                        + (mine ? " is claimed but in neither mask"
                                : " is not claimed but appears in one"));
                assertTrue(!(masks.buildable()[x][y] && masks.circulation()[x][y]),
                        "cell " + x + "," + y + " is both buildable and circulation");
            }
        }
    }

    /** A zoned precinct has no program, and asking it to pack one is a mistake. */
    @Test
    void aZonedPrecinctIsNotPacked() {
        Precinct town = Precinct.settlement("town", 10, 10, GrownTrunkPlan.Profile.town());
        PrecinctFill.Masks empty =
                new PrecinctFill.Masks(new boolean[W][H], new boolean[W][H]);
        assertThrows(IllegalArgumentException.class,
                () -> PrecinctFill.pack(context(1L), town, empty, TraversalAxis.SOUTH_TO_NORTH));
    }

    private static boolean hasAny(boolean[][] mask) {
        for (boolean[] column : mask) {
            for (boolean cell : column) {
                if (cell) return true;
            }
        }
        return false;
    }
}
