package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Separately grown places end up on one road network.
 *
 * <p>Growth joins two places only by accident: an arm stops when it runs into
 * an existing band, so precincts whose networks run near each other without
 * touching stay separate systems. That is invisible in a picture and severe for
 * anything that drives, and it is intermittent — measured over four derived
 * maps, three came out already whole and one came out in three pieces. An
 * intermittent structural fault is exactly the kind that wants solving for
 * rather than hoping about.
 */
class PrecinctInterconnectTest {

    private static final int W = 80;
    private static final int H = 60;

    /** Two horizontal bars with a gap between them, and nothing else. */
    private static int[][] twoBars(int gap) {
        int[][] owner = new int[W][H];
        for (int[] column : owner) java.util.Arrays.fill(column, GrownTrunkPlan.UNOWNED);
        for (int x = 5; x < 30; x++) {
            for (int y = 28; y <= 30; y++) owner[x][y] = 0;
        }
        for (int x = 30 + gap; x < 70; x++) {
            for (int y = 28; y <= 30; y++) owner[x][y] = 1;
        }
        return owner;
    }

    @Test
    void twoNetworksBecomeOne() {
        int[][] owner = twoBars(14);
        assertEquals(2, PrecinctInterconnect.components(owner, W, H));

        PrecinctInterconnect.Result result = PrecinctInterconnect.weld(owner, W, H);
        assertEquals(2, result.componentsBefore());
        assertEquals(1, result.componentsAfter());
        assertEquals(1, PrecinctInterconnect.components(owner, W, H),
                "the weld reported one network but the road mask still has more");
        assertTrue(result.cellsCut() > 0, "one network out of two with no road cut");
    }

    /**
     * The weld is minimal: one link per join, never a road between every pair.
     *
     * <p>Three networks need two links to become one. A pairwise version would
     * cut three and leave a triangle of roads across the map that nothing asked
     * for.
     */
    @Test
    void threeNetworksNeedTwoLinks() {
        int[][] owner = twoBars(14);
        for (int x = 20; x < 50; x++) {
            for (int y = 8; y <= 10; y++) owner[x][y] = 2;
        }
        assertEquals(3, PrecinctInterconnect.components(owner, W, H));
        PrecinctInterconnect.Result result = PrecinctInterconnect.weld(owner, W, H);
        assertEquals(1, result.componentsAfter());
        assertEquals(2, result.weldsCut(), "three networks were joined with "
                + result.weldsCut() + " links, where a spanning tree needs two");
    }

    /** A map that is already whole is left alone. */
    @Test
    void anAlreadyConnectedMapIsNotCut() {
        int[][] owner = twoBars(0);
        assertEquals(1, PrecinctInterconnect.components(owner, W, H),
                "the fixture is supposed to be one network already");
        PrecinctInterconnect.Result result = PrecinctInterconnect.weld(owner, W, H);
        assertEquals(0, result.weldsCut());
        assertEquals(0, result.cellsCut(),
                "road was cut across a map whose network was already whole");
    }

    /**
     * A weld belongs to the place whose road it extends.
     *
     * <p>Road owned by nobody is road no precinct's border, artery or report
     * can account for, and it would silently widen every claim measured off the
     * owner mask.
     */
    @Test
    void aWeldIsSomebodysRoad() {
        int[][] owner = twoBars(14);
        PrecinctInterconnect.weld(owner, W, H);
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                int who = owner[x][y];
                assertTrue(who == GrownTrunkPlan.UNOWNED || who == 0 || who == 1,
                        "cell " + x + "," + y + " came out owned by " + who
                                + ", which is not one of the two places on this map");
            }
        }
    }

    /** The link is cut where the two networks are nearest, not somewhere arbitrary. */
    @Test
    void theLinkIsCutAtTheNarrowestGap() {
        int[][] owner = twoBars(14);
        PrecinctInterconnect.Result result = PrecinctInterconnect.weld(owner, W, H);
        // Two 3-wide bars 14 apart, joined across the gap: the cut is the gap
        // times the weld width, plus the overlap where it meets each bar.
        assertTrue(result.cellsCut() < 14 * 3 * 3, "cut " + result.cellsCut()
                + " cells to bridge a fourteen-cell gap, so the link is wandering rather "
                + "than crossing at the nearest point");
    }
}
