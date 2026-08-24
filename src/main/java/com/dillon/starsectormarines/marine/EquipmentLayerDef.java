package com.dillon.starsectormarines.marine;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/** One actor-local sprite layer authored in shoulder-width coordinates. */
public record EquipmentLayerDef(
        String spritePath,
        float widthShoulders,
        float heightShoulders,
        float pivotX,
        float pivotY,
        State carried,
        State using,
        float recoilShoulders,
        Occlusion firingOcclusion,
        boolean visibleWhileCarried,
        boolean replacePrimaryWhileUsing) implements Serializable {

    public enum Occlusion {
        UNDER_BODY("under-body"),
        OVER_BODY("over-body");

        final String key;

        Occlusion(String key) {
            this.key = key;
        }

        static Occlusion fromKey(String key, String equipmentId) throws JSONException {
            for (Occlusion value : values()) {
                if (value.key.equalsIgnoreCase(key)) return value;
            }
            throw new JSONException("Special equipment '" + equipmentId
                    + "' has unknown carrier occlusion '" + key + "'");
        }
    }

    public record State(float offsetXShoulders, float offsetYShoulders,
                        float angleDegrees, Occlusion occlusion) implements Serializable {
    }

    static EquipmentLayerDef parse(JSONObject json, String equipmentId) throws JSONException {
        String sprite = requireText(json, "sprite", equipmentId);
        float width = positive(json, "widthShoulders", equipmentId);
        float height = positive(json, "heightShoulders", equipmentId);
        JSONArray pivot = json.getJSONArray("pivot");
        if (pivot.length() != 2) {
            throw new JSONException("Special equipment '" + equipmentId
                    + "' carrier pivot must contain [x, y]");
        }
        float pivotX = unit((float) pivot.getDouble(0), "pivot x", equipmentId);
        float pivotY = unit((float) pivot.getDouble(1), "pivot y", equipmentId);
        return new EquipmentLayerDef(sprite, width, height, pivotX, pivotY,
                parseState(json.getJSONObject("carried"), equipmentId),
                parseState(json.getJSONObject("using"), equipmentId),
                nonNegative((float) json.optDouble("recoilShoulders", 0.0),
                        "recoilShoulders", equipmentId),
                optionalOcclusion(json, "firingOcclusion", equipmentId),
                json.optBoolean("visibleWhileCarried", false),
                json.optBoolean("replacePrimaryWhileUsing", false));
    }

    private static State parseState(JSONObject json, String equipmentId) throws JSONException {
        JSONArray offset = json.getJSONArray("offsetShoulders");
        if (offset.length() != 2) {
            throw new JSONException("Special equipment '" + equipmentId
                    + "' carrier offset must contain [x, y]");
        }
        return new State((float) offset.getDouble(0), (float) offset.getDouble(1),
                (float) json.optDouble("angleDegrees", 0.0),
                Occlusion.fromKey(requireText(json, "occlusion", equipmentId), equipmentId));
    }

    private static float positive(JSONObject json, String key, String equipmentId)
            throws JSONException {
        float value = (float) json.getDouble(key);
        if (value <= 0f) {
            throw new JSONException("Special equipment '" + equipmentId + "' " + key
                    + " must be positive");
        }
        return value;
    }

    private static float nonNegative(float value, String key, String equipmentId)
            throws JSONException {
        if (value < 0f) {
            throw new JSONException("Special equipment '" + equipmentId + "' " + key
                    + " cannot be negative");
        }
        return value;
    }

    private static Occlusion optionalOcclusion(JSONObject json, String key,
                                               String equipmentId) throws JSONException {
        String value = json.optString(key, null);
        if (value == null || value.trim().isEmpty() || "null".equals(value.trim())) return null;
        return Occlusion.fromKey(value.trim(), equipmentId);
    }

    private static float unit(float value, String label, String equipmentId)
            throws JSONException {
        if (value < 0f || value > 1f) {
            throw new JSONException("Special equipment '" + equipmentId + "' " + label
                    + " must be between 0 and 1");
        }
        return value;
    }

    private static String requireText(JSONObject json, String key, String equipmentId)
            throws JSONException {
        String value = json.optString(key, null);
        if (value == null || value.trim().isEmpty() || "null".equals(value.trim())) {
            throw new JSONException("Special equipment '" + equipmentId
                    + "' is missing required presentation field '" + key + "'");
        }
        return value.trim();
    }
}
