package com.dillon.starsectormarines.battle.weapon.fx;

import org.json.JSONException;
import org.json.JSONObject;

import java.awt.Color;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

/**
 * One immutable authored particle layer. Ranges are resolved by
 * {@link WeaponFxComposer}; repeating layers expand into delayed particle
 * commands rather than requiring a renderer-owned emitter recipe.
 */
public record FxLayerDef(
        FxLayerKind kind,
        FxFloatRange radius,
        FxFloatRange lifetime,
        FxIntRange count,
        float jitter,
        FxFloatRange delay,
        FxFloatRange emissionDuration,
        FxFloatRange emissionInterval,
        FxFloatRange offsetForward,
        FxFloatRange offsetLateral,
        FxFloatRange velocityForward,
        FxFloatRange velocityLateral,
        Color color) {

    private static final Set<String> FIELDS = Set.of(
            "kind", "radius", "lifetime", "count", "jitter", "delay",
            "emissionDuration", "emissionInterval", "offsetForward",
            "offsetLateral", "velocityForward", "velocityLateral", "color");

    public FxLayerDef {
        if (kind == null || radius == null || lifetime == null || count == null
                || delay == null || emissionDuration == null) {
            throw new IllegalArgumentException("FX layer fields may not be null");
        }
        if (!(radius.min() > 0f) || !(lifetime.min() > 0f)) {
            throw new IllegalArgumentException("FX radius and lifetime must be positive");
        }
        if (count.min() < 1 || !Float.isFinite(jitter) || jitter < 0f
                || delay.min() < 0f || emissionDuration.min() < 0f) {
            throw new IllegalArgumentException("invalid FX count, jitter, or timing");
        }
        if (count.max() > WeaponFxComposer.MAX_COMMANDS_PER_LAYER) {
            throw new IllegalArgumentException("count exceeds per-layer command maximum of "
                    + WeaponFxComposer.MAX_COMMANDS_PER_LAYER);
        }
        boolean repeats = emissionDuration.max() > 0f;
        if (repeats != (emissionInterval != null)) {
            throw new IllegalArgumentException(
                    "emissionInterval is required exactly when emissionDuration is positive");
        }
        if (emissionInterval != null && !(emissionInterval.min() > 0f)) {
            throw new IllegalArgumentException("emissionInterval must be positive");
        }
        if ((double) delay.max() + emissionDuration.max() > Float.MAX_VALUE) {
            throw new IllegalArgumentException("combined delay and emission duration exceed finite timing");
        }
    }

    static FxLayerDef parse(JSONObject json, String definitionId, FxSlot slot, int index)
            throws JSONException {
        rejectUnknownFields(json, definitionId, slot, index);
        String where = definitionId + "." + slot.key + "[" + index + "]";
        FxLayerKind kind = FxLayerKind.fromKey(requireText(json, "kind", where), where);
        FxFloatRange radius = requiredRange(json, "radius", where);
        FxFloatRange lifetime = requiredRange(json, "lifetime", where);
        FxIntRange count = json.has("count")
                ? FxIntRange.parse(json.get("count"), "count", where)
                : new FxIntRange(1, 1);
        float jitter = json.has("jitter")
                ? scalar(json.get("jitter"), "jitter", where) : 0f;
        FxFloatRange delay = json.has("delay")
                ? FxFloatRange.parse(json.get("delay"), "delay", where)
                : new FxFloatRange(0f, 0f);
        FxFloatRange emissionDuration = json.has("emissionDuration")
                ? FxFloatRange.parse(json.get("emissionDuration"), "emissionDuration", where)
                : new FxFloatRange(0f, 0f);
        FxFloatRange emissionInterval = json.has("emissionInterval")
                ? FxFloatRange.parse(json.get("emissionInterval"), "emissionInterval", where)
                : null;
        FxFloatRange offsetForward = optionalRange(json, "offsetForward", where);
        FxFloatRange offsetLateral = optionalRange(json, "offsetLateral", where);
        FxFloatRange velocityForward = optionalRange(json, "velocityForward", where);
        FxFloatRange velocityLateral = optionalRange(json, "velocityLateral", where);
        Color color = json.has("color") ? parseColor(json.get("color"), where) : null;

        try {
            FxLayerDef layer = new FxLayerDef(kind, radius, lifetime, count, jitter,
                    delay, emissionDuration, emissionInterval,
                    offsetForward, offsetLateral, velocityForward, velocityLateral,
                    color);
            validateScheduleBound(layer, where);
            return layer;
        } catch (IllegalArgumentException e) {
            throw new JSONException("Invalid FX layer '" + where + "': " + e.getMessage());
        }
    }

    private static void validateScheduleBound(FxLayerDef layer, String where) throws JSONException {
        if (layer.emissionInterval == null) return;
        double emissions = Math.floor(layer.emissionDuration.max() / layer.emissionInterval.min()) + 1.0;
        double particles = emissions * layer.count.max();
        if (particles > WeaponFxComposer.MAX_COMMANDS_PER_LAYER) {
            throw new JSONException("FX layer '" + where + "' can schedule " + (long) particles
                    + " particles; maximum is " + WeaponFxComposer.MAX_COMMANDS_PER_LAYER);
        }
    }

    private static FxFloatRange requiredRange(JSONObject json, String field, String where)
            throws JSONException {
        if (!json.has(field) || json.isNull(field)) {
            throw new JSONException("FX layer '" + where + "' is missing required field '" + field + "'");
        }
        return FxFloatRange.parse(json.get(field), field, where);
    }

    private static FxFloatRange optionalRange(JSONObject json, String field, String where)
            throws JSONException {
        return json.has(field) && !json.isNull(field)
                ? FxFloatRange.parse(json.get(field), field, where) : null;
    }

    private static float scalar(Object value, String field, String where) throws JSONException {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || !Float.isFinite(number.floatValue())) {
            throw new JSONException("FX layer '" + where + "' field '" + field + "' must be a finite number");
        }
        return number.floatValue();
    }

    private static Color parseColor(Object value, String where) throws JSONException {
        if (!(value instanceof String text)) {
            throw new JSONException("FX layer '" + where + "' color must be a six-digit RGB string");
        }
        String digits = text.startsWith("#") ? text.substring(1) : text;
        if (digits.length() != 6) {
            throw new JSONException("FX layer '" + where + "' color must be six hex digits");
        }
        try {
            return new Color(Integer.parseInt(digits, 16));
        } catch (NumberFormatException e) {
            throw new JSONException("FX layer '" + where + "' color is not hexadecimal: '" + text + "'");
        }
    }

    private static String requireText(JSONObject json, String field, String where) throws JSONException {
        if (!json.has(field) || json.isNull(field)) {
            throw new JSONException("FX layer '" + where + "' is missing required field '" + field + "'");
        }
        String value = json.getString(field).trim();
        if (value.isEmpty()) {
            throw new JSONException("FX layer '" + where + "' field '" + field + "' may not be blank");
        }
        return value;
    }

    private static void rejectUnknownFields(JSONObject json, String definitionId, FxSlot slot, int index)
            throws JSONException {
        Set<String> unknown = new TreeSet<>();
        Iterator<?> keys = json.keys();
        while (keys.hasNext()) {
            String key = String.valueOf(keys.next());
            if (!FIELDS.contains(key)) unknown.add(key);
        }
        if (!unknown.isEmpty()) {
            throw new JSONException("FX definition '" + definitionId + "' slot '" + slot.key
                    + "' layer " + index + " has unknown fields " + unknown);
        }
    }
}
