package com.dillon.starsectormarines.battle.shield;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * What a shield generator projects: the dome's reach and its flux budget.
 *
 * <p>Vanilla gives the planetary shield no combat numbers, so every figure is
 * authored and <b>untuned</b>: they are first-pass values to be set in play,
 * and the dome's strength against ship fire is gameplay's to decide
 * ({@code ground-defences.md}).
 *
 * @param radiusCells         reach of the dome about the generator, in cells
 * @param fluxCapacity        flux the dome absorbs before it overloads
 * @param dissipationPerSecond flux shed per second while the dome is up
 * @param overloadSeconds     how long a dome that filled stays down before it recovers
 * @param captureRadiusCells  how close to the generator a side must stand to take or hold it
 * @param captureSeconds      how long an uncontested side must stand there to take it
 */
public record DomeSpec(float radiusCells, float fluxCapacity, float dissipationPerSecond,
                       float overloadSeconds, float captureRadiusCells, float captureSeconds) {

    public DomeSpec {
        requirePositive(radiusCells, "radiusCells");
        requirePositive(fluxCapacity, "fluxCapacity");
        if (!Float.isFinite(dissipationPerSecond) || dissipationPerSecond < 0f) {
            throw new IllegalArgumentException("dome dissipationPerSecond must be finite and non-negative");
        }
        requirePositive(overloadSeconds, "overloadSeconds");
        requirePositive(captureRadiusCells, "captureRadiusCells");
        requirePositive(captureSeconds, "captureSeconds");
    }

    /** Reads the {@code dome} block of a structure definition. */
    public static DomeSpec parse(JSONObject json) throws JSONException {
        try {
            return new DomeSpec(
                    (float) json.getDouble("radiusCells"),
                    (float) json.getDouble("fluxCapacity"),
                    (float) json.getDouble("dissipationPerSecond"),
                    (float) json.getDouble("overloadSeconds"),
                    (float) json.getDouble("captureRadiusCells"),
                    (float) json.getDouble("captureSeconds"));
        } catch (IllegalArgumentException e) {
            throw new JSONException(e.getMessage());
        }
    }

    private static void requirePositive(float value, String field) {
        if (!Float.isFinite(value) || value <= 0f) {
            throw new IllegalArgumentException("dome " + field + " must be finite and positive");
        }
    }
}
