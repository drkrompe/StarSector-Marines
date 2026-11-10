package com.dillon.starsectormarines.battle.derelict;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyStakeTest {

    @Test
    void aPlantedStakeRisesThenEstablishesOnItsCountdowns() {
        SurveyStake stake = new SurveyStake(4, ClaimLattice.HULL);
        assertNull(stake.tick(SurveyStake.RAISE_SECONDS - 1f));
        assertEquals(SurveyStake.Stage.RAISED, stake.tick(1f));
        assertNull(stake.store(), "a raised stake holds no store yet");
        assertEquals(SurveyStake.Stage.ESTABLISHED, stake.tick(SurveyStake.ESTABLISH_SECONDS));
        assertNotNull(stake.store());
        assertTrue(stake.anchors());
        assertNull(stake.tick(1000f), "established is the last stage");
    }

    @Test
    void aStrandedStakeKeepsItsStageAndBuildsNothing() {
        SurveyStake stake = new SurveyStake(4, ClaimLattice.HULL);
        stake.tick(SurveyStake.RAISE_SECONDS / 2f);
        stake.strand();
        assertNull(stake.tick(SurveyStake.RAISE_SECONDS * 10f));
        assertEquals(SurveyStake.Stage.PLANTED, stake.stage());
        SurveyStake established = new SurveyStake(5, ClaimLattice.HULL, SurveyStake.Stage.ESTABLISHED);
        established.strand();
        assertEquals(SurveyStake.Stage.ESTABLISHED, established.stage());
        assertFalse(established.anchors(), "a stranded stake carries the claim on no further");
    }

    @Test
    void onlyAPlantedStakeIsKilledToScrap() {
        SurveyStake planted = new SurveyStake(1, ClaimLattice.HULL);
        assertTrue(planted.kill());
        assertEquals(SurveyStake.Stage.SCRAP, planted.stage());
        assertFalse(planted.builds());
        SurveyStake raised = new SurveyStake(2, ClaimLattice.HULL, SurveyStake.Stage.RAISED);
        assertFalse(raised.kill(), "a raised stake is fought as a zone, by its node");
    }

    /** Hull <- 0 <- 1 <- 2, and hull <- 3. */
    private static Map<Integer, Integer> chain() {
        Map<Integer, Integer> parents = new TreeMap<>();
        parents.put(0, ClaimLattice.HULL);
        parents.put(1, 0);
        parents.put(2, 1);
        parents.put(3, ClaimLattice.HULL);
        return parents;
    }

    @Test
    void cuttingANodeStrandsEverythingBeyondIt() {
        assertEquals(Set.of(), RelayChain.cut(chain(), s -> true, true));
        assertEquals(Set.of(1, 2), RelayChain.cut(chain(), s -> s != 1, true));
    }

    @Test
    void puttingTheForgeOutStrandsEveryStake() {
        assertEquals(Set.of(0, 1, 2, 3), RelayChain.cut(chain(), s -> true, false));
    }
}
