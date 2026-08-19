package com.dillon.starsectormarines.battle.combat.fx;

/**
 * Visual character of an impact, decoupled from the weapon catalog. Turret
 * kinds, marine primaries, and marine secondaries all map to one of these
 * profiles so {@link ImpactFx} can dispatch by what the impact *looks like*,
 * not by which enum the shot came from.
 *
 * <ul>
 *   <li>{@link #RIFLE} — small spark + tiny dust puff. Fast and incidental.
 *       Rifle, SMG, vulcan-class fire.</li>
 *   <li>{@link #KINETIC} — bigger flash + small smoke. Mid-range autocannon
 *       shells, railgun rounds, dual flak, hephaestus.</li>
 *   <li>{@link #HE} — flash + fire burst + 2-3 smoke puffs. Rockets and
 *       grenades. Caller layers an explosion clip on top.</li>
 *   <li>{@link #CANNON_HE} — heavier gun-launched HE: a sharp muzzle blast,
 *       vanilla explosion frame + shock ring, fire, and lingering smoke.
 *       Used by large direct-fire cannon shells rather than rockets.</li>
 * </ul>
 */
public enum ImpactProfile {
    RIFLE,
    KINETIC,
    HE,
    CANNON_HE;

    /** Whether the impact should pair its particle recipe with an explosion sound. */
    public boolean explosive() {
        return this == HE || this == CANNON_HE;
    }
}
