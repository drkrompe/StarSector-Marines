package com.dillon.starsectormarines.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.dillon.starsectormarines.campaign.refit.RefitOrder;
import com.dillon.starsectormarines.ops.RefitReveal.Phase;
import org.junit.jupiter.api.Test;

class RefitRevealTest {
    private int paid;

    private RefitReveal reveal(String refusal) {
        return new RefitReveal(RefitOrder.LIFE_SUPPORT_RESTORED, () -> { paid++; return refusal; });
    }

    private static void run(RefitReveal reveal, float seconds, boolean ready) {
        for (float t = 0f; t < seconds; t += 0.05f) reveal.advance(0.05f, ready);
    }

    @Test
    void theOrderIsPaidUnderTheCurtainNotAtTheClick() {
        var reveal = reveal(null);
        run(reveal, RefitReveal.FLY, true);
        assertEquals(0, paid, "the flight shows the room as it was");
        run(reveal, RefitReveal.CLOSE + 0.05f, true);
        assertEquals(1, paid);
        assertEquals(1f, reveal.curtain());
        run(reveal, 10f, true);
        assertEquals(Phase.DONE, reveal.phase());
        assertEquals(1, paid, "paid once");
    }

    @Test
    void theCurtainWaitsForADeckStillBeingLaidOut() {
        var reveal = reveal(null);
        run(reveal, RefitReveal.FLY + RefitReveal.CLOSE + RefitReveal.WORK + 2f, false);
        assertEquals(Phase.WORK, reveal.phase());
        assertEquals(1f, reveal.curtain());
        reveal.advance(0.05f, true);
        assertEquals(Phase.OPEN, reveal.phase());
    }

    @Test
    void skippingHurriesToThePaymentAndNeverPastIt() {
        var reveal = reveal(null);
        reveal.skip();
        assertEquals(1, paid);
        assertEquals(Phase.WORK, reveal.phase());
        reveal.skip();
        assertEquals(1, paid);
        reveal.advance(0.01f, true);
        assertEquals(Phase.OPEN, reveal.phase());
    }

    @Test
    void aRefusalLiftsTheCurtainWithoutTheResult() {
        var reveal = reveal("Short of credits.");
        reveal.skip();
        assertEquals("Short of credits.", reveal.refusal());
        reveal.advance(0.01f, true);
        assertEquals(0f, reveal.caption());
        run(reveal, RefitReveal.OPEN + 0.1f, true);
        assertEquals(Phase.RETURN, reveal.phase(), "the camera still goes home after a refusal");
        run(reveal, RefitReveal.RETURN + 0.1f, true);
        assertEquals(Phase.DONE, reveal.phase());
    }

    @Test
    void theCameraFliesHomeBeforeTheTreeReturns() {
        var reveal = reveal(null);
        reveal.skip();
        run(reveal, RefitReveal.OPEN + RefitReveal.SHOW + 0.2f, true);
        assertEquals(Phase.RETURN, reveal.phase());
        reveal.skip();
        assertEquals(Phase.DONE, reveal.phase());
    }

    @Test
    void aCleanRunHasNoRefusal() {
        var reveal = reveal(null);
        reveal.skip();
        assertNull(reveal.refusal());
    }
}
