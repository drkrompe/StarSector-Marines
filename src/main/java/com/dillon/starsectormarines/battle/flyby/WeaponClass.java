package com.dillon.starsectormarines.battle.flyby;

/**
 * Per-weapon delivery model. Pure tag: no behavior on the enum itself.
 *
 * <p>What it selects today is the {@code air.AirOrdnance} a profile runs in
 * with, a projectile fighter carrying bombs and a tracer one carrying guns.
 * The tracer and projectile tuning blocks on {@link FighterProfile} are the
 * last remnants of the deleted overlay's own fire resolution and are read by
 * nothing; they fold into ordnance presets with the rest of that file.
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
