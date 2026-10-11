package com.dillon.starsectormarines.battle.decision.goap.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransitSpacingTest {

    @Test
    void aSquadJustAheadOnTheApproachHoldsTheOneBehind() {
        assertTrue(TransitSpacing.isAheadWithin(0, 0, 5, 0, 100, 0, 9));
    }

    @Test
    void aSquadBehindDoesNotHoldTheOneAhead() {
        assertFalse(TransitSpacing.isAheadWithin(5, 0, 0, 0, 100, 0, 9));
    }

    @Test
    void twoSquadsCannotHoldEachOther() {
        boolean aHeldByB = TransitSpacing.isAheadWithin(0, 0, 4, 3, 50, 50, 9);
        boolean bHeldByA = TransitSpacing.isAheadWithin(4, 3, 0, 0, 50, 50, 9);
        assertFalse(aHeldByB && bHeldByA);
    }

    @Test
    void aSquadBeyondTheGapIsNotFollowedClosely() {
        assertFalse(TransitSpacing.isAheadWithin(0, 0, 20, 0, 100, 0, 9));
    }
}
