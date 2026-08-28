package com.dillon.starsectormarines.ops.battleview;

import java.awt.Color;

/**
 * Reusable durability-bar emit behavior: the ownership-coded gauge that reports
 * an entity's remaining combat durability as a run of {@code SOLID_RECT}s in any
 * layer. Stateless and layer-agnostic; the canonical bar renderer shared by
 * {@link DroneRenderSystem} (DRONES layer) and the UNITS durability sweep. See
 * {@code combat-durability-nouns.md} for what armor and structure mean.
 *
 * <p><b>A row per capacity.</b> A dark plate carries one or two rows. The lower row is
 * structure; the upper row, present only for an entity authored with armor, is the
 * armor capacity — armor above structure, because armor is what a shot chews through
 * first. Each row spans the full bar and fills against its <em>own</em> maximum, so
 * a capacity at full reads as full whatever the other capacity is doing, and "armor gone,
 * hull untouched" is one glance rather than an arithmetic problem.
 *
 * <p><b>Notches.</b> Dark dividers notch each row off in a fixed quantity of
 * that capacity — {@link #STRUCTURE_NOTCH} of structure, {@link #ARMOR_NOTCH} of armor.
 * The two scales differ because the capacities do: authored armor runs half again to
 * twice the structure beside it, and forcing one scale on both would leave the
 * armor row an unreadable comb. Within a row the scale is absolute and identical
 * for every entity in the battle, so notch count and density read magnitude
 * directly. Every {@link #MAJOR_EVERY_NOTCHES}th divider is promoted to full height,
 * and a tier whose spacing falls below {@link #MIN_DIVIDER_SPACING_PX} is dropped
 * whole rather than smeared — the bar degrades to majors and then to none instead
 * of going illegible. That decision is taken once for the whole bar rather than
 * per row: the two capacities sit near enough to the threshold that letting them choose
 * separately makes one row visibly finer than the other over a rounding error.
 *
 * <p><b>Reading ownership.</b> {@link Allegiance} is coded on four channels at
 * once, so the bar survives a busy field, a colorblind reader, and a small zoom:
 * hue, row thickness, bar width, and the keel along the plate's bottom edge.
 * Player bars are the loudest thing on screen; neutral bars are deliberately
 * short, thin, and narrow so non-combatants do not compete with the fight for
 * attention.
 *
 * <p>Callers own bar <em>placement</em> (where {@code baseY} sits relative to the
 * entity, via the layer's own gap policy) and the nominal width, which should span
 * the body the bar belongs to; every other dimension is intrinsic style and lives
 * here as the single source of truth. All heights are screen pixels and therefore
 * zoom-independent. Use {@link #height} when something must stack above a bar.
 */
public final class DurabilityBarDecor {

    /** Structure points per notch in the lower row. */
    public static final float STRUCTURE_NOTCH = 25f;
    /** Armor points per notch in the upper row — coarser, because armor capacities run larger. */
    public static final float ARMOR_NOTCH = 50f;
    /** Every Nth divider in a row is promoted to a full-height major. */
    public static final int MAJOR_EVERY_NOTCHES = 5;
    /** A divider tier closer together than this is dropped instead of smeared. */
    private static final float MIN_DIVIDER_SPACING_PX = 2.5f;
    /** Refuses to walk a divider tier that could never be drawn, however large the quantity. */
    private static final int MAX_DIVIDERS = 512;

    /** Frame thickness enclosing the rows, and the gridline between them. */
    private static final float FRAME_PX = 1f;

