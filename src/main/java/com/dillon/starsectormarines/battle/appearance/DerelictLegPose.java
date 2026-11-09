package com.dillon.starsectormarines.battle.appearance;

/**
 * Where a derelict walker leg's knee stands, solved in three dimensions and seen from above: the hip is held
 * {@link #HIP_HEIGHT_CELLS} off the floor, the thigh ({@link #THIGH_CELLS}) and shin ({@link #SHIN_CELLS}) are rigid,
 * and the thigh keeps its length and the knee bends up, in the upright plane through hip and foot. A foot near the hip raises the knee high, so both
 * segments look short from above; a foot far out flattens the leg. A foot gripping a wall stands
 * {@link #WALL_FOOT_HEIGHT_CELLS} up its face. Seen from above, the knee always lies on the line from hip to foot,
 * between {@link #KNEE_MIN_SHARE} and {@link #KNEE_MAX_SHARE} of the way out (the hip sits under the torso, so
 * that puts the knee near the middle of the leg as seen): the true solve would put it over the
 * hip when the foot stands as high as the hip (a wall grip) and past the foot when the foot tucks in, and either reads
 * as a knee on the wrong end of the leg (owner, 2026-11-09). Where the solve falls outside that share the knee is held
 * to it and raised so the thigh keeps its length: a foot coming in lifts the knee. Presentation only.
 */
public final class DerelictLegPose {

    public static final float HIP_HEIGHT_CELLS = 0.70f;
    public static final float THIGH_CELLS = 1.30f;
    public static final float SHIN_CELLS = 1.60f;
    /** How far up a wall's face a gripping foot stands. */
    public static final float WALL_FOOT_HEIGHT_CELLS = 0.80f;
    /** How high a stepping foot lifts at the top of its step. */
    public static final float STEP_LIFT_CELLS = 0.45f;
    /** The nearest to the hip the knee is seen, as a share of the hip-to-foot distance. */
    public static final float KNEE_MIN_SHARE = 0.30f;
    /** The furthest from the hip the knee is seen, as a share of the hip-to-foot distance. */
    public static final float KNEE_MAX_SHARE = 0.50f;

    private DerelictLegPose() {
    }

    /**
     * The knee for a hip at (hx, hy) and a foot at (fx, fy) standing {@code footHeight} off the floor, as
     * {@code out = {kneeX, kneeY, kneeHeight}}. A foot beyond the leg's reach leaves the leg straight toward it, the
     * knee no lower than the hip.
     */
    public static void knee(float hx, float hy, float fx, float fy, float footHeight, float[] out) {
        knee(hx, hy, HIP_HEIGHT_CELLS, fx, fy, footHeight, out);
    }

    /** As {@link #knee(float, float, float, float, float, float[])}, the hip {@code hipHeight} off the floor. */
    public static void knee(float hx, float hy, float hipHeight, float fx, float fy, float footHeight, float[] out) {
        float dx = fx - hx;
        float dy = fy - hy;
        float flat = (float) Math.sqrt(dx * dx + dy * dy);
        float dz = footHeight - hipHeight;
        float reach = THIGH_CELLS + SHIN_CELLS;
        float span = (float) Math.sqrt(flat * flat + dz * dz);
        float d = Math.max(Math.abs(THIGH_CELLS - SHIN_CELLS) + 1e-3f, Math.min(reach - 1e-3f, span));
        // Along the hip-to-foot line, a; up off it, h (the knee bends away from the floor).
        float a = (THIGH_CELLS * THIGH_CELLS - SHIN_CELLS * SHIN_CELLS + d * d) / (2f * d);
        float h = (float) Math.sqrt(Math.max(0f, THIGH_CELLS * THIGH_CELLS - a * a));
        // Unit vectors in the upright plane: u along the line (flat, dz), w perpendicular to it, pointing up.
        float ux = span > 1e-4f ? flat / span : 1f;
        float uz = span > 1e-4f ? dz / span : 0f;
        float wx = -uz;
        float wz = ux;
        if (wz < 0f) {
            wx = -wx;
            wz = -wz;
        }
        float kFlat = a * ux + h * wx;
        float kZ = hipHeight + a * uz + h * wz;
        float held = Math.max(KNEE_MIN_SHARE * flat, Math.min(KNEE_MAX_SHARE * flat, kFlat));
        if (held != kFlat) {
            kFlat = held;
            kZ = hipHeight + (float) Math.sqrt(Math.max(0f, THIGH_CELLS * THIGH_CELLS - kFlat * kFlat));
        }
        float dirX = flat > 1e-4f ? dx / flat : 0f;
        float dirY = flat > 1e-4f ? dy / flat : 1f;
        out[0] = hx + dirX * kFlat;
        out[1] = hy + dirY * kFlat;
        out[2] = kZ;
    }
}
