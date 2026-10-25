package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a round landing on a plated Rav shows ({@code SwarmHitFeedback}): the armour law read as glances. */
class SwarmHitFeedbackTest {

    @Test
    void theGlanceShareIsThePartOfTheRatingThePenetrationMisses() {
        assertEquals(0.5f, SwarmHitFeedback.glanceShare(7f, 700f, 14f), 1e-5f);
        assertEquals(0f, SwarmHitFeedback.glanceShare(18f, 700f, 14f), 1e-5f);
        assertEquals(0f, SwarmHitFeedback.glanceShare(7f, 0f, 14f), 1e-5f, "a stripped body turns nothing");
        assertEquals(0f, SwarmHitFeedback.glanceShare(7f, 20f, 0f), 1e-5f);
    }

    @Test
    void aRoundGlancesBitesShellOrReachesFlesh() {
        assertEquals(SwarmHitFeedback.GLANCE, SwarmHitFeedback.kind(7f, 700f, 692f, 14f, 0.2f));
        assertEquals(SwarmHitFeedback.CHITIN, SwarmHitFeedback.kind(7f, 700f, 692f, 14f, 0.8f));
        assertEquals(SwarmHitFeedback.ICHOR, SwarmHitFeedback.kind(18f, 30f, 0f, 14f, 0.2f),
                "the round that breaks the plate goes through to the flesh");
        assertEquals(SwarmHitFeedback.ICHOR, SwarmHitFeedback.kind(7f, 0f, 0f, 14f, 0f));
    }

    @Test
    void aboutTheGlanceShareOfRoundsGlance() {
        int glances = 0, rounds = 4000;
        for (int i = 0; i < rounds; i++) {
            float roll = SwarmHitFeedback.roll(77L, i / 4, i % 4);
            if (SwarmHitFeedback.kind(7f, 700f, 690f, 14f, roll) == SwarmHitFeedback.GLANCE) glances++;
        }
        assertEquals(0.5, glances / (double) rounds, 0.04);
        assertEquals(SwarmHitFeedback.roll(77L, 3, 1), SwarmHitFeedback.roll(77L, 3, 1));
    }

    @Test
    void aHeavierRoundThrowsABiggerPuff() {
        assertEquals(1f, SwarmHitFeedback.scale(SwarmHitFeedback.REFERENCE_DAMAGE), 1e-5f);
        assertTrue(SwarmHitFeedback.scale(100f) > SwarmHitFeedback.scale(14f));
        assertTrue(SwarmHitFeedback.scale(14f) > SwarmHitFeedback.scale(5f));
        assertEquals(SwarmHitFeedback.MIN_SCALE, SwarmHitFeedback.scale(0.5f), 1e-5f);
        assertEquals(SwarmHitFeedback.MAX_SCALE, SwarmHitFeedback.scale(10000f), 1e-5f);
    }

    @Test
    void aRoundStrikesTheRimFacingItsGun() {
        float[] at = new float[2];
        // Facing north (0 degrees), 2 cells long each way and 1 across; a gun due east.
        SwarmHitFeedback.strikePoint(10f, 10f, 0f, 0f, 2f, 1f, 30f, 10f, at);
        assertEquals(10f + SwarmHitFeedback.RIM_IN, at[0], 1e-4f);
        assertEquals(10f, at[1], 1e-4f);
        // A gun dead ahead strikes the nose, which lies further out.
        SwarmHitFeedback.strikePoint(10f, 10f, 0f, 0f, 2f, 1f, 10f, 40f, at);
        assertEquals(10f, at[0], 1e-4f);
        assertEquals(10f + 2f * SwarmHitFeedback.RIM_IN, at[1], 1e-4f);
    }
}
