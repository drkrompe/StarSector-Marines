package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The vent's breathing rule: it puffs, rests and puffs again, before the battle's clock started as after. */
class SignatureLookTest {

    @Test
    void aVentBreathesThenRests() {
        float on = 0f, off = 0f;
        for (float t = -30f; t < 30f; t += .05f) {
            float b = SignatureLook.breath(.3f, t);
            assertTrue(b >= 0f && b <= 1f, "breath " + b + " at " + t);
            if (b > .5f) on++;
            if (b == 0f) off++;
        }
        assertTrue(on > 0 && off > 0, "a vent both breathes and rests, before and after zero");
    }

    @Test
    void breathingRepeatsOnItsPeriod() {
        assertEquals(SignatureLook.breath(.2f, 3f), SignatureLook.breath(.2f, 3f - SignatureLook.BREATH_SECONDS), 1e-4f);
    }
}
