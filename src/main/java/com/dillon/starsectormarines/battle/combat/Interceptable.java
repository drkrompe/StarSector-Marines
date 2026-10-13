package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * Ordnance in flight that point defence may shoot down, whatever carries it.
 *
 * <p>A defender (a warship's flak mount, a carried interceptor pod) needs only
 * where the thing is, how fast it crosses, how tough it is, whose it is, and a
 * way to mark it dead. A destroyed body never lands: interception is not
 * damage, it is the removal of a committed landing.
 */
public interface Interceptable {

    /** The side that fired it. */
    Faction faction();

    /** World X now, in cells. */
    float x();

    /** World Y now, in cells. */
    float y();

    /** Cells per second along its flight now. */
    float speedCells();

    /** How much punishment it takes, in the game's hit points; a tougher body is harder to stop. */
    float hitpoints();

    /** World X of the ground it is bound for, in cells; where it is now when it has no declared aim. */
    default float landingX() { return x(); }

    /** World Y of the ground it is bound for, in cells. */
    default float landingY() { return y(); }

    /** Whether point defence has already destroyed it. */
    boolean isIntercepted();

    /**
     * Whether point defence may engage it now. True for ordnance generally; false for a payload that is no
     * longer a body in the air, such as a beam already released, which nothing can shoot down.
     */
    default boolean canBeIntercepted() { return true; }

    /** Marks it destroyed; it is dropped without landing. */
    void intercept();

    /** Hit points up to which ordnance is as soft as flak expects (a Locust is 60, an Annihilator rocket 50). */
    float SOFT_HITPOINTS = 60f;
    /** Hit points over the soft figure that halve a round's chance. */
    float TOUGHNESS_SCALE = 600f;

    /**
     * How much a body's hit points blunt a defender's chance: none up to {@link #SOFT_HITPOINTS}, then
     * {@code 1 / (1 + excess / TOUGHNESS_SCALE)}, so a swarm of weak missiles is the case flak was
     * designed for and a torpedo is not. Zero or unknown hit points are soft.
     */
    static float toughnessFactor(float hitpoints) {
        if (hitpoints <= SOFT_HITPOINTS) return 1f;
        return 1f / (1f + (hitpoints - SOFT_HITPOINTS) / TOUGHNESS_SCALE);
    }
}
