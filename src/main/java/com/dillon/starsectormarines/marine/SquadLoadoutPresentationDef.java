package com.dillon.starsectormarines.marine;

import org.json.JSONException;
import org.json.JSONObject;

/** Data-authored collectible presentation for one deterministic squad loadout. */
public record SquadLoadoutPresentationDef(
        String id,
        Kind kind,
        int tier,
        SquadLoadoutRarity rarity,
        String provenance,
        String factionLogo,
        String lore) {

    public enum Kind {
        WEAPON,
        ARMOR
    }

    public static SquadLoadoutPresentationDef parse(JSONObject json) throws JSONException {
        String id = requireText(json, "id", "squad loadout");
        Kind kind;
        SquadLoadoutRarity rarity;
        try {
            kind = Kind.valueOf(requireText(json, "kind", id));
            rarity = SquadLoadoutRarity.valueOf(requireText(json, "rarity", id));
        } catch (IllegalArgumentException invalidVocabulary) {
            throw new JSONException(id + " has invalid kind or rarity");
        }
        int tier = json.optInt("tier", 0);
        if (tier < 1 || tier > 5) {
            throw new JSONException(id + " tier must be between 1 and 5");
        }
        return new SquadLoadoutPresentationDef(id, kind, tier, rarity,
                requireText(json, "provenance", id),
                requireText(json, "factionLogo", id),
                requireText(json, "lore", id));
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
