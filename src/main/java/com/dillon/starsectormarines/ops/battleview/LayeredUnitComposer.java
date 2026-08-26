package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.LayeredWeaponFamily;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.LayerPose;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.LayerTransform;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.EquipmentLayerDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialUsePose;

/** Emits one modular infantry actor from shoulder-relative authored transforms. */
final class LayeredUnitComposer {

    private static final float SOURCE_SHOULDER_PX = 150f;

    // Sprite pivots authored against the retained source PNGs.
    private static final float FOOT_PIVOT_X = 19f;
    private static final float FOOT_PIVOT_Y = 26f;
    private static final float STANDARD_WEAPON_PIVOT_Y = 73f;
    private static final float ROCKET_PIVOT_Y = 84f;
    private static final float FLASH_PIVOT_X = 16f;
    private static final float FLASH_PIVOT_Y = 28f;
    private static final float CLAW_PIVOT_Y_FRACTION = 0.92f;

    private LayeredUnitComposer() {}

    static void emit(DrawList out, LayeredUnitAssets assets, LayeredSpriteCache head,
                     WeaponDef primary, boolean drawWeaponLayers,
                     SpecialEquipmentDef special, EquipmentGrade equipmentGrade,
                     float actorX, float actorY, float shoulderPx,
                     float facingDeg, float headLookDeg, float locomotionPhase,
                     float weaponPhase, int pose, int flags, float alpha) {
        emit(out, assets, head, primary, drawWeaponLayers, special, equipmentGrade,
                actorX, actorY, shoulderPx, facingDeg, headLookDeg, locomotionPhase,
                weaponPhase, pose, flags, alpha, null);
    }

    static void emit(DrawList out, LayeredUnitAssets assets, LayeredSpriteCache head,
                     WeaponDef primary, boolean drawWeaponLayers,
                     SpecialEquipmentDef special, EquipmentGrade equipmentGrade,
                     float actorX, float actorY, float shoulderPx,
                     float facingDeg, float headLookDeg, float locomotionPhase,
                     float weaponPhase, int pose, int flags, float alpha,
                     LayerPose authoredPose) {
        emit((layer, centerX, centerY, width, height, angleDegrees, red, green, blue, opacity) ->
                        out.addSprite(RenderLayer.UNITS, layer.sprite, centerX, centerY,
                                width, height, angleDegrees, red, green, blue, opacity),
                assets, head, primary, drawWeaponLayers, special, equipmentGrade,
                actorX, actorY, shoulderPx, facingDeg, headLookDeg, locomotionPhase,
                weaponPhase, pose, flags, alpha, authoredPose);
    }

    static void emit(SpriteEmitter out, LayeredUnitAssets assets, LayeredSpriteCache head,
                     WeaponDef primary, boolean drawWeaponLayers,
                     SpecialEquipmentDef special, EquipmentGrade equipmentGrade,
                     float actorX, float actorY, float shoulderPx,
                     float facingDeg, float headLookDeg, float locomotionPhase,
                     float weaponPhase, int pose, int flags, float alpha) {
        emit(out, assets, head, primary, drawWeaponLayers, special, equipmentGrade,
                actorX, actorY, shoulderPx, facingDeg, headLookDeg, locomotionPhase,
                weaponPhase, pose, flags, alpha, null);
    }

