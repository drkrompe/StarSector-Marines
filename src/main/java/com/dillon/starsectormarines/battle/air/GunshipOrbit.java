package com.dillon.starsectormarines.battle.air;

/**
 * The ring a gunship flies about its target, and the numbers that size it.
 *
 * <p>A gunship attacks by <em>circling</em>, not by running in: it holds a ring of fixed radius
 * about a ground point, so its turret stays over the ground for the whole visit and covers it from
 * every side as it goes round. This class is the geometry of that ring and nothing else. Pure
 * functions over a pose and a centre, so the shape is testable without a simulation and
 * {@link AirSystem} only has to ask "where do I steer".
 *
 * <p><b>Steered by a carrot, not by a path.</b> The craft is aimed at a point a little way round
 * the ring from where it is. From outside the ring that point is the tangent point, so the
 * approach joins the ring without crossing it; from inside it is simply ahead along the ring, so
 * a craft that finds itself inside drifts out to the radius instead of cutting a chord.
 *
 * <p><b>Which way round</b> is decided once, on entry, as the direction that asks the least
 * turning of the craft as it arrives, and then held, so the craft is never seen reversing its
 * circuit mid-visit.
 *
 * <p><b>Flown, not drawn with compasses.</b> A gunship's ring is not a circle. It is an oval
 * that slowly turns and wobbles, so no two laps are the same: the craft tightens in to work the
 * ground and slows and dips as it does, then opens out, climbs and picks up pace on the wide leg.
 * A constant radius at a constant pace and height turned the craft at one fixed rate for the whole
 * visit, which read as a model on a turntable. The swing is bounded by {@link #SWING_FRACTION}, so
 * the turret keeps the ground within reach from every point of it. Cover fighters keep the plain
 * ring: they hold a station between jobs rather than work a target.
 *
 * <p>Heading uses the sprite convention shared by {@link AirBody}: {@code 0°} is +Y, positive
 * counter-clockwise.
 */
public final class GunshipOrbit {

    /** Broad overwatch circuit, still within the turret's reach of the station centre. */
    public static final float RADIUS_CELLS = 16f;
    /** Cruise on the ring, cells/sec. Slower than the airframe's own cruise: a gunship holds station, it does not race round it. */
    public static final float ORBIT_SPEED = 4f;
    /** Seconds on the ring before the craft turns for the exit. */
    public static final float STATION_SECONDS = 40f;
    /** The craft is on the ring, for the purposes of starting the clock, once within this multiple of the radius. */
    public static final float ENTER_FACTOR = 1.15f;

    /** The furthest the worked ring swings in or out of its radius, as a fraction of it. */
    public static final float SWING_FRACTION = 0.24f;
    /** How fast the oval turns about the ground it is centred on, radians/sec. */
    private static final float LOITER_DRIFT_RAD_PER_SEC = 0.11f;
    /** The lowest a working gunship dips, as an altitude fraction; it is at full height on the widest leg. */
    public static final float LOW_ALTITUDE_T = 0.82f;

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
        mission.orbitCentreX = mission.lzX;
        mission.orbitCentreY = mission.lzY;
        mission.strikeSortie = false;
        mission.orbitRadius = RADIUS_CELLS;
        mission.orbitSecondsLeft = STATION_SECONDS;
        mission.orbitClockwise = clockwiseFrom(bodyX, bodyY, facingDeg,
                mission.lzX, mission.lzY, RADIUS_CELLS);
        // Each sortie's oval starts turned to wherever it came from, so two visits do not trace the same shape.
        mission.orbitLoiterPhase = (float) Math.atan2(bodyY - mission.lzY, bodyX - mission.lzX);
    }

    /**
     * The worked ring's radius at {@code angle} round the centre: an oval with a third-lobe wobble,
     * turned by {@code loiterPhase}. Stays within {@link #SWING_FRACTION} of {@code radius}.
     */
    public static float workedRadius(float radius, double angle, float loiterPhase) {
        double swing = 0.75 * Math.sin(2 * angle + loiterPhase) + 0.25 * Math.sin(3 * angle - 1.7 * loiterPhase);
        return (float) (radius * (1 + SWING_FRACTION * swing));
    }

    /** {@link #workedRadius} at the craft's own bearing from the centre. */
    public static float workedRadiusAt(float bodyX, float bodyY, float centreX, float centreY,
                                       float radius, float loiterPhase) {
        return workedRadius(radius, Math.atan2(bodyY - centreY, bodyX - centreX), loiterPhase);
    }

    /** The oval's turn after {@code dt} seconds. */
    public static float advanceLoiter(float loiterPhase, float dt) {
        return (float) ((loiterPhase + LOITER_DRIFT_RAD_PER_SEC * dt) % (2 * Math.PI));
    }

    /**
     * The pace for a craft on a worked ring of this local radius: slower through the tight ends and
     * faster along the wide legs, as a pilot flies it, with a slow surge on top.
     */
    public static float workedPace(float localRadius, float radius, float loiterPhase) {
        float surge = 1f + 0.1f * (float) Math.sin(3.1 * loiterPhase);
        return ORBIT_SPEED * (float) Math.sqrt(localRadius / radius) * surge;
    }

    /** The height a working gunship holds at this local radius: low where it tightens in, full height on the wide leg. */
    public static float workedAltitude(float localRadius, float radius) {
        float t = (localRadius / radius - (1 - SWING_FRACTION)) / (2 * SWING_FRACTION);
        t = Math.max(0f, Math.min(1f, t));
        return LOW_ALTITUDE_T + (1f - LOW_ALTITUDE_T) * t;
    }

    /**
     * The point to steer at on the worked ring: as {@link #carrot}, with the radius taken at the
     * carrot's own bearing.
     */
    public static float[] workedCarrot(float bodyX, float bodyY, float centreX, float centreY,
                                       float radius, boolean clockwise, float loiterPhase) {
        double rx = bodyX - centreX;
        double ry = bodyY - centreY;
        double d = Math.hypot(rx, ry);
        double theta = d < 1e-6 ? 0.0 : Math.atan2(ry, rx);
        double lead = MIN_LEAD_RAD;
        float local = workedRadius(radius, theta, loiterPhase);
        if (d > local) lead = Math.max(lead, Math.acos(local / d));
        double angle = clockwise ? theta - lead : theta + lead;
        float r = workedRadius(radius, angle, loiterPhase);
        return new float[]{
                (float) (centreX + r * Math.cos(angle)),
                (float) (centreY + r * Math.sin(angle))};
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
        return paced(base, currentSpeed, dt, ORBIT_SPEED);
    }

    /** {@link #paced} toward {@code pace} rather than the ring's fixed cruise. */
    public static AirHandling paced(AirHandling base, float currentSpeed, float dt, float pace) {
        float cap = Math.max(pace, currentSpeed - base.brakingAccel() * dt);
        return new Paced(base, Math.min(cap, base.maxSpeed()));
    }

    private record Paced(AirHandling base, float maxSpeed) implements AirHandling {
        @Override public float accel() { return base.accel(); }
        @Override public float brakingAccel() { return base.brakingAccel(); }
        @Override public float maxTurnRateDegPerSec() { return base.maxTurnRateDegPerSec(); }
        @Override public float lateralDriftDamping() { return base.lateralDriftDamping(); }
        @Override public float stationDamping() { return base.stationDamping(); }
    }
}
