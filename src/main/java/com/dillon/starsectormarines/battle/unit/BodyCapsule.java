package com.dillon.starsectormarines.battle.unit;

/**
 * The soft-collision shape of an elongated ground body: a segment laid along
 * the body's facing, swept by a radius. A round body is a capsule of zero
 * length, which is every body but the ones named here.
 *
 * <p>This is a separation shape only. {@link UnitType#radius} stays the body's
 * size to everything else that asks — navigation occupancy, the spatial index,
 * ballistics, blast catch and selection — because those questions are about
 * where the body <em>is</em>, which the centre answers, while the crowding
 * question is about how much ground it covers, which a hound-shaped body does
 * not answer with a circle. Design: {@code continuous-positions-nouns.md}.
 *
 * <p>Body frame: forward is the layered facing ({@code LAYERED_FACING_DEGREES},
 * sprite degrees, 0 = +Y, counter-clockwise), so the forward unit vector is
 * {@code (-sin f, cos f)}.
 */
public final class BodyCapsule {

    /**
     * The Rav Bloom Runner, measured off its drawn skeleton: the carapace runs
     * from about 0.65 cells behind the sim position (where the tail thins to
     * its spike, which is left out) to the nose 0.85 cells ahead, and is about
     * 0.46 cells across.
     */
    static final float RUNNER_HALF_LENGTH = 0.52f;
    static final float RUNNER_RADIUS = 0.23f;
    static final float RUNNER_FORWARD_OFFSET = 0.10f;

    private BodyCapsule() {
    }

    /** Whether {@code type} is drawn long enough to be shoved as a capsule. */
    public static boolean isElongated(UnitType type) {
        return type == UnitType.SWARM_RUNNER;
    }

    /**
     * Half the segment's length, cells; {@code 0} for a round body. A swarm
     * body's capsule scales linearly with its growth stage
     * ({@link SwarmStage#scale}); {@code null} reads as an adult.
     */
    public static float halfLength(UnitType type, SwarmStage stage) {
        return isElongated(type) ? RUNNER_HALF_LENGTH * SwarmStage.of(stage).scale : 0f;
    }

    /** How far ahead of the sim position the segment's midpoint sits, cells. */
    public static float forwardOffset(UnitType type, SwarmStage stage) {
        return isElongated(type) ? RUNNER_FORWARD_OFFSET * SwarmStage.of(stage).scale : 0f;
    }

    /**
     * The swept radius, cells. A round body's is its own radius, except a
     * swarm body's, which is its stage's drawn radius: an elder Brute is
     * capped under half a cell to the grid and shoved at its full size.
     */
    public static float radius(UnitType type, float bodyRadius, SwarmStage stage) {
        if (isElongated(type)) return RUNNER_RADIUS * SwarmStage.of(stage).scale;
        return type.isSwarm() ? SwarmStage.of(stage).drawnRadius(type.radius) : bodyRadius;
    }

    /** Farthest the capsule reaches from the sim position, cells. */
    public static float reach(UnitType type, float bodyRadius, SwarmStage stage) {
        return Math.abs(forwardOffset(type, stage)) + halfLength(type, stage) + radius(type, bodyRadius, stage);
    }

    /**
     * The closest points between segment {@code P} (midpoint {@code (px, py)},
     * half-extent {@code (phx, phy)}) and segment {@code Q}, written as
     * {@code out = {pointOnP.x, pointOnP.y, pointOnQ.x, pointOnQ.y}}. A segment
     * of zero half-extent is a point, so circle-circle, circle-capsule and
     * capsule-capsule are one call. Parallel segments resolve to the midpoint
     * of their overlap, so the answer does not depend on which end a parallel
     * pair is measured from.
     */
    public static void closestPoints(float px, float py, float phx, float phy,
                                     float qx, float qy, float qhx, float qhy,
                                     float[] out) {
        // Segments as P(s) = P0 + s*d1 and Q(t) = Q0 + t*d2, s and t in [0, 1].
        float p0x = px - phx, p0y = py - phy;
        float q0x = qx - qhx, q0y = qy - qhy;
        float d1x = 2f * phx, d1y = 2f * phy;
        float d2x = 2f * qhx, d2y = 2f * qhy;
        float rx = p0x - q0x, ry = p0y - q0y;
        float a = d1x * d1x + d1y * d1y;
        float e = d2x * d2x + d2y * d2y;
        float f = d2x * rx + d2y * ry;
        float s;
        float t;
        if (a <= EPS && e <= EPS) {
            s = 0f;
            t = 0f;
        } else if (a <= EPS) {
            s = 0f;
            t = clamp01(f / e);
        } else {
            float c = d1x * rx + d1y * ry;
            if (e <= EPS) {
                t = 0f;
                s = clamp01(-c / a);
            } else {
                float b = d1x * d2x + d1y * d2y;
                float denom = a * e - b * b;
                if (denom > PARALLEL_SIN2 * a * e) {
                    s = clamp01((b * f - c * e) / denom);
                    t = (b * s + f) / e;
                    if (t < 0f) {
                        t = 0f;
                        s = clamp01(-c / a);
                    } else if (t > 1f) {
                        t = 1f;
                        s = clamp01((b - c) / a);
                    }
                } else {
                    // Parallel: take the middle of the stretch where the two
                    // overlap along P's axis, or the nearer ends if they do not.
                    float q1 = -c / a;              // Q0 projected onto P, in s
                    float q2 = (b - c) / a;         // Q1 projected onto P, in s
                    float lo = Math.max(0f, Math.min(q1, q2));
                    float hi = Math.min(1f, Math.max(q1, q2));
                    s = lo <= hi ? 0.5f * (lo + hi) : (hi < 0f ? 0f : 1f);
                    float sx = p0x + d1x * s - q0x;
                    float sy = p0y + d1y * s - q0y;
                    t = clamp01((d2x * sx + d2y * sy) / e);
                    s = clamp01(((q0x + d2x * t - p0x) * d1x + (q0y + d2y * t - p0y) * d1y) / a);
                }
            }
        }
        out[0] = p0x + d1x * s;
        out[1] = p0y + d1y * s;
        out[2] = q0x + d2x * t;
        out[3] = q0y + d2y * t;
    }

    private static final float EPS = 1e-9f;
    /** Below this squared sine of the angle between them, two segments are treated as parallel. */
    private static final float PARALLEL_SIN2 = 1e-6f;

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
