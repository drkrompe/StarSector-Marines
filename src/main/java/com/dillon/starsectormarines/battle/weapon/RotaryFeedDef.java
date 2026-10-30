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
 * <p>A <b>beam</b> ({@link #beam}, {@code sim.beam}) is the energy weapon's held trigger (mechs-nouns.md, Beam
 * weapons): no barrels and no belt, only an emitter that lays pulses down the lance at a fixed rate while the
 * trigger is held, each committing its share of the beam's flux a second. Flux is the only thing that stops it,
 * so it has no belt to empty, no hopper and nothing for a loader or a ready rack to multiply.
 *
 * @param fluxPerRound flux each round commits to the chassis reservoir
 * @param beam a beam: unlimited, flux-bound, at speed on the press; {@code belt} and {@code refillPerSecond} unused
 * @param wanderPeriodSeconds a beam's aim crawl ({@link BeamWander}): beam seconds between the path's knots; 0 for a
 *     feed whose every round draws its own offset
 * @param wanderAmplitude a beam's crawl amplitude, 1 spending the weapon's error budget exactly; unused without a period
 */
public record RotaryFeedDef(float spinUpSeconds, float coastSeconds, float spinDownSeconds,
                            float firstTurnRate, float fullRate, int belt, float refillPerSecond,
                            float fluxPerRound, boolean beam, float wanderPeriodSeconds, float wanderAmplitude) {

    /**
     * The engagement the rated rate is quoted over: what a commander, a target picker or a stat sheet
     * should expect from the gun in a fight, as opposed to its peak.
     */
    public static final float RATED_ENGAGEMENT_SECONDS = 30f;

    private static final Set<String> FIELDS = Set.of("spinUp", "coast", "spinDown", "firstTurnRate",
            "fullRate", "belt", "refill", "fluxPerRound");
    private static final Set<String> BEAM_FIELDS = Set.of("pulseRate", "fluxPerSecond", "wanderPeriod",
            "wanderAmplitude");

    /**
     * An autonomous beam stops asking for pulses once the reservoir holds this share of its capacity, and asks again
     * at {@link #BEAM_AI_RESUME_SHARE}: it fires in measured lances and leaves the frame's other mounts headroom
     * (a missile salvo's 40 still fits under the AI's 90 of the stock 100), rather than riding the reservoir up to
     * the AI's own hold at 75.
     */
    public static final float BEAM_AI_STOP_SHARE = 0.5f;
    public static final float BEAM_AI_RESUME_SHARE = 0.3f;

    /** The stock frame a beam's rated rate is quoted on: 100 capacity shedding 6 a second ({@code MechFlux}). */
    private static final float STOCK_CAPACITY = 100f;
    private static final float STOCK_DISSIPATION = 6f;

    public RotaryFeedDef {
        if (!(spinUpSeconds >= 0f) || coastSeconds < 0f || !(spinDownSeconds >= 0f)
                || !(firstTurnRate > 0f) || fullRate < firstTurnRate
                || !beam && (belt < 1 || !(refillPerSecond > 0f))
                || fluxPerRound < 0f || beam && !(fluxPerRound > 0f)
                || !(wanderPeriodSeconds >= 0f) || !Float.isFinite(wanderPeriodSeconds)
                || wanderPeriodSeconds > 0f && (!beam || !(wanderAmplitude > 0f) || !Float.isFinite(wanderAmplitude))) {
            throw new IllegalArgumentException("Invalid rotary feed");
        }
    }

    /** A rotary gun's feed: barrels, a belt and a hopper. */
    public RotaryFeedDef(float spinUpSeconds, float coastSeconds, float spinDownSeconds, float firstTurnRate,
                         float fullRate, int belt, float refillPerSecond, float fluxPerRound) {
        this(spinUpSeconds, coastSeconds, spinDownSeconds, firstTurnRate, fullRate, belt, refillPerSecond,
                fluxPerRound, false, 0f, 0f);
    }

    /**
     * A beam laying {@code pulseRate} pulses a second down its lance while held, costing {@code fluxPerSecond}: at
     * speed on the press, stopped on release, no belt.
     */
    public static RotaryFeedDef beam(float pulseRate, float fluxPerSecond) {
        return beam(pulseRate, fluxPerSecond, 0f, 0f);
    }

    /**
     * As {@link #beam(float, float)}, its aim crawling along a {@link BeamWander} path whose knots are
     * {@code wanderPeriodSeconds} of beam time apart at {@code wanderAmplitude}; a period of 0 aims every pulse afresh.
     */
    public static RotaryFeedDef beam(float pulseRate, float fluxPerSecond, float wanderPeriodSeconds,
                                     float wanderAmplitude) {
        if (!(pulseRate > 0f) || !(fluxPerSecond > 0f)) throw new IllegalArgumentException("Invalid beam");
        return new RotaryFeedDef(0f, 0f, 0f, pulseRate, pulseRate, 0, 0f, fluxPerSecond / pulseRate, true,
                wanderPeriodSeconds, wanderAmplitude);
    }

    /**
     * A fresh crawl for one mount of this beam, or null when its pulses aim afresh: a rotary gun, or a beam authored
     * without a wander.
     */
    public BeamWander newWander() {
        return beam && wanderPeriodSeconds > 0f
                ? new BeamWander(wanderPeriodSeconds, wanderAmplitude) : null;
    }

    /** Flux a second with the trigger held at full rate: a beam's whole cost, a rotary gun's at full spin. */
    public float fluxPerSecond() {
        return fluxPerRound * fullRate;
    }

    /**
     * This feed on a machine whose loader refills at {@code loaderFactor} ({@link LoaderPace}) and whose ready rack
     * holds {@code beltFactor} times the belt; {@code this} for the standard loader and the authored belt, and for a
     * beam, which has neither.
     */
    public RotaryFeedDef fitted(float loaderFactor, float beltFactor) {
        if (beam || loaderFactor == LoaderPace.STANDARD && beltFactor == 1f) return this;
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
     * autonomous gunner keeps: the figure estimates and sheets read instead of the peak. A beam's limit is flux
     * rather than a belt: on the stock frame from a cool start, the share an autonomous gunner spends before it holds
     * ({@link #BEAM_AI_STOP_SHARE}) plus what the frame sheds over the engagement.
     */
    public float ratedRoundsPerSecond() {
        if (beam) {
            float flux = BEAM_AI_STOP_SHARE * STOCK_CAPACITY + STOCK_DISSIPATION * RATED_ENGAGEMENT_SECONDS;
            return Math.min(fullRate, flux / fluxPerRound / RATED_ENGAGEMENT_SECONDS);
        }
        float rounds = refillPerSecond * RATED_ENGAGEMENT_SECONDS + belt * 0.8f;
        return Math.min(fullRate, rounds / RATED_ENGAGEMENT_SECONDS);
    }

    /** Reads {@code sim.beam}; null when the weapon has none. */
    static RotaryFeedDef parseBeam(JSONObject beam, String weaponId) throws JSONException {
        if (beam == null) return null;
        var keys = beam.keys();
        while (keys.hasNext()) {
            String key = String.valueOf(keys.next());
            if (!BEAM_FIELDS.contains(key)) {
                throw new JSONException("Weapon '" + weaponId + "' beam has unknown field '" + key + "'");
            }
        }
        try {
            return beam((float) beam.getDouble("pulseRate"), (float) beam.getDouble("fluxPerSecond"),
                    (float) beam.optDouble("wanderPeriod", 0.0), (float) beam.optDouble("wanderAmplitude", 1.0));
        } catch (IllegalArgumentException e) {
            throw new JSONException("Weapon '" + weaponId + "' beam: " + e.getMessage());
        }
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
