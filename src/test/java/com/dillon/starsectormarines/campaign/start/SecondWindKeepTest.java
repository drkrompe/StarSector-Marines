package com.dillon.starsectormarines.campaign.start;

import com.dillon.starsectormarines.campaign.start.SecondWindKeep.Action;
import com.dillon.starsectormarines.campaign.start.SecondWindKeep.Cause;
import com.dillon.starsectormarines.campaign.start.SecondWindKeep.Gate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecondWindKeepTest {

    @Test
    void sheIsLeftAloneInTheFleetInStorageOrOnceLost() {
        assertEquals(Action.NONE, SecondWindKeep.decide(true, false, false, false));
        assertEquals(Action.NONE, SecondWindKeep.decide(false, true, false, false));
        assertEquals(Action.NONE, SecondWindKeep.decide(false, false, true, false));
    }

    @Test
    void anOpenDialogMayStillRecoverHerOtherwiseSheIsLost() {
        assertEquals(Action.WAIT, SecondWindKeep.decide(false, false, false, true));
        assertEquals(Action.LOSE, SecondWindKeep.decide(false, false, false, false));
    }

    @Test
    void theCauseIsTheSaleThenTheFightThenTheSalvage() {
        assertEquals(Cause.SOLD, SecondWindKeep.cause(true, true, true));
        assertEquals(Cause.BATTLE, SecondWindKeep.cause(false, true, true));
        assertEquals(Cause.SCUTTLED, SecondWindKeep.cause(false, false, true));
        assertEquals(Cause.OTHER, SecondWindKeep.cause(false, false, false));
        assertFalse(Cause.BATTLE.onTheMarket());
        assertTrue(Cause.SOLD.onTheMarket() && Cause.SCUTTLED.onTheMarket() && Cause.OTHER.onTheMarket());
    }

    @Test
    void aLossAddsOneDModUntilTheCap() {
        assertEquals(1, SecondWindKeep.dModsToAdd(0));
        assertEquals(1, SecondWindKeep.dModsToAdd(SecondWindKeep.D_MOD_CAP - 1));
        assertEquals(0, SecondWindKeep.dModsToAdd(SecondWindKeep.D_MOD_CAP));
        assertEquals(0, SecondWindKeep.dModsToAdd(SecondWindKeep.D_MOD_CAP + 2));
    }

    @Test
    void theBillIsAQuarterOfHerValueAndNeverLessThanLettingHerGoPaid() {
        assertEquals(2250, SecondWindKeep.bill(9000f, 0f));
        assertEquals(2250, SecondWindKeep.bill(9000f, 1800f));
        // Selling her and buying her back is never a profit.
        assertEquals(3100, SecondWindKeep.bill(9000f, 3100f));
        assertEquals(0, SecondWindKeep.bill(-5f, -1f));
    }

    @Test
    void onlyWhatAScuttlingPutInTheHoldsCountsAsSalvage() {
        assertEquals(40f, SecondWindKeep.salvageGained(40f, 10f, 50f));
        assertEquals(15f, SecondWindKeep.salvageGained(40f, 10f, 25f));
        assertEquals(0f, SecondWindKeep.salvageGained(40f, 30f, 20f));
    }

    @Test
    void missionsNeedHerInTheFleetWithTheCompanyAboard() {
        assertEquals(Gate.OPEN, SecondWindKeep.gate(true, true, false, true));
        assertEquals(Gate.NOT_ABOARD, SecondWindKeep.gate(true, true, false, false));
        assertEquals(Gate.STORED, SecondWindKeep.gate(true, false, false, true));
        assertEquals(Gate.LOST, SecondWindKeep.gate(true, false, true, true));
        // Other starts never had her.
        assertEquals(Gate.OPEN, SecondWindKeep.gate(false, false, true, false));
    }
}
