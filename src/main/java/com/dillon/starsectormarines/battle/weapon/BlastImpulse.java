package com.dillon.starsectormarines.battle.weapon;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The shove one explosive round's blast gives what stands in it ({@code sim.blast}; {@code mechs-nouns.md}, Blast
 * impulse): {@code impulse} tonne-cells a second at the burst, falling to nothing at {@code radius} cells as
 * {@code (1 - d / radius) ^ falloff}. A falloff above one is a sharp blast, all of it at the point (a shaped
 * charge); one is an even spread (a fragmentation warhead or a shell). Rounds of a salvo each carry their own;
 * what they add up to on a body is the body's to decide ({@code BlastImpulseRules}).
 */
public record BlastImpulse(float impulse, float radius, float falloff) {

    public BlastImpulse {
        if (!(impulse > 0f) || !Float.isFinite(impulse)) throw new IllegalArgumentException("impulse must be positive");
        if (!(radius > 0f) || !Float.isFinite(radius)) throw new IllegalArgumentException("radius must be positive");
        if (!(falloff > 0f) || !Float.isFinite(falloff)) throw new IllegalArgumentException("falloff must be positive");
    }

    /** The impulse a body {@code distance} cells from the burst takes; zero at and beyond the radius. */
    public float at(float distance) {
        if (distance >= radius) return 0f;
        float share = 1f - Math.max(0f, distance) / radius;
        return impulse * (falloff == 1f ? share : (float) Math.pow(share, falloff));
    }

    /**
     * Parses {@code sim.blast}, or null when absent. The radius defaults to the round's own splash
     * ({@code aoeRadius}) and the falloff to one.
     */
    static BlastImpulse parse(JSONObject value, float aoeRadius, String weaponId) throws JSONException {
        if (value == null) return null;
        float radius = (float) value.optDouble("radius", aoeRadius);
        try {
            return new BlastImpulse((float) value.getDouble("impulse"), radius,
                    (float) value.optDouble("falloff", 1.0));
        } catch (IllegalArgumentException e) {
            throw new JSONException("Weapon '" + weaponId + "' sim.blast: " + e.getMessage());
        }
    }
}
