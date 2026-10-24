package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierFlight;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierLook;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierPack;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HarrierPoseTest {

    private static final HarrierLook LOOK = HarrierLook.LILAC_GLIDER;

    private static HarrierFlight flight(float wingClock, float altitude, float fold) {
        HarrierFlight f = new HarrierFlight(1L, new AirBody(), LOOK);
        f.wingClock = wingClock;
        f.altitude = altitude;
        f.fold = fold;
        return f;
    }

    @Test
    void aSteadyBeatShowsEveryFrameInOrder() {
        Set<Integer> seen = new HashSet<>();
        int last = -1;
        for (int i = 0; i < 64; i++) {
            float t = i * HarrierPose.BEAT_SECONDS / 64f;
            int frame = HarrierPose.beatFrame(flight(t, 0.5f, 0f), HarrierPack.Phase.PULL_UP);
            if (frame != last) {
                assertEquals((last + 1) % HarrierLook.BEAT_FRAMES, frame, "frames advance one at a time");
                last = frame;
            }
            seen.add(frame);
        }
        assertEquals(HarrierLook.BEAT_FRAMES, seen.size());
    }

    @Test
    void aCruisingBurstStartsAndEndsOnTheGlide() {
        HarrierPack.Phase loiter = HarrierPack.Phase.LOITER;
        float burst = HarrierPose.LOITER_BEATS * HarrierPose.LOITER_BEAT_SECONDS;
        assertEquals(0, HarrierPose.beatFrame(flight(0.001f, 1f, 0f), loiter));
        assertEquals(HarrierLook.BEAT_FRAMES - 1, HarrierPose.beatFrame(flight(burst - 0.001f, 1f, 0f), loiter));
        assertEquals(0, HarrierPose.beatFrame(flight(burst + 0.01f, 1f, 0f), loiter));
        assertEquals(0, HarrierPose.beatFrame(flight(HarrierPose.GLIDE_CYCLE_SECONDS - 0.01f, 1f, 0f), loiter));
    }

    @Test
    void foldingRunsThroughTheFoldFramesToTheDive() {
        HarrierPack.Phase dive = HarrierPack.Phase.DIVE;
        assertEquals(LOOK.glide(), HarrierPose.of(flight(0f, 1f, 0.1f), dive).sprite());
        assertEquals(LOOK.fold(1), HarrierPose.of(flight(0f, 1f, 0.4f), dive).sprite());
        assertEquals(LOOK.fold(2), HarrierPose.of(flight(0f, 1f, 0.8f), dive).sprite());
        assertEquals(LOOK.dive(), HarrierPose.of(flight(0f, 1f, 1f), dive).sprite());
    }
}
