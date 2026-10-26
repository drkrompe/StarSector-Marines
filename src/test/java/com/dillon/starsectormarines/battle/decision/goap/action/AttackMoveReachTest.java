package com.dillon.starsectormarines.battle.decision.goap.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttackMoveReachTest {

    @Test
    void aSquadSentToDemolishABodyClosesThroughContactUntilTheBodyIsInItsReach() {
        // Broad Front frozen 5: thirty cells off mother-1 with a 26.4-cell rifle.
        assertTrue(AttackMove.closesToReach(30f, 26.4f));
        assertFalse(AttackMove.closesToReach(25f, 26.4f), "in reach, it stops for contact as any squad does");
        assertFalse(AttackMove.closesToReach(60f, 26.4f), "far off, it fights its way across the field as before");
    }
}
