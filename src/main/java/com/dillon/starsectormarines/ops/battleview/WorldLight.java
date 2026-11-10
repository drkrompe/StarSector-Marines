package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;

/**
 * The light a world's ground is seen in: how much the sunlit ground is washed out toward a pale sky colour, how much
 * of its colour the glare bleaches away, and what colour its cast shadows take. The ground composite
 * ({@link GroundParallaxPipeline}) applies it to the sunlit image, before event lights are added, so a muzzle flash
 * still reads on washed-out ground.
 *
 * <p>Most worlds are seen in {@link #NEUTRAL} light, which leaves the ground exactly as its tiles paint it. An
 * irradiated world is not: hard radiation and a thin, scoured sky give a pale, harsh, washed-out light, with deep,
 * neutral shadows rather than the cool sky-fill a living world's shadows carry.
 *
 * <p>Presentation only, and GL-free so the rule can be tested; nothing here reaches simulation.
 */
public record WorldLight(float washAmount, float washR, float washG, float washB, float desaturation,
                         float shadowR, float shadowG, float shadowB) {

    /** The ground as its tiles paint it, with {@link SunLight}'s sky-filled shadow. */
    public static final WorldLight NEUTRAL = new WorldLight(0f, 1f, 1f, 1f, 0f,
            SunLight.TINT_R, SunLight.TINT_G, SunLight.TINT_B);

    /** Pale, harsh and washed out: bleached toward a white glare, a third of the colour gone, hard neutral shadows. */
    public static final WorldLight IRRADIATED = new WorldLight(0.16f, 0.97f, 0.96f, 0.89f, 0.32f,
            0.40f, 0.41f, 0.43f);

    /** The light a surface palette's world is seen in; null (no planet behind the battle) is neutral. */
    public static WorldLight forPalette(SurfacePalette palette) {
        return palette == SurfacePalette.IRRADIATED ? IRRADIATED : NEUTRAL;
    }

    /** Whether this light leaves the sunlit ground's colour untouched. */
    public boolean neutral() {
        return washAmount == 0f && desaturation == 0f;
    }

    /**
     * One sunlit ground colour channel triple graded by this light, exactly as the composite shader grades it:
     * desaturate toward luma, then wash toward the glare colour. Returned as a new array.
     */
    public float[] grade(float r, float g, float b) {
        float luma = 0.299f * r + 0.587f * g + 0.114f * b;
        r += (luma - r) * desaturation;
        g += (luma - g) * desaturation;
        b += (luma - b) * desaturation;
        return new float[]{r + (washR - r) * washAmount, g + (washG - g) * washAmount, b + (washB - b) * washAmount};
    }
}
