package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;

/**
 * How a capital looks while it drops through the cloud deck and while it climbs
 * back into it: pure curves from the ship's own clocks, so a frame is a function
 * of ship state and tick and nothing here touches the simulation.
 *
 * <p>Everything hangs off one number, the <b>lift</b>: 1 at the top of the deck,
 * 0 at the ship's hover altitude. A descent runs it from 1 to 0 on an ease-out
 * (fast through the deck, settling gently onto the hover), a departure runs it
 * back to 1 on an ease-in (a slow lift-off that gathers pace). The shadow's
 * height, its softness, the haze over the hull and the cloud wisps are all read
 * off the lift, so they cannot disagree about where the ship is.
 *
 * <p>A ship that did not enter from above has a negative entry clock and is at
 * lift 0 for its whole life until it departs, so every existing scene draws as it
 * always did.
 */
public final class WarshipAtmosphere {

    private WarshipAtmosphere() {}

    /** Seconds a descent takes from the top of the deck to the hover. */
    public static final float DESCENT_SECONDS = 6f;
    /** Seconds a climb takes from the hover to the top of the deck. */
    public static final float CLIMB_SECONDS = 6f;
    /** The altitude, in cells, at the top of the cloud deck. */
    public static final float DECK_ALTITUDE_CELLS = 52f;
    /** Seconds the re-entry glow takes to fade from its peak. */
    public static final float GLOW_SECONDS = 4.5f;
    /** Seconds the ground feels a landing: the wash under the hull and the expanding ring. */
    public static final float WASH_SECONDS = 4f;
    /** Seconds before the departure's own wash has died away. */
    public static final float LIFTOFF_WASH_SECONDS = 3.5f;
    /** Seconds from the start of a descent to the shock ring at the touchdown. */
    public static final float RING_SECONDS = 3f;

    /** 1 at the top of the deck, 0 at the hover: the one number the rest is read from. */
    public static float lift(float entrySeconds, float departingSeconds, boolean departing) {
        float lift = 0f;
        if (entrySeconds >= 0f) {
            float u = clamp01(entrySeconds / DESCENT_SECONDS);
            lift = (1f - u) * (1f - u);
        }
        if (departing) {
            float u = clamp01(departingSeconds / CLIMB_SECONDS);
            lift = Math.max(lift, u * u);
        }
        return lift;
    }

    /** The altitude the ship casts its shadow from, in cells, given its hover altitude. */
    public static float altitudeCells(float hoverCells, float lift) {
        return hoverCells + (DECK_ALTITUDE_CELLS - hoverCells) * clamp01(lift);
    }

    /** How soft the shadow's edge is: 0 sharp at the hover, 1 a smear at the top of the deck. */
    public static float shadowSoftness(float lift) {
        return smooth(clamp01(lift));
    }

    /** The shadow's density: it thins as the ship gets higher, because the same shade is spread over more ground. */
    public static float shadowDensity(float lift) {
        return 1f - 0.6f * smooth(clamp01(lift));
    }

    /**
     * How much of the hull is seen through the deck. A ship breaks out of the cloud
     * in the first fifth of a descent and goes back into it in the last half of a climb.
     */
    public static float hullVisibility(float entrySeconds, float departingSeconds, boolean departing) {
        float v = 1f;
        if (entrySeconds >= 0f) {
            float u = clamp01(entrySeconds / DESCENT_SECONDS);
            v = Math.min(v, 0.12f + 0.88f * smooth(clamp01(u / 0.28f)));
        }
        if (departing) {
            float u = clamp01(departingSeconds / CLIMB_SECONDS);
            v = Math.min(v, 1f - smooth(clamp01((u - 0.45f) / 0.55f)));
        }
        return v;
    }

    /** Cloud around the hull: thickest while the ship is high, gone by the time it hovers. */
    public static float cloudCover(float lift) {
        return smooth(clamp01((lift - 0.04f) / 0.96f));
    }

    /** The re-entry heat on the hull, 1 at the start of a descent, fading over {@link #GLOW_SECONDS}. */
    public static float reentryGlow(float entrySeconds) {
        if (entrySeconds < 0f) return 0f;
        float f = 1f - clamp01(entrySeconds / GLOW_SECONDS);
        return f * f * (0.4f + 0.6f * f);
    }

    /** The engine-hot wake of a ship climbing away: up in a second, gone as the hull goes into the deck. */
    public static float climbTrail(float departingSeconds, boolean departing) {
        if (!departing) return 0f;
        float up = smooth(clamp01(departingSeconds / 1.2f));
        float gone = 1f - smooth(clamp01((departingSeconds - CLIMB_SECONDS * 0.6f) / (CLIMB_SECONDS * 0.8f)));
        return up * gone;
    }

