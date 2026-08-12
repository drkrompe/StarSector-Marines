package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/** Shared steering math and state writes for a grid-moving mech chassis. */
public final class MechLocomotion {

    /** A stock heavy chassis pivots through 90 degrees in half a second. */
    public static final float DEFAULT_TURN_RATE_DEGREES = 180f;
    /** Forward travel may begin once the chassis is this close to its path bearing. */
    public static final float MOVE_ALIGNMENT_DEGREES = 8f;

    private MechLocomotion() {}

    /** Sprite heading for a non-zero grid delta. */
    public static float desiredFacing(float dx, float dy) {
        return LayeredAppearance.facingDegrees(Math.round(dx), Math.round(dy));
    }

    /** Signed shortest turn from {@code current} to {@code desired}. */
    public static float deltaDegrees(float current, float desired) {
        return LayeredAppearance.wrapDegrees(desired - current);
    }

    /**
     * Advances the entity's persistent heading by at most its turn-rate budget.
     * Returns the absolute error still remaining after this step.
     */
    public static float turnToward(EntityWorld world, BattleComponents components,
                                   long id, float desired, float dt) {
        float current = world.getFloat(id, components.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_FACING_DEGREES);
        float turnRate = world.getFloat(id, components.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_TURN_RATE);
        float delta = deltaDegrees(current, desired);
        float maxStep = Math.max(0f, turnRate) * Math.max(0f, dt);
        float step = Math.max(-maxStep, Math.min(maxStep, delta));
        float next = LayeredAppearance.wrapDegrees(current + step);
        world.setFloat(id, components.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_FACING_DEGREES, next);
        world.setFloat(id, components.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_ANGULAR_VELOCITY,
                dt > 0f ? step / dt : 0f);
        return Math.abs(deltaDegrees(next, desired));
    }

    public static void stopTurning(EntityWorld world, BattleComponents components, long id) {
        world.setFloat(id, components.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_ANGULAR_VELOCITY, 0f);
    }

    /**
     * Converts constant logical path progress into a two-stage mechanical
     * stride: plant, drive forward, brace at mid-step, drive, settle.
     */
    public static float mechanicalTravelProgress(float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        if (p <= 0.08f) return 0f;
        if (p < 0.46f) return (p - 0.08f) / 0.38f * 0.55f;
        if (p <= 0.55f) return 0.55f;
        if (p < 0.92f) return 0.55f + (p - 0.55f) / 0.37f * 0.45f;
        return 1f;
    }
}
