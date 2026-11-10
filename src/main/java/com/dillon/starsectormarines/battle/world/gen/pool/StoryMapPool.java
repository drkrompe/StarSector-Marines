package com.dillon.starsectormarines.battle.world.gen.pool;

import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * The one map a story mission owns ({@code story-map-pools.md}): a battle seed, generated on the mission's
 * {@code home} palette, that passes its acceptance on its first generation there, with a note and its patch. Every
 * world plays this same map: its layout is always the home palette's, and a planet skin re-dresses it for the world
 * ({@code PlanetSkin}). The owner: "All of these should use a single seed, that we own and make good. They should be
 * skinned for the planet and make sense for the planet."
 */
public record StoryMapPool(StoryMap map, SurfacePalette home, Entry owned) {

    /** The document version this reader takes. */
    public static final int VERSION = 3;

    /**
     * The owned map: its seed, a note saying what it is, and its patch. {@code story} is kept for callers that ask
     * whether a campaign's chapter has a map of its own; the owned map always is one.
     */
    public record Entry(long seed, String note, MapPatch patch, boolean story) {
        public Entry {
            note = note == null ? "" : note;
            patch = patch == null ? MapPatch.NONE : patch;
        }

        public Entry(long seed, String note, MapPatch patch) {
            this(seed, note, patch, false);
        }
    }

    public StoryMapPool {
        if (home == null) throw new IllegalArgumentException(map + " names no home palette");
        if (home.layoutOf() != home) throw new IllegalArgumentException(map + "'s home " + home + " is laid out as another");
        if (owned == null) throw new IllegalArgumentException(map + " owns no map");
    }

    /** The mission's map on any world: the owned one, one entry, whatever the biome. */
    public List<Entry> entries(SurfacePalette biome) { return List.of(owned); }

    /** The entry a campaign battle plays on any world: the owned one, whatever the roll. */
    public Entry pick(SurfacePalette biome, long roll) { return owned; }

    /** The story map on any world: the owned one. */
    public Entry story(SurfacePalette biome) { return owned; }

    /** The owned entry when {@code seed} is its seed, else {@code null}: a seed the mission does not own. */
    public Entry entry(SurfacePalette biome, long seed) {
        return owned.seed() == seed ? owned : null;
    }

    /** The palette the owned map's layout is generated on, whatever world it is fought on. */
    public SurfacePalette layoutFor(SurfacePalette world) { return home; }

    public static StoryMapPool read(StoryMap map, JSONObject json) throws JSONException {
        if (json.getInt("version") != VERSION) throw new JSONException("unsupported pool version for " + map);
        if (!map.id.equals(json.getString("map"))) {
            throw new JSONException("pool file for " + map.id + " names " + json.getString("map"));
        }
        JSONObject e = json.getJSONObject("entry");
        return new StoryMapPool(map, SurfacePalette.valueOf(json.getString("home")),
                new Entry(e.getLong("seed"), e.optString("note", ""), MapPatch.read(e.optJSONObject("patch")), true));
    }
}
