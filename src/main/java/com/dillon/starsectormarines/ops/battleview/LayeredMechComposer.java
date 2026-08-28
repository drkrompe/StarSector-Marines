package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.LayerPose;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.LayerTransform;

/**
 * Emits a mech as hull-width-relative, independently animated hardpoints.
 * Feet use the hip/path bearing; the chassis and every mounted weapon share an
 * independently traversed upper-body bearing around the waist pivot.
 */
final class LayeredMechComposer {

    private static final float SOURCE_REFERENCE_PX = 208f;

    private LayeredMechComposer() {}

    /** Backend-neutral ordered sprite sink used by battle and retained previews. */
    interface Sink {
        void sprite(LayeredSpriteCache sprite, float centerX, float centerY,
                    float width, float height, float angleDegrees, float alpha);
    }

    /** Screen-space projection of the fixed-tick procedural gait state. */
    record GaitPose(float leftFootX, float leftFootY, float leftFootFacing,
                    float rightFootX, float rightFootY, float rightFootFacing,
                    float waistX, float waistY,
                    float leftFootLift, float rightFootLift) { }

    static void emit(DrawList out, LayeredMechAssets assets,
                     float actorX, float actorY, float hullWidth,
                     float hipFacingDeg, float torsoFacingDeg, float locomotionPhase,
                     float chaingunPhase, float srmPhase, float lrmPhase,
                     int flags, int chassis, int arms,
                     int leftShoulder, int rightShoulder,
                     float alpha) {
        emit(drawListSink(out), assets, actorX, actorY, hullWidth, hipFacingDeg, torsoFacingDeg,
                locomotionPhase, chaingunPhase, srmPhase, lrmPhase, flags, chassis,
                arms, leftShoulder, rightShoulder, alpha, null, null);
    }

    static void emit(DrawList out, LayeredMechAssets assets,
                     float actorX, float actorY, float hullWidth,
                     float hipFacingDeg, float torsoFacingDeg, float locomotionPhase,
                     float chaingunPhase, float srmPhase, float lrmPhase,
                     int flags, int chassis, int arms,
                     int leftShoulder, int rightShoulder,
                     float alpha, LayerPose authoredPose) {
        emit(drawListSink(out), assets, actorX, actorY, hullWidth,
                hipFacingDeg, torsoFacingDeg, locomotionPhase,
                chaingunPhase, srmPhase, lrmPhase, flags, chassis, arms,
                leftShoulder, rightShoulder, alpha, authoredPose, null);
    }

    static void emit(DrawList out, LayeredMechAssets assets,
                     float actorX, float actorY, float hullWidth,
                     float hipFacingDeg, float torsoFacingDeg, float locomotionPhase,
                     float chaingunPhase, float srmPhase, float lrmPhase,
                     int flags, int chassis, int arms,
                     int leftShoulder, int rightShoulder,
                     float alpha, LayerPose authoredPose, GaitPose gaitPose) {
        emit(drawListSink(out), assets, actorX, actorY, hullWidth,
                hipFacingDeg, torsoFacingDeg, locomotionPhase,
                chaingunPhase, srmPhase, lrmPhase, flags, chassis, arms,
                leftShoulder, rightShoulder, alpha, authoredPose, gaitPose);
    }

    static void emit(Sink out, LayeredMechAssets assets,
                     float actorX, float actorY, float hullWidth,
                     float hipFacingDeg, float torsoFacingDeg, float locomotionPhase,
                     float chaingunPhase, float srmPhase, float lrmPhase,
                     int flags, int chassis, int arms,
                     int leftShoulder, int rightShoulder,
                     float alpha) {
        emit(out, assets, actorX, actorY, hullWidth, hipFacingDeg, torsoFacingDeg,
                locomotionPhase, chaingunPhase, srmPhase, lrmPhase, flags, chassis,
                arms, leftShoulder, rightShoulder, alpha, null, null);
    }

