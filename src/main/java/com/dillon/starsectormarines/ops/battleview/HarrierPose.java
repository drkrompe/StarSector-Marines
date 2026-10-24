package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.evacuation.flier.HarrierFlight;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierLook;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierPack;

/**
 * Which of a Harrier's drawings shows this frame. A wingbeat is {@link HarrierLook#BEAT_FRAMES} drawings, frame 0
 * the glide with the wings spread, then the upstroke, the wings raised over the back, the downstroke and the
 * recovery. A loitering flier at height glides and beats in short bursts that start and end on the glide; one
 * climbing out of a dive, or low, beats steadily and harder. Going into a dive the wings fold through
 * {@link HarrierLook#FOLD_FRAMES} drawings to the dive drawing, and open back through them climbing out
 * ({@link HarrierFlight#fold}). Presentation only, off the flight's own clocks, so a replay draws the same.
 */
record HarrierPose(String sprite) {

    /** Seconds per wingbeat climbing out or flying low. */
    static final float BEAT_SECONDS = 0.32f;
    /** Seconds per wingbeat in a loiter burst: an easier stroke. */
    static final float LOITER_BEAT_SECONDS = 0.4f;
    /** Wingbeats in one loiter burst, out of every {@link #GLIDE_CYCLE_SECONDS}. */
    static final int LOITER_BEATS = 2;
    static final float GLIDE_CYCLE_SECONDS = 2.2f;
    /** At or above this altitude a loitering flier is cruising, and glides between bursts. */
    static final float CRUISE_ALTITUDE = 0.95f;

    static HarrierPose of(HarrierFlight flight, HarrierPack.Phase phase) {
        HarrierLook look = flight.look;
        if (flight.fold >= 1f) return new HarrierPose(look.dive());
        int fold = (int) (flight.fold * (HarrierLook.FOLD_FRAMES + 1));
        if (fold > 0) return new HarrierPose(look.fold(fold));
        return new HarrierPose(look.beat(beatFrame(flight, phase)));
    }

    /** The wingbeat frame, 0 (the glide) to {@link HarrierLook#BEAT_FRAMES} - 1. */
    static int beatFrame(HarrierFlight flight, HarrierPack.Phase phase) {
        int frames = HarrierLook.BEAT_FRAMES;
        if (phase == HarrierPack.Phase.LOITER && flight.altitude >= CRUISE_ALTITUDE) {
            float c = flight.wingClock % GLIDE_CYCLE_SECONDS;
            if (c >= LOITER_BEATS * LOITER_BEAT_SECONDS) return 0;
            return (int) (c / LOITER_BEAT_SECONDS * frames) % frames;
        }
        return (int) (flight.wingClock / BEAT_SECONDS * frames) % frames;
    }
}
