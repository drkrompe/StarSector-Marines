package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import org.json.JSONException;
import org.json.JSONObject;

/** Data-authored identity, presentation, appearance family, and combat values for infantry armor. */
public record MarineArmorCatalogDef(
        String id,
        String displayName,
        String unitClass,
        String description,
        int tier,
        String iconPath,
        LayeredArmorFamily appearanceFamily,
        float armorPool,
        float armorRating,
        float moveSpeedMult,
        float incomingAccuracyMult) {

    public static MarineArmorCatalogDef parse(JSONObject json) throws JSONException {
        String id = requireText(json, "id", "armor catalog entry");
        JSONObject catalog = json.getJSONObject("catalog");
        JSONObject battle = json.getJSONObject("battle");
        int tier = catalog.getInt("tier");
        if (tier < 1) throw new JSONException("Armor '" + id + "' tier must be positive");
        LayeredArmorFamily appearanceFamily;
        try {
            appearanceFamily = LayeredArmorFamily.valueOf(
                    requireText(battle, "appearanceFamily", id));
        } catch (IllegalArgumentException failure) {
            throw new JSONException("Armor '" + id + "' has unknown appearanceFamily: "
                    + failure.getMessage());
        }
        return new MarineArmorCatalogDef(
                id,
                requireText(catalog, "displayName", id),
                requireText(catalog, "unitClass", id),
                requireText(catalog, "description", id),
                tier,
                requireText(catalog, "iconPath", id),
                appearanceFamily,
                nonNegative(battle, "armorPool", id),
                nonNegative(battle, "armorRating", id),
                positive(battle, "moveSpeedMult", id),
                nonNegative(battle, "incomingAccuracyMult", id));
    }

    private static float nonNegative(JSONObject json, String key, String owner)
            throws JSONException {
        float value = (float) json.getDouble(key);
        if (!Float.isFinite(value) || value < 0f) {
            throw new JSONException(owner + " field '" + key + "' must be finite and non-negative");
        }
        return value;
    }

    private static float positive(JSONObject json, String key, String owner)
            throws JSONException {
        float value = nonNegative(json, key, owner);
        if (value == 0f) throw new JSONException(owner + " field '" + key + "' must be positive");
        return value;
    }

    private static String requireText(JSONObject json, String key, String owner)
            throws JSONException {
        String value = json.optString(key, null);
        if (value == null || value.trim().isEmpty() || "null".equals(value.trim())) {
            throw new JSONException(owner + " is missing required text field '" + key + "'");
        }
        return value.trim();
    }
}