    static void emit(Sink out, LayeredMechAssets assets,
                     float actorX, float actorY, float hullWidth,
                     float hipFacingDeg, float torsoFacingDeg, float locomotionPhase,
                     float chaingunPhase, float srmPhase, float lrmPhase,
                     int flags, int chassis, int arms,
                     int leftShoulder, int rightShoulder,
                     float alpha, LayerPose authoredPose, GaitPose gaitPose) {
        boolean moving = (flags & LayeredMechAppearance.FLAG_MOVING) != 0;
        boolean turning = (flags & LayeredMechAppearance.FLAG_TURNING) != 0;
        boolean stepping = moving || turning;
        float leftStep = stepping
                ? LayeredMechAppearance.mechanicalFootReveal(locomotionPhase, false) : 0f;
        float rightStep = stepping
                ? LayeredMechAppearance.mechanicalFootReveal(locomotionPhase, true) : 0f;
        float waistSway = moving && gaitPose == null
                ? LayeredMechAppearance.walkingWaistSway(locomotionPhase, chassis) : 0f;
        float[] waistOffset = rotate(waistSway * hullWidth, 0f, hipFacingDeg);
        float upperX = gaitPose != null ? gaitPose.waistX() : actorX + waistOffset[0];
        float upperY = gaitPose != null ? gaitPose.waistY() : actorY + waistOffset[1];

        // The light chassis expose articulated bones between their waist and
        // feet. Each bone rotates and stretches to the live foot anchor;
        // Bulwark keeps the older tucked treatment without visible leg bones.
        float footX = LayeredMechAppearance.footLateralOffset(chassis);
        float footY = LayeredMechAppearance.footRearOffset(chassis);
        float footStepReach = LayeredMechAppearance.footStepReach(chassis);
        float leftFootY = footY - footStepReach * leftStep;
        float rightFootY = footY - footStepReach * rightStep;
        LayerTransform leftFoot = layer(authoredPose, "left-foot");
        LayerTransform rightFoot = layer(authoredPose, "right-foot");
        if (gaitPose != null) {
            emitFoot(out, assets.foot, gaitPose.leftFootX(), gaitPose.leftFootY(),
                    hullWidth, gaitPose.leftFootFacing(), gaitPose.leftFootLift(), alpha);
            emitFoot(out, assets.foot, gaitPose.rightFootX(), gaitPose.rightFootY(),
                    hullWidth, gaitPose.rightFootFacing(), gaitPose.rightFootLift(), alpha);
        } else if (leftFoot != null) {
            emitAuthored(out, assets.foot, leftFoot, actorX, actorY, hullWidth,
                    hipFacingDeg, alpha);
            leftFootY = leftFoot.offsetY();
        } else {
            emitCentered(out, assets.foot, actorX, actorY, hullWidth, hipFacingDeg,
                    -footX, leftFootY, 0f, alpha);
        }
        if (gaitPose != null) {
            // Both solved feet were emitted together above to preserve their
            // canonical below-body layer order.
        } else if (rightFoot != null) {
            emitAuthored(out, assets.foot, rightFoot, actorX, actorY, hullWidth,
                    hipFacingDeg, alpha);
            rightFootY = rightFoot.offsetY();
        } else {
            emitCentered(out, assets.foot, actorX, actorY, hullWidth, hipFacingDeg,
                    footX, rightFootY, 0f, alpha);
        }

        LayerTransform leftThigh = layer(authoredPose, "left-thigh");
        LayerTransform rightThigh = layer(authoredPose, "right-thigh");
        // Legs always sit below the upper assembly: pads, then linkages, then
        // the ordinary weapon/chassis/pod stack emitted below.
        if (gaitPose != null
                && (chassis == LayeredMechAppearance.CHASSIS_HOUND
                || chassis == LayeredMechAppearance.CHASSIS_SIROCCO)) {
            emitConnectionTo(out, assets.thighBone, upperX, upperY,
                    gaitPose.leftFootX(), gaitPose.leftFootY(), hullWidth, alpha);
            emitConnectionTo(out, assets.thighBone, upperX, upperY,
                    gaitPose.rightFootX(), gaitPose.rightFootY(), hullWidth, alpha);
        } else if (leftThigh != null && rightThigh != null) {
            emitAuthored(out, assets.thighBone, leftThigh, upperX, upperY, hullWidth,
                    hipFacingDeg, alpha);
            emitAuthored(out, assets.thighBone, rightThigh, upperX, upperY, hullWidth,
                    hipFacingDeg, alpha);
        } else if (leftThigh == null && rightThigh == null
                && (chassis == LayeredMechAppearance.CHASSIS_HOUND
                || chassis == LayeredMechAppearance.CHASSIS_SIROCCO)) {
            emitConnection(out, assets.thighBone, upperX, upperY, hullWidth, hipFacingDeg,
                    -footX - waistSway, leftFootY, alpha);
            emitConnection(out, assets.thighBone, upperX, upperY, hullWidth, hipFacingDeg,
                    footX - waistSway, rightFootY, alpha);
        }

        LayerTransform chassisTransform = layer(authoredPose, "chassis");
        float upperFacingDeg = torsoFacingDeg
                + (gaitPose == null && chassisTransform != null
                ? chassisTransform.angleDegrees() : 0f);

        float cgKick = 0.025f * LayeredMechAppearance.recoil(chaingunPhase);
        emitArms(out, assets, chassis, arms, upperX, upperY, hullWidth,
                upperFacingDeg, cgKick, alpha);

        float srmKick = ((flags & LayeredMechAppearance.FLAG_SRM_ACTIVE) != 0)
                ? 0.018f * LayeredMechAppearance.recoil(srmPhase) : 0f;
        float lrmKick = ((flags & LayeredMechAppearance.FLAG_LRM_ACTIVE) != 0)
                ? 0.024f * LayeredMechAppearance.recoil(lrmPhase) : 0f;
        boolean podsAboveChassis = chassis == LayeredMechAppearance.CHASSIS_CLEAN
                || chassis == LayeredMechAppearance.CHASSIS_HOUND;
        if (!podsAboveChassis) {
            emitShoulderPods(out, assets, chassis, leftShoulder, rightShoulder,
                    upperX, upperY, hullWidth, upperFacingDeg, srmKick, lrmKick, alpha);
        }

        LayeredSpriteCache chassisSprite = selectChassis(assets, chassis);
        if (gaitPose != null) {
            emitCentered(out, chassisSprite, upperX, upperY, hullWidth, torsoFacingDeg,
                    0f, 0f, 0f, alpha);
        } else if (chassisTransform != null) {
            emitAuthored(out, chassisSprite, chassisTransform, upperX, upperY, hullWidth,
                    torsoFacingDeg, alpha);
        } else {
            emitCentered(out, chassisSprite, upperX, upperY, hullWidth, torsoFacingDeg,
                    0f, 0f, 0f, alpha);
        }

        // Bulwark's racks are exposed above its armor; Hound carries one dorsal
        // SRM rack. Sirocco's paired LRMs stay beneath its broader hull.
        if (podsAboveChassis) {
            emitShoulderPods(out, assets, chassis, leftShoulder, rightShoulder,
                    upperX, upperY, hullWidth, upperFacingDeg, srmKick, lrmKick, alpha);
        }

        if ((flags & LayeredMechAppearance.FLAG_CHAINGUN_FLASH) != 0) {
            emitArmsFlash(out, assets, arms, upperX, upperY, hullWidth,
                    upperFacingDeg, cgKick, alpha);
        }
        if ((flags & LayeredMechAppearance.FLAG_SRM_FLASH) != 0) {
            emitShoulderFlashes(out, assets, chassis, leftShoulder, rightShoulder, true,
                    upperX, upperY, hullWidth, upperFacingDeg, alpha);
        }
        if ((flags & LayeredMechAppearance.FLAG_LRM_FLASH) != 0) {
            emitShoulderFlashes(out, assets, chassis, leftShoulder, rightShoulder, false,
                    upperX, upperY, hullWidth, upperFacingDeg, alpha);
        }
    }

