package com.dillon.starsectormarines.battle.world.gen;

/**
 * One authored berth inside a bay — where the thing the bay is for stands while
 * it is worked on.
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

        /**
         * This heading in the battle facing convention, where a unit spawns at
         * 180 looking south.
         *
         * <p>Stated as a rotation of a direction vector rather than written out
         * per constant, so it stays correct if the convention moves:
         * {@code atan2(dy, dx) - 90}, the same expression aiming and flight use.
         */
        public float degrees() {
            return (float) Math.toDegrees(Math.atan2(dy, dx)) - 90f;
        }

        Facing(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }

        /** The cardinal matching this direction. */
        public static Facing of(int dx, int dy) {
            for (Facing facing : values()) {
                if (facing.dx == dx && facing.dy == dy) return facing;
            }
            throw new IllegalArgumentException("not a cardinal direction: " + dx + "," + dy);
        }
    }

    /** Cell the machine stands on; the footprint extends from here. */
    public final int centerX;
    public final int centerY;
    /**
     * What a berth is for.
     *
     * <p>Stated where the berth is authored rather than worked out later from
     * which room it fell in. Both kinds are a cleared footprint with a heading
     * and servicing beside it, and every consumer that walks the deck's berths
     * wants exactly one of them: a lance is stood in the machine berths, a
     * ship's boats in the boat berths, and neither has any business in the
     * other's. Derived from the enclosing room instead, that distinction would
     * have to be re-derived at each of those consumers and would be wrong
     * silently — a mech standing in a boat bay is a legal spawn.
     */
    public enum Holds {
        /** A mech or a vehicle, worked on where it stands. */
        MACHINE,
        /** A boat, worked on between lifts. */
        BOAT
    }

    /** Clear half-extents around the center, so berths size to what they hold. */
    public final int halfWidth;
    public final int halfHeight;
    public final Facing facing;
    public final Holds holds;

    public Gantry(int centerX, int centerY, int halfWidth, int halfHeight, Facing facing) {
        this(centerX, centerY, halfWidth, halfHeight, facing, Holds.MACHINE);
    }

    public Gantry(int centerX, int centerY, int halfWidth, int halfHeight, Facing facing,
                  Holds holds) {
        if (halfWidth < 0 || halfHeight < 0) {
            throw new IllegalArgumentException("gantry half extents must be >= 0");
        }
        this.centerX = centerX;
        this.centerY = centerY;
        this.halfWidth = halfWidth;
        this.halfHeight = halfHeight;
        this.facing = facing == null ? Facing.NORTH : facing;
        this.holds = holds == null ? Holds.MACHINE : holds;
    }

    public int left()   { return centerX - halfWidth; }
    public int right()  { return centerX + halfWidth; }
    public int bottom() { return centerY - halfHeight; }
    public int top()    { return centerY + halfHeight; }
}
