package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.HqActionItem.Kind;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.Rect;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Locale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The HQ action board projected as a hologram standing over the command deck's plotting
 * table: a slightly tilted sector disc hovering above the table top, a lat-long dome over
 * it, and one body floating in that volume per action item. Every point is a deck point in
 * metres pushed through the guide camera ({@link CommandDeckLayout#project}), vertex by
 * vertex, so the plot stands in the painted room in perspective instead of being pasted
 * over it.
 *
 * <p>An item with a hyperspace position floats over the point where that position falls
 * on the disc (about {@value #SECTOR_SPAN} units to the rim, clamped inside it), with a
 * dashed drop line to a ring on the disc and a faint stem on to the table; one without
 * floats low over the rim, in board order. Each item is a body shaped by its kind
 * ({@link HoloBodies}: ringed world, fortified planet, moon, data card) with a label plate
 * carrying its kind glyph. Hyperspace lanes join the positioned bodies, and the volume
 * ({@link HoloVolume}) supplies the light, the turning dome, motes and a rising scan
 * plane. Obligations falling due within {@value #URGENT_DAYS} days pulse warm; the
 * selected item gets a beam from the table and a bracket; a hovered one brightens.
 *
 * <p>The board holds no fact of its own: everything here is a row of the action list. All
 * motion is a pure function of the time argument, apart from the per-id first-seen times
 * that make a new body rise in; those are view-local and never persisted. The dome turns,
 * but the bodies hold their plot positions, so the hit boxes are time-independent.
 */
final class HqHoloBoard {
    /** Height of the disc's centre above the table top, in metres. */
    static final float LIFT = 0.62f;
    /** Disc radius in metres. */
    static final float RADIUS = 1.25f;
    /** The disc's far edge is raised by this much (radians), so the plot opens towards the camera. */
    static final float TILT = (float) Math.toRadians(22);
    /** Hyperspace units from the core to the disc's rim. */
    static final float SECTOR_SPAN = 25000f;
    /** Positioned items stay inside this fraction of the radius, clear of the rim ring. */
    static final float SECTOR_LIMIT = 0.86f;
    /** Bodies with a plot position float this high over their foot on the disc, in metres. */
    static final float BODY_LOW = 0.15f;
    static final float BODY_HIGH = 0.31f;
    /** Rim items float lower, clear of the near rim's ticks. */
    static final float RIM_HEIGHT = 0.14f;
    static final int URGENT_DAYS = 2;
    static final int TITLE_CHARS = 26;

    private static final Color HOLO = new Color(150, 240, 214);
    private static final Color HOLO_DEEP = new Color(70, 170, 160);
    private static final Color AMBER = new Color(255, 196, 96);
    private static final Color ALARM = new Color(255, 104, 72);
    private static final Color PLATE = new Color(6, 24, 26);
    private static final float SWEEP_SECONDS = 6.5f;
    private static final float RISE_SECONDS = 0.9f;
    /** Rim items share the near arc, left to right, where their labels sit over the open table. */
    private static final float RING_FROM = (float) Math.toRadians(208);
    private static final float RING_TO = (float) Math.toRadians(332);
    private static final int SEGMENTS = 72;
    /** Size of the kind glyph on a label plate, in pixels. */
    private static final float GLYPH = 11f;
    private static final String[] KEEP_CLEAR = {"castle", "sabine"};

    /** A marker's clickable area and its label's, in banner pixels after the fit. */
    record Hit(Rect marker, Rect label) {
        /** The smallest rectangle holding both. */
        Rect bounds() {
            float x0 = Math.min(marker.x(), label.x());
            float y0 = Math.min(marker.y(), label.y());
            float x1 = Math.max(marker.x() + marker.width(), label.x() + label.width());
            float y1 = Math.max(marker.y() + marker.height(), label.y() + label.height());
            return new Rect(x0, y0, x1 - x0, y1 - y0);
        }
    }

    /**
     * One item's settled place: the body's deck point, its foot on the disc, its screen
     * centre, its foot on the table, how far its drawing reaches (pixels) and its label box.
     */
    private record Placed(HqActionItem item, float[] deck, float[] foot, float mx, float my, float fx, float fy,
                          float extent, String text, Rect label) { }

    private final BitmapFont font;
    private final Map<String, Float> firstSeen = new HashMap<>();
    private final HoloSpace.Art art = new HoloSpace.Art();

    HqHoloBoard() {
        this(Fonts.INSIGNIA_15_AA);
    }

    HqHoloBoard(BitmapFont font) {
        this.font = font;
    }

    // ---- geometry ---------------------------------------------------------------

    /** Deck point (metres) of a point on the disc given in disc-local metres: a right, b away. */
    static float[] discPoint(CommandDeckLayout layout, float a, float b) {
        float[] centre = layout.tableCentre();
        return new float[]{centre[0] + a, centre[1] + LIFT + b * (float) Math.sin(TILT),
                centre[2] + b * (float) Math.cos(TILT)};
    }

    /** Disc-local metres for a hyperspace position: the span maps to the rim, clamped inside it. */
    static float[] sectorPoint(float[] sector) {
        float a = sector[0] / SECTOR_SPAN * RADIUS;
        float b = sector[1] / SECTOR_SPAN * RADIUS;
        float r = (float) Math.hypot(a, b);
        float limit = RADIUS * SECTOR_LIMIT;
        if (r > limit) {
            a *= limit / r;
            b *= limit / r;
        }
        return new float[]{a, b};
    }

    /** Disc-local metres of the {@code index}th of {@code count} rim items: evenly along the near arc. */
    static float[] ringPoint(int index, int count) {
        double angle = count <= 1 ? (RING_FROM + RING_TO) * 0.5
                : RING_FROM + (RING_TO - RING_FROM) * index / (double) (count - 1);
        return new float[]{RADIUS * (float) Math.cos(angle), RADIUS * (float) Math.sin(angle)};
    }

    /** Disc-local placement of every item, keyed by id, in board order. */
    static Map<String, float[]> discPlacement(HqActionBoard board) {
        Map<String, float[]> out = new LinkedHashMap<>();
        if (board == null) return out;
        int ringCount = 0;
        for (HqActionItem item : board.items()) if (item.sector() == null) ringCount++;
        int ring = 0;
        for (HqActionItem item : board.items()) {
            out.put(item.id(), item.sector() != null ? sectorPoint(item.sector()) : ringPoint(ring++, ringCount));
        }
        return out;
    }

    /** Height of an item's body above its foot on the disc, in metres: varied per item, so the plot has depth. */
    static float bodyHeight(HqActionItem item) {
        float h = HoloSpace.hash(item.id().hashCode());
        return item.sector() == null ? RIM_HEIGHT + 0.04f * h : BODY_LOW + (BODY_HIGH - BODY_LOW) * h;
    }

    /** Deck point of a body floating {@code height} metres straight above the disc point (a, b). */
    static float[] bodyPoint(CommandDeckLayout layout, float a, float b, float height) {
        float[] foot = discPoint(layout, a, b);
        return new float[]{foot[0], foot[1] + height, foot[2]};
    }

    /**
     * Where each item's body and label land on a banner with this fit, keyed by item id
     * in board order. Settled positions: rise-in, pulse and the dome's slow turn never
     * move a hit box, because the bodies stay at their plot positions while the volume
     * turns round them.
     */
    Map<String, Hit> hits(CommandDeckLayout layout, CommandDeckLayout.Fit fit, HqActionBoard board) {
        Map<String, Hit> out = new LinkedHashMap<>();
        for (Placed p : place(layout, fit, board)) {
            float half = p.extent() + 3f;
            out.put(p.item().id(), new Hit(new Rect(p.mx() - half, p.my() - half, half * 2f, half * 2f), p.label()));
        }
        return out;
    }

    private List<Placed> place(CommandDeckLayout layout, CommandDeckLayout.Fit fit, HqActionBoard board) {
        List<Placed> placed = new ArrayList<>();
        if (board == null || layout == null || !layout.projects()) return placed;
        HoloSpace space = new HoloSpace(layout, fit);
        Map<String, float[]> disc = discPlacement(board);
        float h = lineHeight() + 4f;
        // Every body first, so a label can keep off the others.
        List<float[]> local = new ArrayList<>();
        List<float[]> decks = new ArrayList<>();
        List<float[]> centres = new ArrayList<>();
        List<Float> extents = new ArrayList<>();
        List<Rect> markers = new ArrayList<>();
        for (HqActionItem item : board.items()) {
            float[] l = disc.get(item.id());
            float[] deck = bodyPoint(layout, l[0], l[1], bodyHeight(item));
            float[] m = space.screen(deck);
            float extent = Math.max(7f, HoloBodies.radius(item.kind()) * HoloBodies.reach(item.kind())
                    * space.pixelsPerMetre(deck[2]));
            local.add(l);
            decks.add(deck);
            centres.add(m);
            extents.add(extent);
            markers.add(new Rect(m[0] - extent, m[1] - extent, extent * 2f, extent * 2f));
        }
        List<Rect> blocked = new ArrayList<>(keepClear(layout, fit));
        blocked.addAll(markers);
        for (int i = 0; i < board.items().size(); i++) {
            HqActionItem item = board.items().get(i);
            float[] deck = decks.get(i);
            Rect own = markers.get(i);
            float mx = centres.get(i)[0], my = centres.get(i)[1];
            float gap = extents.get(i) + 5f;
            float[] f = space.screen(deck[0], CommandDeckLayout.TABLE_TOP, deck[2]);
            String text = labelText(item);
            float w = textWidth(text) + 10f + GLYPH + 5f;
            Rect right = new Rect(mx + gap, my - h * 0.5f, w, h);
            Rect left = new Rect(mx - gap - w, my - h * 0.5f, w, h);
            Rect above = new Rect(mx - w * 0.5f, my - gap - h, w, h);
            Rect below = new Rect(mx - w * 0.5f, my + gap, w, h);
            // Hung off the marker's inner side, for a marker hard against a figure.
            float innerX = local.get(i)[0] >= 0f ? mx + gap - w : mx - gap;
            Rect aboveInner = new Rect(innerX, my - gap - h, w, h);
            Rect belowInner = new Rect(innerX, my + gap, w, h);
            List<Rect> candidates = local.get(i)[0] >= 0f
                    ? List.of(right, left, above, below, belowInner, aboveInner)
                    : List.of(left, right, above, below, belowInner, aboveInner);
            List<Rect> others = new ArrayList<>(blocked);
            others.remove(own);
            Rect label = null;
            for (Rect candidate : candidates) {
                if (clearOf(candidate, others)) {
                    label = candidate;
                    break;
                }
            }
            if (label == null) label = unstack(candidates.get(1), others);
            blocked.add(label);
            float[] foot = discPoint(layout, local.get(i)[0], local.get(i)[1]);
            placed.add(new Placed(item, deck, foot, mx, my, f[0], f[1], extents.get(i), text, label));
        }
        return placed;
    }

    /** Nudges a label up, then down, until it overlaps none already placed. */
    private static Rect unstack(Rect label, List<Rect> taken) {
        if (clearOf(label, taken)) return label;
        for (int step = 1; step <= 6; step++) {
            for (int sign : new int[]{-1, 1}) {
                Rect moved = new Rect(label.x(), label.y() + sign * step * (label.height() * 0.5f + 1f),
                        label.width(), label.height());
                if (clearOf(moved, taken)) return moved;
            }
        }
        return label;
    }

    private static boolean clearOf(Rect r, List<Rect> others) {
        for (Rect o : others) {
            if (r.x() < o.x() + o.width() && o.x() < r.x() + r.width()
                    && r.y() < o.y() + o.height() && o.y() < r.y() + r.height()) return false;
        }
        return true;
    }

    /**
     * The people at the table. A label must not sit on them: they are drawn over the
     * hologram, so a label there would lose its words under an arm or a face.
     */
    private static List<Rect> keepClear(CommandDeckLayout layout, CommandDeckLayout.Fit fit) {
        List<Rect> out = new ArrayList<>();
        for (String id : KEEP_CLEAR) {
            for (CommandDeckLayout.Figure f : layout.figures()) {
                if (!f.id().equals(id)) continue;
                out.add(new Rect(fit.x(f.x()), fit.y(f.y()), f.w() * fit.scale(), f.h() * fit.scale()));
            }
        }
        return out;
    }

    static String labelText(HqActionItem item) {
        String title = item.title() == null ? "" : item.title().trim();
        if (title.length() > TITLE_CHARS) title = title.substring(0, TITLE_CHARS - 1).trim() + "…";
        if (!item.hasDeadline()) return title;
        return title + "  " + item.daysLeft() + "d";
    }

    static boolean urgent(HqActionItem item) {
        return item.kind() == Kind.OBLIGATION && item.hasDeadline() && item.daysLeft() <= URGENT_DAYS;
    }

    private float textWidth(String text) {
        float measured = font == null ? 0f : font.measureWidth(text);
        return measured > 0f ? measured : text.length() * 7.5f;
    }

    private float lineHeight() {
        int measured = font == null ? 0 : font.getLineHeight();
        return measured > 0 ? measured : 15f;
    }

    // ---- drawing ----------------------------------------------------------------

    /**
     * Draws the hologram. {@code selectedId} and {@code hoveredId} may be null or name no
     * item; {@code board} may be null or empty, which leaves the bare scanning volume.
     */
    void draw(CanvasContext context, CommandDeckLayout layout, CommandDeckLayout.Fit fit, HqActionBoard board,
              String selectedId, String hoveredId, float time) {
        if (layout == null || !layout.projects()) return;
        HoloSpace space = new HoloSpace(layout, fit);
        float flicker = 0.93f + 0.07f * (float) Math.sin(time * 7.3) * (float) Math.sin(time * 2.1 + 0.4);
        List<Placed> placed = place(layout, fit, board);
        observe(placed, time);

        HoloVolume.light(context, space, flicker);
        emitter(context, space, flicker);
        HoloVolume.globeBack(context, space, time, flicker);
        disc(context, space, time, flicker);
        HoloVolume.ticks(context, space, time, flicker);
        HoloVolume.scan(context, space, time, flicker);
        HoloVolume.motes(context, space, time, flicker);
        HoloVolume.core(context, space, art.get("holo-star"), time, flicker);
        List<float[]> positioned = new ArrayList<>();
        for (Placed p : placed) {
            if (p.item().sector() != null && rise(p.item().id(), time) >= 1f) positioned.add(p.deck());
        }
        HoloVolume.lanes(context, space, positioned, time, flicker);

        // Back to front: far bodies first, so near drop lines and bodies pass over them.
        placed.sort((p, q) -> Float.compare(q.deck()[2], p.deck()[2]));
        for (Placed p : placed) {
            String id = p.item().id();
            body(context, space, p, id.equals(selectedId), id.equals(hoveredId), time, flicker);
        }
        HoloVolume.globeFront(context, space, time, flicker);
        // Labels last, over everything in the volume, so they stay legible.
        for (Placed p : placed) {
            String id = p.item().id();
            label(context, space, p, id.equals(selectedId), id.equals(hoveredId), time);
        }
    }

    private void observe(List<Placed> placed, float time) {
        Set<String> present = new HashSet<>();
        for (Placed p : placed) {
            present.add(p.item().id());
            Float seen = firstSeen.get(p.item().id());
            if (seen == null || seen > time) firstSeen.put(p.item().id(), time);
        }
        firstSeen.keySet().retainAll(present);
    }

    private float rise(String id, float time) {
        Float seen = firstSeen.get(id);
        if (seen == null) return 1f;
        float t = clamp01((time - seen) / RISE_SECONDS);
        return t * t * (3f - 2f * t);
    }

    /** Where a body is drawn at this moment: rising from the table, with a slight bob once up. */
    private float[] liveDeck(Placed p, float time) {
        float rise = rise(p.item().id(), time);
        float lift = CommandDeckLayout.TABLE_TOP + (p.deck()[1] - CommandDeckLayout.TABLE_TOP) * rise;
        float bob = 0.006f * rise * (float) Math.sin(time * 1.7 + HoloSpace.hash(p.item().id().hashCode()) * 6.3f);
        return new float[]{p.deck()[0], lift + bob, p.deck()[2]};
    }

    /** The emitter on the table top: a small ring and a faint cone up to the disc's sides. */
    private void emitter(CanvasContext context, HoloSpace space, float flicker) {
        float[] tc = space.layout.tableCentre();
        float[] emitter = space.screen(tc[0], CommandDeckLayout.TABLE_TOP, tc[2]);
        for (int i = 0; i < 24; i++) {
            double a0 = Math.PI * 2 * i / 24, a1 = Math.PI * 2 * (i + 1) / 24;
            float[] p0 = space.screen(tc[0] + 0.16f * (float) Math.cos(a0), CommandDeckLayout.TABLE_TOP,
                    tc[2] + 0.16f * (float) Math.sin(a0));
            float[] p1 = space.screen(tc[0] + 0.16f * (float) Math.cos(a1), CommandDeckLayout.TABLE_TOP,
                    tc[2] + 0.16f * (float) Math.sin(a1));
            context.line(p0[0], p0[1], p1[0], p1[1], alpha(HOLO, 130 * flicker), 1.2f);
        }
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI * 0.5 * i;
            float[] rim = space.disc(RADIUS * (float) Math.cos(angle), RADIUS * (float) Math.sin(angle), 0f);
            context.line(emitter[0], emitter[1], rim[0], rim[1], alpha(HOLO_DEEP, 30 * flicker), 1f);
        }
    }

    /** The plot's floor: the disc, its range rings, cross hairs and the scan sweep. */
    private void disc(CanvasContext context, HoloSpace space, float time, float flicker) {
        float[][] rim = new float[SEGMENTS + 1][];
        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = Math.PI * 2 * i / SEGMENTS;
            rim[i] = space.disc(RADIUS * (float) Math.cos(angle), RADIUS * (float) Math.sin(angle), 0f);
        }
        float[] centre = space.disc(0f, 0f, 0f);
        float[] body = new float[SEGMENTS * 2];
        for (int i = 0; i < SEGMENTS; i++) {
            body[i * 2] = rim[i][0];
            body[i * 2 + 1] = rim[i][1];
        }
        HoloSpace.fill(context, body, alpha(HOLO_DEEP, 26 * flicker));
        // Range rings, brighter on the near side.
        for (float fraction : new float[]{0.33f, 0.66f}) {
            float[] prev = null;
            for (int i = 0; i <= SEGMENTS; i++) {
                double angle = Math.PI * 2 * i / SEGMENTS;
                float s = (float) Math.sin(angle);
                float[] p = space.disc(RADIUS * fraction * (float) Math.cos(angle), RADIUS * fraction * s, 0f);
                if (prev != null) context.line(prev[0], prev[1], p[0], p[1], alpha(HOLO, (44 - 20 * s) * flicker), 1f);
                prev = p;
            }
        }
        for (int i = 0; i < SEGMENTS; i++) {
            float s = (float) Math.sin(Math.PI * 2 * (i + 0.5) / SEGMENTS);
            context.line(rim[i][0], rim[i][1], rim[i + 1][0], rim[i + 1][1], alpha(HOLO, (160 - 50 * s) * flicker),
                    s < 0f ? 1.8f : 1.2f);
        }
        // Cross hairs through the core.
        for (int i = 0; i < 2; i++) {
            float[] a = rim[i * SEGMENTS / 4];
            float[] b = rim[i * SEGMENTS / 4 + SEGMENTS / 2];
            context.line(a[0], a[1], b[0], b[1], alpha(HOLO, 30 * flicker), 1f);
        }
        // The scan sweep: a leading spoke over a fading wedge.
        double lead = Math.PI * 2 * fraction(time / SWEEP_SECONDS);
        int wedge = 14;
        for (int k = wedge; k >= 1; k--) {
            double a0 = lead - k * 0.04, a1 = lead - (k - 1) * 0.04;
            float[] p0 = space.disc(RADIUS * (float) Math.cos(a0), RADIUS * (float) Math.sin(a0), 0f);
            float[] p1 = space.disc(RADIUS * (float) Math.cos(a1), RADIUS * (float) Math.sin(a1), 0f);
            float fade = 1f - (k - 1) / (float) wedge;
            HoloSpace.fill(context, new float[]{centre[0], centre[1], p0[0], p0[1], p1[0], p1[1]},
                    alpha(HOLO, 34 * fade * fade * flicker));
        }
        float[] tip = space.disc(RADIUS * (float) Math.cos(lead), RADIUS * (float) Math.sin(lead), 0f);
        context.line(centre[0], centre[1], tip[0], tip[1], alpha(HOLO, 150 * flicker), 1.4f);
    }

    /**
     * One item: its foot ring on the disc, the drop line up to the body, the faint stem
     * on down to the table (or the selection's beam), the body itself, and its pulse.
     */
    private void body(CanvasContext context, HoloSpace space, Placed p, boolean selected, boolean hovered,
                      float time, float flicker) {
        HqActionItem item = p.item();
        float rise = rise(item.id(), time);
        Color tone = tone(item, time);
        float[] foot = p.foot();
        float[] deck = liveDeck(p, time);
        float[] at = space.screen(deck);
        float[] onDisc = space.screen(foot);
        float[] onTable = {p.fx(), p.fy()};

        // Where the body stands on the plot: a small ring on the disc beneath it.
        if (rise > 0.5f) {
            float r = 0.045f;
            float[] prev = null;
            for (int i = 0; i <= 16; i++) {
                double angle = Math.PI * 2 * i / 16;
                float[] q = space.screen(foot[0] + r * (float) Math.cos(angle), foot[1],
                        foot[2] + r * (float) Math.sin(angle));
                if (prev != null) context.line(prev[0], prev[1], q[0], q[1], alpha(tone, 130 * flicker * rise), 1f);
                prev = q;
            }
        }
        if (selected) {
            float beat = 0.75f + 0.25f * (float) Math.sin(time * 4.0);
            context.line(onTable[0], onTable[1], at[0], at[1], alpha(tone, 60 * beat), 7f * space.fit.scale() + 2f);
            context.line(onTable[0], onTable[1], at[0], at[1], alpha(tone, 220 * beat), 2f);
            float[] prev = null;
            for (int i = 0; i <= 20; i++) {
                double angle = Math.PI * 2 * i / 20;
                float[] q = space.screen(deck[0] + 0.09f * (float) Math.cos(angle), CommandDeckLayout.TABLE_TOP,
                        deck[2] + 0.09f * (float) Math.sin(angle));
                if (prev != null) context.line(prev[0], prev[1], q[0], q[1], alpha(tone, 200 * beat), 1.4f);
                prev = q;
            }
        } else {
            // The drop line from the body to its foot, dashed, and a fainter stem on to the table.
            int dashes = 6;
            for (int k = 0; k < dashes; k++) {
                float t0 = k / (float) dashes, t1 = (k + 0.6f) / (float) dashes;
                context.line(at[0] + (onDisc[0] - at[0]) * t0, at[1] + (onDisc[1] - at[1]) * t0,
                        at[0] + (onDisc[0] - at[0]) * t1, at[1] + (onDisc[1] - at[1]) * t1,
                        alpha(tone, 120 * flicker * rise), 1f);
            }
            context.line(onDisc[0], onDisc[1], onTable[0], onTable[1], alpha(tone, 34 * flicker * rise), 1f);
        }
        float dot = 1.5f * space.fit.scale() + 0.6f;
        context.fillRect(onTable[0] - dot, onTable[1] - dot * 0.6f, dot * 2f, dot * 1.2f, alpha(tone, 120 * flicker));

        float bright = (selected ? 1f : hovered ? 0.95f : 0.74f) * flicker;
        float grow = (0.35f + 0.65f * rise) * (hovered || selected ? 1.15f : 1f);
        HoloBodies.draw(context, space, art, item.kind(), deck, grow, tone, bright, time, item.id().hashCode());
        if (urgent(item)) {
            float wave = fraction(time / 1.4f);
            ring(context, at[0], at[1], p.extent() * (0.8f + 0.9f * wave), alpha(tone, 170 * (1f - wave) * rise), 1.3f);
        }
        if (selected || hovered) {
            float reach = HoloBodies.radius(item.kind()) * HoloBodies.reach(item.kind()) * 1.3f;
            HoloBodies.bracket(context, space, deck, reach,
                    alpha(selected ? Color.WHITE : tone, (selected ? 220 : 140) * rise), time);
        }
    }

    private void label(CanvasContext context, HoloSpace space, Placed p, boolean selected, boolean hovered,
                       float time) {
        HqActionItem item = p.item();
        float labelRise = clamp01(rise(item.id(), time) * 1.6f - 0.6f);
        if (labelRise <= 0f) return;
        Color tone = tone(item, time);
        float bright = selected ? 1f : hovered ? 0.95f : 0.72f;
        float[] at = space.screen(liveDeck(p, time));
        Rect l = p.label();
        // A leader from the body's edge to the nearest point of the label plate.
        float lx = Math.max(l.x(), Math.min(l.x() + l.width(), at[0]));
        float ly = Math.max(l.y(), Math.min(l.y() + l.height(), at[1]));
        float dx = lx - at[0], dy = ly - at[1];
        float length = (float) Math.hypot(dx, dy);
        float start = p.extent() * 0.55f;
        if (length > start + 2f) {
            context.line(at[0] + dx / length * start, at[1] + dy / length * start, lx, ly,
                    alpha(tone, 110 * labelRise * bright), 1f);
        }
        context.fillRect(l.x(), l.y(), l.width(), l.height(), alpha(PLATE, (selected ? 165 : 130) * labelRise));
        context.line(l.x(), l.y(), l.x(), l.y() + l.height(), alpha(tone, 210 * labelRise * bright), 2f);
        SvgAsset glyph = art.get("holo-" + item.kind().name().toLowerCase(Locale.ROOT).replace('_', '-'));
        float gx = l.x() + 5f, gy = l.y() + (l.height() - GLYPH) * 0.5f;
        Color glyphTone = alpha(tone, 235 * labelRise * Math.max(bright, 0.85f));
        if (glyph != null) context.svg(glyph, gx, gy, GLYPH, GLYPH, glyphTone);
        else ring(context, gx + GLYPH * 0.5f, gy + GLYPH * 0.5f, GLYPH * 0.4f, glyphTone, 1.2f);
        if (font != null) {
            context.text(font, p.text(), gx + GLYPH + 5f, l.y() + 2f, alpha(selected || hovered
                    ? Color.WHITE : tone, 255 * (selected ? 1f : hovered ? 0.95f : 0.85f) * labelRise));
        }
    }

    private static void ring(CanvasContext context, float cx, float cy, float r, Color color, float width) {
        HoloSpace.ring(context, cx, cy, r, color, width);
    }

    /** The item's light: holo green, or a warm amber-to-red pulse for an obligation falling due. */
    private static Color tone(HqActionItem item, float time) {
        if (!urgent(item)) return HOLO;
        float beat = 0.5f + 0.5f * (float) Math.sin(time * Math.PI * 2 / 1.4f);
        return HoloSpace.mix(AMBER, ALARM, beat);
    }

    private static Color alpha(Color color, float alpha) { return HoloSpace.alpha(color, alpha); }

    private static float clamp01(float v) { return HoloSpace.clamp01(v); }

    private static float fraction(float value) { return HoloSpace.fraction(value); }
}
