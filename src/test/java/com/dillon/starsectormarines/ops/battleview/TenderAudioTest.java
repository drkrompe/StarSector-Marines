package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.tender.TenderCues;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenderAudioTest {
    @Test
    void pausedFrameCannotReplayAndRewindStartsFresh() {
        TenderAudio.Mix mix = new TenderAudio.Mix();
        assertTrue(mix.frame(100));
        assertTrue(mix.admit(TenderCues.Kind.WELD, 1_000_000_000L));
        assertFalse(mix.frame(100));
        assertTrue(mix.frame(101));
        assertFalse(mix.admit(TenderCues.Kind.WELD, 1_100_000_000L));
        assertTrue(mix.frame(0));
        assertTrue(mix.admit(TenderCues.Kind.WELD, 1_100_000_000L));
    }

    @Test
    void acceleratedSwarmIsBoundedWithoutSuppressingCompletionOrDamage() {
        TenderAudio.Mix mix = new TenderAudio.Mix();
        assertTrue(mix.admit(TenderCues.Kind.WELD, 0));
        assertFalse(mix.admit(TenderCues.Kind.WELD, 200_000_000L));
        assertTrue(mix.admit(TenderCues.Kind.STUN, 200_000_000L));
        assertTrue(mix.admit(TenderCues.Kind.COMPLETE, 200_000_000L));
        assertTrue(mix.admit(TenderCues.Kind.WELD, 300_000_000L));
        assertTrue(mix.admit(TenderCues.Kind.DOCK, 300_000_000L));
        assertFalse(mix.admit(TenderCues.Kind.DOCK, 300_000_001L));
    }
}
