package com.dillon.starsectormarines.battle.vehicle;

/**
 * Two hulls meeting ({@code convoy-nouns.md}, hull contact): whether their footprints overlap and by how much, and
 * the yield rule that decides who brakes. Pure.
 *
 * <p>Footprints are oriented rectangles; overlap is the separating-axis test over the four edge normals, and
 * the axis of least overlap is the contact normal. The rule then has two halves, and both are needed:
 * <ul>
 *   <li><b>Separation</b> resolves what overlap there is, split by mass: the lighter hull is shoved most of the way.
 *       Alone it is a spring, and a column driving into the hull ahead of it would shove it along forever.</li>
 *   <li><b>Yield</b> takes away the speed a hull is closing on another with: a follower matches the pace of the
 *       hull ahead, and a hull crossing another's bow keeps going while that one waits. Alone it deadlocks a
 *       head-on meet, because both are closing and both would wait.</li>
 * </ul>
 * So in a head-on meet one hull gives way: the lighter, or on equal mass the later spawned, stops, and the
 * other comes on at a crawl. Meanwhile both pull to their own side at {@link #PASS_SPEED} ({@link #lateral}), each
 * toward the side the other is not on, the same pace whatever they weigh: passing is two drivers steering, not a
 * shove, and a mass-split lateral would put the light hull against the wall where its own planner can no longer
 * start. A lane too narrow for the two to pass is a stall like any other, left to the stall ladder.
 *
 * <p>Frame: cells; facing 0 is +y and forward is {@code (-sin, cos)}, as {@link GroundBody}.
 */
public final class HullContact {

    /** Gap a yielding hull keeps: it brakes this far short of touching. */
    public static final float SKIN = .3f;
    /** The pace the hull given way to comes on at, cells a second, while the two are in contact head-on. */
    public static final float CRAWL = .6f;
    /** How fast two hulls nose to nose each pull to their own side, cells a second. */
    public static final float PASS_SPEED = .8f;
    /** Two hulls are head-on when their forward axes point within this of opposite ({@code cos}). */
    public static final float HEAD_ON = -.5f;
    /** A hull moving less across the normal than this ({@code cos}) is going past, not into, the other. */
    public static final float INTO = .2f;
    /** Below this lateral offset two head-on hulls are dead in line, and each takes its own right. */
    public static final float CENTRED = .2f;

    private HullContact() {}

    /**
     * Whether the footprints of {@code a} and {@code b}, each inflated by half of {@code skin}, overlap. On true,
     * {@code out[0..1]} is the unit contact normal from {@code a} toward {@code b} and {@code out[2]} the depth
     * along it, skin included: {@code out[2] - skin} is how far the bare footprints interpenetrate.
     */
    public static boolean overlap(float ax, float ay, float aFacing, float aHalfL, float aHalfW,
                                  float bx, float by, float bFacing, float bHalfL, float bHalfW,
                                  float skin, float[] out) {
        double ar = Math.toRadians(aFacing), br = Math.toRadians(bFacing);
        float afx = (float) -Math.sin(ar), afy = (float) Math.cos(ar);
        float bfx = (float) -Math.sin(br), bfy = (float) Math.cos(br);
        float dx = bx - ax, dy = by - ay;
        float best = Float.MAX_VALUE, nx = 0f, ny = 0f;
        for (int i = 0; i < 4; i++) {
            float ux, uy;
            switch (i) {
                case 0 -> { ux = afx; uy = afy; }
                case 1 -> { ux = afy; uy = -afx; }
                case 2 -> { ux = bfx; uy = bfy; }
                default -> { ux = bfy; uy = -bfx; }
            }
            float ra = aHalfL * Math.abs(afx * ux + afy * uy) + aHalfW * Math.abs(afy * ux - afx * uy);
            float rb = bHalfL * Math.abs(bfx * ux + bfy * uy) + bHalfW * Math.abs(bfy * ux - bfx * uy);
            float d = dx * ux + dy * uy;
            float depth = ra + rb + skin - Math.abs(d);
            if (depth <= 0f) return false;
            if (depth < best) {
                best = depth;
                float sign = d >= 0f ? 1f : -1f;
                nx = ux * sign;
                ny = uy * sign;
            }
        }
        out[0] = nx;
        out[1] = ny;
        out[2] = best;
        return true;
    }

    /**
     * The speed a hull may keep while touching another: its closing speed along {@code towardOther} (the hull's
     * forward against the normal toward the other, {@code cos}) brought down to {@code allowed}, sign kept. A
     * hull moving past the other rather than into it keeps its speed.
     */
    public static float yieldSpeed(float speed, float towardOther, float allowed) {
        float along = Math.signum(speed) * towardOther;
        if (along < INTO) return speed;
        float closing = Math.abs(speed) * along;
        float keep = Math.max(0f, allowed);
        if (closing <= keep) return speed;
        return Math.signum(speed) * keep / along;
    }

    /** Whether {@code a} gives way to {@code b} in a head-on meet: the lighter does, and on equal mass the later id. */
    public static boolean yields(float massA, long idA, float massB, long idB) {
        if (massA != massB) return massA < massB;
        return idA > idB;
    }

    /**
     * The sideways part of the shove that steers two head-on hulls past each other, as a unit vector applied to
     * {@code b} (and its opposite to {@code a}): perpendicular to the normal, toward the side {@code b} already
     * stands on from {@code a}, or when they are dead in line toward {@code b}'s own right, which is {@code a}'s
     * left. Writes {@code out[0..1]}.
     */
    public static void lateral(float ax, float ay, float aFacing, float bx, float by, float nx, float ny,
                               float[] out) {
        float lx = -ny, ly = nx;
        double ar = Math.toRadians(aFacing);
        float arx = (float) Math.cos(ar), ary = (float) Math.sin(ar);   // a's right
        float offset = (bx - ax) * arx + (by - ay) * ary;
        float side = Math.abs(offset) > CENTRED ? Math.signum(offset) : -1f;
        if ((lx * arx + ly * ary) * side < 0f) { lx = -lx; ly = -ly; }
        out[0] = lx;
        out[1] = ly;
    }
}
