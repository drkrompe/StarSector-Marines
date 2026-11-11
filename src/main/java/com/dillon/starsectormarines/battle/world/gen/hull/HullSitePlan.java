package com.dillon.starsectormarines.battle.world.gen.hull;

import com.dillon.starsectormarines.battle.nav.Direction;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where a story map's patch puts a fallen hull ({@code fallen-hull-nouns.md}, In a battle: Setup): the hull's middle
 * cell, the way its bow points, how long the trench it ploughed runs behind its stern, and the stakes that already
 * stand at the chapter's point in the arc, each by a cell the nearest lattice point to it takes.
 *
 * <p>Read from a pool entry's patch as
 * {@code "hull": {"x", "y", "heading", "trench", "stakes": [{"x", "y", "stage"}], "yards"}};
 * {@code heading} is {@code north} (+y), {@code east}, {@code south} or {@code west}. {@code yards}, a row, makes the
 * worked fields wholly south of it the port's yards ({@link PortYards}); absent or 0, the fields are left as laid.
 */
public record HullSitePlan(int x, int y, Direction heading, int trench, List<StakeAt> stakes, int yards) {

    /**
     * The shortest and longest trench a hull may have ploughed off its bow, cells. Short is allowed: the broken hull
     * also ploughs the ground between its sections.
     */
    public static final int MIN_TRENCH = 12, MAX_TRENCH = 160;

    /** A stake standing at the chapter's start: the cell it is wanted at, and how far it has risen. */
    public record StakeAt(int x, int y, HullSite.StakeStage stage) {
        public StakeAt {
            if (stage == null) throw new IllegalArgumentException("a stake names its stage");
        }
    }

    public HullSitePlan {
        if (heading == null || heading.dx * heading.dy != 0) throw new IllegalArgumentException("a hull lies on a cardinal heading");
        if (trench < MIN_TRENCH || trench > MAX_TRENCH) throw new IllegalArgumentException("trench of " + trench + " cells");
        stakes = stakes == null ? List.of() : List.copyOf(stakes);
        if (yards < 0) throw new IllegalArgumentException("yards south of row " + yards);
    }

    /** The patch op's form; null for an absent one. */
    public static HullSitePlan read(JSONObject json) throws JSONException {
        if (json == null) return null;
        Direction heading = switch (json.getString("heading").toLowerCase(Locale.ROOT)) {
            case "north" -> Direction.N;
            case "east" -> Direction.E;
            case "south" -> Direction.S;
            case "west" -> Direction.W;
            default -> throw new JSONException("a hull heads north, east, south or west, not " + json.getString("heading"));
        };
        List<StakeAt> stakes = new ArrayList<>();
        JSONArray array = json.optJSONArray("stakes");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject s = array.getJSONObject(i);
                stakes.add(new StakeAt(s.getInt("x"), s.getInt("y"),
                        HullSite.StakeStage.valueOf(s.getString("stage").toUpperCase(Locale.ROOT))));
            }
        }
        return new HullSitePlan(json.getInt("x"), json.getInt("y"), heading, json.optInt("trench", 72), stakes,
                json.optInt("yards", 0));
    }
}
