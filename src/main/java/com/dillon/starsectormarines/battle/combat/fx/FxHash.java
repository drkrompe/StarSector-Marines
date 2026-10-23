package com.dillon.starsectormarines.battle.combat.fx;

/**
 * Cadence and jitter for presentation emitters that must draw nothing from the simulation's random stream (a crippled
 * mech's smoke, a damaged hull's): each is a hash of the body's id, the tick and a salt naming the use, so a frame
 * stays a pure function of simulation state and adding an emitter changes no battle.
 */
public final class FxHash {

    private FxHash() {}

    /** Whether an emitter with this period puffs on {@code tick}; each body and emitter has its own phase. */
    public static boolean onCadence(long id, int tick, int periodTicks, int salt) {
        int phase = (int) Long.remainderUnsigned(mix(id * 0x9E3779B97F4A7C15L + salt), periodTicks);
        return Math.floorMod(tick + phase, periodTicks) == 0;
    }

    /** A deterministic value in [-1, 1) for this body, tick and use. */
    public static float jitter(long id, int tick, int salt) {
        long h = mix(id * 0x9E3779B97F4A7C15L ^ ((long) tick << 20) ^ salt);
        return ((h >>> 40) / (float) (1L << 24)) * 2f - 1f;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
