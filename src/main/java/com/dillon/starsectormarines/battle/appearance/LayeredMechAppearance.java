package com.dillon.starsectormarines.battle.appearance;

/** Animation vocabulary for a modular true-overhead mech composition. */
public final class LayeredMechAppearance {

    public static final int FLAG_MOVING = 1;
    public static final int FLAG_CHAINGUN_ACTIVE = 1 << 1;
    public static final int FLAG_CHAINGUN_FLASH = 1 << 2;
    public static final int FLAG_SRM_ACTIVE = 1 << 3;
    public static final int FLAG_SRM_FLASH = 1 << 4;
    public static final int FLAG_LRM_ACTIVE = 1 << 5;
    public static final int FLAG_LRM_FLASH = 1 << 6;
    public static final int FLAG_TURNING = 1 << 7;

    public static final float FLASH_SECONDS = 0.075f;
    /** Maximum upper-chassis traverse to either side of the planted hips. */
    public static final float MAX_TORSO_TWIST_DEGREES = 70f;

    public static final int ARMS_CHAINGUN = 0;
    public static final int ARMS_LINEAR_CANNON = 1;
    public static final int ARMS_NOSE_CHAINGUN = 2;
    public static final int ARMS_HEAVY_CANNON = 3;
    public static final int CHASSIS_CLEAN = 0;
    public static final int CHASSIS_SOCKETED = 1;
    public static final int CHASSIS_HOUND = 2;
    public static final int CHASSIS_SIROCCO = 3;
    public static final int POD_NONE = 0;
    public static final int POD_SMALL_SRM = 1;
    public static final int POD_SMALL_LRM = 2;
    public static final int POD_LARGE_SRM = 3;
    public static final int POD_LARGE_LRM = 4;
    /** Compatibility names retained for authored tests and older call sites. */
    public static final int POD_SRM = POD_SMALL_SRM;
    public static final int POD_LRM = POD_LARGE_LRM;
    public static final int POD_HEAVY_SRM = POD_LARGE_SRM;

    private LayeredMechAppearance() {}

    public static float trackPhase(float timer, float spacing) {
        if (spacing <= 0f) return 0f;
        return clamp01((spacing - Math.max(0f, timer)) / spacing);
    }

    public static boolean trackFlash(float cooldown, float cooldownReset,
                                     int remaining, float timer, float spacing) {
        float sinceTrigger = Math.max(0f, cooldownReset - cooldown);
        if (sinceTrigger <= FLASH_SECONDS) return true;
        if (remaining <= 0 || spacing <= 0f) return false;
        float sinceRound = Math.max(0f, spacing - timer);
        return sinceRound <= FLASH_SECONDS;
    }

    public static float recoil(float phase) {
        float t = clamp01(phase);
        return (float) Math.sin(t * Math.PI);
    }

    /** Two planted-foot cycles per quarter turn, derived from persistent heading. */
    public static float turnStepPhase(float facingDegrees) {
        float phase = facingDegrees / 45f;
        phase -= (float) Math.floor(phase);
        return phase;
    }

    /** Upper-chassis bearing constrained by the hip-spine traverse. */
    public static float torsoFacing(float hipFacing, float desiredFacing) {
        float twist = LayeredAppearance.wrapDegrees(desiredFacing - hipFacing);
        twist = Math.max(-MAX_TORSO_TWIST_DEGREES,
                Math.min(MAX_TORSO_TWIST_DEGREES, twist));
        return LayeredAppearance.wrapDegrees(hipFacing + twist);
    }

    /**
     * A planted mechanical step: quick lift, a short held extension, then a
     * quick set-down rather than the infantry animation's smooth sine wave.
     */
    public static float mechanicalFootReveal(float phase, boolean rightFoot) {
        float p = phase + (rightFoot ? 0.5f : 0f);
        p -= (float) Math.floor(p);
        if (p < 0.16f || p >= 0.76f) return 0f;
        if (p < 0.34f) return (p - 0.16f) / 0.18f;
        if (p < 0.56f) return 1f;
        return 1f - (p - 0.56f) / 0.20f;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
