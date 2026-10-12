package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.warship.WarshipLoadout;

/**
 * The Shepherd as a tender carries it: its hull's slots and none of its guns.
 *
 * <p>The hull table and spec still come from the game's own data; what is left
 * off is the vanilla variant's missile rack and machine gun. A tender's weapons
 * are its drones, and a gun it could fire on its own would make the drones a
 * decoration rather than the thing the ship is for.
 */
final class TenderLoadout implements WarshipLoadout {

    @Override public String hullId() {
        return Tender.HULL_ID;
    }

    @Override public String weaponInSlot(String slotId) {
        return null;
    }
}
