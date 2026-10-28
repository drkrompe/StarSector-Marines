package com.dillon.starsectormarines.battle.world.gen.pool;

import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A pooled seed's authored fix ({@code story-map-pools.md}): places pinned
 * before generation, ruin strokes applied at battle assembly through the
 * authored damage layer, and on a Colony Breach colony where its lift, bores
 * and holdouts stand. All ride the regeneration, so a generator change still
 * reaches a patched map.
 *
 * @param lift     the cell the colony's lift is wanted at, or {@code null} to let the setup choose
 * @param bores    the colony's eruption sites, the first wave in order and one mother; empty to let the setup choose
 * @param holdouts a cell inside each building the colony holds out in, in redoubt order; empty to let the setup choose
 */
public record MapPatch(List<Pin> pins, List<DamagePlan.Stroke> ruin, Cell lift, List<Bore> bores,
                       List<Cell> holdouts) {

    /** The patch of a seed that needs none. */
    public static final MapPatch NONE = new MapPatch(List.of(), List.of());

    /** A cell a person put something on, read off the pool's whole-map review frame. */
    public record Cell(int x, int y) { }

    /** An eruption site a person put down: the mother bore, or one of the first wave. */
    public record Bore(int x, int y, boolean mother) { }

    /** The name of the damage layer a patch's ruin is applied as. */
    static final String RUIN_LAYER = "story-map-pool patch";

    /** Moves a plan precinct's seed to this cell before the map grows round it. */
    public record Pin(String place, int x, int y) {
        public Pin {
            if (place == null || place.isBlank()) throw new IllegalArgumentException("a pin names its place");
        }
    }

    public MapPatch {
        pins = List.copyOf(pins);
        ruin = List.copyOf(ruin);
        bores = List.copyOf(bores);
        holdouts = List.copyOf(holdouts);
        Set<String> places = new HashSet<>();
        for (Pin pin : pins) {
            if (!places.add(pin.place())) throw new IllegalArgumentException("place pinned twice: " + pin.place());
        }
        int mothers = 0;
        for (Bore bore : bores) if (bore.mother()) mothers++;
        if (!bores.isEmpty() && mothers != 1) throw new IllegalArgumentException("authored bores name one mother, not " + mothers);
    }

    /** A patch of pins and ruin alone. */
    public MapPatch(List<Pin> pins, List<DamagePlan.Stroke> ruin) {
        this(pins, ruin, null, List.of(), List.of());
    }

    public boolean isEmpty() {
        return pins.isEmpty() && ruin.isEmpty() && lift == null && bores.isEmpty() && holdouts.isEmpty();
    }

    /** The plan with every pinned place moved; a pin naming no place of the plan is an authoring error. */
    public PrecinctPlan pinned(PrecinctPlan plan) {
        if (pins.isEmpty()) return plan;
        List<Precinct> out = new ArrayList<>(plan.precincts().size());
        Set<String> used = new HashSet<>();
        for (Precinct p : plan.precincts()) {
            Pin pin = pinFor(p.name());
            if (pin == null) {
                out.add(p);
                continue;
            }
            used.add(pin.place());
            out.add(new Precinct(p.name(), pin.x(), pin.y(), p.growth(), p.program(), p.boundary(),
                    p.fortification(), p.character(), p.landing()));
        }
        for (Pin pin : pins) {
            if (!used.contains(pin.place())) throw new IllegalArgumentException("the plan has no place " + pin.place());
        }
        return new PrecinctPlan(out, plan.attackerFrom(), plan.standoff(), plan.lanes(), plan.unplacedLanePlaces(),
                plan.movedLaneWaypoints(), plan.landscape(), plan.layout());
    }

    /** The map carrying the patch's ruin as one more initial-damage layer, applied when the battle is assembled. */
    public MapResult ruined(MapResult map, long seed) {
        if (ruin.isEmpty()) return map;
        int width = map.grid.getWidth(), height = map.grid.getHeight();
        List<DamagePlan.Layer> layers = new ArrayList<>();
        if (map.initialDamage != null) layers.addAll(map.initialDamage.layers());
        layers.add(new DamagePlan.Layer(RUIN_LAYER, true, ruin));
        long damageSeed = map.initialDamage != null ? map.initialDamage.seed() : seed;
        return map.withInitialDamage(new DamagePlan(width, height, damageSeed, layers));
    }

    private Pin pinFor(String place) {
        for (Pin pin : pins) if (pin.place().equals(place)) return pin;
        return null;
    }

    /** Reads a patch; an absent one is {@link #NONE}. */
    public static MapPatch read(JSONObject json) throws JSONException {
        if (json == null) return NONE;
        List<Pin> pins = new ArrayList<>();
        JSONArray pinArray = json.optJSONArray("pin");
        if (pinArray != null) {
            for (int i = 0; i < pinArray.length(); i++) {
                JSONObject p = pinArray.getJSONObject(i);
                pins.add(new Pin(p.getString("place"), p.getInt("x"), p.getInt("y")));
            }
        }
        List<DamagePlan.Stroke> ruin = new ArrayList<>();
        JSONArray ruinArray = json.optJSONArray("ruin");
        if (ruinArray != null) {
            for (int i = 0; i < ruinArray.length(); i++) {
                JSONObject s = ruinArray.getJSONObject(i);
                DamagePlan.Mode mode = DamagePlan.Mode.valueOf(s.optString("mode", DamagePlan.Mode.RUIN.name()));
                if (mode == DamagePlan.Mode.ERASE) throw new JSONException("a patch ruins; it has no erase stroke");
                List<DamagePlan.Point> points = new ArrayList<>();
                JSONArray ps = s.getJSONArray("points");
                for (int k = 0; k < ps.length(); k++) {
                    points.add(new DamagePlan.Point(ps.getJSONArray(k).getDouble(0), ps.getJSONArray(k).getDouble(1)));
                }
                ruin.add(new DamagePlan.Stroke(mode, s.getDouble("radius"), points));
            }
        }
        JSONArray liftCell = json.optJSONArray("lift");
        Cell lift = liftCell == null ? null : cell(liftCell);
        List<Bore> bores = new ArrayList<>();
        JSONArray boreArray = json.optJSONArray("bores");
        if (boreArray != null) {
            for (int i = 0; i < boreArray.length(); i++) {
                JSONObject b = boreArray.getJSONObject(i);
                bores.add(new Bore(b.getInt("x"), b.getInt("y"), b.optBoolean("mother", false)));
            }
        }
        List<Cell> holdouts = new ArrayList<>();
        JSONArray holdoutArray = json.optJSONArray("holdouts");
        if (holdoutArray != null) {
            for (int i = 0; i < holdoutArray.length(); i++) holdouts.add(cell(holdoutArray.getJSONArray(i)));
        }
        return new MapPatch(pins, ruin, lift, bores, holdouts);
    }

    private static Cell cell(JSONArray xy) throws JSONException {
        return new Cell(xy.getInt(0), xy.getInt(1));
    }
}
