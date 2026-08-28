package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;

/**
 * Fixed-tick presentation state for one mech's planted-foot gait. Foot anchors
 * live in world-cell coordinates so a supporting foot stays on the ground while
 * the simulation-authoritative body moves or pivots over it.
 */
public final class MechGaitState {

    public static final int NO_SWING_FOOT = -1;
    public static final int LEFT_FOOT = 0;
    public static final int RIGHT_FOOT = 1;

    private static final float TELEPORT_RESET_CELLS = 1.25f;
    private static final float MIN_BODY_SPEED = 0.04f;
    private static final float MIN_TURN_SPEED_DEGREES = 1f;
    private static final float STEP_YAW_TRIGGER_DEGREES = 12f;
    private static final float SETTLE_YAW_TRIGGER_DEGREES = 22f;
    private static final float DOUBLE_SUPPORT_SECONDS = 0.055f;

    private final int chassis;
    private final float hullWidthCells;

    private float leftFootX;
    private float leftFootY;
    private float leftFootFacing;
    private float rightFootX;
    private float rightFootY;
    private float rightFootFacing;

    private int swingFoot = NO_SWING_FOOT;
    private int nextSwingFoot = LEFT_FOOT;
    private float swingProgress;
    private float swingStartX;
    private float swingStartY;
    private float swingStartFacing;
    private float swingTargetX;
    private float swingTargetY;
    private float swingTargetFacing;
    private float doubleSupportTimer;

    private float waistOffsetX;
    private float waistOffsetY;
    private float waistVelocityX;
    private float waistVelocityY;
    private float lastBodyX;
    private float lastBodyY;

    private MechGaitState(float bodyX, float bodyY, float hipFacing,
                          int chassis, float renderScale) {
        this.chassis = chassis;
        this.hullWidthCells = LayeredMechAppearance.hullWidthCells(renderScale);
        reset(bodyX, bodyY, hipFacing);
    }

    public static MechGaitState create(float bodyX, float bodyY, float hipFacing,
                                       MechVariant variant) {
        MechVariant profile = variant != null ? variant : MechVariant.BULWARK;
        return new MechGaitState(bodyX, bodyY, hipFacing,
                profile.chassisAppearance, profile.renderScale);
    }

    /** Replants both feet in the chassis's neutral stance after spawn or teleport. */
    public void reset(float bodyX, float bodyY, float hipFacing) {
        Point left = nominalFoot(bodyX, bodyY, hipFacing, LEFT_FOOT);
        Point right = nominalFoot(bodyX, bodyY, hipFacing, RIGHT_FOOT);
        leftFootX = left.x;
        leftFootY = left.y;
        rightFootX = right.x;
        rightFootY = right.y;
        leftFootFacing = desiredFootFacing(hipFacing, LEFT_FOOT);
        rightFootFacing = desiredFootFacing(hipFacing, RIGHT_FOOT);
        swingFoot = NO_SWING_FOOT;
        nextSwingFoot = LEFT_FOOT;
        swingProgress = 0f;
        doubleSupportTimer = 0f;
        waistOffsetX = 0f;
        waistOffsetY = 0f;
        waistVelocityX = 0f;
        waistVelocityY = 0f;
        lastBodyX = bodyX;
        lastBodyY = bodyY;
    }

    /** Advances foot planting and the support-driven waist spring by one sim tick. */
    public void advance(float bodyX, float bodyY, float hipFacing,
                        float angularVelocityDegrees, float dt) {
        if (dt <= 0f) return;
        float bodyDx = bodyX - lastBodyX;
        float bodyDy = bodyY - lastBodyY;
        if (bodyDx * bodyDx + bodyDy * bodyDy
                > TELEPORT_RESET_CELLS * TELEPORT_RESET_CELLS) {
            reset(bodyX, bodyY, hipFacing);
            return;
        }

        float velocityX = bodyDx / dt;
        float velocityY = bodyDy / dt;
        float speed = length(velocityX, velocityY);
        if (swingFoot != NO_SWING_FOOT) {
            advanceSwing(dt);
        } else {
            doubleSupportTimer = Math.max(0f, doubleSupportTimer - dt);
            if (doubleSupportTimer <= 0f) {
                maybeBeginStep(bodyX, bodyY, hipFacing,
                        velocityX, velocityY, speed, angularVelocityDegrees);
            }
        }
        advanceWaist(bodyX, bodyY, hipFacing, velocityX, velocityY, speed, dt);
        lastBodyX = bodyX;
        lastBodyY = bodyY;
    }

