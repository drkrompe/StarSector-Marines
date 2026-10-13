package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirBody;

/**
 * A ship's low pass: a fast, low run along a straight line from A to B with no station kept,
 * every gun that bears firing on whatever is near the line, and a return to whatever the ship
 * was doing afterwards. A show of force that costs part of the station time.
 *
 * <p>The pass is an order, not a second ship model. It rides the hull's own handling scaled
 * by {@link #SPEED_FACTOR} (a burn), fires through the ordinary laid-area machinery with a
 * circle that slides down the line ahead of the hull (so hand-order rules apply: no posture
 * range or friendly check refuses it, and no damage is multiplied), and is charged
 * {@link #STATION_COST_FACTOR} seconds of station time for every second it flies. The
 * presentation reads {@link #blend()}, {@link #progress()} and the line to draw the sharper
 * shadow, the louder wake and the dust left along the line; nothing in the simulation reads them.
 *
 * <p>The geometry and the time-cost arithmetic are pure and static so they can be tested
 * without a battle; the flight itself is stepped by {@link WarshipSystem}.
 */
public final class LowPass {

    /** The pass speed as a multiple of the hull's table speed: a burn drive's order of magnitude, not a nerf or a buff. */
    public static final float SPEED_FACTOR = 6f;
    /** Acceleration and deceleration while passing, as a multiple of the hull's own, so the burn is reached within the run. */
    public static final float HANDLING_FACTOR = 6f;
    /** Seconds of station time each second of the pass (the run-in included) costs. */
    public static final float STATION_COST_FACTOR = 2f;
    /** Half the width of the corridor the guns work, in cells: the firing circle's radius. */
    public static final float CORRIDOR_HALF_WIDTH_CELLS = 14f;
    /** How far ahead of the hull, along the line, the firing circle is centred, in cells. */
    public static final float LEAD_CELLS = 8f;
    /** The shortest line worth a pass, in cells; a shorter request is refused. */
    public static final float MIN_LENGTH_CELLS = 24f;
    /** The longest line a pass flies, in cells; a longer request is cut to it from A. */
    public static final float MAX_LENGTH_CELLS = 140f;
    /** A hull this close to A starts the pass at once instead of running in, in cells. */
    public static final float RUN_IN_RADIUS_CELLS = 5f;
    /** How far ahead on the line the hull steers, in cells. */
    static final float LOOKAHEAD_CELLS = 12f;
    /** Seconds the louder wake and sharper shadow take to build once the pass begins. */
    public static final float RAMP_SECONDS = 1.5f;
    /** Seconds the effects take to fade once the pass has ended, and how long the dust trail lingers. */
    public static final float FADE_SECONDS = 4f;

    /** Where the pass is: running in to A, flying the line, or finished. */
    public enum Phase { RUN_IN, PASS, DONE }

    private final float ax;
    private final float ay;
    private final float bx;
    private final float by;
    private final float dirX;
    private final float dirY;
    private final float length;
    private Phase phase;
    private float seconds;
    private float endedSeconds = -1f;
    private float progress;
    private boolean startSounded;

    private LowPass(float ax, float ay, float bx, float by, float length, boolean running) {
        this.ax = ax;
        this.ay = ay;
        this.bx = bx;
        this.by = by;
        this.length = length;
        this.dirX = (bx - ax) / length;
        this.dirY = (by - ay) / length;
        this.phase = running ? Phase.RUN_IN : Phase.PASS;
    }

    /**
     * Plans a pass from {@code (ax, ay)} to {@code (bx, by)} for a hull at {@code (shipX, shipY)}, with
     * both ends kept on the map (the hull's centre cannot leave it) and the line cut to
     * {@link #MAX_LENGTH_CELLS}. Returns {@code null} when the line is shorter than
     * {@link #MIN_LENGTH_CELLS} or not finite.
     */
    public static LowPass plan(float shipX, float shipY, float ax, float ay, float bx, float by,
                               float mapWidth, float mapHeight) {
        if (!finite(ax, ay, bx, by)) return null;
        float sax = clamp(ax, 0f, mapWidth);
        float say = clamp(ay, 0f, mapHeight);
        float dx = clamp(bx, 0f, mapWidth) - sax;
        float dy = clamp(by, 0f, mapHeight) - say;
        float len = (float) Math.hypot(dx, dy);
        if (len < MIN_LENGTH_CELLS) return null;
        if (len > MAX_LENGTH_CELLS) {
            dx *= MAX_LENGTH_CELLS / len;
            dy *= MAX_LENGTH_CELLS / len;
            len = MAX_LENGTH_CELLS;
        }
        boolean running = Math.hypot(shipX - sax, shipY - say) > RUN_IN_RADIUS_CELLS;
        return new LowPass(sax, say, sax + dx, say + dy, len, running);
    }

    /** The speed a hull flies the line at, in cells per second. */
    public static float passSpeed(float maxSpeed) {
        return maxSpeed * SPEED_FACTOR;
    }

    /** The acceleration a hull flies a pass with. */
    public static float passAcceleration(float acceleration) {
        return acceleration * HANDLING_FACTOR;
    }

