package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.unit.Entity;

/**
 * What a turret is allowed to shoot at. Used to filter target acquisition so
 * a dedicated anti-air mount doesn't waste shots on infantry, and a point-defense
 * gun doesn't try to chase ground units when a missile is incoming.
 *
 * <p>Today only {@link #A2G} is operationally targetable — there are no
 * airborne entities on the unit list yet, and the in-flight projectile queue
 * isn't surfaced to turret targeting. The other two are named placeholders so
 * the rest of the code can pattern-match on role today; their target lookups
 * return empty until anti-air / point-defense entities land.
 */
public enum TurretRole {
    /** Anti-ground. Targets enemy combatant {@link Entity}s on the cell grid. */
    A2G,
    /** Anti-air. Targets shuttles / fighters once those become targetable entities. */
    AA,
    /** Point defense. Targets in-flight projectiles and missiles once those are surfaced as targets. */
    POINT_DEFENSE
}
