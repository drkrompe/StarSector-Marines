package com.dillon.starsectormarines.battle.story;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ColdStartPicketTest {
    private static ColdStartPicket picket() { return new ColdStartPicket("south_picket", 7, 176, 38, 151, 96, 3); }
    @Test void contactAndTimeAloneNeverEraseOrWithdrawAStandingPicket() {
        var picket = picket();
        picket.observe(10000, 3, 0, true, false);
        assertEquals(ColdStartPicket.State.HOLDING, picket.report().state());
        assertTrue(picket.report().contacted());
        assertFalse(picket.report().overrun());
        assertEquals(0, picket.report().lostMembers());
    }
    @Test void firstCasualtyWithdrawsSurvivorsAndTheyMustPhysicallyReachInnerHold() {
        var picket = picket();
        picket.observe(20, 2, 0, true, false);
        assertEquals(ColdStartPicket.State.WITHDRAWING, picket.report().state());
        assertEquals(20, picket.report().withdrawalTick());
        assertFalse(picket.report().overrun());
        picket.observe(30, 2, 1, false, false);
        assertEquals(ColdStartPicket.State.WITHDRAWING, picket.report().state());
        picket.observe(40, 2, 2, false, false);
        assertEquals(ColdStartPicket.State.INNER_HOLD, picket.report().state());
        assertEquals(1, picket.report().lostMembers());
        picket.observe(60, 2, 0, false, false);
        assertEquals(ColdStartPicket.State.WITHDRAWING, picket.report().state());
        assertEquals(20, picket.report().withdrawalTick());
    }
    @Test void breachWithdrawsLivingMilitiaWithoutInventingCasualties() {
        var picket = picket();
        picket.observe(20, 3, 0, true, true);
        assertTrue(picket.report().overrun());
        assertEquals(ColdStartPicket.State.WITHDRAWING, picket.report().state());
        assertEquals(0, picket.report().lostMembers());
        picket.observe(40, 0, 0, false, false);
        assertEquals(ColdStartPicket.State.LOST, picket.report().state());
        assertEquals(3, picket.report().lostMembers());
    }
}
