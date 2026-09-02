package com.dillon.starsectormarines.marine;

import java.util.Random;

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

    /** Stable per-marine seed: the event that killed them, mixed with who they were. */
    public static long seed(long eventKey, String soldierId) {
        return (eventKey << 32) ^ (soldierId != null ? soldierId.hashCode() : 0);
    }
}