    /**
     * Seconds to cover {@code distance} cells from rest at the pass speed: accelerating at the pass
     * acceleration until the speed is reached and cruising after. Constant-acceleration arithmetic,
     * exact for a hull that starts at rest and a close lower bound otherwise.
     */
    public static float secondsToCover(float distance, float maxSpeed, float acceleration) {
        float v = passSpeed(maxSpeed);
        float a = passAcceleration(acceleration);
        if (!(v > 0f) || !(a > 0f) || distance <= 0f) return 0f;
        float ramp = v * v / (2f * a);
        if (distance <= ramp) return (float) Math.sqrt(2f * distance / a);
        return v / a + (distance - ramp) / v;
    }

    /** Seconds of station time a pass of this much flying costs. */
    public static float stationCost(float flyingSeconds) {
        return flyingSeconds * STATION_COST_FACTOR;
    }

    /**
     * The station time to hold back for a plan, in seconds: the cost of running in to A and flying the
     * line from rest. A ship with less than this left is not sent on the pass.
     */
    public float estimatedCost(float shipX, float shipY, float maxSpeed, float acceleration) {
        float runIn = (float) Math.hypot(shipX - ax, shipY - ay);
        return stationCost(secondsToCover(runIn + length, maxSpeed, acceleration));
    }

    // ---- the line ---------------------------------------------------------------------------------------

    public float startX() { return ax; }
    public float startY() { return ay; }
    public float endX() { return bx; }
    public float endY() { return by; }
    public float length() { return length; }
    public float directionX() { return dirX; }
    public float directionY() { return dirY; }

    /** The hull's heading while it flies the line, in the warship convention (zero is north). */
    public float headingDegrees() {
        return AirBody.facingToward(dirX, dirY);
    }

    /** How far along the line {@code (px, py)} projects, in cells; negative before A, beyond {@link #length()} after B. */
    public float along(float px, float py) {
        return (px - ax) * dirX + (py - ay) * dirY;
    }

    /** How far {@code (px, py)} lies from the infinite line, in cells. */
    public float across(float px, float py) {
        return Math.abs((px - ax) * -dirY + (py - ay) * dirX);
    }

    /** Whether {@code (px, py)} is within the corridor: between A and B (end caps rounded) and a half-width of the line. */
    public boolean inCorridor(float px, float py) {
        float s = along(px, py);
        float clamped = clamp(s, 0f, length);
        float qx = ax + dirX * clamped;
        float qy = ay + dirY * clamped;
        return Math.hypot(px - qx, py - qy) <= CORRIDOR_HALF_WIDTH_CELLS;
    }

    // ---- the state --------------------------------------------------------------------------------------

    public Phase phase() { return phase; }

    /** Whether the hull is still flying this pass (running in or on the line). */
    public boolean active() { return phase != Phase.DONE; }

    /** Seconds since the pass began; stops counting once it has ended. */
    public float seconds() { return seconds; }

    /** Seconds since the pass ended, or negative while it has not. */
    public float endedSeconds() { return endedSeconds; }

    /** How far along the line the hull has got, in cells, never less than it has been. */
    public float progress() { return progress; }

    /** Whether the hull is on the line proper: the guns' corridor, the loud wake and the dust. */
    public boolean onLine() { return phase == Phase.PASS; }

    /**
     * The presentation scalar, 0 to 1: building over {@link #RAMP_SECONDS} while the pass flies, and
     * fading over {@link #FADE_SECONDS} once it has ended. The sharper shadow and louder wake read it.
     */
    public float blend() {
        if (phase != Phase.DONE) return smooth(seconds / RAMP_SECONDS);
        return 1f - smooth(endedSeconds / FADE_SECONDS);
    }

    /** Whether the pass has nothing left to show: ended and faded. */
    public boolean spent() {
        return phase == Phase.DONE && endedSeconds >= FADE_SECONDS;
    }

    // ---- stepping (the warship system's) ---------------------------------------------------------------

    /** Advances the pass's clocks and phase for a hull now at {@code (px, py)}. */
    void advance(float dt, float px, float py) {
        if (phase == Phase.DONE) {
            endedSeconds += dt;
            return;
        }
        seconds += dt;
        if (phase == Phase.RUN_IN && Math.hypot(px - ax, py - ay) <= RUN_IN_RADIUS_CELLS) phase = Phase.PASS;
        if (phase == Phase.PASS) {
            progress = Math.max(progress, along(px, py));
            if (progress >= length - 1f) {
                phase = Phase.DONE;
                endedSeconds = 0f;
            }
        }
    }

    /** Whether the start-of-pass sound has been asked for; sets it, so it is asked for once. */
    boolean takeStartSound() {
        if (startSounded || phase != Phase.PASS) return false;
        startSounded = true;
        return true;
    }

    /** Where the hull steers: A while running in, a lookahead point on the line while passing. */
    float[] steerPoint(float px, float py) {
        if (phase == Phase.RUN_IN) return new float[]{ax, ay};
        float s = Math.max(along(px, py), 0f) + LOOKAHEAD_CELLS;
        return new float[]{ax + dirX * s, ay + dirY * s};
    }

    /** The centre of the firing circle for a hull at {@code (px, py)}: ahead of it on the line, kept between A and B. */
    float[] fireCentre(float px, float py) {
        float s = clamp(along(px, py) + LEAD_CELLS, 0f, length);
        return new float[]{ax + dirX * s, ay + dirY * s};
    }

    private static boolean finite(float... v) {
        for (float f : v) if (!Float.isFinite(f)) return false;
        return true;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static float smooth(float t) {
        float u = clamp(t, 0f, 1f);
        return u * u * (3f - 2f * u);
    }
}
