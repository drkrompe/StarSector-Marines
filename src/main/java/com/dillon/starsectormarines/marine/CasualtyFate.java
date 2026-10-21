package com.dillon.starsectormarines.marine;

import java.util.Random;
import java.util.SplittableRandom;

/**
 * What becomes of one marine the battle counted as a casualty: killed, wounded, or
 * missing. Deterministic for the same seed, so a settlement replayed from the same
 * inputs writes the same service records.
 *
 * <p>A lost fight leaves fewer recoverable bodies than a won one, which is the whole
 * reason the disposition reads the result at all.
 */
public final class CasualtyFate {

    private CasualtyFate() {}

    public static MarineSoldierStatus roll(long seed, boolean victory) {
        float roll = new Random(seed).nextFloat();
        if (victory) {
            return roll < 0.35f ? MarineSoldierStatus.KIA
                    : roll < 0.95f ? MarineSoldierStatus.WIA
                    : MarineSoldierStatus.MIA;
        }
        return roll < 0.50f ? MarineSoldierStatus.KIA
                : roll < 0.80f ? MarineSoldierStatus.WIA
                : MarineSoldierStatus.MIA;
    }

    /**
     * The same roll, with {@code saved} (a share in {@code [0, 1]}) of the would-be
     * dead coming home wounded instead: the company home's sick bay. Only the killed
     * move, so a marine who rolls wounded or missing keeps that fate whatever the bay,
     * and a better bay saves everyone a lesser one did. The save is its own draw,
     * mixed from the seed, because the fate roll above is not evenly spread across
     * neighbouring marines and a share of it would not be the share it says.
     */
    public static MarineSoldierStatus roll(long seed, boolean victory, float saved) {
        MarineSoldierStatus fate = roll(seed, victory);
        if (fate != MarineSoldierStatus.KIA || saved <= 0f) return fate;
        return new SplittableRandom(seed ^ SICK_BAY_SALT).nextDouble() < saved ? MarineSoldierStatus.WIA : fate;
    }

    private static final long SICK_BAY_SALT = 0x5349434b42415951L;

    /** Stable per-marine seed: the event that killed them, mixed with who they were. */
    public static long seed(long eventKey, String soldierId) {
        return (eventKey << 32) ^ (soldierId != null ? soldierId.hashCode() : 0);
    }
}
