package com.dillon.starsectormarines.battle.warship;

/**
 * What a warship has been told to do.
 *
 * <p>An order is the mission's voice, not the ship's: the ship carries out what
 * it is given and chooses only within it. A cinematic names ground and the ship
 * turns, traverses and fires; a later commander, a campaign power or an enemy
 * garrison can issue the same orders to the same ship, which is why the order is
 * a value and not a script.
 */
public sealed interface WarshipOrder {

    /** Fire nothing. A burst already in flight completes. */
    record HoldFire() implements WarshipOrder {
        public static final HoldFire INSTANCE = new HoldFire();
    }

    /**
     * Engage every hostile standing inside a circle of ground, each mount choosing
     * for itself among the enemies it can reach and traverse to.
     *
     * @param bringGunsToBear when true the ship also turns to the heading that
     *        puts the most firepower on the area, and holds it; when false it
     *        fires from whatever heading it has, so a broadside that cannot bear
     *        stays silent
     */
    record FireOnArea(float x, float y, float radius, boolean bringGunsToBear) implements WarshipOrder {
        public FireOnArea {
            if (!(radius > 0f)) throw new IllegalArgumentException("an area needs a radius");
        }

        public boolean contains(float px, float py) {
            float dx = px - x;
            float dy = py - y;
            return dx * dx + dy * dy <= radius * radius;
        }
    }
}
