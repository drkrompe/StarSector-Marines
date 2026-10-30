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
 * authored per weapon rather than per chassis. The authored refill is the standard loader's; the machine's loader
 * multiplies it ({@link #fitted}, {@link LoaderPace}).
 *
 * <p>A gun with no spin-up (the light machine gun, the autocannons) authors {@code spinUp} 0 and its first-turn
 * rate equal to its full rate: it fires at full rate the instant the trigger is held, and only its belt and hopper
 * limit a hold.
 *
 * @param spinUpSeconds rest to full speed with the trigger held; 0 for a gun that is at speed on the press
 * @param coastSeconds how long the barrels hold full speed after the trigger is let go
 * @param spinDownSeconds full speed to rest once the coast is over; 0 for a gun at rest the moment the coast ends
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
        if (!(spinUpSeconds >= 0f) || coastSeconds < 0f || !(spinDownSeconds >= 0f)
                || !(firstTurnRate > 0f) || fullRate < firstTurnRate || belt < 1
                || !(refillPerSecond > 0f) || fluxPerRound < 0f) {
            throw new IllegalArgumentException("Invalid rotary feed");
        }
    }

    /**
     * This feed on a machine whose loader refills at {@code loaderFactor} ({@link LoaderPace}) and whose ready rack
     * holds {@code beltFactor} times the belt; {@code this} for the standard loader and the authored belt.
     */
    public RotaryFeedDef fitted(float loaderFactor, float beltFactor) {
        if (loaderFactor == LoaderPace.STANDARD && beltFactor == 1f) return this;
        return new RotaryFeedDef(spinUpSeconds, coastSeconds, spinDownSeconds, firstTurnRate, fullRate,
                Math.max(1, Math.round(belt * beltFactor)), LoaderPace.refillPerSecond(refillPerSecond, loaderFactor),
                fluxPerRound);
    }

    /** Whether the gun fires at full rate the instant its trigger is held. */
    public boolean instant() {
        return spinUpSeconds == 0f;
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
