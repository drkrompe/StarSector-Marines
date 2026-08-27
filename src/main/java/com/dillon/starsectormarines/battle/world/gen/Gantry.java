package com.dillon.starsectormarines.battle.world.gen;

/**
 * One authored machine berth inside a vehicle bay — where a mech or a vehicle
 * stands while it is worked on.
 *
 * <p>The same relationship a {@link LandingPad} has to a shuttle: generation
 * authors the berth and its clear footprint, and a host decides what occupies
 * it. That split is what lets one generated bay be the player's own lab on the
 * home deck, a half-empty bay on a prize hull, and a contested objective in a
 * boarding action, without three layouts.
 *
 * <p>A berth is deliberately not a fixture. Nothing stands here in the map
 * itself; the cells stay clear, because the machine that occupies the berth is
 * a unit and arrives from the roster.
 */
public final class Gantry {

    /** Cardinal direction a berthed machine faces — out of the bay, toward its lane. */
    public enum Facing {
        NORTH(0, 1), SOUTH(0, -1), EAST(1, 0), WEST(-1, 0);

        public final int dx;
        public final int dy;

        Facing(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }
    }

    /** Cell the machine stands on; the footprint extends from here. */
    public final int centerX;
    public final int centerY;
    /** Clear half-extents around the center, so berths size to what they hold. */
    public final int halfWidth;
    public final int halfHeight;
    public final Facing facing;

    public Gantry(int centerX, int centerY, int halfWidth, int halfHeight, Facing facing) {
        if (halfWidth < 0 || halfHeight < 0) {
            throw new IllegalArgumentException("gantry half extents must be >= 0");
        }
        this.centerX = centerX;
        this.centerY = centerY;
        this.halfWidth = halfWidth;
        this.halfHeight = halfHeight;
        this.facing = facing == null ? Facing.NORTH : facing;
    }

    public int left()   { return centerX - halfWidth; }
    public int right()  { return centerX + halfWidth; }
    public int bottom() { return centerY - halfHeight; }
    public int top()    { return centerY + halfHeight; }
}
