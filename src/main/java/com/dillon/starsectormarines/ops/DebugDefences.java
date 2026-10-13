package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.TargetProfile;

/**
 * The DEBUG board's statement of which ground defences the target world holds,
 * overriding what its market's industries said. A mission that states none
 * ({@code null}) keeps the market's own answer.
 */
public enum DebugDefences {
    NONE(false, false, false),
    GROUND(true, false, false),
    BATTERIES(true, true, false),
    SHIELD(false, false, true),
    FORTRESS(true, true, true);

    private final boolean groundDefences;
    private final boolean heavyBatteries;
    private final boolean planetaryShield;

    DebugDefences(boolean groundDefences, boolean heavyBatteries, boolean planetaryShield) {
        this.groundDefences = groundDefences;
        this.heavyBatteries = heavyBatteries;
        this.planetaryShield = planetaryShield;
    }

    /** The profile with its three defence signals replaced by this statement. */
    public TargetProfile applyTo(TargetProfile profile) {
        return profile.withDefences(groundDefences, heavyBatteries, planetaryShield);
    }
}
