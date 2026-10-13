package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;

/**
 * The DEBUG board's statement of which ground defences the battle carries,
 * overriding the mission type's own policy. A mission that states none
 * ({@code null}) keeps that policy. Each value is a {@link GroundDefences}
 * declaration.
 */
public enum DebugDefences {
    NONE(0, false, 0),
    BATTERIES(2, false, 0),
    SHIELD(0, true, 0),
    /** The dispersed network alone: untuned count, enough to read the field on the canonical Conquest map. */
    NESTS(0, false, 12),
    FORTRESS(2, true, 12),
    /** Two walled battery forts, each a platform with fire-control bunker, magazine and power building. */
    BATTERY_FORTS(0, false, 0, FortKind.BATTERY_FORT, 2),
    /** One walled shield fort: the generator in an inner keep, with a power plant and a control room. */
    SHIELD_FORT(0, false, 0, FortKind.SHIELD_FORT, 1);

    private final GroundDefences declaration;

    DebugDefences(int batteries, boolean shieldGenerator, int nests) {
        this.declaration = new GroundDefences(batteries, shieldGenerator, nests);
    }

    DebugDefences(int batteries, boolean shieldGenerator, int nests, FortKind fort, int forts) {
        this.declaration = new GroundDefences(batteries, shieldGenerator, nests).withForts(fort, forts);
    }

    /** The declaration this statement makes. */
    public GroundDefences declaration() {
        return declaration;
    }
}

