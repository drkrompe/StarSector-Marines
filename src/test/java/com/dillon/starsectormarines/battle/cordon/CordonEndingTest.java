package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.cordon.CordonSequence.Ending;
import com.dillon.starsectormarines.battle.cordon.CordonSequence.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Cordon's end rules ({@code cordon-nouns.md}, Flow, the end): every way out names its reason. */
class CordonEndingTest {

    @Test
    void aWipedCompanyBreaksTheCordonWhateverElseHolds() {
        assertEquals(new Ending(Outcome.BROKEN, "company_lost"), CordonSequence.ending(false, 0, 1, true));
        assertEquals(new Ending(Outcome.BROKEN, "company_lost"),
                CordonSequence.roadEnding(false, true, true, false, 40, 24, false));
    }

    @Test
    void theLineIsHeldOnItsRedoubts() {
        assertNull(CordonSequence.ending(true, 1, 1, false));
        assertEquals("line_broken", CordonSequence.ending(true, 2, 1, false).beat());
        assertEquals(new Ending(Outcome.HELD, "cordon_held"), CordonSequence.ending(true, 1, 1, true));
    }

    @Test
    void theRoadEndsOnlyOnceTheTownIsFinishedTheCompanyIsBackAndNoConvoyIsStillOut() {
        assertNull(CordonSequence.roadEnding(true, false, true, false, 40, 24, false), "the town is still held");
        assertNull(CordonSequence.roadEnding(true, true, false, false, 40, 24, false), "the company is still out there");
        assertNull(CordonSequence.roadEnding(true, true, true, true, 20, 24, false), "a convoy is still driving out");
        assertEquals(new Ending(Outcome.HELD, "cordon_held"),
                CordonSequence.roadEnding(true, true, true, false, 24, 24, false));
        assertEquals(new Ending(Outcome.BROKEN, "people_short"),
                CordonSequence.roadEnding(true, true, true, false, 23, 24, false));
        // The hard cap reads the same verdict wherever the battle stands.
        assertEquals("cordon_held", CordonSequence.roadEnding(true, false, false, true, 30, 24, true).beat());
    }

    @Test
    void theTownIsFinishedWhenEmptyOrAbandoned() {
        assertNull(CordonSequence.townFinished(48, 12, false));
        assertEquals("town_empty", CordonSequence.townFinished(48, 0, false));
        assertEquals("town_abandoned", CordonSequence.townFinished(48, 12, true));
        assertNull(CordonSequence.townFinished(0, 0, false), "a town that never had people is not finished by that");
        assertEquals(24, CordonSequence.peopleNeeded(48));
        assertEquals(25, CordonSequence.peopleNeeded(49));
        assertTrue(CordonSequence.behindLine(96, 100f, 1));
        assertFalse(CordonSequence.behindLine(97, 100f, 1));
        assertTrue(CordonSequence.behindLine(104, 100f, -1));
    }
}
