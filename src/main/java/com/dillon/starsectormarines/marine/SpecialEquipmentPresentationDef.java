package com.dillon.starsectormarines.marine;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/** Catalog, actor-composition, deployed-world, and preview art for one special item. */
public record SpecialEquipmentPresentationDef(
        String armoryIconPath,
        String aimSpritePath,
        SpecialUsePose usePose,
        EquipmentLayerDef carrierLayer,
        Thrown thrown,
        String fieldSpritePath,
        Deployed deployed,
        Preview preview) implements Serializable {

    public record Deployed(String spritePath, float visualCells) implements Serializable {
    }

    public record Thrown(String spritePath, float widthCells,
                         float heightCells) implements Serializable {
    }

    public record Preview(String state, float phase) implements Serializable {
    }

    static SpecialEquipmentPresentationDef parse(JSONObject json, String equipmentId)
            throws JSONException {
        String armoryIcon = requireText(json, "armoryIcon", equipmentId);
        String aimSprite = optionalText(json, "aimSprite");
        SpecialUsePose usePose = SpecialUsePose.fromKey(
                requireText(json, "usePose", equipmentId), equipmentId);
        JSONObject carrierJson = json.optJSONObject("carrierLayer");
        EquipmentLayerDef carrier = carrierJson != null
                ? EquipmentLayerDef.parse(carrierJson, equipmentId) : null;
        JSONObject thrownJson = json.optJSONObject("thrown");
        Thrown thrown = null;
        if (thrownJson != null) {
            float widthCells = positive(thrownJson, "widthCells", equipmentId);
            float heightCells = positive(thrownJson, "heightCells", equipmentId);
            thrown = new Thrown(requireText(thrownJson, "sprite", equipmentId),
                    widthCells, heightCells);
        }
        String fieldSprite = optionalText(json, "fieldSprite");
        JSONObject deployedJson = json.optJSONObject("deployed");
        Deployed deployed = null;
        if (deployedJson != null) {
            float visualCells = (float) deployedJson.getDouble("visualCells");
            if (visualCells <= 0f) {
                throw new JSONException("Special equipment '" + equipmentId
                        + "' deployed visualCells must be positive");
            }
            deployed = new Deployed(requireText(deployedJson, "sprite", equipmentId), visualCells);
        }
        JSONObject previewJson = json.optJSONObject("preview");
        Preview preview = previewJson != null
                ? new Preview(requireText(previewJson, "state", equipmentId),
                        unit((float) previewJson.optDouble("phase", 1.0), equipmentId))
                : new Preview("carried", 1f);
        if (!"carried".equals(preview.state()) && !"using".equals(preview.state())
                && !"deployed".equals(preview.state())) {
            throw new JSONException("Special equipment '" + equipmentId
                    + "' preview state must be carried, using, or deployed");
        }
        if ("deployed".equals(preview.state()) && deployed == null) {
            throw new JSONException("Special equipment '" + equipmentId
                    + "' previews deployed state without a deployed recipe");
        }
        return new SpecialEquipmentPresentationDef(
                armoryIcon, aimSprite, usePose, carrier, thrown, fieldSprite,
                deployed, preview);
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

    private static float unit(float value, String equipmentId) throws JSONException {
        if (value < 0f || value > 1f) {
            throw new JSONException("Special equipment '" + equipmentId
                    + "' preview phase must be between 0 and 1");
        }
        return value;
    }

    private static String requireText(JSONObject json, String key, String equipmentId)
            throws JSONException {
        String value = optionalText(json, key);
        if (value == null) {
            throw new JSONException("Special equipment '" + equipmentId
                    + "' is missing required presentation field '" + key + "'");
        }
        return value;
    }

    private static String optionalText(JSONObject json, String key) {
        String value = json.optString(key, null);
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() || "null".equals(trimmed) ? null : trimmed;
    }
}
