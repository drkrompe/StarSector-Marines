package com.dillon.starsectormarines.battle;

/**
 * Side a unit fights for in a battle simulation. Marines are the player's
 * squad; defenders are whatever opposed force the mission generated (faction
 * garrison, pirate gang, etc).
 *
 * <p>Win condition (v1): last faction with surviving units wins.
 */
public enum Faction {
    MARINE,
    DEFENDER
}
