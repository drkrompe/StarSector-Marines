package com.dillon.starsectormarines.battle.command;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link AirSupportFacts#blocked}: the sky is shut only when a strike is on offer and every assessed cell refuses it. */
class AirSupportFactsBlockedTest {

    private static final long A = AirSupportFacts.key(1, 1);
    private static final long B = AirSupportFacts.key(2, 2);

    @Test
    void shutWhenEveryAssessedCellIsAboveTheCeiling() {
        assertTrue(new AirSupportFacts(2, Map.of(A, 1.9f, B, 1.2f), 1f).blocked());
    }

    @Test
    void openWhenAnyCellIsAcceptable() {
        assertFalse(new AirSupportFacts(2, Map.of(A, 1.9f, B, 0.3f), 1f).blocked());
    }

    @Test
    void notBlockedWithoutAStrikeToOfferOrAnythingAssessed() {
        assertFalse(new AirSupportFacts(0, Map.of(A, 1.9f), 1f).blocked(), "no pool: nothing to be refused");
        assertFalse(new AirSupportFacts(2, Map.of(), 1f).blocked(), "nothing assessed: no knowledge of a refusal");
        assertFalse(AirSupportFacts.NONE.blocked());
    }
}
