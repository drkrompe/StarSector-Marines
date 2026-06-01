package com.dillon.starsectormarines.battle.air.engine;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves the {@link EngineVoice} flyweight for a vanilla hull id by reading
 * its {@code .ship} spec — the dominant engine-slot {@code style} (tech tier)
 * plus the hull's {@code hullSize}. Lazy-loads {@code data/hulls/<id>.ship}
 * via {@link com.fs.starfarer.api.SettingsAPI#loadJSON(String)} (so modded
 * hulls resolve through the game's load order) and caches the result per hull.
 * Failed resolutions (no hull id, missing {@code .ship}, malformed JSON) log
 * once and cache {@link EngineVoice#DEFAULT}.
 *
 * <p>Sibling of {@link EngineSlotResolver}: same load-and-cache idiom over the
 * same source of truth — {@link ShipSpecEngineParser} reads the per-slot
 * {@code style} this resolver tallies. One picks <em>where</em> the plumes
 * sit; this picks <em>what</em> they sound like.
 */
public final class EngineVoiceResolver {

    private static final Logger LOG = Global.getLogger(EngineVoiceResolver.class);

    /** Key: vanilla hull id. One shared cache across all air entities. */
    private static final Map<String, EngineVoice> CACHE_BY_HULL = new HashMap<>();

    private EngineVoiceResolver() {}

    /**
     * The interned engine voice for {@code hullId}; {@link EngineVoice#DEFAULT}
     * for null / empty / unresolvable hulls. Lazy-loads on first call, cached
     * thereafter. Never returns null.
     */
    public static EngineVoice resolve(String hullId) {
        if (hullId == null || hullId.isEmpty()) return EngineVoice.DEFAULT;
        EngineVoice cached = CACHE_BY_HULL.get(hullId);
        if (cached != null) return cached;
        EngineVoice resolved = doResolve(hullId);
        CACHE_BY_HULL.put(hullId, resolved);
        return resolved;
    }

    private static EngineVoice doResolve(String hullId) {
        String path = "data/hulls/" + hullId + ".ship";
        try {
            JSONObject spec = Global.getSettings().loadJSON(path);
            String hullSize = spec.optString("hullSize", "FRIGATE");
            return EngineVoice.forSpec(dominantStyle(spec), hullSize);
        } catch (Exception e) {
            LOG.warn("EngineVoiceResolver: " + hullId + " (" + path + ") — "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return EngineVoice.DEFAULT;
        }
    }

    /**
     * The most common {@code style} across the hull's {@code engineSlots} (ties
     * resolve to the first in spec order). Hulls with no engine slots fall back
     * to {@code MIDLINE}, which {@link EngineVoice.Tier#fromStyle} maps to the
     * neutral midtek tier.
     */
    private static String dominantStyle(JSONObject spec) {
        JSONArray slots = spec.optJSONArray("engineSlots");
        if (slots == null || slots.length() == 0) return "MIDLINE";
        Map<String, Integer> counts = new HashMap<>();
        String best = null;
        int bestCount = 0;
        for (int i = 0; i < slots.length(); i++) {
            JSONObject slot = slots.optJSONObject(i);
            if (slot == null) continue;
            String style = slot.optString("style", "MIDLINE");
            int c = counts.getOrDefault(style, 0) + 1;
            counts.put(style, c);
            if (c > bestCount) {
                bestCount = c;
                best = style;
            }
        }
        return best != null ? best : "MIDLINE";
    }
}