    /** Plate color — near-black so the gauge reads over any terrain. */
    private static final Color PLATE = new Color(0x07, 0x0A, 0x0E);
    /** Plate opacity relative to the caller's alpha, so bars do not punch holes in the field. */
    private static final float PLATE_ALPHA = 0.86f;
    /** Dividers are the plate color laid back over a row, opaque enough to read as a notch wall. */
    private static final float DIVIDER_ALPHA = 0.92f;
    /** Minor dividers hang this far down a row; majors span it. */
    private static final float MINOR_DIVIDER_FRAC = 0.55f;

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
    /** The drained track keeps the row's hue at low value rather than going flat black. */
    private static final float DRAINED_MIX = 0.24f;
    /** Bevel is the fill hue lifted toward the highlight along the row's top pixel. */
    private static final float BEVEL_MIX = 0.42f;
    /** Rows thinner than this have no room for a bevel pixel that still reads as fill. */
    private static final float BEVEL_MIN_ROW_PX = 3f;
    /** A surviving sliver must stay visible even when the fraction rounds below a pixel. */
    private static final float MIN_FILL_PX = 1f;
    /** Dashed ally keel: five equal slots, painting the even-indexed ones. */
    private static final int KEEL_DASH_SLOTS = 5;

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
        return s.structureH() + (armored ? s.armorH() + FRAME_PX : 0f) + FRAME_PX * 2f;
    }

    /**
     * Emits a structure-only bar centered at {@code cx} with its bottom edge at
     * {@code baseY}, for an entity with no authored armor capacity.
     */
    public static void emit(DrawList out, RenderLayer layer, Allegiance owner,
                            float cx, float baseY, float width,
                            float structure, float maxStructure, float alpha) {
        paint(out, layer, owner, cx, baseY, width, structure, maxStructure, 0f, -1f, alpha);
    }

    /**
     * Emits an armor row over a structure row. Both rows are absolute durability
     * points, not fractions — each row needs its real magnitude to place its notch
     * dividers. Current values are clamped into their own maxima; a non-positive
     * {@code maxStructure} or {@code width} emits nothing, and a non-positive
     * {@code maxArmor} drops the armor row.
     */
    public static void emit(DrawList out, RenderLayer layer, Allegiance owner,
                            float cx, float baseY, float width,
                            float structure, float maxStructure,
                            float armor, float maxArmor, float alpha) {
        paint(out, layer, owner, cx, baseY, width, structure, maxStructure,
                armor, maxArmor > 0f ? maxArmor : -1f, alpha);
    }

    /** A negative {@code maxArmor} means the entity has no armor row. */
    private static void paint(DrawList out, RenderLayer layer, Allegiance owner,
                              float cx, float baseY, float width,
                              float structure, float maxStructure,
                              float armor, float maxArmor, float alpha) {
        if (width <= 0f || maxStructure <= 0f) return;
        Style s = style(owner);
        boolean armored = maxArmor > 0f;
        float plateW = width * s.widthScale();
        if (plateW <= FRAME_PX * 2f) return;

        float x0 = cx - plateW / 2f;
        float x1 = x0 + plateW;
        rect(out, layer, x0, baseY, x1, baseY + height(owner, armored), PLATE, alpha * PLATE_ALPHA);

        float rowX0 = x0 + FRAME_PX;
        float rowX1 = x1 - FRAME_PX;
        float rowW = rowX1 - rowX0;
        // One granularity for the whole bar: the coarser row's verdict wins.
        Tier tier = tier(rowW, maxStructure, STRUCTURE_NOTCH)
                .and(armored ? tier(rowW, maxArmor, ARMOR_NOTCH) : Tier.BOTH);

        float structureY = baseY + FRAME_PX;
        row(out, layer, s, rowX0, structureY, rowX1, structureY + s.structureH(),
                s.accent(), structure, maxStructure, STRUCTURE_NOTCH, tier, alpha);
        if (armored) {
            float armorY = structureY + s.structureH() + FRAME_PX;
            row(out, layer, s, rowX0, armorY, rowX1, armorY + s.armorH(),
                    mix(s.accent(), STEEL, ARMOR_STEEL_MIX), armor, maxArmor, ARMOR_NOTCH,
                    tier, alpha);
        }
        keel(out, layer, s, x0, baseY, x1, alpha);
    }

    /** Which divider tiers a bar has room for. */
    private record Tier(boolean minors, boolean majors) {

        static final Tier BOTH = new Tier(true, true);

        Tier and(Tier other) {
            return new Tier(minors && other.minors(), majors && other.majors());
        }

        boolean draws(boolean major) {
            return major ? majors : minors;
        }
    }

    /** What one row's scale can resolve across {@code rowW} pixels. */
    private static Tier tier(float rowW, float max, float notch) {
        float pxPerPoint = rowW / max;
        return new Tier(notch * pxPerPoint >= MIN_DIVIDER_SPACING_PX,
                notch * MAJOR_EVERY_NOTCHES * pxPerPoint >= MIN_DIVIDER_SPACING_PX);
    }

    /**
     * One row's fill: drained track, left-anchored fill against that row's own
     * maximum, the friendly bevel, then the notch dividers on that row's scale.
     */
    private static void row(DrawList out, RenderLayer layer, Style s,
                            float x0, float y0, float x1, float y1, Color fill,
                            float current, float max, float notch, Tier tier, float alpha) {
        rect(out, layer, x0, y0, x1, y1, mix(fill, PLATE, 1f - DRAINED_MIX), alpha);
        float held = Math.max(0f, Math.min(max, current));
        float fillX1 = x0;
        if (held > 0f) {
            fillX1 = Math.min(x1, x0 + Math.max(MIN_FILL_PX, (x1 - x0) * (held / max)));
            rect(out, layer, x0, y0, fillX1, y1, fill, alpha);
            if (s.bevel() && y1 - y0 >= BEVEL_MIN_ROW_PX) {
                rect(out, layer, x0, y1 - 1f, fillX1, y1, mix(fill, HIGHLIGHT, BEVEL_MIX), alpha);
            }
        }
        dividers(out, layer, x0, y0, y1, (x1 - x0) / max, max, notch, tier, alpha);
    }

    /**
     * Notches a row on its own absolute scale, at the granularity the whole
     * bar settled on. Majors span the row, minors hang partway.
     */
    private static void dividers(DrawList out, RenderLayer layer,
                                 float x0, float y0, float y1,
                                 float pxPerPoint, float max, float notch,
                                 Tier tier, float alpha) {
        int count = (int) Math.ceil(max / notch) - 1;
        if (count <= 0 || (!tier.minors() && !tier.majors())) return;
        count = Math.min(count, MAX_DIVIDERS);

        float minorY0 = y1 - (y1 - y0) * MINOR_DIVIDER_FRAC;
        for (int i = 1; i <= count; i++) {
            boolean major = i % MAJOR_EVERY_NOTCHES == 0;
            if (!tier.draws(major)) continue;
            float x = x0 + i * notch * pxPerPoint;
            rect(out, layer, x, major ? y0 : minorY0, x + 1f, y1, PLATE, alpha * DIVIDER_ALPHA);
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
        float slot = (x1 - x0) / KEEL_DASH_SLOTS;
        for (int i = 0; i < KEEL_DASH_SLOTS; i += 2) {
            rect(out, layer, x0 + slot * i, baseY, x0 + slot * (i + 1), top, s.accent(), alpha);
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