    /**
     * How hard the downwash is working the ground under the hull. Building as the
     * ship comes down, full at the hover, then dying away over {@link #WASH_SECONDS};
     * a lift-off kicks it up again and lets it go as the hull rises.
     */
    public static float groundWash(float entrySeconds, float departingSeconds, boolean departing) {
        float wash = 0f;
        if (entrySeconds >= 0f) {
            float u = entrySeconds / DESCENT_SECONDS;
            if (u < 1f) {
                float near = clamp01((u - 0.35f) / 0.65f);
                wash = near * near;
            } else {
                wash = 1f - clamp01((entrySeconds - DESCENT_SECONDS) / WASH_SECONDS);
            }
        }
        if (departing) {
            float lift = 1f - clamp01(departingSeconds / LIFTOFF_WASH_SECONDS);
            wash = Math.max(wash, lift * lift);
        }
        return wash;
    }

    /** How far through the touchdown shock ring a ship is, 0 before it, 1 once it has gone; negative when there is none. */
    public static float ringProgress(float entrySeconds) {
        if (entrySeconds < 0f) return -1f;
        float since = entrySeconds - DESCENT_SECONDS * 0.82f;
        if (since < 0f) return -1f;
        return clamp01(since / RING_SECONDS);
    }

    /** The ring's radius as a multiple of the hull's length at {@code progress}: quick at first, slowing. */
    public static float ringRadiusHulls(float progress) {
        float eased = 1f - (1f - progress) * (1f - progress);
        return 0.35f + 1.35f * eased;
    }

    /** The ring's strength at {@code progress}: struck hard, then thinning as it widens. */
    public static float ringAlpha(float progress) {
        float f = 1f - progress;
        return f * f;
    }

    /** How much of its hover altitude a hull keeps at the peak of a low pass: it is skimming, so its shadow lies close under it. */
    public static final float PASS_SHADOW_ALTITUDE_KEEP = 0.45f;
    /** How much darker a low pass's shadow is at the peak, as a multiple of a hover's: sharp and dense. */
    public static final float PASS_SHADOW_DENSITY_GAIN = 0.4f;
    /** The longest stretch of line behind a low pass's hull that shows its dust, in cells. */
    public static final float PASS_TRAIL_CELLS = 64f;
    /** Seconds a puff of the pass's dust takes to settle. */
    public static final float PASS_DUST_SECONDS = 7f;

    /** The multiple of the hover altitude a shadow is cast from at low-pass {@code blend} (1 at none, down to the keep share). */
    public static float passShadowAltitudeFactor(float blend) {
        return 1f - (1f - PASS_SHADOW_ALTITUDE_KEEP) * clamp01(blend);
    }

    /** The multiple of the shadow's density at low-pass {@code blend}: 1 at none, denser at the peak. */
    public static float passShadowDensityFactor(float blend) {
        return 1f + PASS_SHADOW_DENSITY_GAIN * clamp01(blend);
    }

    /** The shadow's softness at low-pass {@code blend}: whatever the lift gave it, taken to sharp as the pass builds. */
    public static float passShadowSoftness(float softness, float blend) {
        return softness * (1f - clamp01(blend));
    }

    /**
     * How loud the wake is, 0 to 1: the length of the exhaust and vapour streaks and the strength of the
     * pressure wash under the hull. It is the pass's own blend, so it builds with the run and fades after it.
     */
    public static float passWake(float blend) {
        return clamp01(blend);
    }

    /** How faded a puff of the pass's dust is after {@code ageSeconds}: 1 fresh, 0 settled; thins fast then lingers. */
    public static float passDust(float ageSeconds) {
        float u = clamp01(ageSeconds / PASS_DUST_SECONDS);
        return (1f - u) * (1f - u);
    }

    /** The colours the atmosphere takes from the world it is over: glow, cloud/vapour and kicked-up dust. */
    public record Tint(float[] glow, float[] vapour, float[] dust) {

        private static final Tint ROCK = new Tint(
                new float[]{1.00f, 0.66f, 0.36f}, new float[]{0.86f, 0.88f, 0.92f}, new float[]{0.62f, 0.58f, 0.54f});
        private static final Tint ARID = new Tint(
                new float[]{1.00f, 0.62f, 0.30f}, new float[]{0.93f, 0.88f, 0.80f}, new float[]{0.80f, 0.68f, 0.48f});
        private static final Tint VERDANT = new Tint(
                new float[]{1.00f, 0.70f, 0.42f}, new float[]{0.90f, 0.94f, 0.96f}, new float[]{0.58f, 0.58f, 0.44f});
        private static final Tint FROZEN = new Tint(
                new float[]{0.72f, 0.82f, 1.00f}, new float[]{0.94f, 0.97f, 1.00f}, new float[]{0.90f, 0.93f, 0.98f});

        /** The tint a surface palette gives; null reads as bare rock, the baseline for a battle with no planet behind it. */
        public static Tint forPalette(SurfacePalette palette) {
            if (palette == null) return ROCK;
            return switch (palette) {
                case ARID -> ARID;
                case VERDANT -> VERDANT;
                case FROZEN -> FROZEN;
                default -> ROCK;
            };
        }
    }

    /** A stable per-hull number in [0, 1) for scattering wisps and puffs, from the ship's identity and a slot. */
    public static float scatter(String hullId, int faction, int slot) {
        int h = hullId == null ? 0 : hullId.hashCode();
        h = h * 31 + faction;
        h = h * 0x9E3779B1 + slot * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return (h >>> 8) / (float) (1 << 24);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }
}