    private static LayerTransform layer(LayerPose pose, String id) {
        return pose != null ? pose.layer(id) : null;
    }

    private static void emitAuthored(Sink out, LayeredSpriteCache sprite,
                                     LayerTransform transform,
                                     float actorX, float actorY, float hullWidth,
                                     float facingDeg, float alpha) {
        if (sprite == null || transform == null || !transform.visible()) return;
        float[] pivot = rotate(transform.offsetX() * hullWidth,
                transform.offsetY() * hullWidth, facingDeg);
        float angle = facingDeg + transform.angleDegrees();
        float localCenterX = (0.5f - transform.pivotX())
                * sprite.pxWidth / SOURCE_REFERENCE_PX * transform.scaleX() * hullWidth;
        float localCenterY = (transform.pivotY() - 0.5f)
                * sprite.pxHeight / SOURCE_REFERENCE_PX * transform.scaleY() * hullWidth;
        float[] center = rotate(localCenterX, localCenterY, angle);
        out.sprite(sprite,
                actorX + pivot[0] + center[0], actorY + pivot[1] + center[1],
                sprite.pxWidth / SOURCE_REFERENCE_PX * transform.scaleX() * hullWidth,
                sprite.pxHeight / SOURCE_REFERENCE_PX * transform.scaleY() * hullWidth,
                angle, alpha);
    }

