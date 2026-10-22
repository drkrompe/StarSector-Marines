package com.dillon.starsectormarines.battle.turret;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurretFriendlyFireHoldTest {

    @Test
    void aClearRoundAlwaysGoesAndDrawsNothing() {
        Random rng = new Random(7L);
        assertFalse(TurretFireSystem.holdsRound(false, 1f, rng));
        assertEquals(new Random(7L).nextFloat(), rng.nextFloat(), "a clear round must not touch the stream");
    }

    @Test
    void aFriendlyRoundTakesOneDrawAgainstTheCrewsChance() {
        assertTrue(TurretFireSystem.holdsRound(true, 1f, new Random(1L)));
        assertFalse(TurretFireSystem.holdsRound(true, 0f, new Random(1L)));

        Random rng = new Random(3L);
        Random control = new Random(3L);
        float draw = control.nextFloat();
        assertEquals(draw < TurretFireSystem.CREW_FRIENDLY_HOLD_CHANCE,
                TurretFireSystem.holdsRound(true, TurretFireSystem.CREW_FRIENDLY_HOLD_CHANCE, rng));
        assertEquals(control.nextFloat(), rng.nextFloat(), "the hold takes exactly one draw");
    }
}
