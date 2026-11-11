package com.dillon.starsectormarines.battle.derelict;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StakeTargetsTest {

    /** The hull, then established 1, raised 2 off it, planted 3 off that; and 4, stranded, off the hull. */
    private static Map<Integer, SurveyStake> claim() {
        Map<Integer, SurveyStake> stakes = new TreeMap<>();
        stakes.put(1, new SurveyStake(1, ClaimLattice.HULL, SurveyStake.Stage.ESTABLISHED));
        stakes.put(2, new SurveyStake(2, 1, SurveyStake.Stage.RAISED));
        stakes.put(3, new SurveyStake(3, 2, SurveyStake.Stage.PLANTED));
        SurveyStake cut = new SurveyStake(4, ClaimLattice.HULL, SurveyStake.Stage.RAISED);
        cut.strand();
        stakes.put(4, cut);
        return stakes;
    }

    @Test
    void eachStandingStakeIsKilledByItsPylonOrNodeAndTheKillThatStrandsMostComesFirst() {
        List<StakeTargets.Target> targets = StakeTargets.of(claim(), Map.of(3, 30L), Map.of(1, 10L, 2, 20L, 4, 40L),
                Map.of(1, new int[] {5, 6}, 2, new int[] {7, 8}, 3, new int[] {9, 10}, 4, new int[] {11, 12}));
        assertEquals(3, targets.size(), "the stranded stake is no target");
        assertEquals(1, targets.get(0).pointId(), "the node nearest the hull strands the whole chain");
        assertEquals(StakeTargets.By.NODE, targets.get(0).by());
        assertEquals(10L, targets.get(0).unitId());
        assertEquals(3, targets.get(0).strands());
        assertEquals(5, targets.get(0).x());
        assertEquals(6, targets.get(0).y());
        StakeTargets.Target planted = targets.get(2);
        assertEquals(3, planted.pointId());
        assertEquals(StakeTargets.By.PYLON, planted.by(), "a planted stake is scrapped by its pylon");
        assertEquals(1, planted.strands());
    }

    @Test
    void theClaimIsDownWhenEveryStakeIsScrappedOrStranded() {
        Map<Integer, SurveyStake> stakes = claim();
        assertFalse(StakeTargets.down(stakes));
        stakes.get(1).strand();
        stakes.get(2).strand();
        assertFalse(StakeTargets.down(stakes), "the planted stake still stands");
        stakes.get(3).kill();
        assertTrue(StakeTargets.down(stakes));
        assertTrue(StakeTargets.down(Map.of()), "no stakes: down");
    }
}
