package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.air.AirBody;

/**
 * Tier-neutral animation vocabulary and sampling math for modular top-down
 * infantry.  The appearance system writes these values into the ECS; renderers
 * consume them without reconstructing combat or movement state.
 */
public final class LayeredAppearance {

    public static final int POSE_IDLE = 0;
    public static final int POSE_AIMED = 1;
    public static final int POSE_FIRING = 2;
    public static final int POSE_ROCKET_AIM = 3;
    public static final int POSE_ROCKET_FIRE = 4;

    public static final int FLAG_MOVING = 1;
    public static final int FLAG_MUZZLE_FLASH = 1 << 1;
    public static final int FLAG_WEAPON_OVER_SHOULDER = 1 << 2;

    /** Primary muzzle flash lifetime in sim seconds. */
    public static final float PRIMARY_FLASH_SECONDS = 0.09f;
    /** Rocket backblast/muzzle-flash lifetime after the aim midpoint. */
    public static final float ROCKET_FLASH_SECONDS = 0.14f;
    /** Maximum independent helmet look away from the torso heading. */
    public static final float MAX_HEAD_LOOK_DEGREES = 65f;

    private LayeredAppearance() {}

    /** 0° = north/+Y, positive counter-clockwise, matching SpriteAPI. */
    public static float facingDegrees(int dx, int dy) {
        if (dx == 0 && dy == 0) return 180f;
        return AirBody.facingToward(dx, dy);
    }

    /** Normalizes any angle to [-180, 180). */
    public static float wrapDegrees(float degrees) {
        float wrapped = (degrees + 180f) % 360f;
        if (wrapped < 0f) wrapped += 360f;
        return wrapped - 180f;
    }

    /** Target look relative to the torso, clamped to a plausible neck turn. */
    public static float headLookDegrees(float torsoFacing, float targetFacing) {
        float delta = wrapDegrees(targetFacing - torsoFacing);
        return Math.max(-MAX_HEAD_LOOK_DEGREES, Math.min(MAX_HEAD_LOOK_DEGREES, delta));
    }

    /** Gait phase accumulates one repeating [0,1) walk cycle per cell traveled. */
    public static float locomotionPhase(float gaitPhase) {
        float phase = gaitPhase % 1f;
        return phase < 0f ? phase + 1f : phase;
    }

    /** Alternating positive half-sine used to reveal only one foot tip at a time. */
    public static float leftFootReveal(float phase) {
        return Math.max(0f, (float) Math.sin(phase * Math.PI * 2.0));
    }

    /** Alternating positive half-sine, half a cycle behind the left foot. */
    public static float rightFootReveal(float phase) {
        return Math.max(0f, (float) -Math.sin(phase * Math.PI * 2.0));
    }

    /** Small backward kick along a weapon's local axis, expressed in sw. */
    public static float recoilSw(int pose, float phase) {
        if (pose != POSE_FIRING && pose != POSE_ROCKET_FIRE) return 0f;
        float t = Math.max(0f, Math.min(1f, phase));
        float peak = pose == POSE_ROCKET_FIRE ? 0.055f : 0.025f;
        return peak * (float) Math.sin(t * Math.PI);
    }
}
