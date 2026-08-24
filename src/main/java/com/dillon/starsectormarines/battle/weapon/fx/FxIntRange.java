package com.dillon.starsectormarines.battle.weapon.fx;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.Random;

/** Inclusive authored integer range; a scalar parses as a zero-width range. */
public record FxIntRange(int min, int max) {

    public FxIntRange {
        if (min > max) throw new IllegalArgumentException("invalid integer range [" + min + ", " + max + "]");
    }

    public int sample(Random rng) {
        if (min == max) return min;
        long width = (long) max - min + 1L;
        return (int) (min + rng.nextLong(width));
    }

    static FxIntRange parse(Object value, String field, String definitionId) throws JSONException {
        if (value instanceof Number number) {
            int scalar = integer(number, field, definitionId);
            return new FxIntRange(scalar, scalar);
        }
        if (value instanceof JSONArray array && array.length() == 2) {
            int min = integerValue(array.get(0), field, definitionId);
            int max = integerValue(array.get(1), field, definitionId);
            if (min > max) {
                throw new JSONException("FX definition '" + definitionId + "' field '" + field
                        + "' range minimum exceeds maximum");
            }
            return new FxIntRange(min, max);
        }
        throw new JSONException("FX definition '" + definitionId + "' field '" + field
                + "' must be an integer or a two-integer range");
    }

    private static int integerValue(Object value, String field, String definitionId) throws JSONException {
        if (!(value instanceof Number number)) {
            throw new JSONException("FX definition '" + definitionId + "' field '" + field
                    + "' range must contain only integers");
        }
        return integer(number, field, definitionId);
    }

    private static int integer(Number number, String field, String definitionId) throws JSONException {
        double value = number.doubleValue();
        if (!Double.isFinite(value) || value != Math.rint(value)
                || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new JSONException("FX definition '" + definitionId + "' field '" + field
                    + "' must contain exact integers");
        }
        return (int) value;
    }
}