    private static void emitArms(Sink out, LayeredMechAssets assets,
                                 int chassis, int arms,
                                 float actorX, float actorY, float hullWidth,
                                 float facingDeg, float kick, float alpha) {
        if (arms == LayeredMechAppearance.ARMS_CHAINGUN) {
            float widthScale = chassis == LayeredMechAppearance.CHASSIS_CLEAN ? 0.5f : 1f;
            emitFromRearPivot(out, assets.chaingunArm, actorX, actorY, hullWidth, facingDeg,
                    -0.37f, -0.15f + kick, widthScale, 0f, alpha);
            emitFromRearPivot(out, assets.chaingunArm, actorX, actorY, hullWidth, facingDeg,
                    0.37f, -0.15f + kick, widthScale, 0f, alpha);
        } else if (arms == LayeredMechAppearance.ARMS_NOSE_CHAINGUN) {
            emitFromRearPivot(out, assets.chaingunArm, actorX, actorY, hullWidth, facingDeg,
                    0f, -0.02f + kick, 1f, 0f, alpha);
        } else if (arms == LayeredMechAppearance.ARMS_LINEAR_CANNON) {
            emitFromRearPivot(out, assets.linearCannon, actorX, actorY, hullWidth, facingDeg,
                    -0.37f, -0.15f, 1f, 0f, alpha);
            emitFromRearPivot(out, assets.linearCannon, actorX, actorY, hullWidth, facingDeg,
                    0.37f, -0.15f, 1f, 0f, alpha);
        } else if (arms == LayeredMechAppearance.ARMS_HEAVY_CANNON) {
            emitFromRearPivot(out, assets.heavyCannon, actorX, actorY, hullWidth, facingDeg,
                    0f, -0.05f, 1f, 0f, alpha);
        }
    }

    private static void emitShoulderPods(Sink out, LayeredMechAssets assets,
                                         int chassis, int leftShoulder, int rightShoulder,
                                         float actorX, float actorY, float hullWidth,
                                         float facingDeg, float srmKick, float lrmKick,
                                         float alpha) {
        emitPod(out, assets, leftShoulder, actorX, actorY, hullWidth, facingDeg,
                podLocalX(chassis, leftShoulder, rightShoulder, true),
                srmKick, lrmKick, alpha);
        emitPod(out, assets, rightShoulder, actorX, actorY, hullWidth, facingDeg,
                podLocalX(chassis, leftShoulder, rightShoulder, false),
                srmKick, lrmKick, alpha);
    }

    private static void emitArmsFlash(Sink out, LayeredMechAssets assets, int arms,
                                      float actorX, float actorY, float hullWidth,
                                      float facingDeg, float kick, float alpha) {
        if (arms == LayeredMechAppearance.ARMS_NOSE_CHAINGUN) {
            emitCentered(out, assets.muzzleFlash, actorX, actorY, hullWidth, facingDeg,
                    0f, 0.52f - kick, 0f, alpha);
        } else if (arms == LayeredMechAppearance.ARMS_HEAVY_CANNON) {
            emitCentered(out, assets.muzzleFlash, actorX, actorY, hullWidth, facingDeg,
                    0f, 0.57f, 0f, alpha);
        } else {
            float localY = arms == LayeredMechAppearance.ARMS_LINEAR_CANNON ? 0.51f : 0.39f;
            emitCentered(out, assets.muzzleFlash, actorX, actorY, hullWidth, facingDeg,
                    -0.37f, localY - kick, 0f, alpha);
            emitCentered(out, assets.muzzleFlash, actorX, actorY, hullWidth, facingDeg,
                    0.37f, localY - kick, 0f, alpha);
        }
    }

