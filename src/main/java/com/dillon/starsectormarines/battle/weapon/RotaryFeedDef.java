package com.dillon.starsectormarines.battle.weapon;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

/**
 * A rotary gun's feed, read from a mech weapon's {@code sim.rotary} block: the barrels spin up while the
 * trigger is held, coast at speed for a moment after it is let go, then spin down; the rounds come off a
 * belt that a hopper refills at a fixed rate for the whole fight (mechs-nouns.md, Rotary guns).
 *
 * <p>Spin-up time, refill rate and the weapon's damage are what make a better chaingun, so all three are
 * authored per weapon rather than per chassis.
 *
 * @param spinUpSeconds rest to full speed with the trigger held
 * @param coastSeconds how long the barrels hold full speed after the trigger is let go
 * @param spinDownSeconds full speed to rest once the coast is over
 * @param firstTurnRate rounds a second as the barrels start to turn
 * @param fullRate rounds a second at full speed
 * @param belt rounds the belt holds
 * @param refillPerSecond rounds the hopper puts back on the belt every second, firing or not
 * @param fluxPerRound flux each round commits to the chassis reservoir
 */
public record RotaryFeedDef(float spinUpSeconds, float coastSeconds, float spinDownSeconds,
                            float firstTurnRate, float fullRate, int belt, float refillPerSecond,
                            float fluxPerRound) {

    /**
     * The engagement the rated rate is quoted over: what a commander, a target picker or a stat sheet
     * should expect from the gun in a fight, as opposed to its peak.
     */
    public static final float RATED_ENGAGEMENT_SECONDS = 30f;

    private static final Set<String> FIELDS = Set.of("spinUp", "coast", "spinDown", "firstTurnRate",
            "fullRate", "belt", "refill", "fluxPerRound");

    public RotaryFeedDef {
        if (!(spinUpSeconds > 0f) || coastSeconds < 0f || !(spinDownSeconds > 0f)
                || !(firstTurnRate > 0f) || fullRate < firstTurnRate || belt < 1
                || !(refillPerSecond > 0f) || fluxPerRound < 0f) {
            throw new IllegalArgumentException("Invalid rotary feed");
        }
    }

    /** Rounds a second at {@code spin} (0 at rest, 1 at full speed). */
    public float rate(float spin) {
        float s = Math.max(0f, Math.min(1f, spin));
        return firstTurnRate + (fullRate - firstTurnRate) * s;
    }

    /**
     * Rounds a second over {@link #RATED_ENGAGEMENT_SECONDS} from a full belt, holding back the reserve an
     * autonomous gunner keeps: the figure estimates and sheets read instead of the peak.
     */
    public float ratedRoundsPerSecond() {
        float rounds = refillPerSecond * RATED_ENGAGEMENT_SECONDS + belt * 0.8f;
        return Math.min(fullRate, rounds / RATED_ENGAGEMENT_SECONDS);
    }

    /** Reads {@code sim.rotary}; null when the weapon has none. */
    static RotaryFeedDef parse(JSONObject rotary, String weaponId) throws JSONException {
        if (rotary == null) return null;
        var keys = rotary.keys();
        while (keys.hasNext()) {
            String key = String.valueOf(keys.next());
            if (!FIELDS.contains(key)) {
                throw new JSONException("Weapon '" + weaponId + "' rotary has unknown field '" + key + "'");
            }
        }
        try {
            return new RotaryFeedDef(
                    (float) rotary.getDouble("spinUp"),
                    (float) rotary.getDouble("coast"),
                    (float) rotary.getDouble("spinDown"),
                    (float) rotary.getDouble("firstTurnRate"),
                    (float) rotary.getDouble("fullRate"),
                    rotary.getInt("belt"),
                    (float) rotary.getDouble("refill"),
                    (float) rotary.getDouble("fluxPerRound"));
        } catch (IllegalArgumentException e) {
            throw new JSONException("Weapon '" + weaponId + "' rotary: " + e.getMessage());
        }
    }
}