    static void emit(SpriteEmitter out, LayeredUnitAssets assets, LayeredSpriteCache head,
                     WeaponDef primary, boolean drawWeaponLayers,
                     SpecialEquipmentDef special, EquipmentGrade equipmentGrade,
                     float actorX, float actorY, float shoulderPx,
                     float facingDeg, float headLookDeg, float locomotionPhase,
                     float weaponPhase, int pose, int flags, float alpha,
                     LayerPose authoredPose) {
        float pxPerSw = shoulderPx;
        boolean moving = (flags & LayeredAppearance.FLAG_MOVING) != 0;
        boolean rocket = pose == LayeredAppearance.POSE_ROCKET_AIM
                || pose == LayeredAppearance.POSE_ROCKET_FIRE;
        boolean amr = pose == LayeredAppearance.POSE_AMR_AIM
                || pose == LayeredAppearance.POSE_AMR_FIRE;
        boolean overShoulder = (flags & LayeredAppearance.FLAG_WEAPON_OVER_SHOULDER) != 0;

        SpecialEquipmentDef specialDef = special;
        EquipmentLayerDef specialLayer = specialDef != null
                ? specialDef.presentation().carrierLayer() : null;
        SpecialUsePose usePose = specialDef != null
                ? specialDef.presentation().usePose() : null;
        boolean specialUsing = switch (usePose != null ? usePose : SpecialUsePose.THROW) {
            case SHOULDER_LAUNCHER -> rocket;
            case BRACED_RIFLE -> amr;
            case THROW -> pose == LayeredAppearance.POSE_SMOKE_THROW;
            case PLANT -> pose == LayeredAppearance.POSE_SATCHEL_PLANT;
        };
        boolean specialFiring = pose == LayeredAppearance.POSE_ROCKET_FIRE
                || pose == LayeredAppearance.POSE_AMR_FIRE;
        boolean genericSpecialLayer = specialLayer != null;
        boolean drawSpecialLayer = genericSpecialLayer
                && (specialUsing || specialLayer.visibleWhileCarried());
        boolean replacePrimary = drawSpecialLayer && specialUsing
                && specialLayer.replacePrimaryWhileUsing();
        boolean drawPrimaryLayers = drawWeaponLayers && !replacePrimary;

        LayeredWeaponFamily weaponFamily = drawPrimaryLayers
                ? LayeredWeaponFamily.fromPrimary(primary) : null;
        LayeredSpriteCache weapon = drawPrimaryLayers
                ? (rocket ? assets.rocketLauncher
                        : amr
                                ? assets.antiMaterielRifle
                                : assets.weapon(weaponFamily, equipmentGrade))
                : null;

        // Feet always exist underneath the actor. At rest both offsets keep them
        // occluded; locomotion alternately exposes only a toe-shaped tip.
        LayerTransform leftFoot = layer(authoredPose, "left-foot");
        LayerTransform rightFoot = layer(authoredPose, "right-foot");
        if (leftFoot != null) {
            emitAuthored(out, assets.foot, leftFoot, actorX, actorY, pxPerSw,
                    facingDeg, 0f, alpha);
        } else {
            float leftReveal = moving ? LayeredAppearance.leftFootReveal(locomotionPhase) : 0f;
            emitFoot(out, assets.foot, actorX, actorY, pxPerSw, facingDeg,
                    -0.12f, -0.2333f - 0.10f * leftReveal, false, alpha);
        }
        if (rightFoot != null) {
            emitAuthored(out, assets.foot, rightFoot, actorX, actorY, pxPerSw,
                    facingDeg, 0f, alpha);
        } else {
            float rightReveal = moving ? LayeredAppearance.rightFootReveal(locomotionPhase) : 0f;
            emitFoot(out, assets.foot, actorX, actorY, pxPerSw, facingDeg,
                    0.12f, -0.2333f - 0.10f * rightReveal, true, alpha);
        }

        float swipe = LayeredAppearance.meleeSwipe(pose, weaponPhase);
        boolean leftStrikes = LayeredAppearance.leftClawStrikes(locomotionPhase);
        ClawTransform leftClaw = clawTransform(true, leftStrikes ? swipe : 0f,
                actorX, actorY, pxPerSw, facingDeg);
        ClawTransform rightClaw = clawTransform(false, leftStrikes ? 0f : swipe,
                actorX, actorY, pxPerSw, facingDeg);

        if (assets.foreClaw != null) {
            // Both appendages tuck under the carapace at rest. During contact,
            // leave the striking claw for the foreground pass below.
            if (!leftStrikes || swipe <= 0f) {
                emitClaw(out, assets.foreClaw, leftClaw, pxPerSw, alpha);
            }
            if (leftStrikes || swipe <= 0f) {
                emitClaw(out, assets.foreClaw, rightClaw, pxPerSw, alpha);
            }
        }

        WeaponTransform wt = drawPrimaryLayers
                ? weaponTransform(weapon, weaponFamily, rocket, amr, pose, weaponPhase,
                    actorX, actorY, pxPerSw, facingDeg)
                : null;

        LayeredSpriteCache specialSprite = drawSpecialLayer
                ? assets.specialEquipment(specialDef.id()) : null;
        EquipmentLayerComposer.Placement specialPlacement = specialSprite != null
                ? EquipmentLayerComposer.resolve(specialLayer, specialUsing, specialFiring,
                        weaponPhase,
                        actorX, actorY, pxPerSw, facingDeg)
                : null;
        LayerTransform specialTransform = layer(authoredPose, "special");
        if (specialPlacement != null && specialTransform != null) {
            specialPlacement = authoredEquipmentPlacement(specialSprite, specialTransform,
                    actorX, actorY, pxPerSw, facingDeg, specialPlacement);
        }

        if (specialPlacement != null
                && specialPlacement.occlusion() == EquipmentLayerDef.Occlusion.UNDER_BODY) {
            emitEquipmentLayer(out, specialSprite, specialPlacement, alpha);
        }

        LayerTransform primaryTransform = layer(authoredPose, "primary");
        if (primaryTransform == null && rocket) {
            primaryTransform = layer(authoredPose, "rocket-launcher");
        }
        if (drawPrimaryLayers && !overShoulder) {
            if (primaryTransform != null) {
                emitAuthored(out, weapon, primaryTransform, actorX, actorY, pxPerSw,
                        facingDeg, 0f, alpha);
            } else {
                emitSprite(out, weapon, wt.cx, wt.cy, pxPerSw, wt.angleDeg, alpha);
            }
        }

        // Body and helmet have independent rotations but share the actor pivot.
        LayerTransform bodyTransform = layer(authoredPose, "body");
        if (bodyTransform != null) {
            emitAuthored(out, assets.body, bodyTransform, actorX, actorY, pxPerSw,
                    facingDeg, 0f, alpha);
        } else {
            float[] bodyCenter = worldPoint(actorX, actorY, 0f, -0.12f, pxPerSw, facingDeg);
            emitSprite(out, assets.body, bodyCenter[0], bodyCenter[1], pxPerSw,
                    facingDeg, alpha);
        }

        if (assets.foreClaw != null && swipe > 0f) {
            emitClaw(out, assets.foreClaw, leftStrikes ? leftClaw : rightClaw,
                    pxPerSw, alpha);
        }

        if (specialPlacement != null
                && specialPlacement.occlusion() == EquipmentLayerDef.Occlusion.OVER_BODY) {
            emitEquipmentLayer(out, specialSprite, specialPlacement, alpha);
        }

        // Authored firing occlusion may deliberately move a shoulder weapon
        // over the body while leaving a braced rifle underneath it.
        if (drawPrimaryLayers && overShoulder) {
            emitSprite(out, weapon, wt.cx, wt.cy, pxPerSw, wt.angleDeg, alpha);
        }

        LayerTransform headTransform = layer(authoredPose, "head");
        if (headTransform != null) {
            emitAuthored(out, head, headTransform, actorX, actorY, pxPerSw,
                    facingDeg, headLookDeg, alpha);
        } else {
            float[] headCenter = worldPoint(actorX, actorY, 0f, 0.08f, pxPerSw, facingDeg);
            emitSprite(out, head, headCenter[0], headCenter[1], pxPerSw,
                    facingDeg + headLookDeg, alpha);
        }

        if ((flags & LayeredAppearance.FLAG_MUZZLE_FLASH) != 0) {
            if (specialPlacement != null && specialFiring) {
                emitEquipmentFlash(out, assets.muzzleFlash, specialPlacement, pxPerSw, alpha);
            } else if (drawPrimaryLayers) {
                emitFlash(out, assets.muzzleFlash, wt, weapon, pxPerSw, alpha);
            }
        }
    }

