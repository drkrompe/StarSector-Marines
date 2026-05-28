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
 *   <li>{@link #HE} — flash + fire burst + 2-3 smoke puffs. Heavy mortar,
 *       rocket launcher. Caller layers an explosion clip on top.</li>
 * </ul>
 */
public enum ImpactProfile {
    RIFLE,
    KINETIC,
    HE
}
