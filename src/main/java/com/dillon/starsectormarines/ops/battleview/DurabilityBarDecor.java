package com.dillon.starsectormarines.ops.battleview;

import java.awt.Color;

/**
 * Reusable durability-bar emit behavior: the ownership-coded gauge that reports
 * an entity's remaining combat durability as a run of {@code SOLID_RECT}s in any
 * layer. Stateless and layer-agnostic; the canonical bar renderer shared by
 * {@link DroneRenderSystem} (DRONES layer) and the UNITS durability sweep. See
 * {@code combat-durability-nouns.md} for what the two pools mean.
 *
 * <p><b>One bar, two materials.</b> Armor and structure share a single band on a
 * single scale — the bar's full length is the entity's total authored durability,
 * {@code maxStructure + maxArmor}. Structure occupies the left of the filled run
 * and armor the outer end, so damage eats the bar continuously from the right:
 * first through the steel-tinted armor, then into the accent-colored structure,
 * then into the empty track. Nothing about the drain stutters at the boundary,
 * which is the point — armor is the outer layer of one pool of life, not a second
 * gauge to read.
 *
 * <p><b>Segments.</b> Dark dividers cut the band every {@link #SEGMENT_UNIT}
 * points of durability, with a full-height divider every
 * {@link #MAJOR_EVERY_SEGMENTS} of those. The scale is absolute and identical for
 * every entity, so tick density <em>is</em> the magnitude reading: a militiaman
 * carries no divider at all, a marine one or two, an emplacement a handful, a
 * heavy mech a dense comb. Counting works where counting is possible and density
 * carries the rest. A tier whose spacing would fall below
 * {@link #MIN_TICK_SPACING_PX} is dropped rather than smeared into mush, so the
 * bar degrades to majors-only and then to no ticks instead of going illegible.
 *
 * <p><b>Reading ownership.</b> {@link Allegiance} is coded on four channels at
 * once, so the bar survives a busy field, a colorblind reader, and a small zoom:
 * hue, band thickness, bar width, and the keel along the plate's bottom edge.
 * Player bars are the loudest thing on screen; neutral bars are deliberately
 * short, thin, and narrow so non-combatants do not compete with the fight for
 * attention.
 *
 * <p>Callers own bar <em>placement</em> (where {@code baseY} sits relative to the
 * entity, via the layer's own gap policy) and the nominal width, which should
 * span the body the bar belongs to; every other dimension is intrinsic style and
 * lives here as the single source of truth. All heights are screen pixels and
 * therefore zoom-independent. Use {@link #height} when something must stack above
 * a bar.
 */
public final class DurabilityBarDecor {

    /** Durability points per minor segment. Absolute and shared by every entity. */
    public static final float SEGMENT_UNIT = 25f;
    /** Every Nth minor divider is promoted to a full-height major divider. */
    public static final int MAJOR_EVERY_SEGMENTS = 5;
    /** A divider tier closer together than this is dropped instead of smeared. */
    private static final float MIN_TICK_SPACING_PX = 2.5f;
    /** Refuses to walk a divider tier that could never be drawn, however large the pool. */
    private static final int MAX_TICKS = 512;

    /** Frame thickness enclosing the band. */
    private static final float FRAME_PX = 1f;

    /** Plate color — near-black so the gauge reads over any terrain. */
    private static final Color PLATE = new Color(0x07, 0x0A, 0x0E);
    /** Plate opacity relative to the caller's alpha, so bars do not punch holes in the field. */
    private static final float PLATE_ALPHA = 0.86f;
    /** Dividers are the plate color laid back over the band. */
    private static final float TICK_ALPHA = 0.78f;
    /** Minor dividers rise this far up the band; majors span it. */
    private static final float MINOR_TICK_FRAC = 0.55f;

    /**
     * Armor reads as plate metal: the ownership hue pulled most of the way toward a
     * mid steel that <em>desaturates</em> rather than merely lightening. A pale
     * wash separates armor from a red structure fill but barely registers against a
     * bright cyan one, and the player's bar is the one that has to read first.
     */
    private static final Color STEEL = new Color(0x9A, 0xA8, 0xB4);
    private static final float ARMOR_STEEL_MIX = 0.7f;
    /** The bevel wants the near-white end of steel, not the muted plate tone. */
    private static final Color HIGHLIGHT = new Color(0xE8, 0xF1, 0xF8);
    /** The drained track keeps the ownership hue at low value rather than going flat black. */
    private static final float DRAINED_MIX = 0.24f;
    /** Bevel is the fill hue lifted toward the highlight along the band's top pixel. */
    private static final float BEVEL_MIX = 0.42f;
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
    private record Style(Color accent, float bandH, float widthScale, Keel keel, boolean bevel) {
    }

