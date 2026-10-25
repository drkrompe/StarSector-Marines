package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The battle's Bombard count: a cap across the field, drawn only at the bores nearest the line. */
class BombardQuotaTest {

    @Test
    void aBoreNearTheLineDrawsWhileTheFieldIsUnderTheCap() {
        assertTrue(BombardQuota.allowed(false, 0, 0));
        assertTrue(BombardQuota.allowed(false, BombardQuota.CAP - 1, BombardQuota.CAP - 1));
    }

    @Test
    void theCapHoldsAcrossTheBattle() {
        assertFalse(BombardQuota.allowed(false, BombardQuota.CAP, 0));
    }

    @Test
    void aBoreFurtherBackThanTheCapNeverDraws() {
        assertFalse(BombardQuota.allowed(false, 0, BombardQuota.CAP));
    }

    @Test
    void aBoreHoldsOneAtATime() {
        assertFalse(BombardQuota.allowed(true, 0, 0));
    }
}
