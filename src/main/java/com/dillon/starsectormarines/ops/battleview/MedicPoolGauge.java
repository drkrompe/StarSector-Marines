package com.dillon.starsectormarines.ops.battleview;

/**
 * What the medic's mark says about the energy pool ({@code squad-medic.md}):
 * how full it is, and whether it holds enough to begin a treatment.
 *
 * <p>The start gate a medic actually applies is the smaller of the patient's
 * need and {@code minStartEnergy}, so a medic below the gate can still top off
 * a scratch. The mark reads the general case: below {@code minStartEnergy} the
 * medic cannot take on anybody worth treating, and the pool is drawn spent.
 */
final class MedicPoolGauge {

    /** Residue below which a pool counts as having reached its gate, matching the sim's own tolerance. */
    private static final float EPSILON = 1e-4f;

    private MedicPoolGauge() { }

    /** The pool as a fraction of its cap, in {@code [0, 1]}; a pool with no cap reads empty. */
    static float fill(float energy, float max) {
        if (!(max > 0f) || !(energy > 0f)) return 0f;
        return Math.min(1f, energy / max);
    }

    /** Whether the pool holds enough to begin a worthwhile treatment. */
    static boolean ready(float energy, float minStartEnergy) {
        return energy >= minStartEnergy - EPSILON;
    }
}