    private void maybeBeginStep(float bodyX, float bodyY, float hipFacing,
                                float velocityX, float velocityY, float speed,
                                float angularVelocityDegrees) {
        Point leftNominal = nominalFoot(bodyX, bodyY, hipFacing, LEFT_FOOT);
        Point rightNominal = nominalFoot(bodyX, bodyY, hipFacing, RIGHT_FOOT);
        float leftStrain = distance(leftFootX, leftFootY, leftNominal.x, leftNominal.y);
        float rightStrain = distance(rightFootX, rightFootY, rightNominal.x, rightNominal.y);
        float leftYaw = Math.abs(MechLocomotion.deltaDegrees(leftFootFacing,
                desiredFootFacing(hipFacing, LEFT_FOOT)));
        float rightYaw = Math.abs(MechLocomotion.deltaDegrees(rightFootFacing,
                desiredFootFacing(hipFacing, RIGHT_FOOT)));
        boolean active = speed >= MIN_BODY_SPEED
                || Math.abs(angularVelocityDegrees) >= MIN_TURN_SPEED_DEGREES;
        float reachTrigger = stepTriggerCells();
        float yawTrigger = active ? STEP_YAW_TRIGGER_DEGREES : SETTLE_YAW_TRIGGER_DEGREES;
        float settleMultiplier = active ? 1f : 1.7f;
        boolean leftNeedsStep = leftStrain >= reachTrigger * settleMultiplier
                || leftYaw >= yawTrigger;
        boolean rightNeedsStep = rightStrain >= reachTrigger * settleMultiplier
                || rightYaw >= yawTrigger;
        if (!leftNeedsStep && !rightNeedsStep) return;

        int foot = nextSwingFoot;
        if (foot == LEFT_FOOT && !leftNeedsStep
                || foot == RIGHT_FOOT && !rightNeedsStep) {
            foot = foot == LEFT_FOOT ? RIGHT_FOOT : LEFT_FOOT;
        } else if (leftNeedsStep && rightNeedsStep) {
            float preferred = foot == LEFT_FOOT ? leftStrain : rightStrain;
            float other = foot == LEFT_FOOT ? rightStrain : leftStrain;
            if (other > preferred * 1.35f) {
                foot = foot == LEFT_FOOT ? RIGHT_FOOT : LEFT_FOOT;
            }
        }
        beginStep(foot, bodyX, bodyY, hipFacing, velocityX, velocityY);
    }

    private void beginStep(int foot, float bodyX, float bodyY, float hipFacing,
                           float velocityX, float velocityY) {
        swingFoot = foot;
        swingProgress = 0f;
        swingStartX = foot == LEFT_FOOT ? leftFootX : rightFootX;
        swingStartY = foot == LEFT_FOOT ? leftFootY : rightFootY;
        swingStartFacing = foot == LEFT_FOOT ? leftFootFacing : rightFootFacing;

        Point nominal = nominalFoot(bodyX, bodyY, hipFacing, foot);
        float leadSeconds = stepDurationSeconds() * 0.82f;
        float targetX = nominal.x + velocityX * leadSeconds;
        float targetY = nominal.y + velocityY * leadSeconds;
        Point constrained = constrainLanding(bodyX, bodyY, hipFacing, foot,
                targetX, targetY);
        swingTargetX = constrained.x;
        swingTargetY = constrained.y;
        swingTargetFacing = desiredFootFacing(hipFacing, foot);
    }

    private void advanceSwing(float dt) {
        swingProgress = Math.min(1f,
                swingProgress + dt / stepDurationSeconds());
        float eased = smootherStep(swingProgress);
        float x = lerp(swingStartX, swingTargetX, eased);
        float y = lerp(swingStartY, swingTargetY, eased);
        float facing = LayeredAppearance.wrapDegrees(swingStartFacing
                + MechLocomotion.deltaDegrees(swingStartFacing, swingTargetFacing) * eased);
        if (swingFoot == LEFT_FOOT) {
            leftFootX = x;
            leftFootY = y;
            leftFootFacing = facing;
        } else {
            rightFootX = x;
            rightFootY = y;
            rightFootFacing = facing;
        }
        if (swingProgress >= 1f) {
            nextSwingFoot = swingFoot == LEFT_FOOT ? RIGHT_FOOT : LEFT_FOOT;
            swingFoot = NO_SWING_FOOT;
            swingProgress = 0f;
            doubleSupportTimer = DOUBLE_SUPPORT_SECONDS;
        }
    }

