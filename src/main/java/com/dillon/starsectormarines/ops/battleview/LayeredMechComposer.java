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

        // Feet and arm anchors are below the hull in draw order. Their pivots sit
        // deep inside the chassis, so only toes/barrels can cross its silhouette.
        emitCentered(out, assets.foot, actorX, actorY, hullWidth, hipFacingDeg,
                -0.17f, -0.28f - 0.055f * leftStep, 0f, alpha);
        emitCentered(out, assets.foot, actorX, actorY, hullWidth, hipFacingDeg,
                0.17f, -0.28f - 0.055f * rightStep, 0f, alpha);

        float cgKick = 0.025f * LayeredMechAppearance.recoil(chaingunPhase);
        if (arms == LayeredMechAppearance.ARMS_CHAINGUN) {
            emitFromRearPivot(out, assets.chaingunArm, actorX, actorY, hullWidth, torsoFacingDeg,
                    -0.37f, -0.15f + cgKick, 0f, alpha);
            emitFromRearPivot(out, assets.chaingunArm, actorX, actorY, hullWidth, torsoFacingDeg,
                    0.37f, -0.15f + cgKick, 0f, alpha);
        } else if (arms == LayeredMechAppearance.ARMS_LINEAR_CANNON) {
            emitFromRearPivot(out, assets.linearCannon, actorX, actorY, hullWidth, torsoFacingDeg,
                    -0.37f, -0.15f, 0f, alpha);
            emitFromRearPivot(out, assets.linearCannon, actorX, actorY, hullWidth, torsoFacingDeg,
                    0.37f, -0.15f, 0f, alpha);
        }

        LayeredSpriteCache chassisSprite = chassis == LayeredMechAppearance.CHASSIS_SOCKETED
                ? assets.socketedChassis : assets.chassis;
        emitCentered(out, chassisSprite, actorX, actorY, hullWidth, torsoFacingDeg,
                0f, 0f, 0f, alpha);

        // Shoulder pods sit above the hull, but most rear casing stays occluded
        // because their centers remain well inside the chassis silhouette.
        float srmKick = ((flags & LayeredMechAppearance.FLAG_SRM_ACTIVE) != 0)
                ? 0.018f * LayeredMechAppearance.recoil(srmPhase) : 0f;
        float lrmKick = ((flags & LayeredMechAppearance.FLAG_LRM_ACTIVE) != 0)
                ? 0.024f * LayeredMechAppearance.recoil(lrmPhase) : 0f;
        emitPod(out, assets, leftShoulder, actorX, actorY, hullWidth, torsoFacingDeg,
                -0.23f, srmKick, lrmKick, alpha);
        emitPod(out, assets, rightShoulder, actorX, actorY, hullWidth, torsoFacingDeg,
                0.23f, srmKick, lrmKick, alpha);

        if ((flags & LayeredMechAppearance.FLAG_CHAINGUN_FLASH) != 0) {
            emitCentered(out, assets.muzzleFlash, actorX, actorY, hullWidth, torsoFacingDeg,
                    -0.37f, 0.39f - cgKick, 0f, alpha);
            emitCentered(out, assets.muzzleFlash, actorX, actorY, hullWidth, torsoFacingDeg,
                    0.37f, 0.39f - cgKick, 0f, alpha);
        }
        if ((flags & LayeredMechAppearance.FLAG_SRM_FLASH) != 0) {
            emitPodFlash(out, assets, leftShoulder, LayeredMechAppearance.POD_SRM,
                    actorX, actorY, hullWidth, torsoFacingDeg, -0.23f, 0.11f, alpha);
            emitPodFlash(out, assets, rightShoulder, LayeredMechAppearance.POD_SRM,
                    actorX, actorY, hullWidth, torsoFacingDeg, 0.23f, 0.11f, alpha);
            emitPodFlash(out, assets, leftShoulder, LayeredMechAppearance.POD_HEAVY_SRM,
                    actorX, actorY, hullWidth, torsoFacingDeg, -0.23f, 0.12f, alpha);
            emitPodFlash(out, assets, rightShoulder, LayeredMechAppearance.POD_HEAVY_SRM,
                    actorX, actorY, hullWidth, torsoFacingDeg, 0.23f, 0.12f, alpha);
        }
        if ((flags & LayeredMechAppearance.FLAG_LRM_FLASH) != 0) {
            emitPodFlash(out, assets, leftShoulder, LayeredMechAppearance.POD_LRM,
                    actorX, actorY, hullWidth, torsoFacingDeg, -0.23f, 0.12f, alpha);
            emitPodFlash(out, assets, rightShoulder, LayeredMechAppearance.POD_LRM,
                    actorX, actorY, hullWidth, torsoFacingDeg, 0.23f, 0.12f, alpha);
        }
    }

    private static void emitPodFlash(DrawList out, LayeredMechAssets assets,
                                     int installedPod, int firingPod,
                                     float actorX, float actorY, float hullWidth,
                                     float facingDeg, float localX, float localY,
                                     float alpha) {
        if (installedPod == firingPod) {
            emitCentered(out, assets.muzzleFlash, actorX, actorY, hullWidth, facingDeg,
                    localX, localY, 0f, alpha);
        }
    }

    private static void emitPod(DrawList out, LayeredMechAssets assets, int pod,
                                float actorX, float actorY, float hullWidth,
                                float facingDeg, float localX,
                                float srmKick, float lrmKick, float alpha) {
        if (pod == LayeredMechAppearance.POD_SRM) {
            emitFromRearPivot(out, assets.srmPod, actorX, actorY, hullWidth, facingDeg,
                    localX, -0.38f - srmKick, 0f, alpha);
        } else if (pod == LayeredMechAppearance.POD_HEAVY_SRM) {
            emitFromRearPivot(out, assets.lrmPod, actorX, actorY, hullWidth, facingDeg,
                    localX, -0.38f - srmKick, 0f, alpha);
        } else if (pod == LayeredMechAppearance.POD_LRM) {
            emitFromRearPivot(out, assets.lrmPod, actorX, actorY, hullWidth, facingDeg,
                    localX, -0.38f - lrmKick, 0f, alpha);
        }
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
                                          float relativeAngle, float alpha) {
        float scale = hullWidth / 208f;
        float centerForward = sprite.pxHeight * scale * 0.5f;
        float[] pivot = rotate(localX * hullWidth, localY * hullWidth, facingDeg);
        float[] fromPivot = rotate(0f, centerForward, facingDeg + relativeAngle);
        out.addSprite(RenderLayer.UNITS, sprite.sprite,
                actorX + pivot[0] + fromPivot[0], actorY + pivot[1] + fromPivot[1],
                sprite.pxWidth * scale, sprite.pxHeight * scale,
                facingDeg + relativeAngle, 1f, 1f, 1f, alpha);
    }

    private static float[] rotate(float x, float y, float degrees) {
        double radians = Math.toRadians(degrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        return new float[]{x * cos - y * sin, x * sin + y * cos};
    }
}
