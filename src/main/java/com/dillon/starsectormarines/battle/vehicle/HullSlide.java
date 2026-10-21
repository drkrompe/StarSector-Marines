package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * What a hull does with the velocity and spin a hit gave it ({@link HullImpact}): it slides and turns
 * against its tires or tracks until they have taken it all, and it rocks on its suspension. Sliding friction
 * is a fixed deceleration resolved in the hull's own frame, so a wheeled hull struck from behind rolls on
 * while one struck in the side scrubs to a stop, and the spin is scrubbed by the same sideways grip working
 * at the ends of the hull. Walls stop a slide as they stop a drive. Serial; ticked once per tick after the
 * hulls have driven.
 */
public final class HullSlide {

    /** The suspension: natural frequency (radians a second), damping ratio, and how far it can travel (cells). */
    private static final float JOLT_W = 14f, JOLT_ZETA = .35f, JOLT_MAX = .12f;
    /** The sprung hull takes the hit harder than the chassis under it. */
    public static final float JOLT_GAIN = 6f;
    private static final float STILL = 1e-3f;

    private HullSlide() {}

    /** Hands a hit's exchange to the hull: its slide, and the kick to its suspension. */
    public static void apply(GroundBody body, HullImpact.Result hit) {
        body.knockVx += hit.hullVx();
        body.knockVy += hit.hullVy();
        body.knockOmega += hit.hullOmega();
        body.joltVx += hit.hullVx() * JOLT_GAIN;
        body.joltVy += hit.hullVy() * JOLT_GAIN;
    }

    /** Whether the hull is still sliding, turning or rocking from a hit. */
    public static boolean moving(GroundBody body) {
        return body.knockVx != 0f || body.knockVy != 0f || body.knockOmega != 0f
                || body.joltX != 0f || body.joltY != 0f || body.joltVx != 0f || body.joltVy != 0f;
    }

    public static void tick(GroundBody body, VehicleType type, NavigationGrid grid, float radius, float dt) {
        if (!moving(body)) return;
        double rad = Math.toRadians(body.facingDegrees);
        float fx = (float) -Math.sin(rad), fy = (float) Math.cos(rad);
        float along = body.knockVx * fx + body.knockVy * fy;
        float across = body.knockVx * fy - body.knockVy * fx;
        along = scrub(along, type.gripAlong() * dt);
        across = scrub(across, type.gripAcross() * dt);
        float length = type.visualLengthCells, width = type.visualWidthCells;
        float spinGrip = type.gripAcross() * 3f * length / (length * length + width * width);
        body.knockOmega = scrub(body.knockOmega, spinGrip * dt);
        body.knockVx = along * fx + across * fy;
        body.knockVy = along * fy - across * fx;

        // Drag the hull's spring offset back out, slide, then put the new offset on: the rock never drifts.
        body.x -= body.joltX;
        body.y -= body.joltY;
        float sx = body.knockVx * dt, sy = body.knockVy * dt;
        if (sx != 0f || sy != 0f) {
            ManualTerrainMotion.Result moved = ManualTerrainMotion.move(grid, body.x, body.y, sx, sy, radius);
            if (Math.abs(moved.dx()) < Math.abs(sx) * .5f) body.knockVx = 0f;   // up against something
            if (Math.abs(moved.dy()) < Math.abs(sy) * .5f) body.knockVy = 0f;
            body.x = moved.x();
            body.y = moved.y();
        }
        body.facingDegrees += (float) Math.toDegrees(body.knockOmega * dt);

        float ax = -JOLT_W * JOLT_W * body.joltX - 2f * JOLT_ZETA * JOLT_W * body.joltVx;
        float ay = -JOLT_W * JOLT_W * body.joltY - 2f * JOLT_ZETA * JOLT_W * body.joltVy;
        body.joltVx += ax * dt;
        body.joltVy += ay * dt;
        body.joltX = clamp(body.joltX + body.joltVx * dt);
        body.joltY = clamp(body.joltY + body.joltVy * dt);
        if (Math.abs(body.joltX) < STILL * .1f && Math.abs(body.joltVx) < STILL) body.joltX = body.joltVx = 0f;
        if (Math.abs(body.joltY) < STILL * .1f && Math.abs(body.joltVy) < STILL) body.joltY = body.joltVy = 0f;
        body.x += body.joltX;
        body.y += body.joltY;
    }

    /** {@code v} brought toward zero by {@code step}, never past it: sliding friction. */
    static float scrub(float v, float step) {
        if (v > step) return v - step;
        if (v < -step) return v + step;
        return 0f;
    }

    private static float clamp(float v) {
        return Math.max(-JOLT_MAX, Math.min(JOLT_MAX, v));
    }
}
