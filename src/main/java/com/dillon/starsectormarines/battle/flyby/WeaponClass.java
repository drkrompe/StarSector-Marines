package com.dillon.starsectormarines.battle.flyby;

/**
 * Per-weapon delivery model. Picks which fire-resolution path a profile takes
 * in the flyby overlay. Pure tag — no behavior on the enum itself; the
 * dispatch lives in {@link FlybyOverlay}'s fire methods.
 */
public enum WeaponClass {
    /**
     * Hitscan line of fire — instant endpoint resolution. Damage applies on the
     * same tick the shot is fired; tracer particle is purely visual. Default for
     * all baseline fighters (chainguns, autocannons, pulse lasers, ion bolts).
     */
    TRACER,

    /**
     * Homing self-propelled projectile — persists across frames as its own
     * entity, steers toward a locked target, detonates on impact (or after a
     * lifetime cap) with AoE damage. Slower fire cadence than TRACER; missile
     * fighters are precision tools, not spray-and-pray.
     */
    PROJECTILE
}
