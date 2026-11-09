package com.dillon.starsectormarines.battle.ruins;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultCodeTest {

    private static final float HALF = VaultCode.ENTER_SECONDS / 2f;

    @Test
    void theCodeIsEnteredOnlyPoweredKnownAndHeldAtTheKeypad() {
        VaultCode code = new VaultCode();
        assertFalse(code.tick(false, true, true, VaultCode.ENTER_SECONDS), "unpowered");
        assertFalse(code.tick(true, false, true, VaultCode.ENTER_SECONDS), "the records unread");
        assertFalse(code.tick(true, true, true, HALF));
        assertTrue(code.tick(true, true, true, HALF));
    }

    @Test
    void steppingOffStartsTheEntryAgainAndAnEnteredCodeStays() {
        VaultCode code = new VaultCode();
        code.tick(true, true, true, HALF);
        code.tick(true, true, false, 0.05f);
        assertFalse(code.tick(true, true, true, HALF));
        code.tick(true, true, true, VaultCode.ENTER_SECONDS);
        assertTrue(code.tick(false, false, false, 1f));
    }
}
