package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.weapon.HeldLoopEnvelope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeldFireLoopsTest {

    @Test
    void aLanceStartsOnceAndHoldsWhilePulsesArrive() {
        HeldFireLoops loops = new HeldFireLoops();
        assertTrue(loops.arm(7L, "pd_laser_loop", 1f, 0f, 0f), "first pulse starts the lance");
        for (int pulse = 0; pulse < 9; pulse++) {
            loops.advance(0.1f);
            assertFalse(loops.arm(7L, "pd_laser_loop", 1f, 0f, 0f), "later pulses keep the loop");
        }
        assertEquals(1, loops.held().size());
    }

    @Test
    void theLoopLapsesWithoutAPulseAndTheNextPulseStartsAgain() {
        HeldFireLoops loops = new HeldFireLoops();
        loops.arm(7L, "pd_laser_loop", 1f, 0f, 0f);
        loops.advance(HeldFireLoops.HOLD_SECONDS * 0.9f);
        assertTrue(loops.isHeld(7L));
        loops.advance(HeldFireLoops.HOLD_SECONDS * 0.2f);
        assertFalse(loops.isHeld(7L));
        assertTrue(loops.arm(7L, "pd_laser_loop", 1f, 0f, 0f), "a pulse after the cooldown is a new lance");
    }

    @Test
    void eachShooterHoldsItsOwnVoice() {
        HeldFireLoops loops = new HeldFireLoops();
        assertTrue(loops.arm(1L, "pd_laser_loop", 1f, 0f, 0f));
        assertTrue(loops.arm(2L, "pd_laser_loop", 1f, 5f, 5f));
        assertEquals(2, loops.held().size());
    }

    // Holds 0.2 s at full, then fades over 0.3 s with its pitch sagging by up to 0.2.
    private static final HeldLoopEnvelope TAIL = new HeldLoopEnvelope(0.5f, 0.3f, 0.05f, 0.2f, true);

    @Test
    void anUnauthoredLoopStaysAtFullUntilItIsLetGo() {
        HeldFireLoops loops = new HeldFireLoops();
        loops.arm(7L, "laser_loop", 0.8f, 0f, 0f);
        loops.advance(HeldFireLoops.HOLD_SECONDS * 0.9f);
        HeldFireLoops.Voice voice = loops.held().get(0);
        assertEquals(0.8f, voice.volume(), 1e-6f);
        assertEquals(1f, voice.pitch(), 1e-6f);
        assertFalse(voice.authored(), "the screen keeps the engine's own fades");
    }

    @Test
    void anAuthoredTailOutlivesTheLastPulseFadingAndSaggingThenLetsGo() {
        HeldFireLoops loops = new HeldFireLoops();
        loops.arm(7L, "laser_loop", TAIL, 1f, 0f, 0f);
        loops.advance(0.19f);
        HeldFireLoops.Voice voice = loops.held().get(0);
        assertEquals(1f, voice.volume(), 1e-6f, "full through the sustain");
        loops.advance(0.16f);
        assertTrue(voice.volume() > 0.1f && voice.volume() < 0.9f, "fading at " + voice.volume());
        assertTrue(voice.pitch() < 1f && voice.pitch() > 0.8f, "sagging at " + voice.pitch());
        float mid = voice.volume();
        loops.advance(0.1f);
        assertTrue(voice.volume() < mid, "still falling");
        loops.advance(0.06f);
        assertFalse(loops.isHeld(7L), "let go at the tail's end");
        assertTrue(loops.arm(7L, "laser_loop", TAIL, 1f, 0f, 0f), "a pulse after the tail is a new lance");
    }

    @Test
    void aPulseInTheTailCarriesTheSameVoiceBackUpWithoutANewIntro() {
        HeldFireLoops loops = new HeldFireLoops();
        loops.arm(7L, "laser_loop", TAIL, 1f, 0f, 0f);
        loops.advance(0.4f);
        HeldFireLoops.Voice voice = loops.held().get(0);
        float faded = voice.volume();
        assertTrue(faded < 0.5f);
        assertFalse(loops.arm(7L, "laser_loop", TAIL, 1f, 0f, 0f), "no intro clip mid-tail");
        assertSame(voice, loops.held().get(0), "the same loop voice carries on");
        assertEquals(faded, voice.volume(), 1e-6f, "no jump on the pulse itself");
        loops.advance(HeldFireLoops.RISE_SECONDS / 2f);
        assertTrue(voice.volume() > faded && voice.volume() < 1f, "rising, at " + voice.volume());
        loops.advance(HeldFireLoops.RISE_SECONDS);
        assertEquals(1f, voice.volume(), 1e-6f);
        assertEquals(1f, voice.pitch(), 1e-6f);
    }

    @Test
    void voicesAreCappedAndANewLanceTakesTheQuietestTail() {
        HeldFireLoops loops = new HeldFireLoops();
        for (long id = 0; id < HeldFireLoops.MAX_VOICES; id++) loops.arm(id, "laser_loop", TAIL, 1f, 0f, 0f);
        assertFalse(loops.arm(99L, "laser_loop", TAIL, 1f, 0f, 0f), "every voice firing: the new lance waits");
        assertFalse(loops.isHeld(99L));
        loops.advance(0.35f);
        for (long id = 1; id < HeldFireLoops.MAX_VOICES; id++) loops.arm(id, "laser_loop", TAIL, 1f, 0f, 0f);
        assertTrue(loops.arm(99L, "laser_loop", TAIL, 1f, 0f, 0f), "the one fading voice gives up its slot");
        assertFalse(loops.isHeld(0L));
        assertEquals(HeldFireLoops.MAX_VOICES, loops.held().size());
    }
}
