package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan;
import com.dillon.starsectormarines.battle.world.gen.layout.Structure;
import com.dillon.starsectormarines.battle.world.gen.pool.MapPatch;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPool;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * A pool file's text with one entry rewritten ({@code story-map-pools.md}): the entry is written in the file's own
 * compact form, keys in the reader's order, and spliced over the old entry's text, so every other entry, the
 * {@code candidates} and the file's hand formatting stay as they were. {@link MapPatch#read} reads what
 * {@link #entry} writes back equal.
 */
public final class PoolEntryText {

    private PoolEntryText() { }

    /** The entry as the pool files write one: seed, note, story, patch. */
    public static String entry(StoryMapPool.Entry e) {
        StringJoiner j = new StringJoiner(", ", "{", "}");
        j.add("\"seed\": " + e.seed());
        if (!e.note().isEmpty()) j.add("\"note\": " + JSONObject.quote(e.note()));
        if (e.story()) j.add("\"story\": true");
        if (!e.patch().isEmpty()) j.add("\"patch\": " + patch(e.patch()));
        return j.toString();
    }

    /** A patch in the pool's form; only the ops it has. */
    public static String patch(MapPatch p) {
        StringJoiner j = new StringJoiner(", ", "{", "}");
        if (!p.pins().isEmpty()) {
            List<String> pins = new ArrayList<>();
            for (MapPatch.Pin pin : p.pins()) {
                pins.add("{\"place\": " + JSONObject.quote(pin.place()) + ", \"x\": " + pin.x() + ", \"y\": " + pin.y() + "}");
            }
            j.add("\"pin\": [" + String.join(", ", pins) + "]");
        }
        if (!p.ruin().isEmpty()) {
            List<String> strokes = new ArrayList<>();
            for (DamagePlan.Stroke s : p.ruin()) {
                List<String> points = new ArrayList<>();
                for (DamagePlan.Point pt : s.points()) points.add("[" + number(pt.x()) + ", " + number(pt.y()) + "]");
                strokes.add("{\"radius\": " + number(s.radius())
                        + (s.mode() == DamagePlan.Mode.RUIN ? "" : ", \"mode\": \"" + s.mode().name() + "\"")
                        + ", \"points\": [" + String.join(", ", points) + "]}");
            }
            j.add("\"ruin\": [" + String.join(", ", strokes) + "]");
        }
        if (p.lift() != null) j.add("\"lift\": [" + p.lift().x() + ", " + p.lift().y() + "]");
        if (!p.bores().isEmpty()) {
            List<String> bores = new ArrayList<>();
            for (MapPatch.Bore b : p.bores()) {
                bores.add("{\"x\": " + b.x() + ", \"y\": " + b.y() + (b.mother() ? ", \"mother\": true" : "") + "}");
            }
            j.add("\"bores\": [" + String.join(", ", bores) + "]");
        }
        if (!p.holdouts().isEmpty()) {
            List<String> cells = new ArrayList<>();
            for (MapPatch.Cell c : p.holdouts()) cells.add("[" + c.x() + ", " + c.y() + "]");
            j.add("\"holdouts\": [" + String.join(", ", cells) + "]");
        }
        if (!p.place().isEmpty()) {
            List<String> placed = new ArrayList<>();
            for (Structure s : p.place()) {
                placed.add("{\"name\": " + JSONObject.quote(s.name()) + ", \"kind\": \"" + s.kind().name() + "\", \"x\": "
                        + s.x() + ", \"y\": " + s.y() + ", \"turns\": " + s.turns() + "}");
            }
            j.add("\"place\": [" + String.join(", ", placed) + "]");
        }
        return j.toString();
    }

    private static String number(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e9) return Long.toString((long) v);
        String s = String.format(Locale.ROOT, "%.3f", v);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /**
     * {@code file} with the {@code index}th entry of {@code biome} under {@code biomes} replaced by {@code entry};
     * everything else byte for byte. Throws when the file has no such entry.
     */
    public static String replace(String file, SurfacePalette biome, int index, String entry) {
        int root = skipSpace(file, 0);
        int[] biomes = member(file, root, "biomes");
        if (biomes == null) throw new IllegalArgumentException("the pool file has no biomes");
        int[] list = member(file, biomes[0], biome.name());
        if (list == null || file.charAt(list[0]) != '[') throw new IllegalArgumentException("the pool file has no " + biome + " maps");
        int i = skipSpace(file, list[0] + 1);
        for (int k = 0; ; k++) {
            if (file.charAt(i) == ']') throw new IllegalArgumentException("the pool file has no " + biome + " entry " + index);
            int end = skipValue(file, i);
            if (k == index) return file.substring(0, i) + entry + file.substring(end);
            i = skipSpace(file, end);
            if (file.charAt(i) == ',') i = skipSpace(file, i + 1);
        }
    }

    /** The span {@code [start, end)} of {@code key}'s value in the object opening at {@code object}, or null. */
    private static int[] member(String s, int object, String key) {
        if (s.charAt(object) != '{') throw new IllegalArgumentException("not an object at " + object);
        int i = skipSpace(s, object + 1);
        while (s.charAt(i) != '}') {
            int keyEnd = skipValue(s, i);
            String name = s.substring(i + 1, keyEnd - 1);
            i = skipSpace(s, keyEnd);
            if (s.charAt(i) != ':') throw new IllegalArgumentException("expected ':' at " + i);
            int value = skipSpace(s, i + 1);
            int end = skipValue(s, value);
            if (name.equals(key)) return new int[]{value, end};
            i = skipSpace(s, end);
            if (s.charAt(i) == ',') i = skipSpace(s, i + 1);
        }
        return null;
    }

    /** The index just past the JSON value starting at {@code i}. */
    private static int skipValue(String s, int i) {
        char c = s.charAt(i);
        if (c == '"') {
            for (int k = i + 1; ; k++) {
                char d = s.charAt(k);
                if (d == '\\') k++;
                else if (d == '"') return k + 1;
            }
        }
        if (c == '{' || c == '[') {
            int depth = 0;
            for (int k = i; ; k++) {
                char d = s.charAt(k);
                if (d == '"') { k = skipValue(s, k) - 1; continue; }
                if (d == '{' || d == '[') depth++;
                else if (d == '}' || d == ']') { if (--depth == 0) return k + 1; }
            }
        }
        int k = i;
        while (k < s.length() && ",}] \t\r\n".indexOf(s.charAt(k)) < 0) k++;
        return k;
    }

    private static int skipSpace(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }
}
