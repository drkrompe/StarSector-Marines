package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/** Shared steering math and state writes for a grid-moving mech chassis. */
public final class MechLocomotion {

    /** Maximum stock-heavy hip traverse speed, in degrees per sim-second. */
    public static final float DEFAULT_TURN_RATE_DEGREES = 180f;
    /** Angular acceleration/braking budget that gives the hips visible mass. */
    public static final float DEFAULT_TURN_ACCELERATION_DEGREES = 360f;
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

    /** Advances the persistent heading with bounded angular speed and acceleration. */
    public static float turnToward(EntityWorld world, BattleComponents components,
                                   long id, float desired, float dt) {
        float current = world.getFloat(id, components.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_FACING_DEGREES);
        float angularVelocity = world.getFloat(id, components.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_ANGULAR_VELOCITY);
        float turnRate = world.getFloat(id, components.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_TURN_RATE);
        AngularStep next = dampedTurn(current, angularVelocity, desired, turnRate,
                DEFAULT_TURN_ACCELERATION_DEGREES, dt);
        world.setFloat(id, components.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_FACING_DEGREES, next.facingDegrees());
        world.setFloat(id, components.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_ANGULAR_VELOCITY,
                next.angularVelocityDegrees());
        return Math.abs(deltaDegrees(next.facingDegrees(), desired));
    }

    /** Brakes residual hip rotation without discarding its momentum in one tick. */
    public static void stopTurning(EntityWorld world, BattleComponents components,
                                   long id, float dt) {
        float current = world.getFloat(id, components.MECH_LOCOMOTION,
                BattleComponents.MECH_LOCOMOTION_FACING_DEGREES);
        turnToward(world, components, id, current, dt);
    }

    /**
     * Acceleration-limited shortest-arc turn. The stopping-speed envelope slows
     * the body before the goal, while an intent reversal must first arrest the
     * old angular momentum instead of snapping instantly to the opposite turn.
     */
    public static AngularStep dampedTurn(float current, float angularVelocity,
                                         float desired, float maxTurnRate,
                                         float angularAcceleration, float dt) {
        float safeDt = Math.max(0f, dt);
        float safeRate = Math.max(0f, maxTurnRate);
        float safeAcceleration = Math.max(0f, angularAcceleration);
        if (safeDt == 0f || safeRate == 0f) {
            return new AngularStep(LayeredAppearance.wrapDegrees(current), 0f);
        }

        float error = deltaDegrees(current, desired);
        float desiredVelocity = Math.copySign(safeRate, error);
        if (safeAcceleration > 0f) {
            float stoppingVelocity = (float) Math.sqrt(
                    2f * safeAcceleration * Math.abs(error));
            desiredVelocity = Math.copySign(Math.min(safeRate, stoppingVelocity), error);
        }
        if (Math.abs(error) < 0.0001f) desiredVelocity = 0f;

        float velocityStep = safeAcceleration > 0f
                ? safeAcceleration * safeDt : safeRate;
        float nextVelocity = approach(angularVelocity, desiredVelocity, velocityStep);
        nextVelocity = Math.max(-safeRate, Math.min(safeRate, nextVelocity));
        float facingStep = nextVelocity * safeDt;
        if (facingStep != 0f && Math.signum(facingStep) == Math.signum(error)
                && Math.abs(facingStep) >= Math.abs(error)) {
            return new AngularStep(LayeredAppearance.wrapDegrees(desired), 0f);
        }
        return new AngularStep(LayeredAppearance.wrapDegrees(current + facingStep),
                nextVelocity);
    }

    private static float approach(float current, float desired, float maxStep) {
        if (current < desired) return Math.min(desired, current + maxStep);
        return Math.max(desired, current - maxStep);
    }

    public record AngularStep(float facingDegrees, float angularVelocityDegrees) { }

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
