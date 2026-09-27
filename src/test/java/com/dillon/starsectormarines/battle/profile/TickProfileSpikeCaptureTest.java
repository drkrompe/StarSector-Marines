package com.dillon.starsectormarines.battle.profile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TickProfileSpikeCaptureTest {

    @Test
    void automaticCaptureDefaultsOffWithoutStoppingLiveWindows() {
        TickProfile profile = new TickProfile();
        assertFalse(profile.isAutoSpikeCaptureEnabled());
        profile.captureSpikeIfEligible(400, 40_000_000L, 5_000_000L, null);
        assertNull(profile.consumeSpike());

        for (int tick = TickProfile.WARMUP_TICKS;
             tick < TickProfile.WARMUP_TICKS + TickProfile.WINDOW_TICKS; tick++) {
            profile.begin(tick);
            profile.lap(TickProfile.Phase.UPDATE_UNITS);
            profile.endTick(tick, null);
        }
        assertEquals(TickProfile.WINDOW_TICKS, profile.sampleCount());
        assertNull(profile.consumeSpike());
    }

    @Test
    void enabledCapturePreservesSpikeTickAndFrozenInnerCounters() {
        TickProfile profile = new TickProfile();
        profile.setAutoSpikeCaptureEnabled(true);
        TickInnerProfile inner = new TickInnerProfile();
        inner.record(TickInnerProfile.Bucket.ACTION_EXECUTE, 12_000_000L);
        profile.captureSpikeIfEligible(400, 40_000_000L, 5_000_000L, inner);
        inner.reset();

        TickProfile.Spike spike = profile.consumeSpike();
        assertNotNull(spike);
        assertEquals(400, spike.tickIndex);
        assertEquals(40_000_000L, spike.totalNanos);
        assertEquals(5_000_000L, spike.baselineNanos);
        assertEquals(12_000_000L,
                spike.innerSnapshot.nanosOf(TickInnerProfile.Bucket.ACTION_EXECUTE));
        assertNull(profile.consumeSpike());
    }

    @Test
    void disablingDropsPendingSnapshotAndRearmingOnlyCapturesFreshTicks() {
        TickProfile profile = new TickProfile();
        profile.setAutoSpikeCaptureEnabled(true);
        profile.captureSpikeIfEligible(400, 40_000_000L, 5_000_000L,
                new TickInnerProfile());
        profile.setAutoSpikeCaptureEnabled(false);
        assertNull(profile.consumeSpike());
        profile.captureSpikeIfEligible(401, 40_000_000L, 5_000_000L, null);
        assertNull(profile.consumeSpike());
        profile.setAutoSpikeCaptureEnabled(true);
        assertNull(profile.consumeSpike());
        profile.captureSpikeIfEligible(402, 40_000_000L, 5_000_000L, null);
        assertEquals(402, profile.consumeSpike().tickIndex);
    }

    @Test
    void enabledCaptureKeepsFloorMultiplierAndFirstPendingTick() {
        TickProfile profile = new TickProfile();
        profile.setAutoSpikeCaptureEnabled(true);
        profile.captureSpikeIfEligible(400, 999_999L, 100_000L, null);
        profile.captureSpikeIfEligible(401, 10_000_000L, 0L, null);
        profile.captureSpikeIfEligible(402, 14_999_999L, 5_000_000L, null);
        assertNull(profile.consumeSpike());

        profile.captureSpikeIfEligible(403, 15_000_000L, 5_000_000L, null);
        profile.captureSpikeIfEligible(404, 40_000_000L, 5_000_000L, null);
        assertEquals(403, profile.consumeSpike().tickIndex);
    }

    @Test
    void manualInnerSnapshotRemainsAvailableWithAutomaticCaptureOff() {
        TickProfile profile = new TickProfile();
        TickInnerProfile inner = new TickInnerProfile();
        inner.record(TickInnerProfile.Bucket.ACTION_EXECUTE, 12_000_000L);
        profile.captureSpikeIfEligible(400, 40_000_000L, 5_000_000L, inner);
        assertNull(profile.consumeSpike());
        assertEquals(12_000_000L,
                inner.snapshot().nanosOf(TickInnerProfile.Bucket.ACTION_EXECUTE));
    }
}
