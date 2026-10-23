package com.dillon.starsectormarines.battle.world.gen.pool;

import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The vetted maps one story map plays from ({@code story-map-pools.md}), a few
 * for each world biome: each a battle seed that passes the mission's acceptance
 * on its first generation on that biome, a note saying what it is, and its patch.
 */
public record StoryMapPool(StoryMap map, Map<SurfacePalette, List<Entry>> biomes) {

    /** One pooled map. */
    public record Entry(long seed, String note, MapPatch patch) {
        public Entry {
            note = note == null ? "" : note;
            patch = patch == null ? MapPatch.NONE : patch;
        }
    }

    public StoryMapPool {
        Map<SurfacePalette, List<Entry>> copy = new EnumMap<>(SurfacePalette.class);
        for (SurfacePalette biome : SurfacePalette.values()) {
            List<Entry> entries = biomes.get(biome);
            if (entries == null || entries.isEmpty()) throw new IllegalArgumentException(map + " has no " + biome + " maps");
            Set<Long> seeds = new HashSet<>();
            for (Entry e : entries) {
                if (!seeds.add(e.seed())) throw new IllegalArgumentException(map + " pools " + biome + " seed " + e.seed() + " twice");
            }
            copy.put(biome, List.copyOf(entries));
        }
        biomes = Map.copyOf(copy);
    }

    /** The maps of one biome, in pool order. */
    public List<Entry> entries(SurfacePalette biome) { return biomes.get(biome); }

    /** The entry a campaign battle plays on a world of this biome for a roll its event carries. */
    public Entry pick(SurfacePalette biome, long roll) {
        List<Entry> entries = entries(biome);
        return entries.get(Math.floorMod(roll, entries.size()));
    }

    /** The pooled entry for this seed on this biome, or {@code null} for a seed the pool does not hold there. */
    public Entry entry(SurfacePalette biome, long seed) {
        for (Entry e : entries(biome)) if (e.seed() == seed) return e;
        return null;
    }

    public static StoryMapPool read(StoryMap map, JSONObject json) throws JSONException {
        if (json.getInt("version") != 2) throw new JSONException("unsupported pool version for " + map);
        if (!map.id.equals(json.getString("map"))) {
            throw new JSONException("pool file for " + map.id + " names " + json.getString("map"));
        }
        JSONObject biomes = json.getJSONObject("biomes");
        Map<SurfacePalette, List<Entry>> out = new EnumMap<>(SurfacePalette.class);
        for (SurfacePalette biome : SurfacePalette.values()) {
            JSONArray seeds = biomes.optJSONArray(biome.name());
            if (seeds == null) continue;
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < seeds.length(); i++) {
                JSONObject e = seeds.getJSONObject(i);
                entries.add(new Entry(e.getLong("seed"), e.optString("note", ""), MapPatch.read(e.optJSONObject("patch"))));
            }
            out.put(biome, entries);
        }
        return new StoryMapPool(map, out);
    }
}
