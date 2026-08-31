package com.dillon.starsectormarines.ops.battleview;

import java.awt.image.BufferedImage;

/**
 * The composite's cast-shadow term, on the CPU.
 *
 * <p>A faithful mirror of the occlusion loop in
 * {@link GroundParallaxPipeline}'s {@code FRAGMENT_SRC}: the same step
 * schedule, the same soft ramp, the same running maximum, the same tint
 * applied as a multiplier. It exists because the real shader cannot run
 * anywhere the evidence does — headless suites and unit tests have no GL
 * context — and because three separate re-derivations of the same twenty
 * lines would disagree within a week.
 *
 * <p><b>This is a model of the shader, not the shader.</b> It proves the
 * geometry: how far a wall of a stated height reaches under a sun at a stated
 * elevation, and which way. It cannot prove the GLSL compiles, that its
 * uniforms are named and bound correctly, or that the padded height target is
 * addressed correctly against a real texture. Only running the game does that.
 */
final class GroundSunShadowReference {

    private GroundSunShadowReference() {}

    /** A height field in metres above the ground datum, sampled at a world-cell position. */
    interface HeightField {
        float metersAt(float worldX, float worldY);
    }

    /**
     * Fraction of the sun this ground is cut off from, in {@code 0..1}.
     *
     * @param tallestMeters the tallest surface the map can place, which is what
     *                      sizes the march — the same rule
     *                      {@code GroundParallaxPipeline.shadowRangeCells} follows.
     *                      Fixing it instead would cap the reach of anything
     *                      taller and report that height stopped mattering.
     */
    static float shadowAt(HeightField field, float worldX, float worldY,
                          float sunAzimuthDegrees, float sunElevationDegrees,
                          float tallestMeters) {
        float rise = (float) Math.tan(Math.toRadians(sunElevationDegrees));
        float rangeCells = Math.min(GroundParallaxPipeline.MAX_SHADOW_RANGE_CELLS,
                tallestMeters / rise);
        float stepCells = Math.min(rangeCells / GroundParallaxPipeline.SHADOW_STEPS,
                GroundParallaxPipeline.SHADOW_STEP_MAX_CELLS);
        double azimuth = Math.toRadians(sunAzimuthDegrees);
        float dirX = (float) Math.cos(azimuth);
        float dirY = (float) Math.sin(azimuth);

        float base = field.metersAt(worldX, worldY);
        float shadow = 0f;
        for (int i = 1; i <= GroundParallaxPipeline.SHADOW_STEPS; i++) {
            float t = i * stepCells;
            float occluder = field.metersAt(worldX + dirX * t, worldY + dirY * t);
            float ray = base + t * rise;
            float lit = (occluder - ray) / GroundParallaxPipeline.SHADOW_SOFTNESS_METERS;
            shadow = Math.max(shadow, Math.max(0f, Math.min(1f, lit)));
        }
        return shadow;
    }

    /**
     * {@code ground} with the sun applied, leaving the original untouched.
     *
     * <p>{@code worldAt} maps a pixel to the world cell under it, which is the
     * one thing that differs between callers: a synthetic scene addresses its
     * own grid, while a rendered map has cell {@code y=0} along its bottom edge.
     * Getting that flip wrong silently mirrors every shadow, so it is the
     * caller's to state rather than this class's to assume.
     */
    static BufferedImage shade(BufferedImage ground, HeightField field, PixelToWorld worldAt,
                               float sunAzimuthDegrees, float sunElevationDegrees,
                               float strength, float tallestMeters) {
        int w = ground.getWidth();
        int h = ground.getHeight();
        BufferedImage output = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = ground.getRGB(x, y);
                float k = strength <= 0f ? 0f : strength * shadowAt(field,
                        worldAt.worldX(x), worldAt.worldY(y),
                        sunAzimuthDegrees, sunElevationDegrees, tallestMeters);
                int r = Math.round(((rgb >>> 16) & 0xFF)
                        * lerp(1f, SunLight.TINT_R, k));
                int g = Math.round(((rgb >>> 8) & 0xFF)
                        * lerp(1f, SunLight.TINT_G, k));
                int b = Math.round((rgb & 0xFF)
                        * lerp(1f, SunLight.TINT_B, k));
                output.setRGB(x, y, (rgb & 0xFF000000) | r << 16 | g << 8 | b);
            }
        }
        return output;
    }

    /** Where a pixel of the image being shaded sits in world cells. */
    interface PixelToWorld {
        float worldX(int pixelX);

        float worldY(int pixelY);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
