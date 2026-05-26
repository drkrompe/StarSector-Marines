package com.dillon.starsectormarines.battle.unit;

/**
 * Side a unit fights for in a battle simulation. Marines are the player's
 * squad; defenders are whatever opposed force the mission generated (faction
 * garrison, pirate gang, etc). CIVILIAN is the neutral bucket — non-combatants
 * present on the map (residents, lab techs, dockworkers) who don't count
 * toward either side's elimination objective and who get shot at only
 * incidentally. Future missions with multiple opposed factions can keep using
 * MARINE/DEFENDER as the two-sided abstraction; tier comes from
 * {@link UnitType}, not faction.
 *
 * <p>Win condition (v1): last faction with surviving combatants wins.
 */
public enum Faction {
    MARINE,
    DEFENDER,
    CIVILIAN
}
