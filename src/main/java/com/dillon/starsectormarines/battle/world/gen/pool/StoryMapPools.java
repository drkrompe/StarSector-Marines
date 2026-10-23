package com.dillon.starsectormarines.battle.world.gen.pool;

import com.fs.starfarer.api.Global;
import org.json.JSONObject;

import java.util.EnumMap;
import java.util.Map;

/** Every story map's pool, installed with the other world catalogs at application load. */
public final class StoryMapPools {
    private static volatile Map<StoryMap, StoryMapPool> pools = Map.of();

    private StoryMapPools() {}

    @FunctionalInterface public interface Source { JSONObject read(String path) throws Exception; }

    public static boolean installed() { return pools.size() == StoryMap.values().length; }

    public static StoryMapPool pool(StoryMap map) {
        StoryMapPool pool = pools.get(map);
        if (pool == null) throw new IllegalStateException("Story map pools have not been installed: " + map.id);
        return pool;
    }

    public static void loadBuiltins(Source source) throws Exception {
        Map<StoryMap, StoryMapPool> loaded = new EnumMap<>(StoryMap.class);
        for (StoryMap map : StoryMap.values()) loaded.put(map, StoryMapPool.read(map, source.read(map.path())));
        pools = Map.copyOf(loaded);
    }

    public static void loadBuiltins() throws Exception { loadBuiltins(path -> Global.getSettings().loadJSON(path, true)); }
}
