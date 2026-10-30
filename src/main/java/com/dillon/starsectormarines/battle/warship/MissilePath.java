package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirBody;

/**
 * The flight of one missile from its muzzle to where it lands, as a closed-form
 * function of time: position, forward speed and heading at any instant are
 * evaluated, never integrated, so the renderer, point defence and the landing all
 * read one clock and nothing can drift.
 *
 * <h2>Along the line</h2>
 * Three kinematic segments, read from the missile's own spec ({@link MissileFlightSpec}):
 * <i>boost</i> from the launch speed at the motor's acceleration up to its cruising
 * speed, <i>cruise</i> at that speed while the motor burns, and, if the aimed point
 * is further than the motor can carry it, <i>coast</i> with the speed decaying
 * exponentially (drag, in atmosphere) so the round lands short by what the coast
 * loses. Gravity moves nothing a warship fires. The arrival time is therefore the
 * kinematics', not {@code distance / speed}, and a missile that would arrive inside
 * its arming time is held to it.
 *
 * <h2>Across the line</h2>
 * Two lateral terms, both zero at the muzzle and at the landing, so the endpoints
 * are exactly the committed ones:
 * <ul>
 *   <li>a <b>bulge</b>: the launch fan, a single-signed arc to one side of the
 *       line, so a volley leaves the muzzle as a trumpet;</li>
 *   <li>a <b>weave</b>: a sinusoid in time under a {@code sin(pi u)} envelope. An
 *       unguided rocket only wobbles, lazily and small; a guided missile jinks, with
 *       an amplitude bounded by what its own turn rate can do
 *       ({@link #weaveBound}), so a stiff missile weaves little and a swarm
 *       missile with a high turn rate jinks hard.</li>
 * </ul>
 * Every random-looking quantity is a pure hash of the round's seed, taken once at
 * fire time, so the path never draws from the shared stream and adding rounds
 * cannot move another round's.
 */
public final class MissilePath {

    /** An unguided rocket's launch fan, the widest half-angle it is given. */
    static final float ROCKET_FAN_DEGREES = 5f;
    /** A guided missile's launch fan: it is steered out of it, so it is narrow. */
    static final float GUIDED_FAN_DEGREES = 1.5f;
    /** An unguided rocket's wobble as a share of its bulge, which keeps the bulge the shape that reads. */
    static final float ROCKET_WOBBLE_SHARE = 0.3f;
    /** The desired weave amplitude of a guided missile as a share of the distance it flies. */
    static final float GUIDED_WEAVE_SHARE = 0.05f;
    /** The share of the turn-rate limit a weave is allowed to spend, leaving room for the bulge and the envelope. */
    static final float TURN_MARGIN = 0.8f;
    /** The coast is followed for this many drag time constants, by which the round has all but stopped. */
    static final float COAST_TAUS = 3.9f;
    /** Drag time constant, seconds, for a missile whose spec gives no deceleration. */
    static final float DEFAULT_COAST_TAU = 1.5f;

    private final float fromX;
    private final float fromY;
    private final float dirX;
    private final float dirY;
    private final float length;
    private final float aimedLength;
    // Kinematics, in cells and seconds.
    private final float v0;
    private final float vMax;
    private final float acc;
    private final float boostEnd;
    private final float burnEnd;
    private final float tau;
    private final float sBoost;
    private final float sBurn;
    private final float vBurn;
    private final float seconds;
    // Shaping, in cells and hertz.
    private final float bulge;
    private final float weaveAmplitude;
    private final float weaveHz;
    private final float weavePhase;
    /** The share of the flight, at its end, flown straight down the line: the terminal run. Zero for none. */
    private final float terminal;

    private MissilePath(float fromX, float fromY, float toX, float toY, float v0, float vMax, float acc,
                        float burn, float tau, float armingSeconds, float bulge, float weaveAmplitude,
                        float weaveHz, float weavePhase) {
        this(fromX, fromY, toX, toY, v0, vMax, acc, burn, tau, armingSeconds, bulge, weaveAmplitude, weaveHz,
                weavePhase, 0f);
    }