    private static final Style PLAYER = new Style(
            new Color(0x58, 0xC8, 0xFF), 5f, 1f, Keel.SOLID, true);
    private static final Style ALLY = new Style(
            new Color(0x62, 0xD9, 0x6E), 4f, 1f, Keel.DASHED, true);
    private static final Style NEUTRAL = new Style(
            new Color(0xC2, 0xB5, 0x8E), 3f, 0.55f, Keel.NONE, false);
    private static final Style ENEMY = new Style(
            new Color(0xF0, 0x52, 0x3C), 4f, 1f, Keel.NONE, false);

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
     * assuming a thickness. Armor no longer changes the height — it shares the
     * structure band.
     */
    public static float height(Allegiance owner) {
        return style(owner).bandH() + FRAME_PX * 2f;
    }

    /**
     * Emits a bar for an entity with no authored armor pool. Its whole length is
     * structure.
     */
    public static void emit(DrawList out, RenderLayer layer, Allegiance owner,
                            float cx, float baseY, float width,
                            float structure, float maxStructure, float alpha) {
        emit(out, layer, owner, cx, baseY, width, structure, maxStructure, 0f, 0f, alpha);
    }

    /**
     * Emits an armor-and-structure bar. Pools are absolute durability points, not
     * fractions — the bar needs the real magnitudes to place its segment dividers
     * and to scale both materials against one length. Current values are clamped
     * into their own maxima; a non-positive {@code maxStructure} or {@code width}
     * emits nothing.
     */
    public static void emit(DrawList out, RenderLayer layer, Allegiance owner,
                            float cx, float baseY, float width,
                            float structure, float maxStructure,
                            float armor, float maxArmor, float alpha) {
        if (width <= 0f || maxStructure <= 0f) return;
        Style s = style(owner);
        float plateW = width * s.widthScale();
        if (plateW <= FRAME_PX * 2f) return;

        float armorCap = Math.max(0f, maxArmor);
        float total = maxStructure + armorCap;
        float hp = clamp(structure, maxStructure);
        float plate = clamp(armor, armorCap);

        float x0 = cx - plateW / 2f;
        float x1 = x0 + plateW;
        float y0 = baseY;
        float y1 = baseY + height(owner);
        rect(out, layer, x0, y0, x1, y1, PLATE, alpha * PLATE_ALPHA);

        float bandX0 = x0 + FRAME_PX;
        float bandX1 = x1 - FRAME_PX;
        float bandY0 = baseY + FRAME_PX;
        float bandY1 = bandY0 + s.bandH();
        float inner = bandX1 - bandX0;

        Color accent = s.accent();
        rect(out, layer, bandX0, bandY0, bandX1, bandY1,
                mix(accent, PLATE, 1f - DRAINED_MIX), alpha);

        // Structure runs from the left; armor continues past it to the filled
        // edge. One run, drained right to left, through two materials.
        float structureX1 = fillEdge(bandX0, bandX1, inner * (hp / total));
        if (hp > 0f) {
            rect(out, layer, bandX0, bandY0, structureX1, bandY1, accent, alpha);
        }
        float armorX1 = structureX1;
        if (plate > 0f) {
            armorX1 = fillEdge(structureX1, bandX1, inner * (plate / total));
            rect(out, layer, structureX1, bandY0, armorX1, bandY1,
                    mix(accent, STEEL, ARMOR_STEEL_MIX), alpha);
        }
        if (s.bevel() && s.bandH() >= 3f && armorX1 > bandX0) {
            rect(out, layer, bandX0, bandY1 - 1f, armorX1, bandY1,
                    mix(accent, HIGHLIGHT, BEVEL_MIX), alpha);
        }

        segments(out, layer, bandX0, bandY0, bandY1, inner / total, total, alpha);
        keel(out, layer, s, x0, baseY, x1, alpha);
    }

    /**
     * Divides the band on the shared absolute scale. Majors span the band, minors
     * rise partway; a tier too dense to read is dropped whole rather than drawn
     * as a smear.
     */
    private static void segments(DrawList out, RenderLayer layer,
                                 float bandX0, float bandY0, float bandY1,
                                 float pxPerPoint, float total, float alpha) {
        int count = (int) Math.ceil(total / SEGMENT_UNIT) - 1;
        if (count <= 0) return;
        boolean minors = SEGMENT_UNIT * pxPerPoint >= MIN_TICK_SPACING_PX;
        boolean majors = SEGMENT_UNIT * MAJOR_EVERY_SEGMENTS * pxPerPoint >= MIN_TICK_SPACING_PX;
        if (!minors && !majors) return;
        count = Math.min(count, MAX_TICKS);

        float minorY0 = bandY1 - (bandY1 - bandY0) * MINOR_TICK_FRAC;
        for (int i = 1; i <= count; i++) {
            boolean major = i % MAJOR_EVERY_SEGMENTS == 0;
            if (major ? !majors : !minors) continue;
            float x = bandX0 + i * SEGMENT_UNIT * pxPerPoint;
            rect(out, layer, x, major ? bandY0 : minorY0, x + 1f, bandY1,
                    PLATE, alpha * TICK_ALPHA);
        }
    }

    /** A surviving sliver stays visible even when its share rounds below a pixel. */
    private static float fillEdge(float from, float limit, float span) {
        if (span <= 0f) return from;
        return Math.min(limit, from + Math.max(MIN_FILL_PX, span));
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

    private static float clamp(float value, float max) {
        return Math.max(0f, Math.min(max, value));
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
