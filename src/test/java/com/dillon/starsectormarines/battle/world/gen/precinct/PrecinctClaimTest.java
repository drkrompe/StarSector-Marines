package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A programmed precinct claims its ground before a zoned one does.
 *
 * <p>The two allowances mean different things. A garrison's is a need — the
 * ground its buildings have to stand on — and a settlement's is a frontage
 * measure, a description of how far its streets reach. Seeded into one frontier
 * the settlement enters with a cost-zero source on every road cell it grew and
 * the garrison enters with one, so on any map where the streets run past the
 * installation the settlement takes the ground first and the installation is
 * built short. Need goes before frontage.
 */
class PrecinctClaimTest {

    private static final int W = 60;
    private static final int H = 40;

    /** Enough for the garrison to want more ground than the gaps between streets. */
    private static final int GARRISON_BUDGET = 800;
    private static final int TOWN_BUDGET = 2000;

    /**
     * The frontier claims a polled cell's four neighbours before re-testing the
     * budget, so a precinct overruns its allowance by up to one such step. That
     * slack is the shipped behaviour — {@code PrecinctAllowanceTest} already
     * allows for it — and is not what this suite is about.
     */
    private static final int FRONTIER_OVERRUN = 3;

    /** A street grid over the whole map, every cell of it the town's. */
    private static int[][] streetGrid(int pitch) {
        int[][] owner = new int[W][H];
        for (int[] column : owner) Arrays.fill(column, GrownTrunkPlan.UNOWNED);
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                if (x % pitch == 0 || y % pitch == 0) owner[x][y] = 0;
            }
        }
        return owner;
    }

    /** Erases the town's streets from a box, so a seed can be far from all of them. */
    private static void clearRoadAround(int[][] owner, int cx, int cy, int radius) {
        for (int x = Math.max(0, cx - radius); x <= Math.min(W - 1, cx + radius); x++) {
            for (int y = Math.max(0, cy - radius); y <= Math.min(H - 1, cy + radius); y++) {
                owner[x][y] = GrownTrunkPlan.UNOWNED;
            }
        }
    }

    private static Precinct town() {
        return Precinct.settlement("town", 3, 3, GrownTrunkPlan.Profile.town());
    }

    private static Precinct garrison(String name, int x, int y) {
        return Precinct.garrison(name, x, y, GrownTrunkPlan.Profile.hamlet(),
                FortressProgram.garrison());
    }

    /** Claims the pair, town first, so list order is against the garrison. */
    private static int[] claimSizes(List<Precinct> precincts, int[][] owner, int[] budget) {
        int[][] claim = PrecinctClaim.byKind(precincts).assign(owner, W, H, budget);
        return PrecinctClaim.sizes(claim, precincts.size());
    }

    /**
     * A garrison surrounded by somebody else's streets is still given the ground
     * it needs.
     *
     * <p>Its seed here sits on one of the town's own road cells, which the pool
     * absorbs and pays for: a programmed precinct takes whatever ground its blob
     * covers, road included, because road inside an installation is its
     * circulation rather than the town's street.
     */
    @Test
    void aProgrammedPrecinctIsGivenItsWholeAllowanceThroughAnothersStreets() {
        List<Precinct> precincts = List.of(town(), garrison("garrison", 30, 18));
        int[] sizes = claimSizes(precincts, streetGrid(6),
                new int[]{TOWN_BUDGET, GARRISON_BUDGET});

        assertTrue(sizes[1] >= GARRISON_BUDGET, "the garrison was allowed " + GARRISON_BUDGET
                + " cells and claimed " + sizes[1] + ", so the town's streets took the ground "
                + "its program has to stand on before it could reach any of it");
        assertTrue(sizes[1] <= GARRISON_BUDGET + FRONTIER_OVERRUN, "the garrison claimed "
                + sizes[1] + " against an allowance of " + GARRISON_BUDGET + ", so a "
                + "programmed precinct going first is no longer stopping at its budget");
        assertTrue(sizes[0] > 0, "the town claimed nothing, so putting the garrison first "
                + "has starved the settlement instead of merely letting it yield");
    }

    /**
     * The same answer with the seed nowhere near a street, so the first test is
     * not passing on the one road cell it happened to start from.
     */
    @Test
    void theAnswerDoesNotDependOnTheSeedStartingOnARoad() {
        int[][] owner = streetGrid(6);
        clearRoadAround(owner, 30, 18, 8);
        int[] sizes = claimSizes(List.of(town(), garrison("garrison", 30, 18)), owner,
                new int[]{TOWN_BUDGET, GARRISON_BUDGET});

        assertTrue(sizes[1] >= GARRISON_BUDGET && sizes[1] <= GARRISON_BUDGET + FRONTIER_OVERRUN,
                "with its seed eight cells clear of any street the garrison claimed "
                        + sizes[1] + " against an allowance of " + GARRISON_BUDGET
                        + ", so where the seed falls is deciding how much ground a program gets");
    }

    /**
     * Order within the programmed pass still does not matter, because they all
     * advance together in one frontier.
     *
     * <p>Two garrisons ten cells apart, each allowed a blob wanting a radius of
     * about fourteen, so neither can have its ground without contesting the
     * other's. Run one place at a time the first listed would pool straight over
     * the second's seed cell, and a seed on claimed ground is skipped — the
     * second garrison would come out with no ground at all rather than merely
     * with less.
     */
    @Test
    void twoProgrammedPrecinctsSplitContestedGroundRatherThanRacing() {
        int[][] owner = new int[W][H];
        for (int[] column : owner) Arrays.fill(column, GrownTrunkPlan.UNOWNED);
        List<Precinct> precincts = List.of(garrison("west", 25, 20), garrison("east", 35, 20));
        int contested = 400;
        int[] sizes = claimSizes(precincts, owner, new int[]{contested, contested});

        assertTrue(sizes[0] > 0 && sizes[1] > 0, "the two garrisons claimed " + sizes[0]
                + " and " + sizes[1] + " cells, so one of them pooled straight over the "
                + "other's seed and the programmed pass is running them in turn");
        int larger = Math.max(sizes[0], sizes[1]);
        int smaller = Math.min(sizes[0], sizes[1]);
        assertTrue(larger - smaller <= larger / 5, "one garrison claimed " + larger
                + " cells against the other's " + smaller + " for the same allowance, so "
                + "contested ground is going to whoever was listed first rather than to "
                + "whoever is nearer");
        assertTrue(larger <= contested + FRONTIER_OVERRUN, "a garrison claimed " + larger
                + " against an allowance of " + contested + ", so contesting a neighbour "
                + "let it past its own budget");
    }
}
