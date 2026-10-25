package com.dillon.starsectormarines.battle.turret;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A hardpoint's directional flux shield: the arc it projects and the generator and capacitors behind it
 * ({@code ground-turret-tiers.md}). Fitted only where the structure's look carries a shield (the field-emitter
 * styles); the structure says what the generator can do, the builder whether one is fitted.
 *
 * <p>Every figure is authored and first-pass, to be set in play. Flux is counted in damage: a blocked round adds
 * its damage times {@code fluxPerDamage}.
 *
 * @param radiusCells          how far the arc stands from the gun, in cells
 * @param arcDegrees           the arc's full width when raised
 * @param fluxCapacity         flux the capacitors hold; reaching it overloads the shield
 * @param dissipationPerSecond flux the generator sheds every second, shield up or down
 * @param upkeepPerSecond      flux holding the shield up costs every second
 * @param fluxPerDamage        flux per point of damage a blocked round would have done
 * @param fluxPerRound         flux each round the gun fires costs while the shield is up
 * @param raiseSeconds         how long the arc takes to grow from nothing to its full width
 * @param lowerSeconds         how long it takes to fold away
 * @param overloadSeconds      how long an overloaded shield is down, its gun silent, while the capacitors vent
 * @param turnDegPerSecond     how fast the arc swings round to face a new threat
 * @param lingerSeconds        how long the shield stays up after the last threat
 * @param gunDraw              how an energy gun draws on the same capacitors, or null for a gun that does not
 */
public record FluxShieldSpec(float radiusCells, float arcDegrees, float fluxCapacity, float dissipationPerSecond,
                             float upkeepPerSecond, float fluxPerDamage, float fluxPerRound, float raiseSeconds,
                             float lowerSeconds, float overloadSeconds, float turnDegPerSecond,
                             float lingerSeconds, GunDraw gunDraw) {

    /**
     * An energy gun fed from the shield's capacitors: every round it fires charges them {@code perRound}, shield up
     * or down, so firing spends the shield's headroom. Once they pass {@code holdAbove} of capacity the gun holds fire
     * until they have bled down to {@code resumeBelow}: its rest between spells of fire.
     */
    public record GunDraw(float perRound, float holdAbove, float resumeBelow) {
        public GunDraw {
            positive(perRound, "gunDraw.perRound");
            if (!(resumeBelow >= 0f && resumeBelow < holdAbove && holdAbove <= 1f)) {
                throw new IllegalArgumentException("fluxShield gunDraw needs 0 <= resumeBelow < holdAbove <= 1");
            }
        }
    }

    /** A shield whose gun does not draw on its capacitors. */
    public FluxShieldSpec(float radiusCells, float arcDegrees, float fluxCapacity, float dissipationPerSecond,
                          float upkeepPerSecond, float fluxPerDamage, float fluxPerRound, float raiseSeconds,
                          float lowerSeconds, float overloadSeconds, float turnDegPerSecond, float lingerSeconds) {
        this(radiusCells, arcDegrees, fluxCapacity, dissipationPerSecond, upkeepPerSecond, fluxPerDamage,
                fluxPerRound, raiseSeconds, lowerSeconds, overloadSeconds, turnDegPerSecond, lingerSeconds, null);
    }

    public FluxShieldSpec {
        positive(radiusCells, "radiusCells");
        if (!Float.isFinite(arcDegrees) || arcDegrees <= 0f || arcDegrees > 360f) {
            throw new IllegalArgumentException("fluxShield arcDegrees must be in (0, 360]");
        }
        positive(fluxCapacity, "fluxCapacity");
        nonNegative(dissipationPerSecond, "dissipationPerSecond");
        nonNegative(upkeepPerSecond, "upkeepPerSecond");
        nonNegative(fluxPerDamage, "fluxPerDamage");
        nonNegative(fluxPerRound, "fluxPerRound");
        positive(raiseSeconds, "raiseSeconds");
        positive(lowerSeconds, "lowerSeconds");
        positive(overloadSeconds, "overloadSeconds");
        positive(turnDegPerSecond, "turnDegPerSecond");
        nonNegative(lingerSeconds, "lingerSeconds");
    }

    /** Reads a structure's {@code fluxShield} block. */
    public static FluxShieldSpec parse(String structureId, JSONObject json) throws JSONException {
        try {
            return new FluxShieldSpec(
                    (float) json.getDouble("radiusCells"),
                    (float) json.getDouble("arcDegrees"),
                    (float) json.getDouble("fluxCapacity"),
                    (float) json.getDouble("dissipationPerSecond"),
                    (float) json.getDouble("upkeepPerSecond"),
                    (float) json.getDouble("fluxPerDamage"),
                    (float) json.getDouble("fluxPerRound"),
                    (float) json.getDouble("raiseSeconds"),
                    (float) json.getDouble("lowerSeconds"),
                    (float) json.getDouble("overloadSeconds"),
                    (float) json.getDouble("turnDegPerSecond"),
                    (float) json.getDouble("lingerSeconds"),
                    json.has("gunDraw") ? new GunDraw(
                            (float) json.getJSONObject("gunDraw").getDouble("perRound"),
                            (float) json.getJSONObject("gunDraw").getDouble("holdAbove"),
                            (float) json.getJSONObject("gunDraw").getDouble("resumeBelow")) : null);
        } catch (IllegalArgumentException e) {
            throw new JSONException("Structure '" + structureId + "': " + e.getMessage());
        }
    }

    private static void positive(float value, String field) {
        if (!Float.isFinite(value) || value <= 0f) {
            throw new IllegalArgumentException("fluxShield " + field + " must be finite and positive");
        }
    }

    private static void nonNegative(float value, String field) {
        if (!Float.isFinite(value) || value < 0f) {
            throw new IllegalArgumentException("fluxShield " + field + " must be finite and non-negative");
        }
    }
}
