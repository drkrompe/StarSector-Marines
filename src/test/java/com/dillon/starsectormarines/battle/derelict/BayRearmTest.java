package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.derelict.BayRearm.Step;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BayRearmTest {

    @Test
    void itSetsOffOnlyWithEveryRackSpentAndNothingInHand() {
        assertEquals(Step.NONE, new BayRearm().decide(false, false, false, true, false, false), "a pack left");
        assertEquals(Step.NONE, new BayRearm().decide(true, false, true, true, false, false), "in a fight");
        assertEquals(Step.WALK, new BayRearm().decide(true, false, false, true, false, false));
        assertEquals(Step.LOAD, new BayRearm().decide(true, false, false, true, false, true));
    }

    @Test
    void itKeepsAtItUntilFullThoughThePacksAreNoLongerAllSpent() {
        BayRearm rearm = new BayRearm();
        rearm.decide(true, false, false, true, false, true);
        assertEquals(Step.LOAD, rearm.decide(false, false, false, true, false, true));
        assertEquals(Step.NONE, rearm.decide(false, true, false, true, false, true));
        assertEquals(Step.NONE, rearm.decide(false, false, false, true, false, true), "half full: not again");
    }

    @Test
    void theBayLostTheZoneDarkOrTheContingencyBreakItOff() {
        for (int k = 0; k < 3; k++) {
            BayRearm rearm = new BayRearm();
            rearm.decide(true, false, false, true, false, false);
            Step step = k == 0 ? rearm.decide(true, false, false, false, false, false)
                    : k == 1 ? rearm.decide(true, false, true, true, false, false)
                    : rearm.decide(true, false, false, true, true, false);
            assertEquals(Step.NONE, step);
        }
        assertEquals(Step.NONE, new BayRearm().decide(true, false, false, true, true, false), "never in the contingency");
    }

    @Test
    void packsGoInAtTheirPace() {
        BayRearm rearm = new BayRearm();
        assertEquals(0, rearm.packs(BayRearm.PACK_SECONDS * 0.5f));
        assertEquals(1, rearm.packs(BayRearm.PACK_SECONDS * 0.6f));
        assertEquals(2, rearm.packs(BayRearm.PACK_SECONDS * 2f));
        assertTrue(BayRearm.atBay(10f, 10f, 10f + BayRearm.REARM_REACH, 10f - BayRearm.REARM_REACH));
        assertFalse(BayRearm.atBay(10f, 10f, 10.5f + BayRearm.REARM_REACH, 10f));
    }
}
