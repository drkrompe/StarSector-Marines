package com.dillon.starsectormarines.battle.warship;

/**
 * The duel helm: where a ship fighting a hostile hull puts itself, as pure functions of the two hulls' own guns.
 *
 * <p>A ship fights at the range its guns give it the advantage, read from the weapons both sides carry and never
 * authored per hull. Its <em>reach</em> is the reach of its strongest anti-ship weapon. A ship that outranges its
 * target holds off between the two reaches, inside its own and outside the target's, and slides across the target's
 * nose while it does, so it is not a stationary mark; a ship that is outranged (or evenly matched) closes straight
 * in to well inside its own reach, since standing off would only let the other side shoot unanswered. The heading
 * is chosen separately ({@link WarshipShipRules#bestHeading}), which keeps a front shield toward the target
 * because the guns are there too. Speed, acceleration and braking are the hull's own: the helm only moves the
 * station the ship is already flying to.
 */
public final class WarshipDuelHelm {

    private WarshipDuelHelm() {}

    /** A ship outranges its target only when its reach is longer by at least this many cells. */
    static final float OUTRANGE_MARGIN_CELLS = 2f;
    /** An outranged or even ship closes to this share of its own reach. */
    static final float CLOSE_SHARE = 0.6f;
    /** How far round the target ahead of itself a ship holding off sets its station, in degrees, so it keeps sliding. */
    static final float SLIDE_LEAD_DEGREES = 25f;
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

    /**
     * Where the ship should be now: on the circle of its preferred range about the target, at its current bearing
     * from the target, turned by {@code slideDegrees} when it holds off; kept inside the map.
     *
     * @return {@code [x, y]}
     */
    public static float[] station(float shipX, float shipY, float targetX, float targetY, float range,
                                  float slideDegrees, float halfLength, float mapWidth, float mapHeight) {
        double angle = Math.atan2(shipY - targetY, shipX - targetX);
        if (Math.abs(shipX - targetX) < 1e-4f && Math.abs(shipY - targetY) < 1e-4f) angle = 0.0;
        angle += Math.toRadians(slideDegrees);
        float x = targetX + (float) Math.cos(angle) * range;
        float y = targetY + (float) Math.sin(angle) * range;
        float margin = EDGE_MARGIN_CELLS + halfLength;
        x = Math.max(margin, Math.min(mapWidth - margin, x));
        y = Math.max(margin, Math.min(mapHeight - margin, y));
        return new float[]{x, y};
    }
}
