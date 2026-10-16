package com.dillon.starsectormarines.battle.warship;

/**
 * The duel helm: where a ship fighting a hostile hull puts itself, as pure functions of the two hulls' own guns and
 * of where the target is going.
 *
 * <p>Ships fight in atmosphere as ships of the line do, not as fighters: they come alongside and trade fire on
 * parallel lines, and they do not make nose-on passes through each other. So the station a ship flies to is on the
 * target's <em>beam</em> (square to the target's own course, on whichever side the ship already is), a little ahead
 * of it so the two lines converge, at the range its guns give it the advantage. That range is read from the weapons
 * both sides carry and never authored per hull. Its <em>reach</em> is the reach of its strongest anti-ship gun. A
 * ship that outranges its target holds off between the two reaches, inside its own and outside the target's; one
 * that is outranged (or evenly matched) closes to {@link #CLOSE_SHARE} of its own reach, since standing off would
 * only let the other side shoot unanswered. Neither ever closes nearer than the two hulls and a clearance, so big
 * hulls do not overlap. Standing on the target's beam also keeps a ship out of a bow battery, which is where most
 * hulls carry their heaviest gun.
 *
 * <p>The heading is chosen separately ({@link WarshipShipRules#bestHeading}): the side that bears, where the guns
 * allow a broadside. Speed, acceleration and braking are the hull's own ({@link AtmosphericHandling}): the helm only
 * moves the station the ship is already flying to.
 */
public final class WarshipDuelHelm {

    private WarshipDuelHelm() {}

    /** A ship outranges its target only when its reach is longer by at least this many cells. */
    static final float OUTRANGE_MARGIN_CELLS = 2f;
    /** An outranged or even ship closes to this share of its own reach. */
    static final float CLOSE_SHARE = 0.75f;
    /** Open air, in cells, a duelling ship keeps between its hull and its target's at the least. */
    static final float CLEARANCE_CELLS = 6f;
    /** How far ahead of the target's beam the station sits, as a share of the range: the lines converge. */
    static final float CONVERGE_SHARE = 0.25f;
    /** The station is kept this many cells plus half the hull inside the map edge. */
    static final float EDGE_MARGIN_CELLS = 3f;

    /** The reach, in cells, of the ship's strongest weapon that may fire at a hull; zero when it has none. */
    public static float reach(Warship ship) {
        Warship.Mount m = WarshipShipRules.strongest(ship);
        return m == null ? 0f : WarshipScale.cells(m.weapon.rangePx());
    }

    /** Whether a ship with {@code mine} reach holds off a target with {@code theirs}, rather than closing. */
    public static boolean holdsOff(float mine, float theirs) {
        return mine >= theirs + OUTRANGE_MARGIN_CELLS;
    }

    /**
     * The range, centre to centre, a ship wants from its target: midway between the two reaches when it outranges
     * the target, {@link #CLOSE_SHARE} of its own reach otherwise.
     */
    public static float preferredRange(float mine, float theirs) {
        return holdsOff(mine, theirs) ? 0.5f * (mine + theirs) : CLOSE_SHARE * mine;
    }

    /** {@link #preferredRange}, but never nearer than the two hulls' half-lengths and {@link #CLEARANCE_CELLS}. */
    public static float preferredRange(float mine, float theirs, float halfLength, float targetHalfLength) {
        return Math.max(preferredRange(mine, theirs), halfLength + targetHalfLength + CLEARANCE_CELLS);
    }

    /**
     * Where the ship should be now: {@code range} out on the target's beam, on the side the ship is on, and
     * {@link #CONVERGE_SHARE} of the range ahead along the target's course; kept inside the map. {@code targetNose}
     * is the bearing of the target's nose in the {@code atan2} convention. A ship dead ahead or astern of its target
     * takes the beam {@code preferPort} names.
     *
     * @return {@code [x, y]}
     */
    public static float[] station(float shipX, float shipY, float targetX, float targetY, float targetNose,
                                  float range, boolean preferPort, float halfLength, float mapWidth, float mapHeight) {
        double nose = Math.toRadians(targetNose);
        float ax = (float) Math.cos(nose);
        float ay = (float) Math.sin(nose);
        float bx = -ay;
        float by = ax;
        float side = (shipX - targetX) * bx + (shipY - targetY) * by;
        if (side < -1e-3f || (Math.abs(side) <= 1e-3f && !preferPort)) {
            bx = -bx;
            by = -by;
        }
        float x = targetX + bx * range + ax * range * CONVERGE_SHARE;
        float y = targetY + by * range + ay * range * CONVERGE_SHARE;
        float margin = EDGE_MARGIN_CELLS + halfLength;
        x = Math.max(margin, Math.min(mapWidth - margin, x));
        y = Math.max(margin, Math.min(mapHeight - margin, y));
        return new float[]{x, y};
    }
}
