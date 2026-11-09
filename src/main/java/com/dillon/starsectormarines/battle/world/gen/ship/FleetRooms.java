package com.dillon.starsectormarines.battle.world.gen.ship;

/**
 * The sizes of a fleet-facilities company's mock rooms, as its upgrades have bought them
 * (`fleet-facilities.md`). Nothing about any hull: the deck these lay out is a small standard
 * one that no screen shows whole, only cropped to the room being worked in.
 *
 * @param gantries working gantries in the Mech Lab, zero to four; with none there is no Mech Lab
 * @param boatBerths working boat berths, each its own bay
 * @param bunkrooms barracks bunkrooms, nine billets each
 */
public record FleetRooms(int gantries, int boatBerths, int bunkrooms) {
    public FleetRooms {
        if (gantries < 0 || gantries > 4) throw new IllegalArgumentException("a lance has four gantries: " + gantries);
        if (boatBerths < 0) throw new IllegalArgumentException("berths cannot be negative: " + boatBerths);
        if (bunkrooms < 0) throw new IllegalArgumentException("bunkrooms cannot be negative: " + bunkrooms);
    }
}