    private MissilePath(float fromX, float fromY, float toX, float toY, float v0, float vMax, float acc,
                        float burn, float tau, float armingSeconds, float bulge, float weaveAmplitude,
                        float weaveHz, float weavePhase, float terminal) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        this.fromX = fromX;
        this.fromY = fromY;
        this.dirX = distance > 1e-5f ? dx / distance : 0f;
        this.dirY = distance > 1e-5f ? dy / distance : 1f;
        this.aimedLength = distance;
        this.vMax = Math.max(vMax, 0.01f);
        this.v0 = Math.max(0f, Math.min(v0, this.vMax));
        this.acc = acc;
        this.boostEnd = acc > 0f ? (this.vMax - this.v0) / acc : 0f;
        this.burnEnd = Math.max(burn > 0f ? burn : Float.MAX_VALUE, boostEnd);
        this.tau = tau;
        this.sBoost = acc > 0f ? this.v0 * boostEnd + 0.5f * acc * boostEnd * boostEnd : 0f;
        this.sBurn = burnEnd == Float.MAX_VALUE ? Float.MAX_VALUE : sBoost + this.vMax * (burnEnd - boostEnd);
        this.vBurn = this.vMax;
        // How long to cover the aimed distance, or to run out of coast short of it.
        float reach = burnEnd == Float.MAX_VALUE ? Float.MAX_VALUE : sBurn + vBurn * tau * (1f - (float) Math.exp(-COAST_TAUS));
        float flight;
        float flown;
        if (distance <= reach) {
            flight = timeToCover(distance);
            flown = distance;
        } else {
            flight = burnEnd + COAST_TAUS * tau;
            flown = reach;
        }
        this.length = flown;
        this.seconds = Math.max(flight, armingSeconds);
        this.bulge = bulge;
        this.weaveAmplitude = weaveAmplitude;
        this.weaveHz = weaveHz;
        this.weavePhase = weavePhase;
        this.terminal = Math.max(0f, Math.min(0.8f, terminal));
    }

    /**
     * A missile's flight to {@code (toX, toY)}, shaped by its guidance class and the
     * round's {@code seed}. The path ends at the aimed point unless the motor cannot
     * carry the round that far, in which case it ends where the coast stops: read
     * {@link #landX()} and {@link #landY()} for where.
     */
    public static MissilePath plan(MissileFlightSpec spec, float fromX, float fromY, float toX, float toY, long seed) {
        return plan(spec, fromX, fromY, toX, toY, seed, 1f);
    }

    /**
     * {@link #plan}, for a missile whose speeds are all {@code speedScale} times its spec's: a missile at a hull, which
     * flies {@link WarshipScale#HULL_RANGE} times as far in the same seconds, so its motor's burn, its coast and its
     * flight time are the spec's at hull-fight range.
     */
    public static MissilePath plan(MissileFlightSpec spec, float fromX, float fromY, float toX, float toY, long seed,
                                   float speedScale) {
        return plan(spec, fromX, fromY, toX, toY, seed, speedScale, 0f, 1f);
    }

    /**
     * {@link #plan}, for a rocket loosed from a hull making {@code carrySpeed} cells a second along the rocket's line: it
     * leaves with the hull's way on top of its own launch speed, its top speed is raised by the same, and its motor
     * pushes {@code accelScale} times as hard, so a strafing hull's rockets run out ahead of it instead of being
     * overflown.
     */
    public static MissilePath carried(MissileFlightSpec spec, float fromX, float fromY, float toX, float toY, long seed,
                                      float carrySpeed, float accelScale) {
        return plan(spec, fromX, fromY, toX, toY, seed, 1f, Math.max(0f, carrySpeed), accelScale);
    }

    private static MissilePath plan(MissileFlightSpec spec, float fromX, float fromY, float toX, float toY, long seed,
                                    float speedScale, float carry, float accelScale) {
        float vMax = WarshipScale.cells(spec.maxSpeedPx()) * speedScale + carry;
        float v0 = WarshipScale.cells(spec.launchSpeedPx()) * speedScale + carry;
        float acc = WarshipScale.cells(spec.accelerationPx()) * speedScale * accelScale;
        float dec = WarshipScale.cells(spec.decelerationPx()) * speedScale;
        float tau = dec > 0f ? Math.max(0.4f, Math.min(3f, vMax / dec)) : DEFAULT_COAST_TAU;
        float dx = toX - fromX;
        float dy = toY - fromY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        boolean unguided = spec.guidance() == MissileFlightSpec.Guidance.UNGUIDED;
        float side = unit(seed, 1) < 0.5f ? -1f : 1f;
        float fanDegrees = (unguided ? ROCKET_FAN_DEGREES : GUIDED_FAN_DEGREES) * (0.35f + 0.65f * unit(seed, 2));
        // A launch angle of fan degrees off the line, bent back onto it, peaks at length*tan(fan)/pi.
        float bulge = side * distance * (float) Math.tan(Math.toRadians(fanDegrees)) / (float) Math.PI;
        float phase = unit(seed, 3) * 2f * (float) Math.PI;
        float hz;
        float amplitude;
        if (unguided) {
            hz = 1.6f + 0.8f * unit(seed, 4);
            amplitude = Math.abs(bulge) * ROCKET_WOBBLE_SHARE;
        } else {
            float agility = Math.min(1f, spec.turnRateDegPerSec() / 240f);
            hz = (0.4f + 0.5f * agility) * (0.9f + 0.2f * unit(seed, 4));
            float desired = GUIDED_WEAVE_SHARE * distance * (0.6f + 0.4f * unit(seed, 5))
                    * (spec.guidance() == MissileFlightSpec.Guidance.POORLY_GUIDED ? 0.6f : 1f);
            amplitude = Math.min(desired, weaveBound(vMax, spec.turnRateDegPerSec(), hz));
        }
        return new MissilePath(fromX, fromY, toX, toY, v0, vMax, acc, spec.burnSeconds(), tau,
                spec.armingSeconds(), bulge, amplitude, hz, phase);
    }

    /** {@code -Dbattle.warship.missileFlight=false} is the control: a missile at a hull flies the ground flight's shape. */
    public static final boolean HULL_FLIGHT = !"false".equalsIgnoreCase(System.getProperty("battle.warship.missileFlight"));
    /** The widest a guided missile at a hull leaves its line toward the tube's bearing, in degrees. */
    static final float GUIDED_LAUNCH_DEGREES = 40f;
    /** The widest a heavy body (a torpedo, a Harpoon) leaves its line: it is pointed, then runs in. */
    static final float HEAVY_LAUNCH_DEGREES = 12f;
    /** The terminal run's share of the flight: a guided missile's dive, and a heavy body's long straight run-in. */
    static final float GUIDED_TERMINAL = 0.25f;
    static final float HEAVY_TERMINAL = 0.45f;
    /** A heavy body's weave against a guided missile's: it holds its line. */
    static final float HEAVY_WEAVE_SHARE = 0.25f;

    /**
     * A missile's flight at a hull, read as one: it leaves the tube along the barrel's bearing ({@code launchDegrees},
     * the hull-facing convention) and swings onto its line in a <b>launch arc</b>, weaves through the middle of its
     * flight, and flies the last of it straight down the line in a <b>terminal run</b>. A heavy body (a torpedo or a
     * Harpoon) is pointed rather than swung: a short arc, barely a weave, and a long straight run-in, so a torpedo reads
     * as a slow deliberate blow a defender sees coming. An unguided rocket keeps its own wobble. Kinematics along the
     * line are {@link #plan}'s, so its timing, speed and landing are unchanged; only the shape across the line differs.
     */
    public static MissilePath atHull(MissileFlightSpec spec, float fromX, float fromY, float toX, float toY, long seed,
                                     float speedScale, float launchDegrees, boolean heavy) {
        MissilePath base = plan(spec, fromX, fromY, toX, toY, seed, speedScale);
        if (!HULL_FLIGHT || spec.guidance() == MissileFlightSpec.Guidance.UNGUIDED && !heavy) return base;
        // How far the barrel's bearing lies off the line, signed to the line's left.
        double r = Math.toRadians(launchDegrees);
        float lx = (float) -Math.sin(r);
        float ly = (float) Math.cos(r);
        float along = lx * base.dirX + ly * base.dirY;
        float across = lx * -base.dirY + ly * base.dirX;
        float off = (float) Math.toDegrees(Math.atan2(across, along));
        float limit = heavy ? HEAVY_LAUNCH_DEGREES : GUIDED_LAUNCH_DEGREES;
        float terminal = heavy ? HEAVY_TERMINAL : GUIDED_TERMINAL;
        // A tube already on the line still fans a little, to the side its seed gives, so a volley is a trumpet.
        float fan = Math.abs(off) < 2f ? Math.signum(base.bulge == 0f ? 1f : base.bulge) * limit * 0.25f
                : Math.max(-limit, Math.min(limit, off));
        // The arc is flown over the shaped share of the flight, so its peak is that share's.
        float shaped = base.aimedLength * (1f - terminal);
        float bulge = shaped * (float) Math.tan(Math.toRadians(fan)) / (float) Math.PI;
        float weave = heavy ? base.weaveAmplitude * HEAVY_WEAVE_SHARE : base.weaveAmplitude * (1f - terminal);
        return new MissilePath(fromX, fromY, toX, toY, base.v0, base.vMax, base.acc,
                base.burnEnd == Float.MAX_VALUE ? 0f : base.burnEnd, base.tau, base.seconds, bulge, weave,
                base.weaveHz, base.weavePhase, terminal);
    }

    /** The terminal run's share of the flight; zero for a path with none. */
    public float terminalShare() {
        return terminal;
    }

    /** A round that flies a plain line at one speed: a gun's shell, and the control the shaped paths are measured against. */
    public static MissilePath straight(float fromX, float fromY, float toX, float toY, float speedCellsPerSecond) {
        return new MissilePath(fromX, fromY, toX, toY, speedCellsPerSecond, speedCellsPerSecond, 0f, 0f,
                DEFAULT_COAST_TAU, 0f, 0f, 0f, 0f, 0f);
    }

    /**
     * The largest weave amplitude, in cells, that a missile cruising at
     * {@code cruiseCellsPerSecond} with the given turn rate can fly at {@code hz}
     * without its lateral acceleration, {@code A (2 pi f)^2}, exceeding the
     * {@code v * omega} its turn rate allows. Zero for a missile that cannot turn.
     */
    public static float weaveBound(float cruiseCellsPerSecond, float turnRateDegPerSecond, float hz) {
        if (turnRateDegPerSecond <= 0f || hz <= 0f) return 0f;
        float omega = (float) Math.toRadians(turnRateDegPerSecond);
        float w = 2f * (float) Math.PI * hz;
        return TURN_MARGIN * cruiseCellsPerSecond * omega / (w * w);
    }

    /** A stateless unit-interval hash of {@code (seed, stream)}: SplitMix64 finalised to [0, 1). */
    public static float unit(long seed, int stream) {
        long z = seed + (stream + 1) * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (float) ((z >>> 40) / (double) (1L << 24));
    }

    private float timeToCover(float d) {
        if (acc > 0f && d <= sBoost) {
            return (-v0 + (float) Math.sqrt(v0 * v0 + 2f * acc * d)) / acc;
        }
        if (burnEnd == Float.MAX_VALUE || d <= sBurn) {
            return boostEnd + (d - sBoost) / vMax;
        }
        float reach = vBurn * tau;
        float left = Math.min(d - sBurn, reach * 0.999f);
        return burnEnd - tau * (float) Math.log(1f - left / reach);
    }

    /** Distance along the line flown by time {@code t}, in cells. */
    public float distance(float t) {
        if (t <= 0f) return 0f;
        float s;
        if (acc > 0f && t <= boostEnd) {
            s = v0 * t + 0.5f * acc * t * t;
        } else if (t <= burnEnd) {
            s = sBoost + vMax * (t - boostEnd);
        } else {
            float c = t - burnEnd;
            s = sBurn + vBurn * tau * (1f - (float) Math.exp(-c / tau));
        }
        return Math.min(s, length);
    }

    /** Forward speed at time {@code t}, in cells per second; zero once the round has landed. */
    public float speed(float t) {
        if (t >= seconds || t < 0f) return 0f;
        float v;
        if (acc > 0f && t <= boostEnd) v = v0 + acc * t;
        else if (t <= burnEnd) v = vMax;
        else v = vBurn * (float) Math.exp(-(t - burnEnd) / tau);
        return distance(t) >= length ? 0f : v;
    }

    /** Offset to the left of the line of flight at time {@code t}, in cells. */
    public float lateral(float t) {
        if (length <= 1e-4f) return 0f;
        float u = Math.max(0f, Math.min(1f, distance(t) / length));
        // The terminal run: the lateral shape is flown out over the leading share, then the round comes straight in.
        if (terminal > 0f) u = Math.min(1f, u / (1f - terminal));
        float envelope = (float) Math.sin(Math.PI * u);
        float wave = weaveAmplitude * (float) Math.sin(2f * Math.PI * weaveHz * t + weavePhase);
        return envelope * (bulge + wave);
    }

    /** World X of the round at time {@code t}. */
    public float x(float t) {
        return fromX + dirX * distance(t) - dirY * lateral(t);
    }

    /** World Y of the round at time {@code t}. */
    public float y(float t) {
        return fromY + dirY * distance(t) + dirX * lateral(t);
    }

    /** Where it lands, X: the aimed point, or short of it by what the coast lost. */
    public float landX() {
        return fromX + dirX * length;
    }

    /** Where it lands, Y. */
    public float landY() {
        return fromY + dirY * length;
    }

    /** Seconds from the muzzle to the landing, arming time included. */
    public float totalSeconds() {
        return seconds;
    }

    /** Cells actually flown along the line, which is less than {@link #aimedLength()} when the round falls short. */
    public float length() {
        return length;
    }

    /** Cells from the muzzle to the point that was aimed at. */
    public float aimedLength() {
        return aimedLength;
    }

    /** Whether the motor could not carry the round to the point aimed at. */
    public boolean fallsShort() {
        return length < aimedLength - 1e-3f;
    }

    /** The launch fan's bulge, signed, in cells; the lateral peak of the path with no weave. */
    public float bulge() {
        return bulge;
    }

    /** The weave amplitude, in cells. */
    public float weaveAmplitude() {
        return weaveAmplitude;
    }

    /** The weave frequency, in hertz. */
    public float weaveHz() {
        return weaveHz;
    }

    /** The direction the nose points at time {@code t}: the path's tangent, in the hull-facing convention. */
    public float headingDegrees(float t) {
        float e = 0.02f;
        float a = Math.max(0f, t - e);
        float b = Math.min(seconds, t + e);
        if (b - a < 1e-4f) b = a + e;
        float dx = x(b) - x(a);
        float dy = y(b) - y(a);
        if (dx * dx + dy * dy < 1e-10f) return AirBody.facingToward(dirX, dirY);
        return AirBody.facingToward(dx, dy);
    }
}