    private static LayerTransform layer(LayerPose pose, String id) {
        return pose != null ? pose.layer(id) : null;
    }

    private static void emitAuthored(SpriteEmitter out, LayeredSpriteCache sprite,
                                     LayerTransform transform,
                                     float actorX, float actorY, float swPx,
                                     float facingDeg, float extraAngle, float alpha) {
        if (sprite == null || transform == null || !transform.visible()) return;
        float[] pivot = worldPoint(actorX, actorY, transform.offsetX(),
                transform.offsetY(), swPx, facingDeg);
        float localCenterX = (0.5f - transform.pivotX())
                * sprite.pxWidth / SOURCE_SHOULDER_PX * transform.scaleX() * swPx;
        float localCenterY = (transform.pivotY() - 0.5f)
                * sprite.pxHeight / SOURCE_SHOULDER_PX * transform.scaleY() * swPx;
        float angle = facingDeg + transform.angleDegrees() + extraAngle;
        float[] centerOffset = rotate(localCenterX, localCenterY, angle);
        out.add(sprite, pivot[0] + centerOffset[0], pivot[1] + centerOffset[1],
                sprite.pxWidth / SOURCE_SHOULDER_PX * transform.scaleX() * swPx,
                sprite.pxHeight / SOURCE_SHOULDER_PX * transform.scaleY() * swPx,
                angle, 1f, 1f, 1f, alpha);
    }

