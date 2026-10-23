package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.cordon.CordonSequence.Ending;
import com.dillon.starsectormarines.battle.cordon.CordonSequence.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The Cordon's end rules ({@code cordon-nouns.md}, Flow, the end): every way out names its reason. */
class CordonEndingTest {

    @Test
    void aWipedCompanyBreaksTheCordonWhateverElseHolds() {
        // The Road with the gap held, enough convoys left and none short: only the company is gone.
        assertEquals(new Ending(Outcome.BROKEN, "company_lost"),
                CordonSequence.ending(false, 1, 1, false, true, 2, 2, 2, false));
        // Even on the tick the window closes: nobody is left to have held it.
        assertEquals(new Ending(Outcome.BROKEN, "company_lost"),
                CordonSequence.ending(false, 0, 1, false, false, 0, 0, 0, true));
    }

    @Test
    void theOtherRulesStillNameTheirReason() {
        assertNull(CordonSequence.ending(true, 1, 1, false, true, 2, 1, 2, false));
        assertEquals("line_broken", CordonSequence.ending(true, 2, 1, false, false, 0, 0, 0, false).beat());
        assertEquals("gap_closed", CordonSequence.ending(true, 1, 1, true, true, 3, 0, 2, false).beat());
        assertEquals("convoys_lost", CordonSequence.ending(true, 0, 1, false, true, 1, 1, 2, false).beat());
        assertEquals("convoys_short", CordonSequence.ending(true, 0, 1, false, true, 2, 1, 2, true).beat());
        assertEquals(new Ending(Outcome.HELD, "cordon_held"),
                CordonSequence.ending(true, 1, 1, false, true, 2, 2, 2, true));
    }
}
