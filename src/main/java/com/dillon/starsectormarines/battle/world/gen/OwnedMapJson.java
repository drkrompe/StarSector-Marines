package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Box;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Building;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Cell;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Depth;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Drop;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Ground;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Pad;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Prop;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Rise;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Settlement;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Site;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Wall;
import com.dillon.starsectormarines.battle.world.gen.OwnedMap.Work;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.OpeningOperationFacilityFiller.Frontage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes an {@link OwnedMap} document ({@code owned-maps.md}, The document). A cell is {@code [x, y]}, a box
 * {@code [x0, y0, x1, y1]}; enums are their constant names. Every list is optional and empty when absent, so a map
 * names only what it has. Writing then reading gives the same map, which is what the map editor relies on.
 */
public final class OwnedMapJson {

    private OwnedMapJson() { }

    /** The map a document describes; refuses a newer version or a malformed piece, naming it. */
    public static OwnedMap read(JSONObject o) throws JSONException {
        int version = o.optInt("version", 1);
        if (version > OwnedMap.VERSION) throw new IllegalArgumentException("owned map version " + version + " is newer than " + OwnedMap.VERSION);
        return new OwnedMap(o.getString("id"), o.getInt("width"), o.getInt("height"),
                text(o, "roads"), text(o, "roadGraphFrom"), text(o, "terrain"),
                cell(o.getJSONArray("origin")), cell(o.getJSONArray("objective")),
                list(o, "grounds", g -> new Ground(OwnedMap.GroundRole.valueOf(g.getString("role")),
                        box(g.getJSONArray("box")), g.optInt("radius", 0))),
                list(o, "pads", p -> new Pad(cell(p.getJSONArray("at")), p.getInt("halfWidth"), p.getInt("halfHeight"),
                        LandingPad.Approach.valueOf(p.getString("approach")),
                        LandingPad.Purpose.valueOf(p.getString("purpose")))),
                list(o, "sites", s -> new Site(s.getString("name"), TacticalNode.Kind.valueOf(s.getString("kind")),
                        text(s, "building"), s.has("at") ? cell(s.getJSONArray("at")) : null,
                        s.has("area") ? box(s.getJSONArray("area")) : null,
                        Faction.valueOf(s.getString("guard")), s.optInt("value", 0), s.optInt("garrison", 0))),
                list(o, "settlements", OwnedMapJson::settlement),
                list(o, "walls", w -> new Wall(cell(w.getJSONArray("from")), cell(w.getJSONArray("to")))),
                list(o, "props", p -> new Prop(cell(p.getJSONArray("at")), p.getString("id"))),
                list(o, "drops", OwnedMapJson::drop),
                list(o, "rises", r -> new Rise(r.getString("name"), OwnedMap.RiseRole.valueOf(r.getString("role")),
                        box(r.getJSONArray("box")), (float) r.optDouble("share", 1.0))),
                cells(o.optJSONArray("routes")));
    }

    private static Settlement settlement(JSONObject s) throws JSONException {
        return new Settlement(s.getString("name"), s.getString("program"),
                OwnedMap.SettlementRole.valueOf(s.getString("role")), box(s.getJSONArray("lot")),
                cells(s.optJSONArray("gates")),
                list(s, "buildings", OwnedMapJson::building),
                list(s, "works", w -> new Work(OwnedMap.WorkOp.valueOf(w.getString("op")),
                        w.has("box") ? box(w.getJSONArray("box")) : null,
                        w.has("at") ? cell(w.getJSONArray("at")) : null, text(w, "id"), w.optInt("turns", 0))),
                text(s, "terraceInto"));
    }

    private static Building building(JSONObject b) throws JSONException {
        OwnedMap.Form form = OwnedMap.Form.valueOf(b.getString("form"));
        return new Building(b.getString("name"), form, b.getString("plan"),
                b.has("at") ? cell(b.getJSONArray("at")) : null, b.optInt("turns", 0),
                b.has("box") ? box(b.getJSONArray("box")) : null,
                b.has("frontage") ? Frontage.valueOf(b.getString("frontage")) : null,
                b.optBoolean("opposed", false));
    }

    private static Drop drop(JSONObject d) throws JSONException {
        return new Drop(d.getString("name"), OwnedMap.DropRole.valueOf(d.getString("role")),
                cells(d.getJSONArray("path")), d.getInt("width"), cells(d.optJSONArray("crossings")),
                d.optInt("crossingWidth", 0),
                list(d, "depths", m -> new Depth(cell(m.getJSONArray("at")), (float) m.getDouble("share"))));
    }

