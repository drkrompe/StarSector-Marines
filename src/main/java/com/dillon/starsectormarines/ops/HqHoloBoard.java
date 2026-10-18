package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.HqActionItem.Kind;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.Rect;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The HQ action board projected as a hologram over the command deck's plotting table: a
 * slightly tilted sector disc hovering above the table top, with one pinned marker per
 * action item. Every point is a deck point in metres pushed through the guide camera
 * ({@link CommandDeckLayout#project}), so the plot stands in the painted room in
 * perspective instead of being pasted over it.
 *
 * <p>An item with a hyperspace position sits where that position falls on the disc
 * (about {@value #SECTOR_SPAN} units to the rim, clamped inside it); one without sits on
 * the rim, in board order. Each marker has a stem down to the table, a glyph for its kind
 * (diamond story, triangle obligation, square letter, circle side job; small SVG assets
 * tinted here) and a short label. Obligations falling due within {@value #URGENT_DAYS}
 * days pulse warm; the selected item gets a beam from the table; a hovered one brightens.
 *
 * <p>The board holds no fact of its own: everything here is a row of the action list. All
 * motion is a pure function of the time argument, apart from the per-id first-seen times
 * that make a new marker rise in; those are view-local and never persisted.
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

    /** One item's settled place: deck point, screen marker, table foot and label box. */
    private record Placed(HqActionItem item, float[] deck, float mx, float my, float fx, float fy,
                          float glyph, String text, Rect label) { }

    private final BitmapFont font;
    private final Map<String, Float> firstSeen = new HashMap<>();
    private Map<Kind, SvgAsset> glyphs;
    private boolean glyphsFailed;

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

    /**
     * Where each item's marker and label land on a banner with this fit, keyed by item id
     * in board order. Settled positions: rise-in and pulse never move a hit box.
     */
    Map<String, Hit> hits(CommandDeckLayout layout, CommandDeckLayout.Fit fit, HqActionBoard board) {
        Map<String, Hit> out = new LinkedHashMap<>();
        for (Placed p : place(layout, fit, board)) {
            float half = p.glyph() * 0.5f + 3f;
            out.put(p.item().id(), new Hit(new Rect(p.mx() - half, p.my() - half, half * 2f, half * 2f), p.label()));
        }
        return out;
    }

    private List<Placed> place(CommandDeckLayout layout, CommandDeckLayout.Fit fit, HqActionBoard board) {
        List<Placed> placed = new ArrayList<>();
        if (board == null || layout == null || !layout.projects()) return placed;
        Map<String, float[]> disc = discPlacement(board);
        float glyph = Math.max(10f, 17f * fit.scale());
        float h = lineHeight() + 4f;
        float gap = glyph * 0.5f + 6f;
        // Every marker first, so a label can keep off the others' glyphs.
        List<float[]> local = new ArrayList<>();
        List<float[]> decks = new ArrayList<>();
        List<Rect> markers = new ArrayList<>();
        for (HqActionItem item : board.items()) {
            float[] l = disc.get(item.id());
            float[] deck = discPoint(layout, l[0], l[1]);
            float[] m = layout.project(deck[0], deck[1], deck[2]);
            float half = glyph * 0.5f + 2f;
            local.add(l);
            decks.add(deck);
            markers.add(new Rect(fit.x(m[0]) - half, fit.y(m[1]) - half, half * 2f, half * 2f));
        }
        List<Rect> blocked = new ArrayList<>(keepClear(layout, fit));
        blocked.addAll(markers);
        for (int i = 0; i < board.items().size(); i++) {
            HqActionItem item = board.items().get(i);
            float[] deck = decks.get(i);
            Rect own = markers.get(i);
            float mx = own.x() + own.width() * 0.5f, my = own.y() + own.height() * 0.5f;
            float[] f = layout.project(deck[0], CommandDeckLayout.TABLE_TOP, deck[2]);
            String text = labelText(item);
            float w = textWidth(text) + 10f;
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
            placed.add(new Placed(item, deck, mx, my, fit.x(f[0]), fit.y(f[1]), glyph, text, label));
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
     * item; {@code board} may be null or empty, which leaves the bare scanning disc.
     */
    void draw(CanvasContext context, CommandDeckLayout layout, CommandDeckLayout.Fit fit, HqActionBoard board,
              String selectedId, String hoveredId, float time) {
        if (layout == null || !layout.projects()) return;
        float flicker = 0.93f + 0.07f * (float) Math.sin(time * 7.3) * (float) Math.sin(time * 2.1 + 0.4);
        disc(context, layout, fit, time, flicker);
        List<Placed> placed = place(layout, fit, board);
        observe(placed, time);
        // Back to front: the far markers first, so near stems and labels sit over them.
        placed.sort((p, q) -> Float.compare(q.deck()[2], p.deck()[2]));
        for (Placed p : placed) stem(context, layout, fit, p, p.item().id().equals(selectedId), time, flicker);
        for (Placed p : placed) {
            String id = p.item().id();
            marker(context, p, id.equals(selectedId), id.equals(hoveredId), time, flicker);
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

    private void disc(CanvasContext context, CommandDeckLayout layout, CommandDeckLayout.Fit fit, float time,
                      float flicker) {
        float[][] rim = new float[SEGMENTS + 1][];
        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = Math.PI * 2 * i / SEGMENTS;
            rim[i] = screen(layout, fit, RADIUS * (float) Math.cos(angle), RADIUS * (float) Math.sin(angle));
        }
        float[] centre = screen(layout, fit, 0f, 0f);
        // The emitter: a small ring on the table and a faint cone up to the disc's sides.
        float[] tc = layout.tableCentre();
        float[] emitter = table(layout, fit, tc[0], tc[2]);
        for (int i = 0; i < 24; i++) {
            double a0 = Math.PI * 2 * i / 24, a1 = Math.PI * 2 * (i + 1) / 24;
            float[] p0 = table(layout, fit, tc[0] + 0.16f * (float) Math.cos(a0), tc[2] + 0.16f * (float) Math.sin(a0));
            float[] p1 = table(layout, fit, tc[0] + 0.16f * (float) Math.cos(a1), tc[2] + 0.16f * (float) Math.sin(a1));
            context.line(p0[0], p0[1], p1[0], p1[1], alpha(HOLO, 120 * flicker), 1.2f);
        }
        for (int i : new int[]{0, SEGMENTS / 2, SEGMENTS * 3 / 4}) {
            context.line(emitter[0], emitter[1], rim[i][0], rim[i][1], alpha(HOLO_DEEP, 34 * flicker), 1f);
        }
        // Faint body of the disc.
        for (int i = 0; i < SEGMENTS; i++) {
            context.fillQuad(centre[0], centre[1], rim[i][0], rim[i][1], rim[i + 1][0], rim[i + 1][1],
                    rim[i + 1][0], rim[i + 1][1], alpha(HOLO_DEEP, 20 * flicker));
        }
        // Range rings, the inner two dimmer.
        for (float fraction : new float[]{0.33f, 0.66f}) {
            float[] prev = null;
            for (int i = 0; i <= SEGMENTS; i++) {
                double angle = Math.PI * 2 * i / SEGMENTS;
                float[] p = screen(layout, fit, RADIUS * fraction * (float) Math.cos(angle),
                        RADIUS * fraction * (float) Math.sin(angle));
                if (prev != null) context.line(prev[0], prev[1], p[0], p[1], alpha(HOLO, 58 * flicker), 1f);
                prev = p;
            }
        }
        for (int i = 0; i < SEGMENTS; i++) {
            context.line(rim[i][0], rim[i][1], rim[i + 1][0], rim[i + 1][1], alpha(HOLO, 150 * flicker), 1.4f);
        }
        // Rim ticks every ten degrees, longer at the quarters.
        for (int i = 0; i < 36; i++) {
            double angle = Math.PI * 2 * i / 36;
            float inner = i % 9 == 0 ? 0.9f : 0.96f;
            float[] p0 = screen(layout, fit, RADIUS * inner * (float) Math.cos(angle), RADIUS * inner * (float) Math.sin(angle));
            float[] p1 = screen(layout, fit, RADIUS * (float) Math.cos(angle), RADIUS * (float) Math.sin(angle));
            context.line(p0[0], p0[1], p1[0], p1[1], alpha(HOLO, (i % 9 == 0 ? 150 : 80) * flicker), 1f);
        }
        // Cross hairs through the core.
        for (int i = 0; i < 4; i++) {
            float[] a = rim[i * SEGMENTS / 4];
            float[] b = rim[(i * SEGMENTS / 4 + SEGMENTS / 2) % SEGMENTS];
            if (i < 2) context.line(a[0], a[1], b[0], b[1], alpha(HOLO, 34 * flicker), 1f);
        }
        // The scan sweep: a leading spoke over a fading wedge.
        double lead = Math.PI * 2 * fraction(time / SWEEP_SECONDS);
        int wedge = 14;
        for (int k = wedge; k >= 1; k--) {
            double a0 = lead - k * 0.04, a1 = lead - (k - 1) * 0.04;
            float[] p0 = screen(layout, fit, RADIUS * (float) Math.cos(a0), RADIUS * (float) Math.sin(a0));
            float[] p1 = screen(layout, fit, RADIUS * (float) Math.cos(a1), RADIUS * (float) Math.sin(a1));
            float fade = 1f - (k - 1) / (float) wedge;
            context.fillQuad(centre[0], centre[1], p0[0], p0[1], p1[0], p1[1], p1[0], p1[1],
                    alpha(HOLO, 46 * fade * fade * flicker));
        }
        float[] tip = screen(layout, fit, RADIUS * (float) Math.cos(lead), RADIUS * (float) Math.sin(lead));
        context.line(centre[0], centre[1], tip[0], tip[1], alpha(HOLO, 190 * flicker), 1.6f);
        float core = 3f * fit.scale() + 1f;
        context.fillRect(centre[0] - core, centre[1] - core, core * 2f, core * 2f, alpha(HOLO, 170 * flicker));
    }

    private void stem(CanvasContext context, CommandDeckLayout layout, CommandDeckLayout.Fit fit, Placed p,
                      boolean selected, float time, float flicker) {
        float rise = rise(p.item().id(), time);
        float my = p.fy() + (p.my() - p.fy()) * rise;
        Color tone = tone(p.item(), time);
        if (selected) {
            float beat = 0.75f + 0.25f * (float) Math.sin(time * 4.0);
            context.line(p.fx(), p.fy(), p.mx(), my, alpha(tone, 70 * beat), 7f * fit.scale() + 2f);
            context.line(p.fx(), p.fy(), p.mx(), my, alpha(tone, 230 * beat), 2f);
            // A footprint ring where the beam meets the table.
            float[] prev = null;
            for (int i = 0; i <= 20; i++) {
                double angle = Math.PI * 2 * i / 20;
                float[] q = table(layout, fit, p.deck()[0] + 0.09f * (float) Math.cos(angle),
                        p.deck()[2] + 0.09f * (float) Math.sin(angle));
                if (prev != null) context.line(prev[0], prev[1], q[0], q[1], alpha(tone, 200 * beat), 1.4f);
                prev = q;
            }
        } else {
            context.line(p.fx(), p.fy(), p.mx(), my, alpha(tone, 95 * flicker * rise), 1f);
        }
        float dot = 1.5f * fit.scale() + 0.6f;
        context.fillRect(p.fx() - dot, p.fy() - dot * 0.6f, dot * 2f, dot * 1.2f, alpha(tone, 140 * flicker));
    }

    private void marker(CanvasContext context, Placed p, boolean selected, boolean hovered, float time,
                        float flicker) {
        HqActionItem item = p.item();
        float rise = rise(item.id(), time);
        float my = p.fy() + (p.my() - p.fy()) * rise;
        Color tone = tone(item, time);
        float bright = selected ? 1f : hovered ? 0.95f : 0.72f;
        float size = p.glyph() * (hovered || selected ? 1.18f : 1f);
        if (urgent(item)) {
            // An expanding ring under an urgent obligation.
            float wave = fraction(time / 1.4f);
            float r = size * (0.6f + 1.1f * wave);
            ring(context, p.mx(), my, r, alpha(tone, 170 * (1f - wave) * rise), 1.3f);
        }
        if (selected || hovered) ring(context, p.mx(), my, size * 0.95f, alpha(tone, (selected ? 200 : 120) * rise), 1f);
        glyph(context, item.kind(), p.mx(), my, size, alpha(tone, 255 * bright * flicker * rise));

        Rect l = p.label();
        float labelRise = clamp01(rise * 1.6f - 0.6f);
        if (labelRise <= 0f) return;
        // A leader from the marker to the label's near edge.
        float lx = l.x() > p.mx() ? l.x() : l.x() + l.width();
        float ly = Math.max(l.y() + 2f, Math.min(l.y() + l.height() - 2f, my));
        if (l.y() + l.height() < my - 1f || l.y() > my + 1f || Math.abs(lx - p.mx()) > size) {
            context.line(p.mx(), my, lx, ly, alpha(tone, 90 * labelRise * bright), 1f);
        }
        context.fillRect(l.x(), l.y(), l.width(), l.height(), alpha(PLATE, (selected ? 150 : 115) * labelRise));
        context.line(l.x(), l.y(), l.x(), l.y() + l.height(), alpha(tone, 200 * labelRise * bright), 2f);
        if (font != null) {
            context.text(font, p.text(), l.x() + 6f, l.y() + 2f, alpha(selected || hovered
                    ? Color.WHITE : tone, 255 * (selected ? 1f : hovered ? 0.95f : 0.82f) * labelRise));
        }
    }

    private void glyph(CanvasContext context, Kind kind, float cx, float cy, float size, Color tint) {
        SvgAsset asset = asset(kind);
        if (asset != null) {
            context.svg(asset, cx - size * 0.5f, cy - size * 0.5f, size, size, tint);
            return;
        }
        // Fallback outline when the SVG glyphs are unavailable (a headless caller with no asset source).
        float h = size * 0.5f;
        switch (kind) {
            case STORY -> outline(context, tint, cx, cy - h, cx + h, cy, cx, cy + h, cx - h, cy);
            case OBLIGATION -> outline(context, tint, cx, cy - h, cx + h, cy + h * 0.8f, cx - h, cy + h * 0.8f);
            case LETTER -> outline(context, tint, cx - h, cy - h * 0.75f, cx + h, cy - h * 0.75f,
                    cx + h, cy + h * 0.75f, cx - h, cy + h * 0.75f);
            case SIDE_JOB -> ring(context, cx, cy, h * 0.8f, tint, 1.6f);
        }
    }

    private SvgAsset asset(Kind kind) {
        if (glyphsFailed) return null;
        if (glyphs == null) {
            try {
                Map<Kind, SvgAsset> loaded = new EnumMap<>(Kind.class);
                for (Kind k : Kind.values()) {
                    loaded.put(k, SvgAsset.load("graphics/ui/hq-command-deck/holo-"
                            + k.name().toLowerCase().replace('_', '-') + ".svg"));
                }
                glyphs = loaded;
            } catch (RuntimeException unavailable) {
                glyphsFailed = true;
                return null;
            }
        }
        return glyphs.get(kind);
    }

    private static void outline(CanvasContext context, Color color, float... xy) {
        for (int i = 0; i < xy.length; i += 2) {
            int j = (i + 2) % xy.length;
            context.line(xy[i], xy[i + 1], xy[j], xy[j + 1], color, 1.6f);
        }
    }

    private static void ring(CanvasContext context, float cx, float cy, float r, Color color, float width) {
        if (color.getAlpha() == 0 || r <= 0f) return;
        int n = 24;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            context.line(cx + r * (float) Math.cos(a0), cy + r * (float) Math.sin(a0),
                    cx + r * (float) Math.cos(a1), cy + r * (float) Math.sin(a1), color, width);
        }
    }

    /** The item's light: holo green, or a warm amber-to-red pulse for an obligation falling due. */
    private static Color tone(HqActionItem item, float time) {
        if (!urgent(item)) return HOLO;
        float beat = 0.5f + 0.5f * (float) Math.sin(time * Math.PI * 2 / 1.4f);
        return mix(AMBER, ALARM, beat);
    }

    private static float[] screen(CommandDeckLayout layout, CommandDeckLayout.Fit fit, float a, float b) {
        float[] deck = discPoint(layout, a, b);
        float[] uv = layout.project(deck[0], deck[1], deck[2]);
        return new float[]{fit.x(uv[0]), fit.y(uv[1])};
    }

    private static float[] table(CommandDeckLayout layout, CommandDeckLayout.Fit fit, float x, float z) {
        float[] uv = layout.project(x, CommandDeckLayout.TABLE_TOP, z);
        return new float[]{fit.x(uv[0]), fit.y(uv[1])};
    }

    private static Color mix(Color a, Color b, float t) {
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    private static Color alpha(Color color, float alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, Math.round(alpha))));
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static float fraction(float value) { return value - (float) Math.floor(value); }
}
