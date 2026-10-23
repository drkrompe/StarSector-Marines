package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.evacuation.flier.HarrierFlight;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierLook;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierPack;

/**
 * Which of a Harrier's three drawings shows this frame, and how wide: the wings spread (glide), raised over the
 * back (flap) or folded into a dart (dive). A wingbeat is the glide drawing narrowing as the wings come up, the
 * flap drawing at the top, and the glide widening again on the downstroke. A loitering flier glides and beats in
 * short bursts; one climbing out of a dive beats hard; a diver is folded. Presentation only, off the flight's own
 * clock, so a replay draws the same.
 */
record HarrierPose(String sprite, float widthScale) {

    /** Seconds per wingbeat. */
    static final float BEAT_SECONDS = 0.32f;
    /** A loitering flier beats this long out of every {@link #GLIDE_CYCLE_SECONDS}. */
    static final float LOITER_BEAT_SECONDS = 0.64f;
    static final float GLIDE_CYCLE_SECONDS = 2.2f;

    static HarrierPose of(HarrierFlight flight, HarrierPack.Phase phase) {
        HarrierLook look = flight.look;
        if (phase == HarrierPack.Phase.DIVE && !flight.struck) return new HarrierPose(look.dive(), 1f);
        boolean beating = phase != HarrierPack.Phase.LOITER || flight.altitude < 0.95f
                || flight.wingClock % GLIDE_CYCLE_SECONDS < LOITER_BEAT_SECONDS;
        if (!beating) return new HarrierPose(look.glide(), 1f);
        float f = (flight.wingClock % BEAT_SECONDS) / BEAT_SECONDS;
        if (f < 0.25f) return new HarrierPose(look.glide(), 0.78f);
        if (f < 0.5f) return new HarrierPose(look.flap(), 1f);
        if (f < 0.75f) return new HarrierPose(look.glide(), 0.85f);
        return new HarrierPose(look.glide(), 1f);
    }
}