    /** The document for {@code map}; {@link #read} of it gives the same map. */
    public static JSONObject write(OwnedMap map) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("version", OwnedMap.VERSION);
        o.put("id", map.id());
        o.put("width", map.width());
        o.put("height", map.height());
        if (map.roads() != null) o.put("roads", map.roads());
        if (map.roadGraphFrom() != null) o.put("roadGraphFrom", map.roadGraphFrom());
        if (map.terrain() != null) o.put("terrain", map.terrain());
        o.put("origin", cell(map.origin()));
        o.put("objective", cell(map.objective()));
        JSONArray grounds = new JSONArray();
        for (Ground g : map.grounds()) {
            JSONObject j = new JSONObject().put("role", g.role().name()).put("box", box(g.box()));
            if (g.radius() > 0) j.put("radius", g.radius());
            grounds.put(j);
        }
        o.put("grounds", grounds);
        JSONArray pads = new JSONArray();
        for (Pad p : map.pads()) pads.put(new JSONObject().put("at", cell(p.at())).put("halfWidth", p.halfWidth())
                .put("halfHeight", p.halfHeight()).put("approach", p.approach().name()).put("purpose", p.purpose().name()));
        o.put("pads", pads);
        JSONArray sites = new JSONArray();
        for (Site s : map.sites()) {
            JSONObject j = new JSONObject().put("name", s.name()).put("kind", s.kind().name());
            if (s.building() != null) j.put("building", s.building());
            if (s.at() != null) j.put("at", cell(s.at()));
            if (s.area() != null) j.put("area", box(s.area()));
            j.put("guard", s.guard().name()).put("value", s.value()).put("garrison", s.garrison());
            sites.put(j);
        }
        o.put("sites", sites);
        JSONArray settlements = new JSONArray();
        for (Settlement s : map.settlements()) settlements.put(settlement(s));
        o.put("settlements", settlements);
        JSONArray walls = new JSONArray();
        for (Wall w : map.walls()) walls.put(new JSONObject().put("from", cell(w.from())).put("to", cell(w.to())));
        o.put("walls", walls);
        JSONArray props = new JSONArray();
        for (Prop p : map.props()) props.put(new JSONObject().put("at", cell(p.at())).put("id", p.id()));
        o.put("props", props);
        JSONArray drops = new JSONArray();
        for (Drop d : map.drops()) {
            JSONObject j = new JSONObject().put("name", d.name()).put("role", d.role().name())
                    .put("path", cells(d.path())).put("width", d.width());
            if (!d.crossings().isEmpty()) j.put("crossings", cells(d.crossings())).put("crossingWidth", d.crossingWidth());
            if (!d.depths().isEmpty()) {
                JSONArray depths = new JSONArray();
                for (Depth m : d.depths()) depths.put(new JSONObject().put("at", cell(m.at())).put("share", (double) m.share()));
                j.put("depths", depths);
            }
            drops.put(j);
        }
        o.put("drops", drops);
        JSONArray rises = new JSONArray();
        for (Rise r : map.rises()) rises.put(new JSONObject().put("name", r.name()).put("role", r.role().name())
                .put("box", box(r.box())).put("share", (double) r.share()));
        o.put("rises", rises);
        o.put("routes", cells(map.routes()));
        return o;
    }

    private static JSONObject settlement(Settlement s) throws JSONException {
        JSONObject j = new JSONObject().put("name", s.name()).put("program", s.program()).put("role", s.role().name())
                .put("lot", box(s.lot())).put("gates", cells(s.gates()));
        JSONArray buildings = new JSONArray();
        for (Building b : s.buildings()) {
            JSONObject k = new JSONObject().put("name", b.name()).put("form", b.form().name()).put("plan", b.plan());
            if (b.at() != null) k.put("at", cell(b.at()));
            if (b.turns() != 0) k.put("turns", b.turns());
            if (b.box() != null) k.put("box", box(b.box()));
            if (b.frontage() != null) k.put("frontage", b.frontage().name());
            if (b.opposed()) k.put("opposed", true);
            buildings.put(k);
        }
        j.put("buildings", buildings);
        JSONArray works = new JSONArray();
        for (Work w : s.works()) {
            JSONObject k = new JSONObject().put("op", w.op().name());
            if (w.box() != null) k.put("box", box(w.box()));
            if (w.at() != null) k.put("at", cell(w.at()));
            if (w.id() != null) k.put("id", w.id());
            if (w.turns() != 0) k.put("turns", w.turns());
            works.put(k);
        }
        j.put("works", works);
        if (s.terraceInto() != null) j.put("terraceInto", s.terraceInto());
        return j;
    }

    @FunctionalInterface
    private interface Reader<T> { T read(JSONObject o) throws JSONException; }

    private static <T> List<T> list(JSONObject o, String key, Reader<T> reader) throws JSONException {
        JSONArray a = o.optJSONArray(key);
        List<T> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            try {
                out.add(reader.read(a.getJSONObject(i)));
            } catch (JSONException | IllegalArgumentException e) {
                throw new IllegalArgumentException(key + "[" + i + "]: " + e.getMessage(), e);
            }
        }
        return out;
    }

    private static String text(JSONObject o, String key) {
        return o.has(key) && !o.isNull(key) ? o.optString(key, null) : null;
    }

    private static Cell cell(JSONArray a) throws JSONException {
        if (a.length() != 2) throw new IllegalArgumentException("a cell is [x, y]");
        return new Cell(a.getInt(0), a.getInt(1));
    }

    private static Box box(JSONArray a) throws JSONException {
        if (a.length() != 4) throw new IllegalArgumentException("a box is [x0, y0, x1, y1]");
        return new Box(a.getInt(0), a.getInt(1), a.getInt(2), a.getInt(3));
    }

    private static List<Cell> cells(JSONArray a) throws JSONException {
        List<Cell> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) out.add(cell(a.getJSONArray(i)));
        return out;
    }

    private static JSONArray cell(Cell c) {
        return new JSONArray().put(c.x()).put(c.y());
    }

    private static JSONArray box(Box b) {
        return new JSONArray().put(b.x0()).put(b.y0()).put(b.x1()).put(b.y1());
    }

    private static JSONArray cells(List<Cell> cells) {
        JSONArray a = new JSONArray();
        for (Cell c : cells) a.put(cell(c));
        return a;
    }
}
