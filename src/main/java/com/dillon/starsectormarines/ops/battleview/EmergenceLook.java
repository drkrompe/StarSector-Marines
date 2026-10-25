package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.evacuation.SwarmEmergence;

/**
 * How a Rav climbing out of its bore is drawn ({@link SwarmEmergence}; `colony-breach-nouns.md`, Emergence): from
 * the dark of the hole, smaller and nearly black, it rises and lights to its full size and colour as it comes out,
 * on an ease-out so the scramble is quick at first and settles as it stands. Presentation only.
 */
final class EmergenceLook {

    /** The drawn size, as a share of full, at the first tick out of the hole. */
    static final float START_SCALE = 0.6f;
    /** The light on it, as a share of full, at the first tick: still in the shaft's shadow. */
    static final float START_SHADE = 0.2f;
    /** The share of the climb over which it fades in from nothing. */
    static final float FADE_SHARE = 0.18f;

    private EmergenceLook() {
    }

    private static float eased(float rise) {
        float r = 1f - Math.max(0f, Math.min(1f, rise));
        return 1f - r * r;
    }

    static float scale(float rise) { return START_SCALE + (1f - START_SCALE) * eased(rise); }

    static float shade(float rise) { return START_SHADE + (1f - START_SHADE) * eased(rise); }

    static float alpha(float rise) { return Math.min(1f, Math.max(0f, rise) / FADE_SHARE); }

    /** An emitter onto the unit layer that darkens every layer by {@code shade}. */
    static LayeredUnitComposer.SpriteEmitter shaded(DrawList out, float shade) {
        return (layer, x, y, w, h, angle, r, g, b, a) -> out.addSprite(RenderLayer.UNITS, layer.sprite, x, y, w, h,
                angle, r * shade, g * shade, b * shade, a);
    }
}
