package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Several places grown into one another, asked of the growth directly.
 *
 * <p>What is worth pinning here is not that growth happens — the single-seed
 * case already covers that — but the three things that only exist once there is
 * more than one place: every seed gets its own budget, the ground is attributed
 * to somebody, and the border between two of them is a fact about where their
 * arms met rather than a line anybody drew.
 */
class PrecinctGrowthTest {

    private static final int W = 200;
    private static final int H = 140;

    private static List<GrownTrunkPlan.Seed> threePlaces() {
        return List.of(
                new GrownTrunkPlan.Seed(50, 70, GrownTrunkPlan.Profile.city()),
                new GrownTrunkPlan.Seed(150, 45, GrownTrunkPlan.Profile.town()),
                new GrownTrunkPlan.Seed(120, 110, GrownTrunkPlan.Profile.hamlet()));
    }

    /**
     * Each seed grows. A shared frontier makes it easy to write a loop where
     * whichever seed branches first spends everything and the others never run,
     * and the result still looks like a map.
     */
    @Test
    void everyPlaceGetsRoadOfItsOwn() {
        GrownTrunkPlan.Grown grown =
                GrownTrunkPlan.grow(W, H, new Random(42L), threePlaces());
        int[] road = new int[3];
        for (int[] column : grown.owner()) {
            for (int who : column) {
                if (who != GrownTrunkPlan.UNOWNED) road[who]++;
            }
        }
        for (int i = 0; i < road.length; i++) {
            assertTrue(road[i] > 0, "place " + i + " grew no road at all, so one seed spent "
                    + "the frontier and the others never ran");
        }
    }

    /**
     * A budget belongs to a seed, not to the map.
     *
     * <p>Cutting one place's junction budget must not change how much road
     * another place gets. It is the same rng stream either way, so the arms
     * differ; what may not differ is that a smaller neighbour makes this one
     * bigger, which is what a shared budget would do.
     */
    @Test
    void oneShrunkenPlaceDoesNotEnlargeAnother() {
        GrownTrunkPlan.Profile tiny = GrownTrunkPlan.Profile.of(0f);
        List<GrownTrunkPlan.Seed> shrunk = List.of(
                threePlaces().get(0),
                new GrownTrunkPlan.Seed(150, 45, tiny),
                threePlaces().get(2));

        int before = roadFor(GrownTrunkPlan.grow(W, H, new Random(7L), threePlaces()), 1);
        int after = roadFor(GrownTrunkPlan.grow(W, H, new Random(7L), shrunk), 1);
        assertTrue(after < before, "the town kept its road after its budget was cut to the "
                + "sparsest profile there is, so the budget is not reaching it");
    }

    private static int roadFor(GrownTrunkPlan.Grown grown, int who) {
        int n = 0;
        for (int[] column : grown.owner()) {
            for (int cell : column) {
                if (cell == who) n++;
            }
        }
        return n;
    }

    /** Nearest-road leaves nothing over: every cell belongs to somebody. */
    @Test
    void nearestClaimsTheWholeMap() {
        GrownTrunkPlan.Grown grown =
                GrownTrunkPlan.grow(W, H, new Random(42L), threePlaces());
        int[][] claim = PrecinctClaim.nearest().assign(grown.owner(), W, H, null);
        int unowned = 0;
        for (int[] column : claim) {
            for (int who : column) {
                if (who == GrownTrunkPlan.UNOWNED) unowned++;
            }
        }
        assertEquals(0, unowned, "nearest-road left " + unowned + " cells unclaimed");
        assertEquals(W * H, PrecinctClaim.sizes(claim, 3)[0]
                + PrecinctClaim.sizes(claim, 3)[1] + PrecinctClaim.sizes(claim, 3)[2]);
    }

    /**
     * A budget is a cap, and what it does not cover stays open country.
     *
     * <p>The cap has to count the road the precinct already owns, because that
     * is ground it has taken: measured, a flat budget of the sort that looks
     * generous is spent almost entirely on the arms themselves, leaving a
     * precinct that is its own street plan and nothing else. That is why a
     * precinct budget belongs to be derived from its program rather than picked.
     */
    @Test
    void aBudgetedClaimStopsAtItsBudget() {
        GrownTrunkPlan.Grown grown =
                GrownTrunkPlan.grow(W, H, new Random(42L), threePlaces());
        int[] budget = {6000, 3000, 1500};
        int[][] claim = PrecinctClaim.budgeted().assign(grown.owner(), W, H, budget);
        int[] sizes = PrecinctClaim.sizes(claim, 3);
        for (int i = 0; i < sizes.length; i++) {
            assertTrue(sizes[i] <= budget[i] + 4,
                    "place " + i + " claimed " + sizes[i] + " cells against a budget of "
                            + budget[i] + "; a budget that a breadth-first expansion may "
                            + "overshoot by a frontier is not a cap");
        }
        assertTrue(sizes[0] + sizes[1] + sizes[2] < W * H,
                "a budgeted claim took the whole map, so it is not capping anything");
    }

    /** A border cell has a differently-owned neighbour, and there is one to find. */
    @Test
    void placesMeetAlongABorderTheGrowthDecided() {
        GrownTrunkPlan.Grown grown =
                GrownTrunkPlan.grow(W, H, new Random(42L), threePlaces());
        int[][] claim = PrecinctClaim.nearest().assign(grown.owner(), W, H, null);
        List<int[]> border = PrecinctClaim.border(claim, W, H);
        assertTrue(border.size() > 20, "three places grown into one another share only "
                + border.size() + " border cells, so they are not meeting");
        for (int[] cell : border) {
            int who = claim[cell[0]][cell[1]];
            boolean touchesOther =
                    (cell[0] + 1 < W && claim[cell[0] + 1][cell[1]] != who)
                            || (cell[1] + 1 < H && claim[cell[0]][cell[1] + 1] != who);
            assertTrue(touchesOther, "a border cell at " + cell[0] + "," + cell[1]
                    + " has no differently-owned neighbour");
        }
    }
}
