package com.dillon.starsectormarines.battle.evacuation.flier;

import com.dillon.starsectormarines.battle.appearance.SpineBend;

/**
 * A Harrier's body under its wings, advanced once a tick with its flight: the waist bends through a turn as a
 * Runner's or a Brute's back does ({@link SpineBend}), the tail trails behind it segment by segment, lagging the
 * turn and swaying with the wingbeat, the head looks about, and the whole body pumps with the stroke. The wings
 * ride on the thorax; the drawing composes the segments about their joints ({@code HarrierBodyComposer}).
 *
 * <p>Angles are degrees, each segment's relative to the one before it (the abdomen to the thorax, the first tail
 * segment to the abdomen, and so on); positive turns left, as a facing does. The head is relative to the thorax.
 * Presentation only, a pure function of the flight's own history, so a replay draws the same.
 */
public final class HarrierBodyRig {

    /** Tail segments behind the abdomen. */
    public static final int TAIL_SEGMENTS = 3;

    /** The waist's give through a turn: degrees per degree a second, the most, and the lag (softer than a Brute's). */
    static final float WAIST_PER_YAW_RATE = 0.08f, WAIST_MAX = 16f, WAIST_SECONDS = 0.15f;
    /** Each tail segment follows the one before it over this long, the tip slowest: the tail whips through a turn. */
    static final float[] TAIL_SECONDS = {0.10f, 0.14f, 0.18f};
    /** The most a tail segment bends against the one before it. */
    static final float TAIL_MAX = 24f;
    /** How far the tail sways with each wingbeat, degrees at the tip, and how far the sway lags along it. */
    static final float TAIL_BEAT_DEG = 7f, TAIL_BEAT_LAG = 0.12f;

    /** How much the body grows at the top of a downstroke, and shrinks at the bottom of the upstroke. */
    public static final float PUMP = 0.035f;

    /** The furthest the head turns from the thorax, and how quickly it turns. */
    static final float HEAD_MAX = 55f, HEAD_SECONDS = 0.22f;
    /** A circling head scans either side of its prey this far, this often (cycles a second); seeded per body. */
    static final float SCAN_DEG = 28f, SCAN_HZ = 0.35f;

    /** The abdomen's bend against the thorax. */
    public float waist;
    /** Each tail segment's bend against the one before it. */
    public final float[] tail = new float[TAIL_SEGMENTS];
    /** The head's turn against the thorax. */
    public float head;
    /** The body's scale this tick, 1 at rest, up to {@code 1 + PUMP} at the top of a stroke. */
    public float pump = 1f;

    /** Each tail segment's own heading, which it eases after the one before it. */
    private final float[] heading = new float[TAIL_SEGMENTS];
    private float facing;
    private boolean started;

    /**
     * One tick. {@code beatPhase} is the stroke ({@link HarrierWingbeat#phase}, -1 gliding); ({@code lookX},
     * {@code lookY}) is what the head looks toward, or NaN for nothing; {@code locked} holds the head on it rather
     * than scanning about it.
     */
    public void advance(HarrierFlight m, float beatPhase, float lookX, float lookY, boolean locked, float dt) {
        float f = m.body.facingDegrees;
        if (!started) {
            started = true;
            facing = f;
            for (int i = 0; i < TAIL_SEGMENTS; i++) heading[i] = f;
        }
        waist = SpineBend.ease(waist, SpineBend.yawRate(facing, f, dt), -WAIST_PER_YAW_RATE, WAIST_MAX,
                WAIST_SECONDS, dt);
        facing = f;

        // The tail: each segment eases its heading after the one ahead of it, so a turn runs down it, and the
        // stroke sways it, the tip furthest and latest.
        float ahead = f + waist;
        for (int i = 0; i < TAIL_SEGMENTS; i++) {
            float share = (i + 1f) / TAIL_SEGMENTS;
            float sway = beatPhase < 0f ? 0f : TAIL_BEAT_DEG * share
                    * (float) Math.sin(2 * Math.PI * (beatPhase - TAIL_BEAT_LAG * (i + 1)));
            float want = ahead + sway / TAIL_SEGMENTS;
            heading[i] += delta(heading[i], want) * Math.min(1f, dt / TAIL_SECONDS[i]);
            tail[i] = Math.max(-TAIL_MAX, Math.min(TAIL_MAX, delta(ahead, heading[i])));
            heading[i] = ahead + tail[i];
            ahead = heading[i];
        }

        pump = 1f + PUMP * HarrierWingbeat.lift(beatPhase);

        float want = 0f;
        if (!Float.isNaN(lookX)) {
            float bearing = (float) Math.toDegrees(Math.atan2(-(lookX - m.body.x), lookY - m.body.y));
            want = delta(f, bearing);
        }
        if (!locked) {
            float seed = (float) ((m.id * 0x9E3779B97F4A7C15L >>> 40) % 1000) / 1000f;
            want += SCAN_DEG * (float) Math.sin(2 * Math.PI * (SCAN_HZ * m.wingClock + seed));
        }
        want = Math.max(-HEAD_MAX, Math.min(HEAD_MAX, want));
        head += (want - head) * Math.min(1f, dt / HEAD_SECONDS);
    }

    /** The short way from one heading to another, degrees. */
    static float delta(float from, float to) {
        return ((to - from) % 360f + 540f) % 360f - 180f;
    }
}
