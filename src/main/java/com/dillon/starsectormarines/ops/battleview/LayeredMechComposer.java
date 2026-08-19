package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;

/**
 * Emits a mech as hull-width-relative, independently animated hardpoints.
 * Feet use the hip/path bearing; the chassis and every mounted weapon share an
 * independently traversed upper-body bearing around the waist pivot.
 */
final class LayeredMechComposer {

    private LayeredMechComposer() {}

    static void emit(DrawList out, LayeredMechAssets assets,
                     float actorX, float actorY, float hullWidth,
                     float hipFacingDeg, float torsoFacingDeg, float locomotionPhase,
                     float chaingunPhase, float srmPhase, float lrmPhase,
                     int flags, int chassis, int arms,
                     int leftShoulder, int rightShoulder,
                     float alpha) {
        boolean moving = (flags & LayeredMechAppearance.FLAG_MOVING) != 0;
        boolean turning = (flags & LayeredMechAppearance.FLAG_TURNING) != 0;
        boolean stepping = moving || turning;
        float leftStep = stepping
                ? LayeredMechAppearance.mechanicalFootReveal(locomotionPhase, false) : 0f;
        float rightStep = stepping
                ? LayeredMechAppearance.mechanicalFootReveal(locomotionPhase, true) : 0f;

        // Feet and arm anchors are below the hull in draw order. The light
        // chassis plant their feet near the mid-body so alternating extension
        // remains legible; the Bulwark retains its mostly-hidden heavy stance.
        float footX = LayeredMechAppearance.footLateralOffset(chassis);
        float footY = LayeredMechAppearance.footRearOffset(chassis);
        emitCentered(out, assets.foot, actorX, actorY, hullWidth, hipFacingDeg,
                -footX, footY - 0.055f * leftStep, 0f, alpha);
        emitCentered(out, assets.foot, actorX, actorY, hullWidth, hipFacingDeg,
                footX, footY - 0.055f * rightStep, 0f, alpha);

        float cgKick = 0.025f * LayeredMechAppearance.recoil(chaingunPhase);
        emitArms(out, assets, chassis, arms, actorX, actorY, hullWidth,
                torsoFacingDeg, cgKick, alpha);

        float srmKick = ((flags & LayeredMechAppearance.FLAG_SRM_ACTIVE) != 0)
                ? 0.018f * LayeredMechAppearance.recoil(srmPhase) : 0f;
        float lrmKick = ((flags & LayeredMechAppearance.FLAG_LRM_ACTIVE) != 0)
                ? 0.024f * LayeredMechAppearance.recoil(lrmPhase) : 0f;
        boolean podsAboveChassis = chassis == LayeredMechAppearance.CHASSIS_CLEAN
                || chassis == LayeredMechAppearance.CHASSIS_HOUND;
        if (!podsAboveChassis) {
            emitShoulderPods(out, assets, chassis, leftShoulder, rightShoulder,
                    actorX, actorY, hullWidth, torsoFacingDeg, srmKick, lrmKick, alpha);
        }

        LayeredSpriteCache chassisSprite = selectChassis(assets, chassis);
        emitCentered(out, chassisSprite, actorX, actorY, hullWidth, torsoFacingDeg,
                0f, 0f, 0f, alpha);

        // Bulwark's racks are exposed above its armor; Hound carries one dorsal
        // SRM rack. Sirocco's paired LRMs stay beneath its broader hull.
        if (podsAboveChassis) {
            emitShoulderPods(out, assets, chassis, leftShoulder, rightShoulder,
                    actorX, actorY, hullWidth, torsoFacingDeg, srmKick, lrmKick, alpha);
        }

        if ((flags & LayeredMechAppearance.FLAG_CHAINGUN_FLASH) != 0) {
            emitArmsFlash(out, assets, arms, actorX, actorY, hullWidth,
                    torsoFacingDeg, cgKick, alpha);
        }
        if ((flags & LayeredMechAppearance.FLAG_SRM_FLASH) != 0) {
            emitShoulderFlashes(out, assets, chassis, leftShoulder, rightShoulder, true,
                    actorX, actorY, hullWidth, torsoFacingDeg, alpha);
        }
        if ((flags & LayeredMechAppearance.FLAG_LRM_FLASH) != 0) {
            emitShoulderFlashes(out, assets, chassis, leftShoulder, rightShoulder, false,
                    actorX, actorY, hullWidth, torsoFacingDeg, alpha);
        }
    }

    private static void emitArms(DrawList out, LayeredMechAssets assets,
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

    private static void emitShoulderPods(DrawList out, LayeredMechAssets assets,
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

    private static void emitArmsFlash(DrawList out, LayeredMechAssets assets, int arms,
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

    private static void emitShoulderFlashes(DrawList out, LayeredMechAssets assets,
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

    private static void emitPodFlash(DrawList out, LayeredMechAssets assets,
                                     int installedPod, boolean srm,
                                     float actorX, float actorY, float hullWidth,
                                     float facingDeg, float localX, float alpha) {
        if (srm ? isSrmPod(installedPod) : isLrmPod(installedPod)) {
            float localY = isSmallPod(installedPod) ? 0.12f : 0.16f;
            emitCentered(out, assets.muzzleFlash, actorX, actorY, hullWidth, facingDeg,
                    localX, localY, 0f, alpha);
        }
    }

    private static void emitPod(DrawList out, LayeredMechAssets assets, int pod,
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

    private static void emitCentered(DrawList out, LayeredSpriteCache sprite,
                               float actorX, float actorY, float hullWidth,
                               float facingDeg, float localX, float localY,
                               float relativeAngle, float alpha) {
        float[] offset = rotate(localX * hullWidth, localY * hullWidth, facingDeg);
        float scale = hullWidth / 208f;
        out.addSprite(RenderLayer.UNITS, sprite.sprite,
                actorX + offset[0], actorY + offset[1],
                sprite.pxWidth * scale, sprite.pxHeight * scale,
                facingDeg + relativeAngle, 1f, 1f, 1f, alpha);
    }

    /** Places the sprite's south/rear edge at a pivot hidden under the hull. */
    private static void emitFromRearPivot(DrawList out, LayeredSpriteCache sprite,
                                          float actorX, float actorY, float hullWidth,
                                          float facingDeg, float localX, float localY,
                                          float widthScale, float relativeAngle, float alpha) {
        float scale = hullWidth / 208f;
        float centerForward = sprite.pxHeight * scale * 0.5f;
        float[] pivot = rotate(localX * hullWidth, localY * hullWidth, facingDeg);
        float[] fromPivot = rotate(0f, centerForward, facingDeg + relativeAngle);
        out.addSprite(RenderLayer.UNITS, sprite.sprite,
                actorX + pivot[0] + fromPivot[0], actorY + pivot[1] + fromPivot[1],
                sprite.pxWidth * scale * widthScale, sprite.pxHeight * scale,
                facingDeg + relativeAngle, 1f, 1f, 1f, alpha);
    }

    private static float[] rotate(float x, float y, float degrees) {
        double radians = Math.toRadians(degrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        return new float[]{x * cos - y * sin, x * sin + y * cos};
    }
}
