package com.dillon.starsectormarines.battle.warship;

import com.fs.starfarer.api.Global;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one seam through which this mod reads the game's own data files — hull
 * specs, weapon specs, projectile specs, variants and the weapon table.
 *
 * <p>In the game the files come from the loaded data, so a mod's hull or a
 * re-tuned vanilla weapon resolves the same way as a stock one. Outside the
 * game there is no settings API, so a tool installs a {@link Source} that reads
 * the install's files instead; without one, a read fails and callers treat the
 * thing as absent rather than broken.
 */
public final class VanillaData {

    /** Where data files come from. Paths are relative to the game's {@code data} root's parent, e.g. {@code data/hulls/onslaught.ship}. */
    public interface Source {
        JSONObject json(String path) throws Exception;

        List<Map<String, String>> csv(String path) throws Exception;
    }

    private static final Source GAME = new Source() {
        @Override public JSONObject json(String path) throws Exception {
            return Global.getSettings().loadJSON(path);
        }

        @Override public List<Map<String, String>> csv(String path) throws Exception {
            JSONArray rows = Global.getSettings().loadCSV(path);
            List<Map<String, String>> out = new ArrayList<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                Map<String, String> map = new LinkedHashMap<>();
                for (Iterator<?> keys = row.keys(); keys.hasNext(); ) {
                    String key = (String) keys.next();
                    map.put(key, row.optString(key, ""));
                }
                out.add(map);
            }
            return out;
        }
    };

    private static volatile Source source = GAME;

    private VanillaData() {}

    /** Replaces where data is read from; {@code null} restores the game's own. */
    public static void useSource(Source replacement) {
        source = replacement != null ? replacement : GAME;
    }

    public static JSONObject json(String path) throws Exception {
        return source.json(path);
    }

    public static List<Map<String, String>> csv(String path) throws Exception {
        return source.csv(path);
    }
}
