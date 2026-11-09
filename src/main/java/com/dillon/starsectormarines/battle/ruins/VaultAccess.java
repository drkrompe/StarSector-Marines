package com.dillon.starsectormarines.battle.ruins;

import java.util.ArrayList;
import java.util.List;

/**
 * How a ruins site's vault door opens beyond its power ({@code ruins-expedition.md}, Works), drawn per site from a
 * pool so the same puzzle does not come up every time (owner pick {@code ruins-vault-access}). The draw reads only a
 * seed taken from the site's ground, so a site drawn again from the same ground draws the same way.
 */
public enum VaultAccess {
    /** Powered, the door waits on two keys held together: the records console and the vault keypad. */
    TWO_KEYS,
    /** Powered, the door waits on the vault code: learned at the records terminal, entered at the vault keypad. */
    RECORDS_CODE,
    /**
     * The last junction feeds the vault door on its store side and the way in on its way-on side: the company gets in
     * first, then throws it.
     */
    CROSSED_FEED,
    /** Power alone opens it: a site with no records and no crossing to make. */
    POWER_ONLY;

    /** Whether the variant needs the records terminal and the vault keypad. */
    public boolean keypad() { return this == TWO_KEYS || this == RECORDS_CODE; }

    /**
     * The site's variant: uniform over those its ground allows. The keypad variants need a records terminal; the
     * crossed feed needs a door on the last layer's way on other than the vault's own to cross with.
     */
    public static VaultAccess draw(long seed, boolean records, boolean crossable) {
        List<VaultAccess> pool = new ArrayList<>(3);
        if (records) {
            pool.add(TWO_KEYS);
            pool.add(RECORDS_CODE);
        }
        if (crossable) pool.add(CROSSED_FEED);
        if (pool.isEmpty()) return POWER_ONLY;
        return pool.get((int) Math.floorMod(mix(seed), (long) pool.size()));
    }

    /** SplitMix64's finaliser: neighbouring seeds draw unrelated variants. */
    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
