package com.dillon.starsectormarines.battle.air;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A carrier bay's airframe rearms after coming home, is replaced after a loss, and never runs out. */
class FighterBayTest {

    private static final float REFIT = 20f;
    private static final float REPLACEMENT = 90f;

    private static void run(FighterBay bay, float seconds) {
        for (float t = 0f; t < seconds - 1e-4f; t += 0.5f) bay.tick(0.5f);
    }

    @Test
    void firstLaunchWaitsForItsArrivalTime() {
        FighterBay bay = new FighterBay(8f, REFIT, REPLACEMENT);
        run(bay, 7.5f);
        assertFalse(bay.ready());
        run(bay, 0.5f);
        assertTrue(bay.ready());
    }

    @Test
    void anAirframeThatComesHomeFliesAgainAfterTheRefit() {
        FighterBay bay = new FighterBay(0f, REFIT, REPLACEMENT);
        bay.launched();
        run(bay, 60f);
        assertFalse(bay.ready(), "an airborne airframe cannot launch again");

        bay.cameHome();
        assertEquals(FighterBay.State.REFITTING, bay.state());
        run(bay, REFIT - 0.5f);
        assertFalse(bay.ready());
        run(bay, 0.5f);
        assertTrue(bay.ready());
    }

    @Test
    void aLostAirframeIsReplacedAfterTheLongerRespawn() {
        FighterBay bay = new FighterBay(0f, REFIT, REPLACEMENT);
        bay.launched();
        bay.lost();
        assertEquals(FighterBay.State.REPLACING, bay.state());
        run(bay, REFIT);
        assertFalse(bay.ready(), "a loss must cost more than a rearm");
        run(bay, REPLACEMENT - REFIT);
        assertTrue(bay.ready());
    }

    @Test
    void theStockNeverRunsOut() {
        FighterBay bay = new FighterBay(0f, REFIT, REPLACEMENT);
        for (int sortie = 0; sortie < 50; sortie++) {
            assertTrue(bay.ready(), "sortie " + sortie);
            bay.launched();
            if (sortie % 2 == 0) {
                bay.lost();
                run(bay, REPLACEMENT);
            } else {
                bay.cameHome();
                run(bay, REFIT);
            }
        }
    }

    @Test
    void anOutcomeIsCountedOnlyForAnAirborneAirframe() {
        FighterBay bay = new FighterBay(0f, REFIT, REPLACEMENT);
        bay.launched();
        bay.lost();
        bay.cameHome();
        assertEquals(FighterBay.State.REPLACING, bay.state(), "a late home-coming must not shorten a replacement");
        assertEquals(REPLACEMENT, bay.secondsToLaunch());
    }
}
