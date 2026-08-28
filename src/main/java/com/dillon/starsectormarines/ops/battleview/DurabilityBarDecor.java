package com.dillon.starsectormarines.ops.battleview;

import java.awt.Color;

/**
 * Reusable durability-bar emit behavior: the ownership-coded gauge that reports
 * an entity's remaining combat durability — the armor pool stacked over the
 * structure pool — as a run of {@code SOLID_RECT}s in any layer. Stateless and
 * layer-agnostic; the canonical bar renderer shared by {@link DroneRenderSystem}
 * (DRONES layer) and the UNITS durability sweep. See
 * {@code combat-durability-nouns.md} for what the two pools mean.
 *
 * <p><b>Anatomy.</b> A dark plate carries one or two inset bands. The lower band
 * is structure; the upper band, present only for an entity authored with armor,
 * is the armor pool — armor above structure, because armor is what a shot chews
 * through first. Each band paints a drained track in a dimmed ownership hue so
 * the missing portion still says whose bar it is, then a fill from the left, then
 * a one-pixel lit bevel on friendly bars. The plate's bottom edge doubles as an
 * ownership keel: solid for the player, dashed for an ally, plain dark frame for
 * everyone else.
 *
 * <p><b>Reading ownership.</b> {@link Allegiance} is coded on four channels at
 * once, so the bar survives a busy field, a colorblind reader, and a small zoom:
 * hue, band thickness, bar width, and the keel. Player bars are the loudest thing
 * on screen; neutral bars are deliberately short, thin, and narrow so
 * non-combatants do not compete with the fight for attention.
 *
 * <p>Callers own bar <em>placement</em> (where {@code baseY} sits relative to the
 * entity, via the layer's own gap policy) and the nominal width; every other
 * dimension is intrinsic style and lives here as the single source of truth. All
 * heights are screen pixels and therefore zoom-independent — a bar stays legible
 * when the camera pulls back. Use {@link #height} when something must stack above
 * a bar.
 */
public final class DurabilityBarDecor {

    /** Frame thickness enclosing the bands, and the dark gap between them. */
    private static final float FRAME_PX = 1f;

    /** Plate color — near-black so the gauge reads over any terrain. */
    private static final Color PLATE = new Color(0x07, 0x0A, 0x0E);
    /** Plate opacity relative to the caller's alpha, so bars do not punch holes in the field. */
    private static final float PLATE_ALPHA = 0.86f;

    /** Armor reads as plate metal: the ownership hue pulled most of the way toward steel. */
    private static final Color STEEL = new Color(0xE8, 0xF1, 0xF8);
    private static final float ARMOR_STEEL_MIX = 0.55f;
    /** The drained track keeps the ownership hue at low value rather than going flat black. */
    private static final float DRAINED_MIX = 0.24f;
    /** Bevel is the fill hue lifted toward steel along the band's top pixel. */
    private static final float BEVEL_MIX = 0.42f;
    /** Bands thinner than this have no room for a bevel pixel that still reads as fill. */
    private static final float BEVEL_MIN_BAND_PX = 3f;
    /** A surviving sliver must stay visible even when the fraction rounds below a pixel. */
    private static final float MIN_FILL_PX = 1f;
    /** Dashed ally keel: five equal cells, painting the even-indexed ones. */
    private static final int KEEL_DASH_CELLS = 5;

    private DurabilityBarDecor() {
    }

    /** How the plate's bottom edge is painted — the friend/foe channel that survives desaturation. */
    private enum Keel {
        /** Plain dark frame edge. */
        NONE,
        /** Unbroken accent underline. */
        SOLID,
        /** Broken accent underline. */
        DASHED
    }

    /** Per-allegiance style. Hue, thickness, width, and keel treatment vary together. */
    private record Style(Color accent, float structureH, float armorH,
                         float widthScale, Keel keel, boolean bevel) {
    }

    private static final Style PLAYER = new Style(
            new Color(0x58, 0xC8, 0xFF), 4f, 3f, 1f, Keel.SOLID, true);
    private static final Style ALLY = new Style(
            new Color(0x62, 0xD9, 0x6E), 3f, 2f, 1f, Keel.DASHED, true);
    private static final Style NEUTRAL = new Style(
            new Color(0xC2, 0xB5, 0x8E), 2f, 2f, 0.55f, Keel.NONE, false);
    private static final Style ENEMY = new Style(
            new Color(0xF0, 0x52, 0x3C), 3f, 2f, 1f, Keel.NONE, false);

    private static Style style(Allegiance owner) {
        return switch (owner) {
            case PLAYER -> PLAYER;
            case ALLY -> ALLY;
            case NEUTRAL -> NEUTRAL;
            case ENEMY -> ENEMY;
        };
    }