    private static void emitShoulderFlashes(Sink out, LayeredMechAssets assets,
                                            int chassis, int leftShoulder, int rightShoulder,
                                            boolean srm,
                                            float actorX, float actorY, float hullWidth,
                                            float facingDeg, float alpha) {
        emitPodFlash(out, assets, leftShoulder, srm, actorX, actorY, hullWidth, facingDeg,
                podLocalX(chassis, leftShoulder, rightShoulder, true), alpha);
        emitPodFlash(out, assets, rightShoulder, srm, actorX, actorY, hullWidth, facingDeg,
                podLocalX(chassis, leftShoulder, rightShoulder, false), alpha);
    }

    private static float podLocalX(int chassis, int leftShoulder, int rightShoulder,
                                   boolean leftSlot) {
        if (chassis == LayeredMechAppearance.CHASSIS_HOUND) {
            boolean leftInstalled = leftShoulder != LayeredMechAppearance.POD_NONE;
            boolean rightInstalled = rightShoulder != LayeredMechAppearance.POD_NONE;
            if (leftInstalled != rightInstalled) return 0f;
            return leftSlot ? -0.24f : 0.24f;
        }
        return leftSlot ? -0.40f : 0.40f;
    }

    private static LayeredSpriteCache selectChassis(LayeredMechAssets assets, int chassis) {
        if (chassis == LayeredMechAppearance.CHASSIS_SOCKETED) return assets.socketedChassis;
        if (chassis == LayeredMechAppearance.CHASSIS_HOUND) return assets.houndChassis;
        if (chassis == LayeredMechAppearance.CHASSIS_SIROCCO) return assets.siroccoChassis;
        return assets.chassis;
    }

    private static void emitPodFlash(Sink out, LayeredMechAssets assets,
                                     int installedPod, boolean srm,
                                     float actorX, float actorY, float hullWidth,
                                     float facingDeg, float localX, float alpha) {
        if (srm ? isSrmPod(installedPod) : isLrmPod(installedPod)) {
            float localY = isSmallPod(installedPod) ? 0.12f : 0.16f;
            emitCentered(out, assets.muzzleFlash, actorX, actorY, hullWidth, facingDeg,
                    localX, localY, 0f, alpha);
        }
    }

    private static void emitPod(Sink out, LayeredMechAssets assets, int pod,
                                float actorX, float actorY, float hullWidth,
                                float facingDeg, float localX,
                                float srmKick, float lrmKick, float alpha) {
        if (isSmallPod(pod)) {
            emitFromRearPivot(out, assets.srmPod, actorX, actorY, hullWidth, facingDeg,
                    localX, -0.30f - (isSrmPod(pod) ? srmKick : lrmKick), 1f, 0f, alpha);
        } else if (isLargePod(pod)) {
            emitFromRearPivot(out, assets.lrmPod, actorX, actorY, hullWidth, facingDeg,
                    localX, -0.30f - (isSrmPod(pod) ? srmKick : lrmKick), 1f, 0f, alpha);
        }
    }

    private static boolean isSmallPod(int pod) {
        return pod == LayeredMechAppearance.POD_SMALL_SRM
                || pod == LayeredMechAppearance.POD_SMALL_LRM;
    }

    private static boolean isLargePod(int pod) {
        return pod == LayeredMechAppearance.POD_LARGE_SRM
                || pod == LayeredMechAppearance.POD_LARGE_LRM;
    }

    private static boolean isSrmPod(int pod) {
        return pod == LayeredMechAppearance.POD_SMALL_SRM
                || pod == LayeredMechAppearance.POD_LARGE_SRM;
    }

    private static boolean isLrmPod(int pod) {
        return pod == LayeredMechAppearance.POD_SMALL_LRM
                || pod == LayeredMechAppearance.POD_LARGE_LRM;
    }

