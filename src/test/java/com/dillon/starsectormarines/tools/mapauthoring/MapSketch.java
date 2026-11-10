package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * What the Maps page has changed on an opened map: the drops, rises, roads, named places and settlements an author
 * drew over it. Immutable, so one edit is one undo step. The owned-map document ({@code owned-maps}) is the saved form;
 * this is the editor's working set of edits, and its JSON is only how the headless tool is handed edits.
 *
 * <p>Coordinates are map cells, y up, as the battle's.
 */
public record MapSketch(String base, List<Drop> drops, List<Rise> rises, List<Road> roads, List<Place> places,
                        List<Settlement> settlements) {

    public record Pt(float x, float y) {
        public Pt {
            if (!Float.isFinite(x) || !Float.isFinite(y)) throw new IllegalArgumentException("a point needs finite cells");
        }
        float distance(Pt o) { return (float) Math.hypot(x - o.x, y - o.y); }
    }

    /** How a crossing spans a drop: a timber deck over it, or the cut left unbroken there as a land bridge. */
    public enum CrossingKind { BRIDGE, LAND_BRIDGE }

    /**
     * A way over a drop, {@code at} the share of the drop's length (0 its first point, 1 its last), {@code width}
     * cells wide along it.
     */
    public record Crossing(float at, int width, CrossingKind kind) {
        public Crossing {
            if (!(at >= 0f && at <= 1f)) throw new IllegalArgumentException("a crossing lies along its drop, 0 to 1");
            if (width < 2 || width > 24) throw new IllegalArgumentException("a crossing is 2 to 24 cells wide");
            if (kind == null) kind = CrossingKind.BRIDGE;
        }
    }

    /**
     * A natural drop cut along {@code path}, {@code width} cells across; its floor {@code depths} metres down at each
     * point of the path ({@code NaturalVoid.DepthMark}), its walls falling in {@code terraces} ledges, the floor
     * last; {@code crossings} where the map says it can be crossed.
     */
    public record Drop(String id, List<Pt> path, float width, List<Float> depths, int terraces,
                       List<Crossing> crossings) {
        public Drop {
            path = List.copyOf(path);
            depths = List.copyOf(depths);
            crossings = crossings == null ? List.of() : List.copyOf(crossings);
            requireId(id);
            if (path.size() < 2) throw new IllegalArgumentException("a drop runs along at least two points");
            if (depths.size() != path.size()) throw new IllegalArgumentException("a drop has a depth at every point");
            for (float d : depths) if (!(d > 0f && d <= 200f)) throw new IllegalArgumentException("a drop is 0 to 200 m deep");
            if (!(width >= 2f && width <= 40f)) throw new IllegalArgumentException("a drop is 2 to 40 cells across");
            if (terraces < 1 || terraces > 8) throw new IllegalArgumentException("a drop falls in 1 to 8 ledges");
        }
        public Drop withPath(List<Pt> p, List<Float> d) { return new Drop(id, p, width, d, terraces, crossings); }
        public Drop withCrossings(List<Crossing> c) { return new Drop(id, path, width, depths, terraces, c); }
    }

    /**
     * High ground standing up along {@code path}, {@code width} cells across; its top {@code heights} metres up at
     * each point, climbing in {@code terraces} terraces ({@code NaturalRise.bank}).
     */
    public record Rise(String id, List<Pt> path, float width, List<Float> heights, int terraces) {
        public Rise {
            path = List.copyOf(path);
            heights = List.copyOf(heights);
            requireId(id);
            if (path.size() < 2) throw new IllegalArgumentException("a rise runs along at least two points");
            if (heights.size() != path.size()) throw new IllegalArgumentException("a rise has a height at every point");
            for (float h : heights) if (!(h > 0f && h <= 100f)) throw new IllegalArgumentException("a rise is 0 to 100 m high");
            if (!(width >= 2f && width <= 60f)) throw new IllegalArgumentException("a rise is 2 to 60 cells across");
            if (terraces < 2 || terraces > 10) throw new IllegalArgumentException("a rise climbs in 2 to 10 terraces");
        }
    }

    public record Road(String id, List<Pt> path, float width, RoadPlan.Material material) {
        public Road {
            path = List.copyOf(path);
            requireId(id);
            if (path.size() < 2) throw new IllegalArgumentException("a road runs along at least two points");
            if (!(width >= 2f && width <= 32f)) throw new IllegalArgumentException("a road is 2 to 32 cells wide");
            if (material == null) material = RoadPlan.Material.GRAVEL;
        }
    }

    /** What a named place is to the mission: where the company lands, a mission's named site, or a plain landmark. */
    public enum Role { LANDING, SITE, LANDMARK }

    public record Place(String id, String label, int x, int y, Role role) {
        public Place {
            requireId(id);
            if (label == null || label.isBlank()) label = id;
            if (role == null) role = Role.SITE;
        }
    }

    /**
     * What a settlement is built as, by program and role: where it stands, never its building style. The style is
     * the world's ({@code Habitat}): an open town on a garden world is a sealed one on a toxic world.
     */
    public enum Program {
        /** A town: houses, streets and its civic places. */
        TOWN,
        /** A walled compound: a works, a garrison or a depot. */
        COMPOUND,
        /** A town sheltered on terraces in a great crevasse's walls ({@code voids.md}, the crevasse town). */
        CREVASSE_TOWN
    }

    /** A settlement's footprint, {@code x}, {@code y} its bottom-left cell. */
    public record Settlement(String id, int x, int y, int w, int h, Program program, String role) {
        public Settlement {
            requireId(id);
            if (w < 4 || h < 4) throw new IllegalArgumentException("a settlement is at least 4 by 4 cells");
            if (program == null) program = Program.TOWN;
            if (role == null) role = "";
        }
        boolean contains(int cx, int cy) { return cx >= x && cy >= y && cx < x + w && cy < y + h; }
    }

    public MapSketch {
        drops = List.copyOf(drops);
        rises = List.copyOf(rises);
        roads = List.copyOf(roads);
        places = List.copyOf(places);
        settlements = List.copyOf(settlements);
        Set<String> ids = new HashSet<>();
        for (Drop d : drops) unique(ids, d.id());
        for (Rise r : rises) unique(ids, r.id());
        for (Road r : roads) unique(ids, r.id());
        for (Place p : places) unique(ids, p.id());
        for (Settlement s : settlements) unique(ids, s.id());
    }

    public static MapSketch empty(String base) {
        return new MapSketch(base, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static void requireId(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("every edit has an id");
    }

    private static void unique(Set<String> ids, String id) {
        if (!ids.add(id)) throw new IllegalArgumentException("two edits are both called " + id);
    }

    /** The landing zone: the first place whose role is {@link Role#LANDING}, or null. */
    public Place landing() {
        for (Place p : places) if (p.role() == Role.LANDING) return p;
        return null;
    }

    /** A fresh id with this prefix, unused by any edit here. */
    public String freshId(String prefix) {
        Set<String> ids = new HashSet<>();
        drops.forEach(d -> ids.add(d.id())); rises.forEach(r -> ids.add(r.id())); roads.forEach(r -> ids.add(r.id()));
        places.forEach(p -> ids.add(p.id())); settlements.forEach(s -> ids.add(s.id()));
        for (int i = 1; ; i++) if (!ids.contains(prefix + "-" + i)) return prefix + "-" + i;
    }

    public MapSketch withDrops(UnaryOperator<List<Drop>> f) { return new MapSketch(base, f.apply(new ArrayList<>(drops)), rises, roads, places, settlements); }
    public MapSketch withRises(UnaryOperator<List<Rise>> f) { return new MapSketch(base, drops, f.apply(new ArrayList<>(rises)), roads, places, settlements); }
    public MapSketch withRoads(UnaryOperator<List<Road>> f) { return new MapSketch(base, drops, rises, f.apply(new ArrayList<>(roads)), places, settlements); }
    public MapSketch withPlaces(UnaryOperator<List<Place>> f) { return new MapSketch(base, drops, rises, roads, f.apply(new ArrayList<>(places)), settlements); }
    public MapSketch withSettlements(UnaryOperator<List<Settlement>> f) { return new MapSketch(base, drops, rises, roads, places, f.apply(new ArrayList<>(settlements))); }

    /** Removes the edit called {@code id}, wherever it is. */
    public MapSketch without(String id) {
        return new MapSketch(base, drops.stream().filter(d -> !d.id().equals(id)).toList(),
                rises.stream().filter(r -> !r.id().equals(id)).toList(), roads.stream().filter(r -> !r.id().equals(id)).toList(),
                places.stream().filter(p -> !p.id().equals(id)).toList(),
                settlements.stream().filter(s -> !s.id().equals(id)).toList());
    }

    // ------------------------------------------------------------------ JSON (the headless tool's edits)

    public JSONObject json() throws JSONException {
        JSONObject o = new JSONObject().put("base", base);
        JSONArray a = new JSONArray();
        for (Drop d : drops) {
            JSONArray c = new JSONArray();
            for (Crossing x : d.crossings()) c.put(new JSONObject().put("at", x.at()).put("width", x.width()).put("kind", x.kind().name()));
            a.put(new JSONObject().put("id", d.id()).put("path", points(d.path())).put("width", d.width())
                    .put("depths", new JSONArray(d.depths())).put("terraces", d.terraces()).put("crossings", c));
        }
        o.put("drops", a);
        a = new JSONArray();
        for (Rise r : rises) a.put(new JSONObject().put("id", r.id()).put("path", points(r.path())).put("width", r.width())
                .put("heights", new JSONArray(r.heights())).put("terraces", r.terraces()));
        o.put("rises", a);
        a = new JSONArray();
        for (Road r : roads) a.put(new JSONObject().put("id", r.id()).put("path", points(r.path())).put("width", r.width())
                .put("material", r.material().name()));
        o.put("roads", a);
        a = new JSONArray();
        for (Place p : places) a.put(new JSONObject().put("id", p.id()).put("label", p.label()).put("x", p.x()).put("y", p.y())
                .put("role", p.role().name()));
        o.put("places", a);
        a = new JSONArray();
        for (Settlement s : settlements) a.put(new JSONObject().put("id", s.id()).put("x", s.x()).put("y", s.y())
                .put("w", s.w()).put("h", s.h()).put("program", s.program().name()).put("role", s.role()));
        o.put("settlements", a);
        return o;
    }

    public static MapSketch read(JSONObject o) throws JSONException {
        List<Drop> drops = new ArrayList<>();
        JSONArray a = o.optJSONArray("drops");
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject d = a.getJSONObject(i);
            List<Pt> path = points(d.getJSONArray("path"));
            List<Crossing> crossings = new ArrayList<>();
            JSONArray c = d.optJSONArray("crossings");
            for (int k = 0; c != null && k < c.length(); k++) {
                JSONObject x = c.getJSONObject(k);
                crossings.add(new Crossing((float) x.getDouble("at"), x.optInt("width", 4),
                        CrossingKind.valueOf(x.optString("kind", "BRIDGE").toUpperCase(Locale.ROOT))));
            }
            drops.add(new Drop(d.getString("id"), path, (float) d.optDouble("width", 8), floats(d.optJSONArray("depths"), path.size(), 40f),
                    d.optInt("terraces", 3), crossings));
        }
        List<Rise> rises = new ArrayList<>();
        a = o.optJSONArray("rises");
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject r = a.getJSONObject(i);
            List<Pt> path = points(r.getJSONArray("path"));
            rises.add(new Rise(r.getString("id"), path, (float) r.optDouble("width", 14), floats(r.optJSONArray("heights"), path.size(), 24f),
                    r.optInt("terraces", 6)));
        }
        List<Road> roads = new ArrayList<>();
        a = o.optJSONArray("roads");
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject r = a.getJSONObject(i);
            roads.add(new Road(r.getString("id"), points(r.getJSONArray("path")), (float) r.optDouble("width", 4),
                    RoadPlan.Material.valueOf(r.optString("material", "GRAVEL").toUpperCase(Locale.ROOT))));
        }
        List<Place> places = new ArrayList<>();
        a = o.optJSONArray("places");
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject p = a.getJSONObject(i);
            places.add(new Place(p.getString("id"), p.optString("label", p.getString("id")), p.getInt("x"), p.getInt("y"),
                    Role.valueOf(p.optString("role", "SITE").toUpperCase(Locale.ROOT))));
        }
        List<Settlement> settlements = new ArrayList<>();
        a = o.optJSONArray("settlements");
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject s = a.getJSONObject(i);
            settlements.add(new Settlement(s.getString("id"), s.getInt("x"), s.getInt("y"), s.getInt("w"), s.getInt("h"),
                    Program.valueOf(s.optString("program", "TOWN").toUpperCase(Locale.ROOT)), s.optString("role", "")));
        }
        return new MapSketch(o.optString("base", ""), drops, rises, roads, places, settlements);
    }

    private static JSONArray points(List<Pt> path) throws JSONException {
        JSONArray a = new JSONArray();
        for (Pt p : path) a.put(new JSONArray().put(p.x()).put(p.y()));
        return a;
    }

    private static List<Pt> points(JSONArray a) throws JSONException {
        List<Pt> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) out.add(new Pt((float) a.getJSONArray(i).getDouble(0), (float) a.getJSONArray(i).getDouble(1)));
        return out;
    }

    /** One value per point: as given, or {@code fallback} everywhere when absent; a single value fills every point. */
    private static List<Float> floats(JSONArray a, int n, float fallback) throws JSONException {
        List<Float> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (a == null || a.length() == 0) out.add(fallback);
            else out.add((float) a.getDouble(Math.min(i, a.length() - 1)));
        }
        return out;
    }
}
