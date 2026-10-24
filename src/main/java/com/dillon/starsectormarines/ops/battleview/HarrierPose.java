package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.evacuation.flier.HarrierFlight;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierLook;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierPack;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierWingbeat;

/**
 * Which of a Harrier's drawings shows this frame. A wingbeat is {@link HarrierLook#BEAT_FRAMES} drawings, frame 0
 * the glide with the wings spread, then the upstroke, the wings raised over the back, the downstroke and the
 * recovery, at the stroke's phase ({@link HarrierWingbeat}). Going into a dive the wings fold through
 * {@link HarrierLook#FOLD_FRAMES} drawings to the dive drawing, and open back through them climbing out
 * ({@link HarrierFlight#fold}). Presentation only, off the flight's own clocks, so a replay draws the same.
 */
record HarrierPose(String sprite) {

    static HarrierPose of(HarrierFlight flight, HarrierPack.Phase phase) {
        HarrierLook look = flight.look;
        if (flight.fold >= 1f) return new HarrierPose(look.dive());
        int fold = (int) (flight.fold * (HarrierLook.FOLD_FRAMES + 1));
        if (fold > 0) return new HarrierPose(look.fold(fold));
        return new HarrierPose(look.beat(beatFrame(flight, phase)));
    }

    /** The wingbeat frame, 0 (the glide) to {@link HarrierLook#BEAT_FRAMES} - 1. */
    static int beatFrame(HarrierFlight flight, HarrierPack.Phase phase) {
        float p = HarrierWingbeat.phase(flight, phase);
        return p < 0f ? 0 : (int) (p * HarrierLook.BEAT_FRAMES) % HarrierLook.BEAT_FRAMES;
    }
}