package com.dillon.starsectormarines.battle.world.gen;

import com.fs.starfarer.api.Global;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The installed owned maps ({@code owned-maps.md}), read once at application load through {@code SettingsAPI} from
 * {@code data/world/maps/<id>.map.json}, so no battle reads the disk. A map's id is its file stem.
 */
public final class OwnedMapCatalog {

    /** Relay Nine's power relay works ({@code RelayNineCompound}). */
    public static final String RELAY_NINE = "relay-nine";

    /** Every map the mod ships. */
    public static final List<String> BUILTINS = List.of(RELAY_NINE);

    private static volatile Map<String, OwnedMap> maps = Map.of();

    private OwnedMapCatalog() { }

    /** Reads one document by its mod-relative path. */
    @FunctionalInterface
    public interface Source { JSONObject read(String path) throws Exception; }

    /** The document path of a map id. */
    public static String path(String id) {
        return "data/world/maps/" + id + ".map.json";
    }

    public static boolean installed() {
        return maps.size() == BUILTINS.size();
    }

    /** The installed map; refuses one that was not installed. */
    public static OwnedMap map(String id) {
        OwnedMap map = maps.get(id);
        if (map == null) throw new IllegalStateException("Owned map has not been installed: " + id);
        return map;
    }

    /** Installs every built-in map from {@code source}, refusing one whose id is not its file stem. */
    public static void loadBuiltins(Source source) throws Exception {
        Map<String, OwnedMap> next = new LinkedHashMap<>();
        for (String id : BUILTINS) {
            OwnedMap map;
            try {
                map = OwnedMapJson.read(source.read(path(id)));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(path(id) + ": " + e.getMessage(), e);
            }
            if (!map.id().equals(id)) throw new IllegalArgumentException(path(id) + " is named " + map.id());
            next.put(id, map);
        }
        maps = Map.copyOf(next);
    }

    public static void loadBuiltins() throws Exception {
        loadBuiltins(path -> Global.getSettings().loadJSON(path, true));
    }
}