    /**
     * Total screen height of the decoration, measured up from {@code baseY}.
     * Callers stacking further decoration above a bar use this rather than
     * assuming a thickness.
     */
    public static float height(Allegiance owner, boolean armored) {
        Style s = style(owner);
        float bands = s.structureH() + (armored ? s.armorH() + FRAME_PX : 0f);
        return bands + FRAME_PX * 2f;
    }

    /**
     * Emits a structure-only bar centered at {@code cx} with its bottom edge at
     * {@code baseY}, for an entity with no authored armor pool. {@code hpFrac} is
     * clamped to {@code 0..1}. A non-positive {@code width} emits nothing.
     */
    public static void emit(DrawList out, RenderLayer layer, Allegiance owner,
                            float cx, float baseY, float width, float hpFrac, float alpha) {
        paint(out, layer, owner, cx, baseY, width, hpFrac, -1f, alpha);
    }

    /**
     * Emits an armor-over-structure bar. {@code armorFrac} is clamped to
     * {@code 0..1}; a fully depleted pool still paints its drained track, because
     * "armor broken" is information the player wants to keep seeing.
     */
    public static void emit(DrawList out, RenderLayer layer, Allegiance owner,
                            float cx, float baseY, float width,
                            float hpFrac, float armorFrac, float alpha) {
        paint(out, layer, owner, cx, baseY, width, hpFrac,
                Math.max(0f, Math.min(1f, armorFrac)), alpha);
    }

    /** A negative {@code armorFrac} means the entity has no armor pool and gets no armor band. */
    private static void paint(DrawList out, RenderLayer layer, Allegiance owner,
                              float cx, float baseY, float width,
                              float hpFrac, float armorFrac, float alpha) {
        if (width <= 0f) return;
        Style s = style(owner);
        boolean armored = armorFrac >= 0f;
        float plateW = width * s.widthScale();
        if (plateW <= FRAME_PX * 2f) return;
        float x0 = cx - plateW / 2f;
        float x1 = x0 + plateW;
        float y1 = baseY + height(owner, armored);

        rect(out, layer, x0, baseY, x1, y1, PLATE, alpha * PLATE_ALPHA);

        float bandX0 = x0 + FRAME_PX;
        float bandX1 = x1 - FRAME_PX;
        float structureY = baseY + FRAME_PX;
        band(out, layer, s, bandX0, structureY, bandX1, structureY + s.structureH(),
                s.accent(), hpFrac, alpha);
        if (armored) {
            float armorY = structureY + s.structureH() + FRAME_PX;
            band(out, layer, s, bandX0, armorY, bandX1, armorY + s.armorH(),
                    mix(s.accent(), STEEL, ARMOR_STEEL_MIX), armorFrac, alpha);
        }
        keel(out, layer, s, x0, baseY, x1, alpha);
    }

    /** Drained track, left-anchored fill, and the friendly bevel for one band. */
    private static void band(DrawList out, RenderLayer layer, Style s,
                             float x0, float y0, float x1, float y1,
                             Color fill, float frac, float alpha) {
        rect(out, layer, x0, y0, x1, y1, mix(fill, PLATE, 1f - DRAINED_MIX), alpha);
        float clamped = Math.max(0f, Math.min(1f, frac));
        if (clamped <= 0f) return;
        float fillX1 = Math.min(x1, x0 + Math.max(MIN_FILL_PX, (x1 - x0) * clamped));
        rect(out, layer, x0, y0, fillX1, y1, fill, alpha);
        if (s.bevel() && y1 - y0 >= BEVEL_MIN_BAND_PX) {
            rect(out, layer, x0, y1 - 1f, fillX1, y1, mix(fill, STEEL, BEVEL_MIX), alpha);
        }
    }

    /** Repaints the plate's bottom frame edge as the ownership keel. */
    private static void keel(DrawList out, RenderLayer layer, Style s,
                             float x0, float baseY, float x1, float alpha) {
        if (s.keel() == Keel.NONE) return;
        float top = baseY + FRAME_PX;
        if (s.keel() == Keel.SOLID) {
            rect(out, layer, x0, baseY, x1, top, s.accent(), alpha);
            return;
        }
        float cell = (x1 - x0) / KEEL_DASH_CELLS;
        for (int i = 0; i < KEEL_DASH_CELLS; i += 2) {
            rect(out, layer, x0 + cell * i, baseY, x0 + cell * (i + 1), top, s.accent(), alpha);
        }
    }

    private static Color mix(Color from, Color to, float t) {
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * t),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t));
    }

    private static void rect(DrawList out, RenderLayer layer,
                             float x0, float y0, float x1, float y1, Color c, float alpha) {
        out.addSolidRect(layer, x0, y0, x1, y1,
                c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alpha);
    }
}
