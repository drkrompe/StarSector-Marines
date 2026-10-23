package com.dillon.starsectormarines.battle.infantry;

/**
 * The squad medic's energy pool as arithmetic ({@code squad-medic.md}): what
 * regenerates, what a point of restoration costs, and when a treatment may
 * begin.
 *
 * <p>The pool is the medic's only budget. It regenerates continuously up to its
 * cap, a treatment spends it per point of health or armor actually restored,
 * and an interrupted treatment simply stops spending, so nothing is wasted and
 * nothing needs refunding.
 *
 * <p>Spending continuously would let a medic stop fighting to dribble out each
 * sliver of energy as it regenerates. Two rules hold that off, and they are
 * deliberately asymmetric (hysteresis):
 *
 * <ul>
 *   <li><b>Start gate.</b> A treatment begins only with at least
 *       {@code min(energy to make the patient whole, minStartEnergy)} in the
 *       pool. A patient who needs little can still be topped off from a small
 *       pool; anybody else waits until there is a worthwhile amount.</li>
 *   <li><b>Continue to empty.</b> Once begun, a treatment spends until the
 *       patient is whole, the pool is empty, or reach is broken. The gate is not
 *       checked again mid-treatment.</li>
 *   <li><b>Wound floor.</b> A patient missing less than a small fraction of
 *       their health and armor together is never started on, however full the
 *       pool.</li>
 * </ul>
 */
final class MedicEnergy {

    /** Below this the pool counts as empty, so float residue never keeps a treatment alive. */
    static final float EMPTY = 1e-4f;

    private MedicEnergy() { }

    /** The pool after {@code dt} seconds of regeneration, clamped to {@code [0, max]}. */
    static float regenerate(float energy, float max, float perSecond, float dt) {
        return clamp(energy + perSecond * dt, max);
    }

    /** How many of {@code wanted} points the pool can pay for. */
    static float affordable(float energy, float wanted, float perPoint) {
        if (wanted <= 0f || energy <= 0f) return 0f;
        if (perPoint <= 0f) return wanted;
        return Math.min(wanted, energy / perPoint);
    }

    /** The pool after paying for {@code points}, never below zero. */
    static float spend(float energy, float points, float perPoint) {
        return Math.max(0f, energy - Math.max(0f, points) * perPoint);
    }

    static boolean empty(float energy) {
        return energy <= EMPTY;
    }

    /** Energy needed to begin a treatment on a patient missing {@code missingPoints}. */
    static float startGate(float missingPoints, float perPoint, float minStartEnergy) {
        return Math.min(missingPoints * perPoint, minStartEnergy);
    }

    /**
     * Whether a treatment may begin.
     *
     * @param missingPoints health and armor the patient is missing together
     * @param maxPoints     the patient's maximum health and armor together
     */
    static boolean mayStart(float energy, float missingPoints, float maxPoints,
                            float perPoint, float minStartEnergy, float woundFloorFraction) {
        if (missingPoints <= 0f || missingPoints < woundFloorFraction * maxPoints) return false;
        return !empty(energy) && energy >= startGate(missingPoints, perPoint, minStartEnergy) - EMPTY;
    }

    private static float clamp(float energy, float max) {
        return Math.max(0f, Math.min(max, energy));
    }
}
