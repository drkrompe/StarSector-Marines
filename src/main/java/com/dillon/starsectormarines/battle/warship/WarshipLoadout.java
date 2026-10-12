package com.dillon.starsectormarines.battle.warship;

/**
 * What a warship is fitted with: the hull whose slots it fills and the weapon
 * each slot carries. The seam between where a loadout comes from (a stock
 * variant file today, a campaign fleet member's fitted variant for a flagship)
 * and {@link Warship#fit(WarshipLoadout, com.dillon.starsectormarines.battle.unit.Faction, float, float, float)},
 * which cares only about the answer.
 */
public interface WarshipLoadout {

    /** The hull whose {@code .ship} spec places the slots. */
    String hullId();

    /** The weapon id fitted in {@code slotId}, or {@code null} when it is empty. */
    String weaponInSlot(String slotId);
}
