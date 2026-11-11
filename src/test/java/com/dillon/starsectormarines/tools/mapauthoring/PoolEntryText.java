package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan;
import com.dillon.starsectormarines.battle.world.gen.layout.Structure;
import com.dillon.starsectormarines.battle.world.gen.pool.MapPatch;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPool;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.world.gen.hull.HullSitePlan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A story map's pool file as the pool files are written ({@code story-map-pools.md}, version 3): two-space indent,
 * every object and array laid open, keys in the reader's order ({@code version, map, home, entry}; an entry's
 * {@code seed, note, patch}; a patch's {@code pin, ruin, lift, bores, holdouts, place, hull}), only the ops a patch has.
 * Writing a file's own entry back gives the file byte for byte, so a save changes only what the page edited.
 */
public final class PoolEntryText {

    private PoolEntryText() { }

    /** The whole pool file of {@code map} owning {@code entry} on {@code home}. */
    public static String file(StoryMap map, SurfacePalette home, StoryMapPool.Entry entry) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", StoryMapPool.VERSION);
        root.put("map", map.id);
        root.put("home", home.name());
        root.put("entry", entry(entry));
        StringBuilder out = new StringBuilder();
        write(out, root, 0);
        return out.append('\n').toString();
    }

    /** The patch alone, on one line, for a tool's reply. */
    public static String patch(MapPatch p) {
        StringBuilder out = new StringBuilder();
        line(out, patchTree(p));
        return out.toString();
    }

    private static Map<String, Object> entry(StoryMapPool.Entry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seed", e.seed());
        if (!e.note().isEmpty()) m.put("note", e.note());
        if (!e.patch().isEmpty()) m.put("patch", patchTree(e.patch()));
        return m;
    }

    private static Map<String, Object> patchTree(MapPatch p) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (!p.pins().isEmpty()) {
            List<Object> pins = new ArrayList<>();
            for (MapPatch.Pin pin : p.pins()) pins.add(object("place", pin.place(), "x", pin.x(), "y", pin.y()));
            m.put("pin", pins);
        }
        if (!p.ruin().isEmpty()) {
            List<Object> strokes = new ArrayList<>();
            for (DamagePlan.Stroke s : p.ruin()) {
                Map<String, Object> stroke = object("radius", s.radius());
                if (s.mode() != DamagePlan.Mode.RUIN) stroke.put("mode", s.mode().name());
                List<Object> points = new ArrayList<>();
                for (DamagePlan.Point pt : s.points()) points.add(List.of(pt.x(), pt.y()));
                stroke.put("points", points);
                strokes.add(stroke);
            }
            m.put("ruin", strokes);
        }
        if (p.lift() != null) m.put("lift", List.of(p.lift().x(), p.lift().y()));
        if (!p.bores().isEmpty()) {
            List<Object> bores = new ArrayList<>();
            for (MapPatch.Bore b : p.bores()) {
                Map<String, Object> bore = object("x", b.x(), "y", b.y());
                if (b.mother()) bore.put("mother", true);
                bores.add(bore);
            }
            m.put("bores", bores);
        }
        if (!p.holdouts().isEmpty()) {
            List<Object> cells = new ArrayList<>();
            for (MapPatch.Cell c : p.holdouts()) cells.add(List.of(c.x(), c.y()));
            m.put("holdouts", cells);
        }
        if (!p.place().isEmpty()) {
            List<Object> placed = new ArrayList<>();
            for (Structure s : p.place()) placed.add(object("name", s.name(), "kind", s.kind().name(), "x", s.x(), "y", s.y(), "turns", s.turns()));
            m.put("place", placed);
        }
        if (p.hull() != null) {
            HullSitePlan h = p.hull();
            Map<String, Object> hull = object("x", h.x(), "y", h.y(), "heading", heading(h.heading()), "trench", h.trench());
            if (!h.stakes().isEmpty()) {
                List<Object> stakes = new ArrayList<>();
                for (HullSitePlan.StakeAt s : h.stakes()) {
                    stakes.add(object("x", s.x(), "y", s.y(), "stage", s.stage().name().toLowerCase(Locale.ROOT)));
                }
                hull.put("stakes", stakes);
            }
            if (h.yards() > 0) hull.put("yards", h.yards());
            if (!h.dress().isEmpty()) {
                List<Object> dress = new ArrayList<>();
                for (HullSitePlan.Dress d : h.dress()) {
                    Map<String, Object> piece = object("kind", d.kind().name().toLowerCase(Locale.ROOT), "x", d.x(), "y", d.y());
                    if (d.length() != 0) piece.put("length", d.length());
                    if (d.turns() != 0) piece.put("turns", d.turns());
                    dress.add(piece);
                }
                hull.put("dress", dress);
            }
            m.put("hull", hull);
        }
        return m;
    }

    private static String heading(Direction d) {
        return switch (d) {
            case N -> "north";
            case E -> "east";
            case S -> "south";
            default -> "west";
        };
    }

    private static Map<String, Object> object(Object... keysAndValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) m.put((String) keysAndValues[i], keysAndValues[i + 1]);
        return m;
    }

    /** {@code value} laid open at {@code depth}, the way the pool files are written. */
    private static void write(StringBuilder out, Object value, int depth) {
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) { out.append("{}"); return; }
            out.append("{\n");
            int k = 0;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                indent(out, depth + 1).append(quote((String) e.getKey())).append(": ");
                write(out, e.getValue(), depth + 1);
                out.append(++k < map.size() ? ",\n" : "\n");
            }
            indent(out, depth).append('}');
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) { out.append("[]"); return; }
            out.append("[\n");
            for (int k = 0; k < list.size(); k++) {
                indent(out, depth + 1);
                write(out, list.get(k), depth + 1);
                out.append(k + 1 < list.size() ? ",\n" : "\n");
            }
            indent(out, depth).append(']');
        } else {
            scalar(out, value);
        }
    }

    /** {@code value} on one line. */
    private static void line(StringBuilder out, Object value) {
        if (value instanceof Map<?, ?> map) {
            out.append('{');
            int k = 0;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (k++ > 0) out.append(", ");
                out.append(quote((String) e.getKey())).append(": ");
                line(out, e.getValue());
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int k = 0; k < list.size(); k++) {
                if (k > 0) out.append(", ");
                line(out, list.get(k));
            }
            out.append(']');
        } else {
            scalar(out, value);
        }
    }

    private static void scalar(StringBuilder out, Object value) {
        if (value instanceof String s) out.append(quote(s));
        else if (value instanceof Double d) out.append(number(d));
        else out.append(value);
    }

    /** A JSON string: quotes, backslashes and control characters escaped, everything else as written. */
    private static String quote(String s) {
        StringBuilder q = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') q.append("\\\"");
            else if (c == '\\') q.append("\\\\");
            else if (c == '\n') q.append("\\n");
            else if (c == '\t') q.append("\\t");
            else if (c == '\r') q.append("\\r");
            else if (c < 0x20) q.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
            else q.append(c);
        }
        return q.append('"').toString();
    }

    private static StringBuilder indent(StringBuilder out, int depth) {
        for (int i = 0; i < depth; i++) out.append("  ");
        return out;
    }

    private static String number(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e9) return Long.toString((long) v);
        String s = String.format(Locale.ROOT, "%.3f", v);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
