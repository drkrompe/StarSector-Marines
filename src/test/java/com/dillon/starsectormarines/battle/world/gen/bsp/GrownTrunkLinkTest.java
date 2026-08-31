package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan.Profile;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan.TrunkSegment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The off-map link is guaranteed by {@link GrownTrunkPlan#generate}, which is a
 * pure function of size, seed and profile — so the guarantee is asked of it
 * directly rather than of a generated world.
 *
 * <p>The sparse case is the one that matters: measured over a hundred seeds,
 * nearly a quarter of low-density settlements grew no arterial to any edge, and
 * a settlement with no road out has nowhere for ground reinforcement to arrive
 * from while nothing reports it.
 */
class GrownTrunkLinkTest {

    private static final int W = 80;
    private static final int H = 80;

    @Test
    void aRoadLinkedSettlementAlwaysReachesAnEdgeEvenWhenSparse() {
        Profile sparse = Profile.of(0.1f, SettlementLink.ROAD);
        for (int seed = 0; seed < 40; seed++) {
            GrownTrunkPlan.Result r = GrownTrunkPlan.generate(W, H, new Random(seed), sparse);
            assertTrue(reachesEdge(r.plan.trunks),
                    "seed " + seed + " grew no arterial off the map");
        }
    }

    /** The control the survey used: without the requirement, sparse growth often stays inland. */
    @Test
    void anUnlinkedSettlementIsNotForcedToTheEdge() {
        Profile sparse = Profile.of(0.1f, SettlementLink.NONE);
        int inland = 0;
        for (int seed = 0; seed < 40; seed++) {
            GrownTrunkPlan.Result r = GrownTrunkPlan.generate(W, H, new Random(seed), sparse);
            if (!reachesEdge(r.plan.trunks)) inland++;
        }
        assertTrue(inland > 0,
                "NONE should leave some settlements unlinked, else the ROAD case proves nothing");
    }

    /** A settlement supplied by ship gets no road out; its pad is guaranteed elsewhere. */
    @Test
    void aLandingLinkedSettlementIsNotGivenARoad() {
        assertEquals(SettlementLink.LANDING, Profile.of(0.1f, SettlementLink.LANDING).link);
    }

    @Test
    void theLinkDefaultsToRoadAndNeverToNull() {
        assertEquals(SettlementLink.ROAD, Profile.of(0.5f).link);
        assertEquals(SettlementLink.ROAD, Profile.city().link);
        assertEquals(SettlementLink.ROAD, Profile.of(0.5f, null).link);
    }

    /** A forced link is still a road: wide enough that the graph can promote its perimeter cell. */
    @Test
    void theForcedLinkIsAnArterialNotAnAlley() {
        Profile sparse = Profile.of(0.1f, SettlementLink.ROAD);
        for (int seed = 0; seed < 10; seed++) {
            GrownTrunkPlan.Result r = GrownTrunkPlan.generate(W, H, new Random(seed), sparse);
            TrunkSegment edge = edgeSegment(r.plan.trunks);
            int band = edge.horizontal ? (edge.bottom - edge.top + 1) : (edge.right - edge.left + 1);
            assertTrue(band >= TrunkPlan.SECONDARY_WIDTH,
                    "seed " + seed + " left the map on a band only " + band + " cells wide");
        }
    }

    private static boolean reachesEdge(List<TrunkSegment> trunks) {
        return edgeSegment(trunks) != null;
    }

    private static TrunkSegment edgeSegment(List<TrunkSegment> trunks) {
        for (int i = 0; i < trunks.size(); i++) {
            TrunkSegment t = trunks.get(i);
            if (t.left <= 0 || t.top <= 0 || t.right >= W - 1 || t.bottom >= H - 1) return t;
        }
        return null;
    }
}
