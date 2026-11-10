package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.SurfaceSignature;

/**
 * How a planet type's {@link SurfaceSignature} reads on screen, GL-free: the colour its wild ground and formations
 * are graded by, whether metal flecks glint in it, and the haze and vapour drifting over it. Drawn by
 * {@link SignatureRenderSystem}. Presentation only.
 */
final class SignatureLook {

    private SignatureLook() { }

    private static final float[] NEUTRAL = {1f, 1f, 1f};
    /** Cast iron and metallic rock: darker and cooler, the grey of worked iron rather than of stone. */
    private static final float[] IRON = {.6f, .62f, .68f};
    /** Venus-like: the light through thick hot air, amber on everything. */
    private static final float[] AMBER = {1f, .86f, .64f};

    /** What the wild ground, its hills and formations are multiplied by; white for a signature that keeps the palette's. */
    static float[] grade(SurfaceSignature signature) {
        return switch (signature) {
            case METAL_FLECKED -> IRON;
            case HOT_HAZE -> AMBER;
            default -> NEUTRAL;
        };
    }

    static boolean graded(SurfaceSignature signature) {
        return grade(signature) != NEUTRAL;
    }

    /** Metal flecks: the share of wild cells carrying one, their size in cells, colour and strength. */
    static final float FLECK_SHARE = .1f, FLECK_CELLS = .06f, FLECK_ALPHA = .7f;
    static final float[] FLECK_RGB = {.78f, .84f, .95f};

    /**
     * How brightly a fleck catches the light, {@code 0..1}: a dull sheen most of the time and a short glint now and
     * then, each fleck on its own beat ({@code phase}, {@code 0..1}).
     */
    static float glint(float phase, float time) {
        float s = (float) Math.sin(time * .9f + phase * 40f);
        float sharp = s <= 0f ? 0f : (float) Math.pow(s, 12);
        return .15f + .85f * sharp;
    }

    /** The haze: its colour, the puff size in cells, the spacing of its grid, drift in cells a second, and its strength. */
    static final float[] HAZE_RGB = {.96f, .72f, .42f};
    static final float HAZE_PUFF = 11f, HAZE_SPACING = 6f, HAZE_DRIFT_X = .55f, HAZE_DRIFT_Y = .18f;
    /**
     * At most this thick where the noise piles it up. Under the units and under the fog, so a marine is never
     * hidden by it and what the player can see stays what the fog says; it softens the ground, not the fight.
     */
    static final float HAZE_ALPHA = .3f;

    /** How thick the haze is at a point, {@code 0..1}: slow banks and thin lanes between them. */
    static float hazeDensity(float x, float y, float time, int seed) {
        float n = NaturalVoidField.Noise.fbm(x * .045f + time * .01f, y * .045f, seed);
        return Math.max(0f, Math.min(1f, (n - .3f) / .5f));
    }

    /** Cryovolcanic vapour: puffs per vent, seconds for one to drift its reach, the reach, its swelling and strength. */
    static final int VENT_PUFFS = 10;
    static final float VENT_SECONDS = 7f, VENT_REACH = 11f, VENT_BASE = 2.2f, VENT_SWELL = 9f, VENT_ALPHA = 1f;
    static final float[] VAPOUR_RGB = {.95f, .98f, 1f};
    /** A puff's shadow: its colour, strength against the puff's, and how far it falls from it per unit of rise, cells. */
    static final float[] SHADOW_RGB = {.32f, .4f, .52f};
    static final float SHADOW_ALPHA = .6f, SHADOW_LIFT = 2.5f;
    /** The vent mound's size in cells. */
    static final float MOUND_CELLS = 6.5f;
    /** A crack: its opening's colour, its depth's, and the lit lip beside it (its shift by widths, and strength). */
    static final float[] CRACK_OPEN_RGB = {.2f, .18f, .17f}, CRACK_DEEP_RGB = {.05f, .045f, .04f};
    static final float CRACK_LIP_SHIFT = .35f, CRACK_LIP_ALPHA = .22f;
    /** The crater decal's bowl spans this share of its width. */
    static final float CRATER_BOWL_SHARE = .52f;

    /** The crater decal's width in cells for a bowl of {@code radius} cells. */
    static float craterCells(float radius) {
        return 2f * radius / CRATER_BOWL_SHARE;
    }

    /** Vent puffs come in breaths: how long one lasts, seconds, and how much of it the vent is breathing out. */
    static final float BREATH_SECONDS = 11f, BREATH_ON = .75f;

    /**
     * How strongly a vent is breathing at {@code time}, {@code 0..1}: a vent puffs, rests and puffs again, each on
     * its own beat ({@code phase}, {@code 0..1}).
     */
    static float breath(float phase, float time) {
        double t = time / BREATH_SECONDS + phase;
        float u = (float) (t - Math.floor(t));
        if (u >= BREATH_ON) return 0f;
        return (float) Math.sin(Math.PI * u / BREATH_ON);
    }
}
