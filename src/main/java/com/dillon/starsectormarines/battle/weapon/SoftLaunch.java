package com.dillon.starsectormarines.battle.weapon;

import com.dillon.starsectormarines.battle.combat.Projectile;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The first stage of a two-stage missile, Javelin-style: a small charge kicks it out of its tube,
 * it rises and coasts unpowered for {@link #seconds}, and only then does the motor light and drive it
 * onto its target. The coast is real flight time, so a weapon that soft-launches has to fire that much
 * earlier to arrive at the same moment; the shape of the climb is presentation.
 *
 * <p>All three readings take the shot's linear time fraction ({@code 0} at launch, {@code 1} on arrival)
 * and its whole flight time, so the sim, the sprite, the contrail and the trail effects agree on where
 * the motor lit.
 *
 * @param seconds   unpowered coast after the tube, before the motor lights
 * @param popCells  ground distance covered during the coast, in cells
 * @param liftCells how high the coast climbs, in cells, drawn as a rise up the screen and given back
 *                  over the powered run
 */
public record SoftLaunch(float seconds, float popCells, float liftCells) {

    public SoftLaunch {
        if (!(seconds > 0f) || popCells < 0f || liftCells < 0f
                || !Float.isFinite(seconds) || !Float.isFinite(popCells) || !Float.isFinite(liftCells)) {
            throw new IllegalArgumentException("soft launch needs a positive coast and non-negative pop and lift");
        }
    }

    static SoftLaunch parse(JSONObject json, String weaponId) throws JSONException {
        if (json == null) return null;
        try {
            return new SoftLaunch((float) json.getDouble("seconds"),
                    (float) json.optDouble("popCells", 0.0),
                    (float) json.optDouble("liftCells", 0.0));
        } catch (IllegalArgumentException invalid) {
            throw new JSONException("Weapon '" + weaponId + "' " + invalid.getMessage());
        }
    }

    /** The share of the flight's time spent coasting. */
    public float coastFraction(float flightSeconds) {
        return Math.min(1f, seconds / Math.max(1e-3f, flightSeconds));
    }

    /** Whether the motor has lit by linear time {@code t}. */
    public boolean ignited(float t, float flightSeconds) {
        return t >= coastFraction(flightSeconds);
    }

    /**
     * The share of the ground distance covered by linear time {@code t}: the kick's pop, easing off
     * as the charge's push is spent, then the powered run over what is left.
     */
    public float progress(float t, float flightSeconds, float distanceCells, boolean boostRamp) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float coast = coastFraction(flightSeconds);
        float pop = distanceCells > 1e-3f ? Math.min(0.5f, popCells / distanceCells) : 0f;
        if (t < coast) {
            float u = t / coast;
            return pop * u * (2f - u);
        }
        float u = coast >= 1f ? 1f : (t - coast) / (1f - coast);
        float powered = boostRamp ? Projectile.applyBoostCurve(u) : u;
        return pop + (1f - pop) * powered;
    }

    /** How high the missile stands at linear time {@code t}: climbing through the coast, levelling onto the target after. */
    public float lift(float t, float flightSeconds) {
        if (t <= 0f || t >= 1f) return 0f;
        float coast = coastFraction(flightSeconds);
        if (t < coast) {
            float u = t / coast;
            return liftCells * u * (2f - u);
        }
        float u = coast >= 1f ? 1f : (t - coast) / (1f - coast);
        return liftCells * (1f - u) * (1f - u);
    }
}
