package com.dillon.starsectormarines.battle.ruins;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultKeysTest {

    private static final float HALF = VaultKeys.TURN_SECONDS / 2f;

    @Test
    void theKeysTurnOnlyPoweredAndHeldTogetherForTheirTime() {
        VaultKeys keys = new VaultKeys();
        assertFalse(keys.tick(false, true, true, VaultKeys.TURN_SECONDS), "unpowered");
        assertFalse(keys.tick(true, true, false, VaultKeys.TURN_SECONDS), "one key alone");
        assertFalse(keys.tick(true, true, true, HALF));
        assertTrue(keys.tick(true, true, true, HALF));
    }

    @Test
    void lettingEitherKeyGoStartsTheTurnAgain() {
        VaultKeys keys = new VaultKeys();
        keys.tick(true, true, true, HALF);
        keys.tick(true, false, true, 0.05f);
        assertFalse(keys.tick(true, true, true, HALF));
    }

    @Test
    void turnedKeysStayTurned() {
        VaultKeys keys = new VaultKeys();
        keys.tick(true, true, true, VaultKeys.TURN_SECONDS);
        assertTrue(keys.tick(false, false, false, 1f));
    }
}
