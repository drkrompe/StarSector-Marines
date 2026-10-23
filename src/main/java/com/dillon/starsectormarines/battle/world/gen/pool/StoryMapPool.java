package com.dillon.starsectormarines.battle.world.gen.pool;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The vetted maps one story map plays from ({@code story-map-pools.md}): each a
 * battle seed that passes the mission's acceptance on its first generation,
 * a note saying what it is, and its patch.
 */
public record StoryMapPool(StoryMap map, List<Entry> entries) {

    /** One pooled map. */
    public record Entry(long seed, String note, MapPatch patch) {
        public Entry {
            note = note == null ? "" : note;
            patch = patch == null ? MapPatch.NONE : patch;
        }
    }

    public StoryMapPool {
        entries = List.copyOf(entries);
        if (entries.isEmpty()) throw new IllegalArgumentException(map + " has an empty pool");
        Set<Long> seeds = new HashSet<>();
        for (Entry e : entries) {
            if (!seeds.add(e.seed())) throw new IllegalArgumentException(map + " pools seed " + e.seed() + " twice");
        }
    }

    /** The entry a campaign battle plays for a roll its event carries: deterministic per roll. */
    public Entry pick(long roll) {
        return entries.get(Math.floorMod(roll, entries.size()));
    }

    /** The pooled entry for this seed, or {@code null} for a seed the pool does not hold. */
    public Entry entry(long seed) {
        for (Entry e : entries) if (e.seed() == seed) return e;
        return null;
    }

    public static StoryMapPool read(StoryMap map, JSONObject json) throws JSONException {
        if (json.getInt("version") != 1) throw new JSONException("unsupported pool version for " + map);
        if (!map.id.equals(json.getString("map"))) {
            throw new JSONException("pool file for " + map.id + " names " + json.getString("map"));
        }
        List<Entry> entries = new ArrayList<>();
        JSONArray seeds = json.getJSONArray("seeds");
        for (int i = 0; i < seeds.length(); i++) {
            JSONObject e = seeds.getJSONObject(i);
            entries.add(new Entry(e.getLong("seed"), e.optString("note", ""), MapPatch.read(e.optJSONObject("patch"))));
        }
        return new StoryMapPool(map, entries);
    }
}