    private static EquipmentLayerComposer.Placement authoredEquipmentPlacement(
            LayeredSpriteCache sprite, LayerTransform transform,
            float actorX, float actorY, float swPx, float facingDeg,
            EquipmentLayerComposer.Placement fallback) {
        if (sprite == null || !transform.visible()) return null;
        float[] pivot = worldPoint(actorX, actorY, transform.offsetX(),
                transform.offsetY(), swPx, facingDeg);
        float localCenterX = (0.5f - transform.pivotX())
                * sprite.pxWidth / SOURCE_SHOULDER_PX * transform.scaleX() * swPx;
        float localCenterY = (transform.pivotY() - 0.5f)
                * sprite.pxHeight / SOURCE_SHOULDER_PX * transform.scaleY() * swPx;
        float angle = facingDeg + transform.angleDegrees();
        float[] centerOffset = rotate(localCenterX, localCenterY, angle);
        return new EquipmentLayerComposer.Placement(
                pivot[0] + centerOffset[0], pivot[1] + centerOffset[1],
                sprite.pxWidth / SOURCE_SHOULDER_PX * transform.scaleX() * swPx,
                sprite.pxHeight / SOURCE_SHOULDER_PX * transform.scaleY() * swPx,
                angle, fallback.occlusion());
    }

    private static void emitFoot(SpriteEmitter out, LayeredSpriteCache foot,
                                 float actorX, float actorY, float swPx,
                                 float facingDeg, float offsetXSw, float offsetYSw,
                                 boolean mirror, float alpha) {
        // The current foot blob is nearly symmetric; mirror is reserved in the
        // signature for a future asymmetric source. Both instances share one PNG.
        float pivotToCenterX = (foot.pxWidth * 0.5f - FOOT_PIVOT_X) / SOURCE_SHOULDER_PX;
        float pivotToCenterY = -(foot.pxHeight * 0.5f - FOOT_PIVOT_Y) / SOURCE_SHOULDER_PX;
        float[] center = worldPoint(actorX, actorY,
                offsetXSw + pivotToCenterX, offsetYSw + pivotToCenterY,
                swPx, facingDeg);
        emitSprite(out, foot, center[0], center[1], swPx, facingDeg, alpha);
    }

    private static ClawTransform clawTransform(boolean left, float swipe,
                                                float actorX, float actorY,
                                                float swPx, float facingDeg) {
        float side = left ? -1f : 1f;
        float offsetX = lerp(0.30f, 0.23f, swipe) * side;
        float offsetY = lerp(0.02f, 0.22f, swipe);
        float restingAngle = left ? 20f : -20f;
        float impactAngle = left ? -8f : 8f;
        float[] pivot = worldPoint(actorX, actorY, offsetX, offsetY, swPx, facingDeg);
        return new ClawTransform(pivot[0], pivot[1],
                facingDeg + lerp(restingAngle, impactAngle, swipe));
    }

