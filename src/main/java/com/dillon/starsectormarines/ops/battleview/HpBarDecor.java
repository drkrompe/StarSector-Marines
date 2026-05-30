package com.dillon.starsectormarines.ops.battleview;

import java.awt.Color;

/**
 * Reusable HP-bar emit behavior: a fixed-style two-rect bar (dark backing +
 * green fill) submitted as two {@link DrawCommand.Kind#SOLID_RECT}s into any
 * layer. Stateless and layer-agnostic — the canonical bar renderer shared by
 * {@link DroneRenderSystem} (DRONES layer) and, after Story J slice 6, the
 * UNITS HP-bar sweep (and adoptable by SHUTTLES later by running a bar sweep
 * last within its own layer).
 *
 * <p>Callers own bar <em>placement</em> (where {@code baseY} sits relative to
 * the entity, via the layer's own gap policy); the bar's height and colors are
 * intrinsic style and live here as the single source of truth.
 */
public final class HpBarDecor {

    /** Bar backing (dark red). */
    public static final Color HP_BG = new Color(0x60, 0x20, 0x20);
    /** Bar fill (green). */
    public static final Color HP_FG = new Color(0x40, 0xC0, 0x40);
    /** Bar thickness in screen px. */
    public static final float HP_BAR_H = 3f;

    private HpBarDecor() {
    }

    /**
     * Emits the backing + fill rects for a horizontal HP bar centered at
     * {@code cx}, its near edge at {@code baseY}, spanning {@code width} px and
     * {@link #HP_BAR_H} tall. {@code hpFrac} is clamped to {@code 0..1} and
     * scales the fill width. A non-positive {@code width} emits nothing; a zero
     * fraction emits the backing only — matching the former inline guards.
     */
    public static void emit(DrawList out, RenderLayer layer, float cx, float baseY,
                            float width, float hpFrac, float alpha) {
        if (width <= 0f) return;
        float x0 = cx - width / 2f;
        float y1 = baseY + HP_BAR_H;
        rect(out, layer, x0, baseY, x0 + width, y1, HP_BG, alpha);
        float frac = Math.max(0f, Math.min(1f, hpFrac));
        if (frac > 0f) {
            rect(out, layer, x0, baseY, x0 + width * frac, y1, HP_FG, alpha);
        }
    }

    private static void rect(DrawList out, RenderLayer layer,
                             float x0, float y0, float x1, float y1, Color c, float alpha) {
        out.addSolidRect(layer, x0, y0, x1, y1,
                c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alpha);
    }
}