    private static void emitCentered(Sink out, LayeredSpriteCache sprite,
                               float actorX, float actorY, float hullWidth,
                               float facingDeg, float localX, float localY,
                               float relativeAngle, float alpha) {
        float[] offset = rotate(localX * hullWidth, localY * hullWidth, facingDeg);
        float scale = hullWidth / 208f;
        out.sprite(sprite,
                actorX + offset[0], actorY + offset[1],
                sprite.pxWidth * scale, sprite.pxHeight * scale,
                facingDeg + relativeAngle, alpha);
    }

    private static void emitFoot(Sink out, LayeredSpriteCache sprite,
                                 float centerX, float centerY, float hullWidth,
                                 float facingDeg, float lift, float alpha) {
        float scale = hullWidth / SOURCE_REFERENCE_PX;
        float liftScale = 1f + Math.max(0f, lift) * 0.045f;
        out.sprite(sprite, centerX, centerY,
                sprite.pxWidth * scale * liftScale,
                sprite.pxHeight * scale * liftScale,
                facingDeg, alpha);
    }

    /** Rotates and length-scales a north-authored sprite between two screen points. */
    private static void emitConnectionTo(Sink out, LayeredSpriteCache sprite,
                                         float waistX, float waistY,
                                         float footX, float footY,
                                         float hullWidth, float alpha) {
        float dx = footX - waistX;
        float dy = footY - waistY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance <= 0.001f) return;
        float visibleLength = Math.max(0.01f, distance - 0.08f * hullWidth);
        float endpointRatio = visibleLength / distance;
        float angle = (float) Math.toDegrees(Math.atan2(-dx, dy));
        float scale = hullWidth / SOURCE_REFERENCE_PX;
        out.sprite(sprite,
                waistX + dx * endpointRatio * 0.5f,
                waistY + dy * endpointRatio * 0.5f,
                sprite.pxWidth * scale, visibleLength,
                angle, alpha);
    }

    /** Rotates and length-scales a north-authored sprite from the waist to an endpoint. */
    private static void emitConnection(Sink out, LayeredSpriteCache sprite,
                                       float actorX, float actorY, float hullWidth,
                                       float facingDeg, float localX, float localY,
                                       float alpha) {
        float distance = (float) Math.sqrt(localX * localX + localY * localY);
        float visibleLength = Math.max(0.01f, distance - 0.08f);
        float endpointRatio = visibleLength / distance;
        float endX = localX * endpointRatio;
        float endY = localY * endpointRatio;
        float length = visibleLength * hullWidth;
        float localAngle = (float) Math.toDegrees(Math.atan2(-localX, localY));
        float[] midpoint = rotate(endX * hullWidth * 0.5f,
                endY * hullWidth * 0.5f, facingDeg);
        float scale = hullWidth / 208f;
        out.sprite(sprite,
                actorX + midpoint[0], actorY + midpoint[1],
                sprite.pxWidth * scale, length,
                facingDeg + localAngle, alpha);
    }

    /** Places the sprite's south/rear edge at a pivot hidden under the hull. */
    private static void emitFromRearPivot(Sink out, LayeredSpriteCache sprite,
                                          float actorX, float actorY, float hullWidth,
                                          float facingDeg, float localX, float localY,
                                          float widthScale, float relativeAngle, float alpha) {
        float scale = hullWidth / 208f;
        float centerForward = sprite.pxHeight * scale * 0.5f;
        float[] pivot = rotate(localX * hullWidth, localY * hullWidth, facingDeg);
        float[] fromPivot = rotate(0f, centerForward, facingDeg + relativeAngle);
        out.sprite(sprite,
                actorX + pivot[0] + fromPivot[0], actorY + pivot[1] + fromPivot[1],
                sprite.pxWidth * scale * widthScale, sprite.pxHeight * scale,
                facingDeg + relativeAngle, alpha);
    }

    private static Sink drawListSink(DrawList out) {
        return (sprite, centerX, centerY, width, height, angleDegrees, alpha) ->
                out.addSprite(RenderLayer.UNITS, sprite.sprite,
                        centerX, centerY, width, height, angleDegrees,
                        1f, 1f, 1f, alpha);
    }

    private static float[] rotate(float x, float y, float degrees) {
        double radians = Math.toRadians(degrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        return new float[]{x * cos - y * sin, x * sin + y * cos};
    }
}
