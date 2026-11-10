package com.dillon.starsectormarines.ops.battleview;

/**
 * How a drone is drawn as floating: it hangs above the ground, so its own silhouette lies apart from it as a shadow,
 * down and to the right of a light from the upper left, and it bobs gently, drawn a little larger and its shadow a
 * little farther off as it rises. Each drone bobs in its own phase, taken from its id, so a screen or a flock never
 * pulses in step. Presentation only: where a drone is and what it hits are unchanged.
 */
final class DroneFloat {

    /**
     * How far a drone's shadow lies from it at the middle of its bob, in drone lengths: in proportion to the drone, so a
     * half-cell swarm dart's shadow stays by it rather than reading as a second drone.
     */
    static final float SHADOW_LENGTHS = .45f;
    /** A shadow's opacity, under a hover hull's (.38): a drone stands higher, so its shadow is fainter. */
    static final float SHADOW_ALPHA = .3f;
    /** Bobs a second. */
    static final float BOB_HZ = .5f;
    /** Peak change in drawn size over a bob, as a fraction of the drone's size. */
    static final float BOB_SCALE = .035f;
    /** Peak change in shadow distance over a bob, as a fraction of its middle distance. */
    static final float BOB_SHADOW = .2f;

    private DroneFloat() {}

    /** Where drone {@code id} is in its bob at {@code seconds}: -1 at the bottom, 1 at the top. */
    static float bob(long id, float seconds) {
        return (float) Math.sin(2.0 * Math.PI * (BOB_HZ * seconds + phase(id)));
    }

    /** The drone's drawn size at bob {@code bob}, as a multiple of its resting size. */
    static float scale(float bob) {
        return 1f + BOB_SCALE * bob;
    }

    /**
     * How far the shadow lies from a drone {@code lengthCells} long, in cells, at bob {@code bob}, for a drone
     * {@code height} of the way off the ground: 1 in flight, falling to 0 as a wreck comes down, so the shadow meets it
     * where it lands.
     */
    static float shadowCells(float lengthCells, float bob, float height) {
        return SHADOW_LENGTHS * lengthCells * (1f + BOB_SHADOW * bob) * Math.max(0f, Math.min(1f, height));
    }

    /** The drone's own offset into its bob, a fraction of a cycle in [0, 1). */
    static float phase(long id) {
        long h = id * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }

    /** The clock a drone bobs to: the host's pinned clock where it has one, otherwise the battle's own time. */
    static float clock(float animationTimeSeconds, int simTick, float tickSeconds) {
        return Float.isFinite(animationTimeSeconds) ? animationTimeSeconds : simTick * tickSeconds;
    }
}
