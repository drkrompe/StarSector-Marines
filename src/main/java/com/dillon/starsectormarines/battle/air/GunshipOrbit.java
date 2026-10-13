package com.dillon.starsectormarines.battle.air;

/**
 * The ring a gunship flies about its target, and the numbers that size it.
 *
 * <p>A gunship attacks by <em>circling</em>, not by running in: it holds a ring of fixed radius
 * about a ground point with one flank toward it, so the guns mounted on that side stay on the
 * ground for the whole visit. This class is the geometry of that ring and nothing else. Pure
 * functions over a pose and a centre, so the shape is testable without a simulation and
 * {@link AirSystem} only has to ask "where do I steer".
 *
 * <p><b>Steered by a carrot, not by a path.</b> The craft is aimed at a point a little way round
 * the ring from where it is. From outside the ring that point is the tangent point, so the
 * approach joins the ring without crossing it; from inside it is simply ahead along the ring, so
 * a craft that finds itself inside drifts out to the radius instead of cutting a chord.
 *
 * <p><b>Which way round</b> is decided once, on entry, as the direction that asks the least
 * turning of the craft as it arrives, and then held: the flank that faces the target is the
 * flank with the guns, so a craft that changed direction mid-visit would swing its working
 * battery to the far side of the hull.
 *
 * <p>Heading uses the sprite convention shared by {@link AirBody}: {@code 0°} is +Y, positive
 * counter-clockwise, so the side a clockwise ring keeps toward its centre is the right-hand
 * (starboard) one.
 */
public final class GunshipOrbit {

    /** Cells between the craft and the ground point it circles. Inside the Heavy MG's reach with room to cover ground past the centre. */
    public static final float RADIUS_CELLS = 10f;
    /** Cruise on the ring, cells/sec. Slower than the airframe's own cruise: a gunship holds station, it does not race round it. */
    public static final float ORBIT_SPEED = 7f;
    /** Seconds on the ring before the craft turns for the exit. */
    public static final float STATION_SECONDS = 40f;
    /** The craft is on the ring, for the purposes of starting the clock, once within this multiple of the radius. */
    public static final float ENTER_FACTOR = 1.15f;

    /** The least angle round the ring the carrot is held ahead of the craft, radians. */
    private static final float MIN_LEAD_RAD = 0.5f;

    private GunshipOrbit() {}

    /**
     * Turns {@code mission} into a gunship sortie on a ring about its own {@code lzX/lzY}, with
     * the direction chosen for a craft arriving at {@code (bodyX, bodyY)} heading
     * {@code facingDeg}.
     */
    public static void begin(ShuttleMission mission, float bodyX, float bodyY, float facingDeg) {
        mission.gunship = true;
        mission.strikeSortie = false;
        mission.orbitRadius = RADIUS_CELLS;
        mission.orbitSecondsLeft = STATION_SECONDS;
        mission.orbitClockwise = clockwiseFrom(bodyX, bodyY, facingDeg,
                mission.lzX, mission.lzY, RADIUS_CELLS);
    }

    /**
     * The direction round the ring that asks the craft for the smaller turn on joining it.
     *
     * @return {@code true} for clockwise (target on the right-hand side of the heading)
     */
    public static boolean clockwiseFrom(float bodyX, float bodyY, float facingDeg,
                                        float centreX, float centreY, float radius) {
        double rx = bodyX - centreX;
        double ry = bodyY - centreY;
        double d = Math.hypot(rx, ry);
        double heading = Math.toRadians(facingDeg + 90f);
        double fx = Math.cos(heading);
        double fy = Math.sin(heading);
        if (d <= radius || d < 1e-6) {
            // On or inside the ring: join in whichever direction the craft is already going.
            double clockwiseDot = d < 1e-6 ? 0.0 : (fx * ry - fy * rx) / d;
            return clockwiseDot >= 0.0;
        }
        // Outside: the straight run to each tangent point, and the one closer to the nose wins.
        double theta = Math.atan2(ry, rx);
        double a = Math.acos(radius / d);
        double cw = tangentHeadingDot(centreX, centreY, radius, theta - a, bodyX, bodyY, fx, fy);
        double ccw = tangentHeadingDot(centreX, centreY, radius, theta + a, bodyX, bodyY, fx, fy);
        return cw >= ccw;
    }

    private static double tangentHeadingDot(float cx, float cy, float radius, double angle,
                                            float bodyX, float bodyY, double fx, double fy) {
        double tx = cx + radius * Math.cos(angle) - bodyX;
        double ty = cy + radius * Math.sin(angle) - bodyY;
        double len = Math.hypot(tx, ty);
        return len < 1e-6 ? 0.0 : (tx * fx + ty * fy) / len;
    }

    /**
     * The point to steer at: ahead of the craft along the ring, or the tangent point while it is
     * still well outside.
     *
     * @return {@code {x, y}}
     */
    public static float[] carrot(float bodyX, float bodyY, float centreX, float centreY,
                                 float radius, boolean clockwise) {
        double rx = bodyX - centreX;
        double ry = bodyY - centreY;
        double d = Math.hypot(rx, ry);
        double theta = d < 1e-6 ? 0.0 : Math.atan2(ry, rx);
        double lead = MIN_LEAD_RAD;
        if (d > radius) lead = Math.max(lead, Math.acos(radius / d));
        double angle = clockwise ? theta - lead : theta + lead;
        return new float[]{
                (float) (centreX + radius * Math.cos(angle)),
                (float) (centreY + radius * Math.sin(angle))};
    }

    /** Whether a craft at this position has joined the ring closely enough to start the visit's clock. */
    public static boolean onTheRing(float bodyX, float bodyY, float centreX, float centreY, float radius) {
        return Math.hypot(bodyX - centreX, bodyY - centreY) <= radius * ENTER_FACTOR;
    }

    /**
     * The airframe's own handling with its cruise brought down to the ring's pace.
     *
     * <p>The cap eases from the speed the craft arrived at rather than snapping to the ring's
     * pace, at the airframe's own braking: the steering integrator clamps forward speed to
     * {@code maxSpeed()} outright, so a hard cap would stop a craft arriving at full cruise in a
     * single tick.
     */
    public static AirHandling paced(AirHandling base, float currentSpeed, float dt) {
        float cap = Math.max(ORBIT_SPEED, currentSpeed - base.brakingAccel() * dt);
        return new Paced(base, Math.min(cap, base.maxSpeed()));
    }

    /** The side of the hull that faces the ring's centre: local {@code +X} for a clockwise ring, {@code -X} for a counter-clockwise one. */
    public static float inboardSide(boolean clockwise) {
        return clockwise ? 1f : -1f;
    }

    private record Paced(AirHandling base, float maxSpeed) implements AirHandling {
        @Override public float accel() { return base.accel(); }
        @Override public float brakingAccel() { return base.brakingAccel(); }
        @Override public float maxTurnRateDegPerSec() { return base.maxTurnRateDegPerSec(); }
        @Override public float lateralDriftDamping() { return base.lateralDriftDamping(); }
        @Override public float stationDamping() { return base.stationDamping(); }
    }
}
