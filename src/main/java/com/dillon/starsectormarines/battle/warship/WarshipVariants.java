package com.dillon.starsectormarines.battle.warship;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Which weapon a vanilla variant puts in each slot of its hull.
 *
 * <p>A variant is the game's own statement of how a hull is fitted, so a
 * warship armed from one carries what the player expects that ship to carry.
 * Stock variants live in a folder per hull and a mod's may sit flat, so both
 * places are tried.
 */
public final class WarshipVariants {

    private WarshipVariants() {}

    /**
     * Slot id to weapon id for {@code variantId}, in the variant's own order, or
     * an empty map when the variant cannot be found.
     */
    public static Map<String, String> weaponsBySlot(String hullId, String variantId) {
        JSONObject variant = load("data/variants/" + hullId + "/" + variantId + ".variant");
        if (variant == null) variant = load("data/variants/" + variantId + ".variant");
        Map<String, String> out = new LinkedHashMap<>();
        if (variant == null) return out;
        JSONArray groups = variant.optJSONArray("weaponGroups");
        if (groups == null) return out;
        for (int i = 0; i < groups.length(); i++) {
            JSONObject weapons = groups.optJSONObject(i) == null
                    ? null : groups.optJSONObject(i).optJSONObject("weapons");
            if (weapons == null) continue;
            for (Iterator<?> keys = weapons.keys(); keys.hasNext(); ) {
                String slot = (String) keys.next();
                out.put(slot, weapons.optString(slot));
            }
        }
        return out;
    }

    private static JSONObject load(String path) {
        try {
            return VanillaData.json(path);
        } catch (Exception e) {
            return null;
        }
    }
}
