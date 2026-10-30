package com.dillon.starsectormarines.battle.weapon;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeldLoopEnvelopeTest {

    @Test
    void aLoopWithNoTailKeysKeepsTheOldHold() throws Exception {
        assertSame(HeldLoopEnvelope.DEFAULT, HeldLoopEnvelope.parse(null, "w"));
        HeldLoopEnvelope plain = HeldLoopEnvelope.parse(new JSONObject().put("loopSound", "laser_loop"), "w");
        assertSame(HeldLoopEnvelope.DEFAULT, plain);
        assertFalse(plain.authored());
        assertEquals(HeldLoopEnvelope.DEFAULT_TAIL, plain.tail(), 0f);
        assertEquals(1f, plain.level(HeldLoopEnvelope.DEFAULT_TAIL * 0.99f), 0f);
    }

    @Test
    void anAuthoredTailHoldsThenFallsSmoothlyWithItsPitch() throws Exception {
        HeldLoopEnvelope e = HeldLoopEnvelope.parse(new JSONObject().put("loopSound", "laser_loop")
                .put("loopTail", 0.5).put("loopFadeOut", 0.3).put("loopPitchDrop", 0.2), "w");
        assertTrue(e.authored());
        assertEquals(0.2f, e.sustain(), 1e-6f);
        assertEquals(HeldLoopEnvelope.DEFAULT_FADE_IN, e.fadeIn(), 0f);
        assertEquals(1f, e.level(0.2f), 1e-6f);
        assertEquals(0.5f, e.level(0.35f), 1e-5f);
        assertEquals(0f, e.level(0.5f), 1e-6f);
        assertEquals(0.9f, e.pitch(0.5f), 1e-6f);
        assertEquals(0.8f, e.pitch(0f), 1e-6f);
    }

    @Test
    void aTailThatWouldDipABurstOrHasNoLoopIsRejected() throws Exception {
        assertThrows(JSONException.class, () -> HeldLoopEnvelope.parse(
                new JSONObject().put("loopTail", 0.5), "w"), "no loopSound");
        assertThrows(JSONException.class, () -> HeldLoopEnvelope.parse(new JSONObject().put("loopSound", "l")
                .put("loopTail", 0.3).put("loopFadeOut", 0.25), "w"), "holds only 0.05 s at full");
        assertThrows(JSONException.class, () -> HeldLoopEnvelope.parse(new JSONObject().put("loopSound", "l")
                .put("loopTail", 0.5).put("loopPitchDrop", 0.8), "w"), "pitch drop past the bound");
    }
}
