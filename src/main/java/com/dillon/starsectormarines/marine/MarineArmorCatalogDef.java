package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Data-authored identity, presentation, appearance family, and combat values for
 * infantry armor.
 *
 * <p>{@link #role} is a closed vocabulary rather than the free display text it
 * replaced. What a pattern is <em>for</em> has to be countable before the
 * catalog's shape can be checked at all ({@code role-and-access.md}); a word
 * only the Armory read could say anything, and did.
 */
public record MarineArmorCatalogDef(
        String id,
        String displayName,
        ArmorRole role,
        String description,
        int tier,
        String iconPath,
        LayeredArmorFamily appearanceFamily,
        float armorCapacity,
        float armorRating,
        float moveSpeedMult,
        float incomingAccuracyMult,
        IntegralSystemDef integralSystem) {

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
                ArmorRole.parse(catalog.optString("role", null), id),
                requireText(catalog, "description", id),
                tier,
                requireText(catalog, "iconPath", id),
                appearanceFamily,
                nonNegative(battle, "armorCapacity", id),
                nonNegative(battle, "armorRating", id),
                positive(battle, "moveSpeedMult", id),
                nonNegative(battle, "incomingAccuracyMult", id),
                battle.has("integralSystem")
                        ? IntegralSystemDef.parse(battle.getJSONObject("integralSystem"), id)
                        : null);
    }

    /**
     * The capability this suit carries, or null. Most patterns carry none —
     * that is what makes one worth hunting for
     * ({@code integral-armor-systems.md}).
     */
    public boolean hasIntegralSystem() {
        return integralSystem != null;
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
