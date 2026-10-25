package com.dillon.starsectormarines.battle.evacuation.flier;

/**
 * A few Harriers off a brood spire's roost flying a circuit over their nest while it is quiet
 * (`colony-breach-nouns.md`, Brood spire, Patrols): out from the spire, round a seeded loop about its bore, and home
 * to land back on its hooks.
 *
 * <p>The patrol moves the pack's anchor along the route ({@link #step}), so its loiter ring wheels along the loop.
 * While it flies the route it only spots what comes within {@link #SPOT_RANGE}, a scout's eye, not a pack's
 * {@link HarrierRules#SENSE_RANGE}; once it has prey it is an ordinary pack, and with its prey gone it picks the
 * route up where it left it. At the end it {@linkplain #homing() comes home} and its members land one by one.
 *
 * <p>Pure and seeded: the same spire and seed fly the same loop.
 */
public final class HarrierPatrol {

    /** Waypoints on the loop about the bore. */
    public static final int LEGS = 5;
    /** Nearest and furthest a waypoint lies from the bore, in cells. */
    public static final float REACH_MIN = 18f, REACH_MAX = 32f;
    /** The share of a lap the loop turns through, least and most. */
    public static final float SWEEP_MIN = 0.55f, SWEEP_MAX = 0.85f;
    /** How fast the anchor moves along the route, in cells a second: under the loiter speed, so the ring keeps up. */
    public static final float SPEED = 2.4f;
    /** How near a flying patrol sees prey, in cells. */
    public static final float SPOT_RANGE = 22f;
    /** A member this near its spire and this low lands on it. */
    public static final float LAND_REACH = 1.2f;
    /** Cells the anchor counts as at a waypoint. */
    static final float ARRIVE = 0.5f;

    /** The route as {x0, y0, x1, y1, ...}: out round the loop and the spire last. */
    final float[] route;
    public final float homeX, homeY;
    private int leg;
    private float x, y;
    /** Harriers it went up with. */
    final int size;
    /** Members that landed back on the spire. */
    int landed;
    /** How fast the anchor flies the route. */
    final float speed;
    /** The waypoint a raid strikes at ({@link HarrierRaid}), or -1 for a patrol's loop. */
    final int strikeLeg;
    private boolean striking, struck;
    private int divesAtStrike = -1;
    private float strikeSeconds, idleSeconds;

    HarrierPatrol(float[] route, float homeX, float homeY, int size) {
        this(route, homeX, homeY, size, SPEED, -1);
    }

    HarrierPatrol(float[] route, float homeX, float homeY, int size, float speed, int strikeLeg) {
        this.speed = speed;
        this.strikeLeg = strikeLeg;
        this.size = size;
        this.route = route;
        this.homeX = homeX;
        this.homeY = homeY;
        this.x = homeX;
        this.y = homeY;
    }

    /** Where the anchor stands now. */
    public float x() { return x; }
    public float y() { return y; }

    /** Harriers it went up with. */
    public int size() { return size; }

    /** Members that have landed back on the spire so far. */
    public int landed() { return landed; }

    /** Whether the patrol has flown its loop and is over its spire, landing. */
    public boolean homing() { return leg >= route.length / 2; }

    /** Whether this is a raid, flying out to strike and back, rather than a patrol's loop. */
    public boolean raid() { return strikeLeg >= 0; }

    /** Where a raid strikes: its target's ground as it was sent. */
    public float strikeX() { return route[Math.max(0, strikeLeg) * 2]; }
    public float strikeY() { return route[Math.max(0, strikeLeg) * 2 + 1]; }

    /** Whether a raid is over its target, hunting there. */
    public boolean striking() { return striking; }

    /** Whether a raid has been over its target and turned for home. */
    public boolean struck() { return struck; }

    /**
     * A raid over its target: it turns for home once it has made {@link HarrierRaid#DIVES} dives there and is
     * {@code loitering} again, or has had no {@code prey} for {@link HarrierRaid#STRIKE_IDLE_SECONDS}, or has stayed
     * {@link HarrierRaid#STRIKE_MAX_SECONDS}. {@code dives} is the pack's count of dives so far.
     */
    void strikeTick(float dt, int dives, boolean loitering, boolean prey) {
        if (!striking) return;
        if (divesAtStrike < 0) divesAtStrike = dives;
        strikeSeconds += dt;
        idleSeconds = prey ? 0f : idleSeconds + dt;
        boolean done = loitering && dives - divesAtStrike >= HarrierRaid.DIVES;
        if (done || idleSeconds >= HarrierRaid.STRIKE_IDLE_SECONDS || strikeSeconds >= HarrierRaid.STRIKE_MAX_SECONDS) {
            striking = false;
            struck = true;
            leg++;
        }
    }

    /** A copy of the route, for evidence and the scene. */
    public float[] route() { return route.clone(); }

    /**
     * Moves the anchor at its speed along the route for {@code dt} seconds, waypoint after waypoint; a raid stops on
     * its strike leg and strikes there until {@link #strikeTick} turns it for home.
     */
    void step(float dt) {
        float left = speed * dt;
        while (left > 0f && !homing() && !striking) {
            float tx = route[leg * 2], ty = route[leg * 2 + 1];
            float dx = tx - x, dy = ty - y, d = (float) Math.hypot(dx, dy);
            if (d <= Math.max(ARRIVE, left)) {
                x = tx;
                y = ty;
                left -= d;
                if (leg == strikeLeg && !struck) {
                    striking = true;
                    break;
                }
                leg++;
                continue;
            }
            x += dx / d * left;
            y += dy / d * left;
            left = 0f;
        }
    }

    /**
     * The loop a spire at ({@code homeX}, {@code homeY}) flies over its bore at ({@code boreX}, {@code boreY}):
     * {@link #LEGS} waypoints starting at the spire's own bearing and turning round the bore through a seeded
     * {@link #SWEEP_MIN} to {@link #SWEEP_MAX} of a lap, either way, each a seeded {@link #REACH_MIN} to
     * {@link #REACH_MAX} out and kept inside a {@code w} by {@code h} map; then the spire.
     */
    public static float[] route(float boreX, float boreY, float homeX, float homeY, long seed, int w, int h) {
        float[] out = new float[(LEGS + 1) * 2];
        double start = Math.atan2(homeY - boreY, homeX - boreX);
        long s = mix(seed);
        double sweep = 2 * Math.PI * (SWEEP_MIN + (SWEEP_MAX - SWEEP_MIN) * unit(s));
        double way = unit(mix(s ^ 0x5EEDL)) < 0.5 ? -1 : 1;
        for (int i = 0; i < LEGS; i++) {
            s = mix(s + 1);
            double a = start + way * sweep * (i + 1) / LEGS;
            float r = REACH_MIN + (REACH_MAX - REACH_MIN) * unit(s);
            out[i * 2] = clamp(boreX + (float) Math.cos(a) * r, w);
            out[i * 2 + 1] = clamp(boreY + (float) Math.sin(a) * r, h);
        }
        out[LEGS * 2] = homeX;
        out[LEGS * 2 + 1] = homeY;
        return out;
    }

    private static float clamp(float v, int size) {
        return Math.max(2f, Math.min(size - 2f, v));
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static float unit(long h) {
        return (h >>> 40) / (float) (1L << 24);
    }
}
