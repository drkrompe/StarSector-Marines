package com.dillon.starsectormarines.campaign.start;

import com.dillon.starsectormarines.campaign.start.SecondWindKeep.Action;
import com.dillon.starsectormarines.campaign.start.SecondWindKeep.Port;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecondWindKeepTest {

    @Test
    void sheIsLeftAloneInTheFleetInStorageOrInTheYard() {
        assertEquals(Action.NONE, SecondWindKeep.decide(true, false, false, true, false));
        assertEquals(Action.NONE, SecondWindKeep.decide(false, true, false, false, false));
        assertEquals(Action.NONE, SecondWindKeep.decide(false, false, true, true, false));
    }

    @Test
    void anOpenDialogMayStillRecoverHer() {
        assertEquals(Action.WAIT, SecondWindKeep.decide(false, false, false, true, true));
        assertEquals(Action.WAIT, SecondWindKeep.decide(false, false, false, false, true));
    }

    @Test
    void aFightSendsHerToTheYardAndAnythingElseBringsHerStraightBack() {
        assertEquals(Action.SEND_TO_YARD, SecondWindKeep.decide(false, false, false, true, false));
        assertEquals(Action.RETURN_TO_FLEET, SecondWindKeep.decide(false, false, false, false, false));
    }

    @Test
    void aLossAddsOneDModUntilTheCap() {
        assertEquals(1, SecondWindKeep.dModsToAdd(0));
        assertEquals(1, SecondWindKeep.dModsToAdd(3));
        assertEquals(1, SecondWindKeep.dModsToAdd(SecondWindKeep.D_MOD_CAP - 1));
        assertEquals(0, SecondWindKeep.dModsToAdd(SecondWindKeep.D_MOD_CAP));
        assertEquals(0, SecondWindKeep.dModsToAdd(SecondWindKeep.D_MOD_CAP + 2));
    }

    @Test
    void theBillIsAQuarterOfHerHullValue() {
        assertEquals(2250, SecondWindKeep.yardBill(9000f));
        assertEquals(0, SecondWindKeep.yardBill(-5f));
    }

    @Test
    void sheIsReadyAfterTheYardDays() {
        assertFalse(SecondWindKeep.ready(SecondWindKeep.YARD_DAYS - 0.5f));
        assertTrue(SecondWindKeep.ready(SecondWindKeep.YARD_DAYS));
        assertEquals(SecondWindKeep.YARD_DAYS, SecondWindKeep.daysLeft(0f));
        assertEquals(1, SecondWindKeep.daysLeft(SecondWindKeep.YARD_DAYS - 0.5f));
        assertEquals(0, SecondWindKeep.daysLeft(SecondWindKeep.YARD_DAYS + 3f));
    }

    @Test
    void onlyTheScuttlingsGainIsTakenBack() {
        assertEquals(40f, SecondWindKeep.scuttleClawback(40f, 10f, 50f));
        // The holds gained less than her yield (part of it did not fit): only what arrived goes.
        assertEquals(15f, SecondWindKeep.scuttleClawback(40f, 10f, 25f));
        // Nothing gained, nothing taken: she was not scuttled.
        assertEquals(0f, SecondWindKeep.scuttleClawback(40f, 30f, 20f));
    }

    @Test
    void theStartPortIsPreferredThenTheNearestFriendlyThenTheNearest() {
        Port start = new Port("start", true, false, 9f);
        Port friendly = new Port("friendly", false, true, 5f);
        Port near = new Port("near", false, false, 1f);
        assertEquals("start", SecondWindKeep.pickYard(List.of(near, friendly, start)).marketId());
        assertEquals("friendly", SecondWindKeep.pickYard(List.of(near, friendly)).marketId());
        assertEquals("near", SecondWindKeep.pickYard(List.of(near, new Port("far", false, false, 4f))).marketId());
        assertNull(SecondWindKeep.pickYard(List.of()));
    }
}