    private void advanceWaist(float bodyX, float bodyY, float hipFacing,
                              float velocityX, float velocityY, float speed, float dt) {
        Point rightAxis = rotate(1f, 0f, hipFacing);
        float supportX;
        float supportY;
        if (swingFoot == LEFT_FOOT) {
            supportX = rightFootX;
            supportY = rightFootY;
        } else if (swingFoot == RIGHT_FOOT) {
            supportX = leftFootX;
            supportY = leftFootY;
        } else {
            supportX = (leftFootX + rightFootX) * 0.5f;
            supportY = (leftFootY + rightFootY) * 0.5f;
        }
        float supportLateral = (supportX - bodyX) * rightAxis.x
                + (supportY - bodyY) * rightAxis.y;
        float maxSway = isLightChassis() ? 0.085f : 0.060f;
        float supportWeight = swingFoot == NO_SWING_FOOT ? 0.12f : 0.30f;
        float lateral = clamp(supportLateral * supportWeight, -maxSway, maxSway);
        float targetX = rightAxis.x * lateral;
        float targetY = rightAxis.y * lateral;
        if (speed > 0.001f) {
            float forwardShift = Math.min(isLightChassis() ? 0.055f : 0.038f,
                    speed * (isLightChassis() ? 0.030f : 0.022f));
            targetX += velocityX / speed * forwardShift;
            targetY += velocityY / speed * forwardShift;
        }

        float stiffness = isLightChassis() ? 52f : 36f;
        float damping = 2f * (float) Math.sqrt(stiffness);
        waistVelocityX += ((targetX - waistOffsetX) * stiffness
                - waistVelocityX * damping) * dt;
        waistVelocityY += ((targetY - waistOffsetY) * stiffness
                - waistVelocityY * damping) * dt;
        waistOffsetX += waistVelocityX * dt;
        waistOffsetY += waistVelocityY * dt;
    }

    private Point nominalFoot(float bodyX, float bodyY, float hipFacing, int foot) {
        float side = LayeredMechAppearance.footLateralOffset(chassis) * hullWidthCells;
        float rear = LayeredMechAppearance.footRearOffset(chassis) * hullWidthCells;
        Point offset = rotate(foot == LEFT_FOOT ? -side : side, rear, hipFacing);
        return new Point(bodyX + offset.x, bodyY + offset.y);
    }

    private Point constrainLanding(float bodyX, float bodyY, float hipFacing,
                                   int foot, float targetX, float targetY) {
        Point local = rotate(targetX - bodyX, targetY - bodyY, -hipFacing);
        float stance = LayeredMechAppearance.footLateralOffset(chassis) * hullWidthCells;
        float minimumSide = stance * 0.48f;
        float localX = foot == LEFT_FOOT
                ? Math.min(local.x, -minimumSide) : Math.max(local.x, minimumSide);
        float localY = local.y;
        float neutralReach = length(stance,
                LayeredMechAppearance.footRearOffset(chassis) * hullWidthCells);
        float maximumReach = neutralReach + hullWidthCells * 0.31f;
        float reach = length(localX, localY);
        if (reach > maximumReach) {
            localX *= maximumReach / reach;
            localY *= maximumReach / reach;
        }
        Point constrained = rotate(localX, localY, hipFacing);
        return new Point(bodyX + constrained.x, bodyY + constrained.y);
    }

    private float desiredFootFacing(float hipFacing, int foot) {
        float toeOut = isLightChassis() ? 4f : 2.5f;
        return LayeredAppearance.wrapDegrees(hipFacing
                + (foot == LEFT_FOOT ? toeOut : -toeOut));
    }

    private float stepTriggerCells() {
        return hullWidthCells * (isLightChassis() ? 0.115f : 0.12f);
    }

    private float stepDurationSeconds() {
        if (chassis == LayeredMechAppearance.CHASSIS_HOUND) return 0.24f;
        if (chassis == LayeredMechAppearance.CHASSIS_SIROCCO) return 0.28f;
        return 0.34f;
    }

    private boolean isLightChassis() {
        return chassis == LayeredMechAppearance.CHASSIS_HOUND
                || chassis == LayeredMechAppearance.CHASSIS_SIROCCO;
    }

    public float leftFootX() { return leftFootX; }
    public float leftFootY() { return leftFootY; }
    public float leftFootFacing() { return leftFootFacing; }
    public float rightFootX() { return rightFootX; }
    public float rightFootY() { return rightFootY; }
    public float rightFootFacing() { return rightFootFacing; }
    public float waistOffsetX() { return waistOffsetX; }
    public float waistOffsetY() { return waistOffsetY; }
    public int swingFoot() { return swingFoot; }
    public float swingProgress() { return swingProgress; }
    public float leftFootLift() { return swingFoot == LEFT_FOOT ? swingLift() : 0f; }
    public float rightFootLift() { return swingFoot == RIGHT_FOOT ? swingLift() : 0f; }

    private float swingLift() {
        return (float) Math.sin(swingProgress * Math.PI);
    }

    private static Point rotate(float x, float y, float degrees) {
        double radians = Math.toRadians(degrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        return new Point(x * cos - y * sin, x * sin + y * cos);
    }

    private static float smootherStep(float value) {
        float t = clamp(value, 0f, 1f);
        return t * t * t * (t * (t * 6f - 15f) + 10f);
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private static float distance(float ax, float ay, float bx, float by) {
        return length(bx - ax, by - ay);
    }

    private static float length(float x, float y) {
        return (float) Math.sqrt(x * x + y * y);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Point(float x, float y) { }
}
