package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.GroundDefences;

/**
 * The DEBUG board's statement of which ground defences the battle carries,
 * overriding the mission type's own policy. A mission that states none
 * ({@code null}) keeps that policy. Each value is a {@link GroundDefences}
 * declaration.
 */
public enum DebugDefences {
    NONE(0, false),
    BATTERIES(2, false),
    SHIELD(0, true),
    FORTRESS(2, true);

    private final GroundDefences declaration;

    DebugDefences(int batteries, boolean shieldGenerator) {
        this.declaration = new GroundDefences(batteries, shieldGenerator);
    }

    /** The declaration this statement makes. */
    public GroundDefences declaration() {
        return declaration;
    }
}

