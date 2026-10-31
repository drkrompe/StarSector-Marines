package com.dillon.starsectormarines.battle.mech;

/**
 * What the internals in a machine's auxiliary compartment make of it at the drop, beyond its frame values and its
 * loader: the one record a mech's frame ({@link MechFrame#systems}) and a vehicle's spawn read alike, so a part does
 * the same thing on every machine (mechs-nouns.md, one machine, one part catalog). Battle reads only these numbers;
 * the parts themselves stay campaign authority.
 *
 * @param structureFactor what the machine's structure is multiplied by (a spall liner's fifth more)
 * @param ammoFactor      what every finite magazine, rack and belt it carries is multiplied by (a ready rack's half
 *                        again)
 */
public record MachineSystems(float structureFactor, float ammoFactor) {

    /** A compartment that changes none of these. */
    public static final MachineSystems NONE = new MachineSystems(1f, 1f);

    public MachineSystems {
        if (!(structureFactor > 0f) || !Float.isFinite(structureFactor)
                || !(ammoFactor > 0f) || !Float.isFinite(ammoFactor)) {
            throw new IllegalArgumentException("Machine system factors must be finite and positive");
        }
    }

    /** {@code rounds} of a finite magazine, rack or belt as this machine carries them: never fewer than one. */
    public int rounds(int rounds) {
        return rounds <= 0 || ammoFactor == 1f ? rounds : Math.max(1, Math.round(rounds * ammoFactor));
    }
}
