package com.dillon.starsectormarines.battle.turret;

/**
 * What a turret is allowed to shoot at. Used to filter target acquisition so
 * a dedicated anti-air mount doesn't waste shots on infantry, and a point-defense
 * gun doesn't try to chase ground units when a missile is incoming.
 *
 * <p>Prepared ground AA emplacements acquire airborne entities through the air body
 * service. Point defense still needs its own projectile target source.
 */
public enum TurretRole {
    /** Anti-ground. Targets enemy combatant {@code Entity}s on the cell grid. */
    A2G,
    /** Anti-air. Targets airborne shuttles and fighters. */
    AA,
    /** Point defense. Targets in-flight projectiles and missiles once those are surfaced as targets. */
    POINT_DEFENSE
}
