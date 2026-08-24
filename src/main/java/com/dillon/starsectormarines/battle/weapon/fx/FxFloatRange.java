package com.dillon.starsectormarines.battle.weapon.fx;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.Random;

/** Inclusive authored float range; a scalar parses as a zero-width range. */
public record FxFloatRange(float min, float max) {

    public FxFloatRange {
        if (!Float.isFinite(min) || !Float.isFinite(max) || min > max) {
            throw new IllegalArgumentException("invalid float range [" + min + ", " + max + "]");
        }
    }

    public float sample(Random rng) {
        return min == max ? min : min + rng.nextFloat() * (max - min);
    }

    static FxFloatRange parse(Object value, String field, String definitionId) throws JSONException {
        if (value instanceof Number number) {
            float scalar = number.floatValue();
            requireFinite(scalar, field, definitionId);
            return new FxFloatRange(scalar, scalar);
        }
        if (value instanceof JSONArray array && array.length() == 2) {
            float min = number(array.get(0), field, definitionId);
            float max = number(array.get(1), field, definitionId);
            if (min > max) {
                throw new JSONException("FX definition '" + definitionId + "' field '" + field
                        + "' range minimum exceeds maximum");
            }
            return new FxFloatRange(min, max);
        }
        throw new JSONException("FX definition '" + definitionId + "' field '" + field
                + "' must be a number or a two-number range");
    }

    private static float number(Object value, String field, String definitionId) throws JSONException {
        if (!(value instanceof Number number)) {
            throw new JSONException("FX definition '" + definitionId + "' field '" + field
                    + "' range must contain only numbers");
        }
        float result = number.floatValue();
        requireFinite(result, field, definitionId);
        return result;
    }

    private static void requireFinite(float value, String field, String definitionId) throws JSONException {
        if (!Float.isFinite(value)) {
            throw new JSONException("FX definition '" + definitionId + "' field '" + field
                    + "' must be finite");
        }
    }
}
