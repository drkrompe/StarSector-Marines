package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.evacuation.BruteChargeRules.Kind;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.marine.ArmorClass;

/**
 * The numbers and pure decisions of a blast's shove ({@code mechs-nouns.md}, Blast impulse): how heavy each kind of
 * body is to a blast, what a shove does to a body on foot, and how the rounds of a salvo add up on one. The weight
 * classes are the Brute charge's ({@link BruteChargeRules#vehicleKind}, {@link BruteChargeRules#mechKind}), and so
 * are the slide, the knockdown and its length. Every number is a starting value to feel out in play.
 */
final class BlastImpulseRules {

    /**
     * What a body on foot weighs to a blast, tonnes: more than the man, because he stands braced on the ground. A
     * power suit is a rig of its own.
     */
    static final float INFANTRY_TONNES = .8f;
    static final float POWER_SUIT_TONNES = 2f;
    /** A shove below this (cells a second) moves no one: the trace a near miss leaves. */
    static final float MIN_DV = .3f;
    /** A shove of at least this on a body on foot staggers it: a step back and a moment's lost aim. */
    static final float STAGGER_DV = 1f;
    static final float STAGGER_HOLD = .25f;
    /**
     * What a body on foot has taken, summed over the salvo, that knocks it down: it goes down for the Brute's
     * trample hold ({@link BruteChargeRules#TRAMPLE_HOLD}) and slides as a thrown body does.
     */
    static final float TUMBLE_DV = 3f;
    /** Seconds over which what a body has taken fades: the rounds of a salvo add up, those of separate ones do not. */
    static final float MEMORY = .5f;
    /** The fastest a blast slides a body on foot: about a Brute's throw ({@link BruteChargeRules#THROW}). */
    static final float BODY_MAX_SPEED = BruteChargeRules.launchSpeed(BruteChargeRules.THROW, BruteChargeRules.BODY_SLIDE);
    /** The fastest blasts slide a hull or a mech, cells a second. */
    static final float HULL_MAX_SPEED = 1.5f;
    /** A hull slid at least this fast kicks up dust where its tires scrub. */
    static final float DUST_DV = .25f;

    private BlastImpulseRules() {}

    /** What a blast does to a body on foot. */
    enum Response { NONE, STEP, STAGGER, TUMBLE }

    /** Whether a blast moves a body of {@code kind} at all: the heavy classes and emplacements stand. */
    static boolean moves(Kind kind) {
        return switch (kind) {
            case INFANTRY, LIGHT_VEHICLE, MEDIUM_VEHICLE, LIGHT_MECH, MEDIUM_MECH -> true;
            case EMPLACEMENT, HEAVY_VEHICLE, HEAVY_MECH -> false;
        };
    }

    /** What a body on foot weighs to a blast. */
    static float infantryTonnes(ArmorClass armor) {
        return armor == ArmorClass.POWER_SUIT ? POWER_SUIT_TONNES : INFANTRY_TONNES;
    }

    /** What a mech of {@code relativeMass} weighs, the Brute's measure ({@link BruteChargeRules#MECH_TONNES}). */
    static float mechTonnes(float relativeMass) {
        return BruteChargeRules.MECH_TONNES * relativeMass;
    }

    /**
     * What a body has taken, cells a second, after {@code seconds} without a blast: it fades exponentially over
     * {@link #MEMORY}.
     */
    static float faded(float taken, float seconds) {
        if (seconds <= 0f) return taken;
        return taken * (float) Math.exp(-seconds / MEMORY);
    }

    /**
     * What a blast giving {@code dv} does to a body on foot that has {@code taken} (already faded) from the blasts
     * before it: the sum decides a knockdown, this blast alone a stagger.
     */
    static Response respond(float dv, float taken) {
        if (dv < MIN_DV) return Response.NONE;
        if (taken + dv >= TUMBLE_DV) return Response.TUMBLE;
        return dv >= STAGGER_DV ? Response.STAGGER : Response.STEP;
    }

    static int ticks(float seconds) {
        return Math.round(seconds / BattleSimulation.TICK_DT);
    }
}