    private static void emitClaw(SpriteEmitter out, LayeredSpriteCache claw,
                                 ClawTransform transform, float swPx, float alpha) {
        float pivotX = claw.pxWidth * 0.5f;
        float pivotY = claw.pxHeight * CLAW_PIVOT_Y_FRACTION;
        float centerX = (claw.pxWidth * 0.5f - pivotX) / SOURCE_SHOULDER_PX * swPx;
        float centerY = -(claw.pxHeight * 0.5f - pivotY) / SOURCE_SHOULDER_PX * swPx;
        float[] centerOffset = rotate(centerX, centerY, transform.angleDeg);
        emitSprite(out, claw, transform.pivotX + centerOffset[0],
                transform.pivotY + centerOffset[1], swPx, transform.angleDeg, alpha);
    }

    private static WeaponTransform weaponTransform(
            LayeredSpriteCache weapon, LayeredWeaponFamily weaponFamily,
            boolean rocket, boolean amr, int pose, float phase,
            float actorX, float actorY, float swPx, float facingDeg) {
        float t = smoothstep(clamp01(phase));

        float carryX = rocket ? 0.28f : 0.1867f;
        float carryY = rocket ? 0.05f : 0.08f;
        float aimX = rocket ? 0.3333f : 0.1733f;
        float aimY = 0.12f;
        float aimFactor;
        if (pose == LayeredAppearance.POSE_ROCKET_AIM) {
            aimFactor = t;
        } else if (pose == LayeredAppearance.POSE_AMR_AIM) {
            aimFactor = t;
        } else if (pose == LayeredAppearance.POSE_FIRING
                || pose == LayeredAppearance.POSE_ROCKET_FIRE
                || pose == LayeredAppearance.POSE_AMR_FIRE) {
            aimFactor = 1f;
        } else if (pose == LayeredAppearance.POSE_AIMED) {
            // Primary fire is observed after the shot. Hold fully aimed through
            // the flash, then recover toward carry for the rest of weapon-up.
            aimFactor = 1f - t;
        } else {
            aimFactor = 0f;
        }
        float offsetX = lerp(carryX, aimX, aimFactor);
        float offsetY = lerp(carryY, aimY, aimFactor);
        float relativeAngle = lerp(45f, 0f, aimFactor);

        float recoil = LayeredAppearance.recoilSw(pose, phase);
        float[] pivot = worldPoint(actorX, actorY, offsetX, offsetY, swPx, facingDeg);
        float totalAngle = facingDeg + relativeAngle;
        float[] recoilOffset = rotate(0f, -recoil * swPx, totalAngle);
        pivot[0] += recoilOffset[0];
        pivot[1] += recoilOffset[1];

        float pivotX = weapon.pxWidth * 0.5f;
        float pivotY = rocket ? ROCKET_PIVOT_Y
                : amr ? weapon.pxHeight * 0.75f
                : weaponFamilyPivotY(weaponFamily, weapon);
        float centerX = (weapon.pxWidth * 0.5f - pivotX) / SOURCE_SHOULDER_PX * swPx;
        float centerY = -(weapon.pxHeight * 0.5f - pivotY) / SOURCE_SHOULDER_PX * swPx;
        float[] centerOffset = rotate(centerX, centerY, totalAngle);
        return new WeaponTransform(pivot[0] + centerOffset[0], pivot[1] + centerOffset[1],
                pivot[0], pivot[1], pivotY, totalAngle);
    }

    private static float weaponFamilyPivotY(LayeredWeaponFamily weaponFamily,
                                             LayeredSpriteCache weapon) {
        return switch (weaponFamily) {
            case SMG, DMR -> weapon.pxHeight * 0.75f;
            case RIFLE, LASER_GUN -> STANDARD_WEAPON_PIVOT_Y;
        };
    }

