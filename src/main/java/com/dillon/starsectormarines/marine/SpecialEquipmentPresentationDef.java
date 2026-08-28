package com.dillon.starsectormarines.marine;

import org.json.JSONArray;
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
        Field field,
        Deployed deployed,
        LayerClips layerClips,
        Preview preview) implements Serializable {

    public record Deployed(String spritePath, float visualCells) implements Serializable {
    }

    public record Thrown(String spritePath, float widthCells,
                         float heightCells) implements Serializable {
    }

    /**
     * Flipbook the deployed field's puffs play, plus the tint each draw
     * multiplies onto it. Frames are addressed on a {@code columns} x
     * {@code rows} grid in image order; frame pixel size is derived from the
     * loaded sheet rather than restated here, so re-deriving the art at a
     * different resolution needs no data change.
     *
     * <p>The tint can only darken — it multiplies. Art bright enough to read
     * as a white obscurant is the deriving script's job, not this field's.
     */
    public record Field(String sheetPath, int columns, int rows,
                        int firstFrame, int frameCount, float framesPerSecond,
                        float tintRed, float tintGreen, float tintBlue)
            implements Serializable {
    }

    public record Preview(String state, float phase) implements Serializable {
    }

    /** Unit-layer variant and clips used while this item owns the actor pose. */
    public record LayerClips(String variant, String using,
                             String firing) implements Serializable {
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
        JSONObject fieldJson = json.optJSONObject("field");
        Field field = fieldJson != null ? parseField(fieldJson, equipmentId) : null;
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
        JSONObject layerClipsJson = json.optJSONObject("layerClips");
        LayerClips layerClips = layerClipsJson != null
                ? new LayerClips(requireText(layerClipsJson, "variant", equipmentId),
                        requireText(layerClipsJson, "using", equipmentId),
                        optionalText(layerClipsJson, "firing"))
                : null;
        return new SpecialEquipmentPresentationDef(
                armoryIcon, aimSprite, usePose, carrier, thrown, field,
                deployed, layerClips, preview);
    }

    private static Field parseField(JSONObject json, String equipmentId)
            throws JSONException {
        String sheet = requireText(json, "sheet", equipmentId);
        int columns = positiveInt(json, "columns", equipmentId);
        int rows = positiveInt(json, "rows", equipmentId);
        int firstFrame = json.getInt("firstFrame");
        int frameCount = positiveInt(json, "frameCount", equipmentId);
        if (firstFrame < 0 || firstFrame + frameCount > columns * rows) {
            throw new JSONException("Special equipment '" + equipmentId
                    + "' field frames " + firstFrame + ".." + (firstFrame + frameCount - 1)
                    + " fall outside its " + columns + "x" + rows + " sheet");
        }
        float fps = positive(json, "framesPerSecond", equipmentId);
        JSONArray tint = json.optJSONArray("tint");
        if (tint == null || tint.length() != 3) {
            throw new JSONException("Special equipment '" + equipmentId
                    + "' field tint must be three components");
        }
        return new Field(sheet, columns, rows, firstFrame, frameCount, fps,
                unit((float) tint.getDouble(0), equipmentId),
                unit((float) tint.getDouble(1), equipmentId),
                unit((float) tint.getDouble(2), equipmentId));
    }

    private static int positiveInt(JSONObject json, String key, String equipmentId)
            throws JSONException {
        int value = json.getInt(key);
        if (value <= 0) {
            throw new JSONException("Special equipment '" + equipmentId + "' " + key
                    + " must be positive");
        }
        return value;
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
                    + "' unit value must be between 0 and 1");
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
