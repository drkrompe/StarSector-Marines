package com.dillon.starsectormarines.battle.air.engine;

import java.awt.Color;

/** Pure local-frame reflected engine illumination; the host clips its result to hull coverage. */
public final class EngineHullLighting {
    public record Light(float x, float y, float inwardX, float inwardY,
                        float sideRadius, float depthRadius, Color color, float strength) {}
    private static final Light[] EMPTY = new Light[0];

    private EngineHullLighting() {}

    /** Slots include appearance-only nozzle offsets; all positions and radii remain in unscaled hull-local cells. */
    public static Light[] lights(EngineSlotData[] correctedSlots, float hullLengthCells,
                                 float intensity, float[] perSlotDemand) {
        if (correctedSlots == null || correctedSlots.length == 0 || intensity <= 0f || hullLengthCells <= 0f)
            return EMPTY;
        Light[] lights = new Light[correctedSlots.length];
        for (int i = 0; i < correctedSlots.length; i++) {
            EngineSlotData slot = correctedSlots[i];
            double angle = Math.toRadians(slot.angleDegrees);
            // Exhaust is (-sin, cos): reflected light extends in the opposite direction, into the hull.
            float inwardX = (float) Math.sin(angle), inwardY = -(float) Math.cos(angle);
            float width = Math.max(0f, slot.widthCells);
            Color flame = EngineStylePalette.flameColor(slot.style);
            Color reflected = new Color((int) (flame.getRed() * .75f + 255 * .25f),
                    (int) (flame.getGreen() * .75f + 255 * .25f),
                    (int) (flame.getBlue() * .75f + 255 * .25f));
            lights[i] = new Light(slot.localX + inwardX * width * .4f,
                    slot.localY + inwardY * width * .4f, inwardX, inwardY,
                    width * 1.1f, Math.min(width * 2.2f, hullLengthCells * .26f), reflected,
                    .35f * EngineBurn.flameAlpha(1f, EngineBurn.slotIntensity(intensity, perSlotDemand, i)));
        }
        return lights;
    }

    /** Smooth elliptical weight, zero at and beyond its boundary. */
    public static float falloff(Light light, float localX, float localY) {
        if (light.sideRadius() <= 0f || light.depthRadius() <= 0f) return 0f;
        float dx = localX - light.x(), dy = localY - light.y();
        float depth = (dx * light.inwardX() + dy * light.inwardY()) / light.depthRadius();
        float side = (dx * -light.inwardY() + dy * light.inwardX()) / light.sideRadius();
        float remaining = Math.max(0f, 1f - depth * depth - side * side);
        return remaining * remaining;
    }

    /** Writes summed, unclamped RGB light into the first three components; does not process hull alpha. */
    public static void sample(Light[] lights, float localX, float localY, float[] rgb) {
        rgb[0] = rgb[1] = rgb[2] = 0f;
        for (Light light : lights) {
            float strength = light.strength() * falloff(light, localX, localY) / 255f;
            rgb[0] += light.color().getRed() * strength;
            rgb[1] += light.color().getGreen() * strength;
            rgb[2] += light.color().getBlue() * strength;
        }
    }
}
