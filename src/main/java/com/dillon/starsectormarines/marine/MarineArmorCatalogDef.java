package com.dillon.starsectormarines.marine;

import org.json.JSONException;
import org.json.JSONObject;

/** Data-authored player-facing identity for one persisted infantry armor package. */
public record MarineArmorCatalogDef(
        String id,
        String unitClass,
        String description) {

    public static MarineArmorCatalogDef parse(JSONObject json) throws JSONException {
        String id = requireText(json, "id", "armor catalog entry");
        JSONObject catalog = json.getJSONObject("catalog");
        return new MarineArmorCatalogDef(
                id,
                requireText(catalog, "unitClass", id),
                requireText(catalog, "description", id));
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