    private static void emitFlash(SpriteEmitter out, LayeredSpriteCache flash,
                                  WeaponTransform weaponTransform,
                                  LayeredSpriteCache weapon, float swPx, float alpha) {
        // Muzzle is at the north edge of every weapon source, centered on X.
        float muzzleDistance = weaponTransform.pivotYPx / SOURCE_SHOULDER_PX * swPx;
        float[] muzzleOffset = rotate(0f, muzzleDistance, weaponTransform.angleDeg);
        float muzzleX = weaponTransform.pivotX + muzzleOffset[0];
        float muzzleY = weaponTransform.pivotY + muzzleOffset[1];

        float centerX = (flash.pxWidth * 0.5f - FLASH_PIVOT_X) / SOURCE_SHOULDER_PX * swPx;
        float centerY = -(flash.pxHeight * 0.5f - FLASH_PIVOT_Y) / SOURCE_SHOULDER_PX * swPx;
        float[] flashOffset = rotate(centerX, centerY, weaponTransform.angleDeg);
        emitSprite(out, flash, muzzleX + flashOffset[0], muzzleY + flashOffset[1],
                swPx, weaponTransform.angleDeg, alpha);
    }

    private static void emitSprite(SpriteEmitter out, LayeredSpriteCache layer,
                                   float cx, float cy, float swPx,
                                   float angleDeg, float alpha) {
        float scale = swPx / SOURCE_SHOULDER_PX;
        out.add(layer, cx, cy,
                layer.pxWidth * scale, layer.pxHeight * scale, angleDeg,
                1f, 1f, 1f, alpha);
    }

    private static void emitEquipmentLayer(SpriteEmitter out, LayeredSpriteCache layer,
                                           EquipmentLayerComposer.Placement placement,
                                           float alpha) {
        out.add(layer,
                placement.centerX(), placement.centerY(),
                placement.width(), placement.height(), placement.angleDegrees(),
                1f, 1f, 1f, alpha);
    }

    private static void emitEquipmentFlash(SpriteEmitter out, LayeredSpriteCache flash,
                                           EquipmentLayerComposer.Placement placement,
                                           float shoulderPx, float alpha) {
        float[] muzzleOffset = rotate(0f, placement.height() * 0.5f,
                placement.angleDegrees());
        float muzzleX = placement.centerX() + muzzleOffset[0];
        float muzzleY = placement.centerY() + muzzleOffset[1];
        float centerX = (flash.pxWidth * 0.5f - FLASH_PIVOT_X)
                / SOURCE_SHOULDER_PX * shoulderPx;
        float centerY = -(flash.pxHeight * 0.5f - FLASH_PIVOT_Y)
                / SOURCE_SHOULDER_PX * shoulderPx;
        float[] flashOffset = rotate(centerX, centerY, placement.angleDegrees());
        emitSprite(out, flash, muzzleX + flashOffset[0], muzzleY + flashOffset[1],
                shoulderPx, placement.angleDegrees(), alpha);
    }

    private static float[] worldPoint(float actorX, float actorY,
                                      float localXSw, float localYSw,
                                      float swPx, float facingDeg) {
        float[] offset = rotate(localXSw * swPx, localYSw * swPx, facingDeg);
        return new float[]{actorX + offset[0], actorY + offset[1]};
    }

    /** Sprite angle: 0 north, positive CCW. */
    private static float[] rotate(float x, float y, float degrees) {
        double rad = Math.toRadians(degrees);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        return new float[]{x * cos - y * sin, x * sin + y * cos};
    }

    private static float clamp01(float value) { return Math.max(0f, Math.min(1f, value)); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float smoothstep(float t) { return t * t * (3f - 2f * t); }

    @FunctionalInterface
    interface SpriteEmitter {
        void add(LayeredSpriteCache layer, float centerX, float centerY,
                 float width, float height, float angleDegrees,
                 float red, float green, float blue, float alpha);
    }

    private static final class WeaponTransform {
        final float cx, cy;
        final float pivotX, pivotY;
        final float pivotYPx;
        final float angleDeg;

        WeaponTransform(float cx, float cy, float pivotX, float pivotY,
                        float pivotYPx, float angleDeg) {
            this.cx = cx;
            this.cy = cy;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.pivotYPx = pivotYPx;
            this.angleDeg = angleDeg;
        }
    }

    private static final class ClawTransform {
        final float pivotX, pivotY;
        final float angleDeg;

        ClawTransform(float pivotX, float pivotY, float angleDeg) {
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.angleDeg = angleDeg;
        }
    }
}
