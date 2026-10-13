package com.dillon.starsectormarines.battle.battery;

import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The fire-control point's rules: who holds it, when it is contested, and how it changes hands. */
class FireControlTest {

    private static final float DT = 1f;

    private static void run(FireControl fc, float seconds, boolean holderPresent, Faction hostile) {
        for (float t = 0f; t < seconds; t += DT) fc.tick(DT, holderPresent, hostile);
    }

    @Test
    void anUntouchedPointStaysWithItsHolderAndFires() {
        FireControl fc = new FireControl(Faction.DEFENDER);
        run(fc, 60f, true, null);
        assertEquals(Faction.DEFENDER, fc.holder());
        assertTrue(fc.operational());
    }

    @Test
    void hostilePresenceContestsAtOnceAndSilencesTheGuns() {
        FireControl fc = new FireControl(Faction.DEFENDER);
        fc.tick(DT, false, Faction.MARINE);
        assertTrue(fc.contested());
        assertFalse(fc.operational());
        assertEquals(Faction.MARINE, fc.challenger());
    }

    @Test
    void anUncontestedHostileTakesThePointAfterTheCaptureTime() {
        FireControl fc = new FireControl(Faction.DEFENDER);
        fc.tick(DT, false, Faction.MARINE);
        run(fc, FireControl.CAPTURE_SECONDS - 1f, false, Faction.MARINE);
        assertTrue(fc.contested(), "not yet");
        run(fc, 2f, false, Faction.MARINE);
        assertEquals(Faction.MARINE, fc.holder());
        assertTrue(fc.operational(), "the new holder has live guns");
        assertNull(fc.challenger());
    }

    @Test
    void theHolderStandingThereKeepsAContestFromProgressing() {
        FireControl fc = new FireControl(Faction.DEFENDER);
        fc.tick(DT, true, Faction.MARINE);
        run(fc, 60f, true, Faction.MARINE);
        assertEquals(Faction.DEFENDER, fc.holder());
        assertTrue(fc.contested(), "both sides present leaves the contest where it is");
        assertEquals(0f, fc.captureProgress(), 1e-6f);
    }

    @Test
    void aHolderLeftAloneOrAnEmptyPointPushesTheContestBack() {
        FireControl fc = new FireControl(Faction.DEFENDER);
        fc.tick(DT, false, Faction.MARINE);
        run(fc, 6f, false, Faction.MARINE);
        assertTrue(fc.captureProgress() > 0f);
        run(fc, FireControl.RESTORE_SECONDS, true, null);
        assertFalse(fc.contested());
        assertEquals(Faction.DEFENDER, fc.holder());
    }

    @Test
    void aCapturedPointIsRetakenByTheSameRules() {
        FireControl fc = new FireControl(Faction.DEFENDER);
        run(fc, FireControl.CAPTURE_SECONDS + 3f, false, Faction.MARINE);
        assertEquals(Faction.MARINE, fc.holder());
        run(fc, FireControl.CAPTURE_SECONDS + 3f, false, Faction.DEFENDER);
        assertEquals(Faction.DEFENDER, fc.holder());
    }

    @Test
    void anAllyTakesAPointForTheMarines() {
        FireControl fc = new FireControl(Faction.DEFENDER);
        run(fc, FireControl.CAPTURE_SECONDS + 3f, false, Faction.ALLY);
        assertEquals(Faction.MARINE, fc.holder());
        assertEquals(Faction.MARINE, new FireControl(Faction.ALLY).holder());
    }
}
