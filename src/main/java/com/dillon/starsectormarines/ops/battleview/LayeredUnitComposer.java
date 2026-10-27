package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.LayeredWeaponFamily;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.LayerPose;
import com.dillon.starsectormarines.battle.appearance.WorkMotion;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.LayerTransform;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.EquipmentLayerDef;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentPresentationDef.HandsetLayer;
import com.dillon.starsectormarines.marine.SpecialUsePose;

/** Emits one modular infantry actor from shoulder-relative authored transforms. */
final class LayeredUnitComposer {

    static final float SOURCE_SHOULDER_PX = 150f;

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
        emit(out, assets, head, primary, drawWeaponLayers, special, equipmentGrade,
                actorX, actorY, shoulderPx, facingDeg, headLookDeg, locomotionPhase,
                weaponPhase, pose, flags, alpha, authoredPose, null);
    }

    static void emit(DrawList out, LayeredUnitAssets assets, LayeredSpriteCache head,
                     WeaponDef primary, boolean drawWeaponLayers,
                     SpecialEquipmentDef special, EquipmentGrade equipmentGrade,
                     float actorX, float actorY, float shoulderPx,
                     float facingDeg, float headLookDeg, float locomotionPhase,
                     float weaponPhase, int pose, int flags, float alpha,
                     LayerPose authoredPose, IntegralSystemDef integralSystem) {
        emit(new DrawListEmitter(out),
                assets, head, primary, drawWeaponLayers, special, equipmentGrade,
                actorX, actorY, shoulderPx, facingDeg, headLookDeg, locomotionPhase,
                weaponPhase, pose, flags, alpha, authoredPose, integralSystem);
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
        emit(out, assets, head, primary, drawWeaponLayers, special, equipmentGrade,
                actorX, actorY, shoulderPx, facingDeg, headLookDeg, locomotionPhase,
                weaponPhase, pose, flags, alpha, authoredPose, null);
    }

    static void emit(SpriteEmitter out, LayeredUnitAssets assets, LayeredSpriteCache head,
                     WeaponDef primary, boolean drawWeaponLayers,
                     SpecialEquipmentDef special, EquipmentGrade equipmentGrade,
                     float actorX, float actorY, float shoulderPx,
                     float facingDeg, float headLookDeg, float locomotionPhase,
                     float weaponPhase, int pose, int flags, float alpha,
                     LayerPose authoredPose, IntegralSystemDef integralSystem) {
        emit(out, assets, head, primary, drawWeaponLayers, special, equipmentGrade,
                actorX, actorY, shoulderPx, facingDeg, headLookDeg, locomotionPhase,
                weaponPhase, pose, flags, alpha, authoredPose, integralSystem, null);
    }

    /** A shoulder pod's actual committed launch, projected by the caller into the actor's screen space. */
    record ShoulderLaunch(float muzzleX, float muzzleY, float bearingDegrees, float phase) {}

    static void emit(DrawList out, LayeredUnitAssets assets, LayeredSpriteCache head,
                     WeaponDef primary, boolean drawWeaponLayers,
                     SpecialEquipmentDef special, EquipmentGrade equipmentGrade,
                     float actorX, float actorY, float shoulderPx,
                     float facingDeg, float headLookDeg, float locomotionPhase,
                     float weaponPhase, int pose, int flags, float alpha,
                     LayerPose authoredPose, IntegralSystemDef integralSystem,
                     ShoulderLaunch launch) {
        emit(new DrawListEmitter(out),
                assets, head, primary, drawWeaponLayers, special, equipmentGrade,
                actorX, actorY, shoulderPx, facingDeg, headLookDeg, locomotionPhase,
                weaponPhase, pose, flags, alpha, authoredPose, integralSystem, launch);
    }

    static void emit(SpriteEmitter out, LayeredUnitAssets assets, LayeredSpriteCache head,
                     WeaponDef primary, boolean drawWeaponLayers,
                     SpecialEquipmentDef special, EquipmentGrade equipmentGrade,
                     float actorX, float actorY, float shoulderPx,
                     float facingDeg, float headLookDeg, float locomotionPhase,
                     float weaponPhase, int pose, int flags, float alpha,
                     LayerPose authoredPose, IntegralSystemDef integralSystem,
                     ShoulderLaunch launch) {
        float pxPerSw = shoulderPx;
        boolean moving = (flags & LayeredAppearance.FLAG_MOVING) != 0;
        boolean rocket = pose == LayeredAppearance.POSE_ROCKET_AIM
                || pose == LayeredAppearance.POSE_ROCKET_FIRE;
        boolean amr = pose == LayeredAppearance.POSE_AMR_AIM
                || pose == LayeredAppearance.POSE_AMR_FIRE;
        boolean overShoulder = (flags & LayeredAppearance.FLAG_WEAPON_OVER_SHOULDER) != 0;

        // A treating medic holds the treatment kit in place of whatever
        // special they carry, for exactly as long as the treatment lasts.
        boolean treating = (flags & LayeredAppearance.FLAG_TREATING) != 0
                && assets.specialEquipment(TreatmentKitLayer.ASSET_KEY) != null;
        SpecialEquipmentDef specialDef = treating ? null : special;
        EquipmentLayerDef specialLayer = treating ? TreatmentKitLayer.DEF
                : specialDef != null ? specialDef.presentation().carrierLayer() : null;
        SpecialUsePose usePose = specialDef != null
                ? specialDef.presentation().usePose() : null;
        boolean specialUsing = treating || switch (usePose != null ? usePose : SpecialUsePose.THROW) {
            case SHOULDER_LAUNCHER -> rocket;
            case BRACED_RIFLE -> amr;
            case THROW -> pose == LayeredAppearance.POSE_SMOKE_THROW;
            case PLANT -> pose == LayeredAppearance.POSE_SATCHEL_PLANT;
            case SHOULDER_MOUNT -> false;
        };
        boolean specialFiring = pose == LayeredAppearance.POSE_ROCKET_FIRE
                || pose == LayeredAppearance.POSE_AMR_FIRE;
        // Not in use, an item with packed art is drawn stowed on the wearer instead.
        LayeredSpriteCache packedSprite = !specialUsing && specialDef != null
                && specialDef.presentation().packedLayer() != null
                ? assets.packed(specialDef.id()) : null;
        if (packedSprite != null) specialLayer = specialDef.presentation().packedLayer();
        boolean genericSpecialLayer = specialLayer != null;
        boolean drawSpecialLayer = genericSpecialLayer
                && (specialUsing || specialLayer.visibleWhileCarried());
        boolean replacePrimary = drawSpecialLayer && specialUsing
                && specialLayer.replacePrimaryWhileUsing();
        // Hands on a console are not holding a rifle; the family's station arms
        // replace it for as long as the actor stands at the work.
        boolean stationFlag = (flags & LayeredAppearance.FLAG_AT_STATION) != 0;
        boolean atStation = stationFlag && assets.stationArm(true) != null;
        WorkMotion motion = LayeredAppearance.workMotion(flags);
        // Exercise is done empty-handed in any outfit: nobody runs, rows, presses or curls with a rifle, so it is
        // put away even by a family with no station arms to draw the hands with.
        boolean exercising = stationFlag && (motion == WorkMotion.RUN || motion == WorkMotion.JOG
                || motion == WorkMotion.ROW || motion == WorkMotion.PRESS || motion == WorkMotion.LIFT);
        // A station with no motion named is worked the ordinary way, across a console.
        if (atStation && motion == WorkMotion.NONE) motion = WorkMotion.CONSOLE;
        LayeredSpriteCache crate = motion == WorkMotion.CARRY
                ? assets.specialEquipment(WorkPropLayer.CRATE) : null;
        boolean drawPrimaryLayers = drawWeaponLayers && !replacePrimary && !atStation && !exercising && crate == null;
        boolean running = atStation && (motion == WorkMotion.RUN || motion == WorkMotion.JOG);
        if (atStation && motion == WorkMotion.ROW) {
            // A rower's whole body slides on the seat: back as the handle comes
            // in, forward again on the recovery.
            float[] seat = worldPoint(actorX, actorY, 0f,
                    ROW_SLIDE_SW * (float) Math.cos(weaponPhase * 2.0 * Math.PI), pxPerSw, facingDeg);
            actorX = seat[0];
            actorY = seat[1];
        }
        if (atStation && (motion == WorkMotion.RUN || motion == WorkMotion.JOG)) {
            // A runner bounds rather than glides. Seen from above that is the
            // body rising toward the camera between footfalls (drawn larger),
            // the weight rolling onto each stance foot in turn, a surge along the
            // belt at each push-off and the shoulders twisting against the arms.
            // The arms pump and the feet show on the same cycle, so a footfall is
            // where the feet cross and the body is lowest.
            double turn = weaponPhase * 2.0 * Math.PI;
            float stride = (float) Math.sin(turn);
            float flight = Math.abs(stride);
            float[] body = worldPoint(actorX, actorY, BOUND_SWAY_SW * stride,
                    BOUND_SURGE_SW * (flight - 0.5f), pxPerSw, facingDeg);
            actorX = body[0];
            actorY = body[1];
            pxPerSw *= 1f + BOUND_RISE * flight;
            facingDeg += BOUND_TWIST_DEG * stride;
        }

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
            float leftReveal = moving ? LayeredAppearance.leftFootReveal(locomotionPhase)
                    : running ? LayeredAppearance.leftFootReveal(weaponPhase) : 0f;
            emitFoot(out, assets.foot, actorX, actorY, pxPerSw, facingDeg,
                    -0.12f, -0.2333f - 0.10f * leftReveal, false, alpha);
        }
        if (rightFoot != null) {
            emitAuthored(out, assets.foot, rightFoot, actorX, actorY, pxPerSw,
                    facingDeg, 0f, alpha);
        } else {
            float rightReveal = moving ? LayeredAppearance.rightFootReveal(locomotionPhase)
                    : running ? LayeredAppearance.rightFootReveal(weaponPhase) : 0f;
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

        LayeredSpriteCache specialSprite = !drawSpecialLayer ? null
                : treating ? assets.specialEquipment(TreatmentKitLayer.ASSET_KEY)
                : packedSprite != null ? packedSprite
                : assets.specialEquipment(specialDef.id());
        // Carried in its walking clip, the phase is how far it is carried and the item sits fully in use.
        boolean specialCarried = specialUsing && !treating && packedSprite == null
                && (flags & LayeredAppearance.FLAG_SPECIAL_CARRIED) != 0;
        EquipmentLayerComposer.Placement specialPlacement = specialSprite != null
                ? equipmentPlacement(specialLayer, specialUsing, specialFiring,
                        specialCarried ? 1f : weaponPhase,
                        assets, authoredPose, actorX, actorY, pxPerSw, facingDeg)
                : null;
        LayerTransform specialTransform = layer(authoredPose, "special");
        // A shoulder pod's committed launch recoils the mount along the salvo's own bearing.
        boolean podLaunch = launch != null && specialPlacement != null && !treating
                && specialDef != null && specialDef.isShoulderPod();
        if (podLaunch) {
            specialPlacement = EquipmentLayerComposer.launch(specialPlacement,
                    launch.muzzleX(), launch.muzzleY(), launch.bearingDegrees(),
                    launch.phase(), shoulderPx, specialLayer.muzzleX(), specialLayer.muzzleY());
        } else if (specialCarried && specialPlacement != null && specialTransform != null) {
            // The walking clip places the item in the hands whatever it is attached to, from its held place at
            // the aim to its carry as the Marine starts walking, and back again when it stops.
            specialPlacement = blendPlacement(specialPlacement,
                    carriedEquipmentPlacement(specialLayer, specialTransform, actorX, actorY, pxPerSw,
                            facingDeg, specialPlacement.occlusion()),
                    smoothstep(clamp01(weaponPhase)));
        } else if (!treating && specialPlacement != null && specialTransform != null
                && specialLayer.attachment() == EquipmentLayerDef.Attachment.ACTOR) {
            // The kit is set down on the ground before the medic at its own size; the
            // clip's hand transform is authored for satchel art and would shrink it.
            specialPlacement = authoredEquipmentPlacement(specialSprite, specialTransform,
                    actorX, actorY, pxPerSw, facingDeg, specialPlacement);
        }

        if (specialPlacement != null
                && specialPlacement.occlusion() == EquipmentLayerDef.Occlusion.UNDER_BODY) {
            emitEquipmentLayer(out, specialSprite, specialPlacement, alpha);
        }

        // A hand-held part exists only while the item is in use; the pack it is corded
        // to is the carried layer above and stays on the actor in every other pose.
        HandsetLayer handsetDef = specialUsing && !treating && specialPlacement != null
                ? specialDef.presentation().handsetLayer() : null;
        LayerTransform handsetTransform = handsetDef != null ? layer(authoredPose, "handset") : null;
        LayeredSpriteCache handsetSprite = handsetTransform != null && handsetTransform.visible()
                ? assets.handset(specialDef.id()) : null;
        LayeredSpriteCache cordSprite = handsetSprite != null
                ? assets.handsetCord(specialDef.id()) : null;
        EquipmentLayerComposer.Placement handsetPlacement = cordSprite != null
                ? authoredHandsetPlacement(handsetDef, handsetTransform,
                        actorX, actorY, pxPerSw, facingDeg) : null;

        // Equipped hardware stays on its mount regardless of ammunition or activity.
        // Activity duration is not a shot phase and must not synthesize recoil.
        EquipmentLayerDef integralLayer = integralSystem != null
                ? integralSystem.carrierLayer() : null;
        LayeredSpriteCache integralSprite = integralLayer != null
                ? assets.integralSystem(integralSystem.id()) : null;
        EquipmentLayerComposer.Placement integralPlacement = integralSprite != null
                ? equipmentPlacement(integralLayer, false, false, 0f, assets,
                        authoredPose, actorX, actorY, pxPerSw, facingDeg) : null;
        if (integralPlacement != null
                && integralPlacement.occlusion() == EquipmentLayerDef.Occlusion.UNDER_BODY) {
            emitEquipmentLayer(out, integralSprite, integralPlacement, alpha);
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

        // Station arms go under the torso: the sleeve covers the elbow and only
        // the forearm and hand show past the body, reaching onto the work.
        float[] rightArm = null;
        WorkMotion armMotion = atStation ? motion : crate != null ? WorkMotion.CARRY : WorkMotion.NONE;
        if (armMotion != WorkMotion.NONE && assets.stationArm(true) != null) {
            LayerTransform authoredBody = layer(authoredPose, "body");
            LayerTransform base = authoredBody != null ? authoredBody : DEFAULT_BODY;
            for (boolean left : new boolean[]{true, false}) {
                float[] arm = workArm(armMotion, left, weaponPhase);
                if (!left) rightArm = arm;
                emitAuthored(out, assets.stationArm(left),
                        stationArmTransform(base, assets.body, assets.stationArm(left), left, arm[0], arm[1]),
                        actorX, actorY, pxPerSw, facingDeg, 0f, alpha);
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

        // Armor shells follow authored torso articulation, never helmet look.
        emitShoulder(out, assets.shoulderLeft, layer(authoredPose, "left-shoulder"),
                bodyTransform, actorX, actorY, pxPerSw, facingDeg, alpha);
        emitShoulder(out, assets.shoulderRight, layer(authoredPose, "right-shoulder"),
                bodyTransform, actorX, actorY, pxPerSw, facingDeg, alpha);

        if (integralPlacement != null
                && integralPlacement.occlusion() == EquipmentLayerDef.Occlusion.OVER_BODY) {
            emitEquipmentLayer(out, integralSprite, integralPlacement, alpha);
        }

        // What the hands hold is over the torso and the forearms, under the head.
        if (crate != null) {
            float[] at = worldPoint(actorX, actorY, 0f, CRATE_FORWARD_SW, pxPerSw, facingDeg);
            emitSprite(out, crate, at[0], at[1], pxPerSw, facingDeg, alpha);
        }
        if (atStation && rightArm != null) {
            emitHeldTool(out, assets, motion, rightArm, weaponPhase,
                    actorX, actorY, pxPerSw, facingDeg, alpha);
        }
        if (stationFlag && motion == WorkMotion.LIFT) {
            emitDumbbells(out, assets, weaponPhase, actorX, actorY, pxPerSw, facingDeg, alpha);
        }

        if (assets.foreClaw != null && swipe > 0f) {
            emitClaw(out, assets.foreClaw, leftStrikes ? leftClaw : rightClaw,
                    pxPerSw, alpha);
        }

        if (specialPlacement != null
                && specialPlacement.occlusion() == EquipmentLayerDef.Occlusion.OVER_BODY) {
            emitEquipmentLayer(out, specialSprite, specialPlacement, alpha);
        }

        if (handsetPlacement != null) {
            emitCord(out, cordSprite, handsetDef, handsetPlacement, specialPlacement,
                    pxPerSw, alpha);
        }

        // Authored firing occlusion may deliberately move a shoulder weapon
        // over the body while leaving a braced rifle underneath it.
        if (drawPrimaryLayers && overShoulder) {
            emitSprite(out, weapon, wt.cx, wt.cy, pxPerSw, wt.angleDeg, alpha);
        }

        // A handset held low, like the flamer's wand at the hip, is under the helmet.
        boolean handsetUnderHead = handsetPlacement != null && handsetDef.underHead();
        if (handsetUnderHead) {
            emitEquipmentLayer(out, handsetSprite, handsetPlacement, alpha);
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

        // A handset raised to the helmet is the last thing drawn, over the head.
        if (handsetPlacement != null && !handsetUnderHead) {
            emitEquipmentLayer(out, handsetSprite, handsetPlacement, alpha);
        }

        if (podLaunch && launch.phase() < 0.3f) {
            emitEquipmentFlash(out, assets.muzzleFlash, specialPlacement, pxPerSw,
                    specialLayer.muzzleX(), specialLayer.muzzleY(),
                    alpha * (1f - launch.phase() / 0.3f));
        }

        if ((flags & LayeredAppearance.FLAG_MUZZLE_FLASH) != 0) {
            if (handsetPlacement != null && specialFiring) {
                // A corded item fires from what is in the hand, not from the pack.
                emitEquipmentFlash(out, assets.muzzleFlash, handsetPlacement, pxPerSw, alpha);
            } else if (specialPlacement != null && specialFiring) {
                emitEquipmentFlash(out, assets.muzzleFlash, specialPlacement, pxPerSw, alpha);
            } else if (drawPrimaryLayers) {
                emitFlash(out, assets.muzzleFlash, wt, weapon, pxPerSw, alpha);
            }
        }
    }

    private static void emitShoulder(SpriteEmitter out, LayeredSpriteCache sprite,
                                     LayerTransform shoulder, LayerTransform body,
                                     float actorX, float actorY, float swPx,
                                     float facingDeg, float alpha) {
        if (sprite == null) return;
        if (shoulder != null) {
            emitAuthored(out, sprite, shoulder, actorX, actorY, swPx, facingDeg, 0f, alpha);
        } else if (body != null) {
            // Independent shells retain the body's source canvas, so a missing
            // joint channel rests at the same registration as its torso.
            emitAuthored(out, sprite, body, actorX, actorY, swPx, facingDeg, 0f, alpha);
        } else {
            float[] center = worldPoint(actorX, actorY, 0f, -0.12f, swPx, facingDeg);
            emitSprite(out, sprite, center[0], center[1], swPx, facingDeg, alpha);
        }
    }

    /**
     * Where each forearm is in a work motion at {@code phase}: degrees turned
     * about its elbow (positive brings the hand inward) and shoulder widths slid
     * forward. The vocabulary is one table so a motion is a row here rather than
     * a branch through the compositor.
     */
    static float[] workArm(WorkMotion motion, boolean left, float phase) {
        double turn = phase * 2.0 * Math.PI;
        return switch (motion) {
            // The two hands work half a cycle apart, each sweeping across the
            // console about its own elbow and reaching in twice a sweep.
            case CONSOLE -> {
                double cycle = turn + (left ? 0.0 : Math.PI);
                yield new float[]{(float) Math.sin(cycle) * STATION_HAND_SWING_DEG,
                        (float) Math.max(0.0, Math.sin(2.0 * cycle)) * STATION_HAND_REACH_SW};
            }
            // The torch hand held out on the seam with a small tremor; the other
            // braced against the work.
            case WELD -> left ? new float[]{3f, 0.02f}
                    : new float[]{6f + (float) Math.sin(turn * 3.0) * 1.5f, 0.05f};
            // The ladle hand circles the pot; the other holds its handle.
            case STIR -> left ? new float[]{2f, 0.015f}
                    : new float[]{(float) Math.cos(turn) * 10f,
                            0.03f + (float) Math.sin(turn) * 0.025f};
            // The knife hand drops sharply onto the board and lifts back; the
            // other holds the food still.
            case CHOP -> left ? new float[]{9f, 0.03f}
                    : new float[]{4f, 0.06f * (float) Math.pow(1.0 - phase, 3.0)};
            // Both hands on one wheel or wrench, heaved round together over most
            // of the cycle and then reset for the next pull.
            case WRENCH -> {
                float heave = phase < 0.75f ? smoothstep(phase / 0.75f)
                        : 1f - smoothstep((phase - 0.75f) / 0.25f);
                float angle = -12f + 24f * heave;
                yield new float[]{left ? -angle : angle, 0.03f};
            }
            // Arms pumping against the stride, opposite to each other.
            case RUN, JOG -> {
                float pump = (float) Math.sin(turn + (left ? 0.0 : Math.PI));
                yield new float[]{pump * 6f, 0.01f + pump * 0.04f};
            }
            // Both hands draw the handle in and reach out again together.
            case ROW -> new float[]{4f, 0.06f * (float) Math.sin(turn)};
            // The bar pushed up off the chest together, slowly, and lowered.
            case PRESS -> new float[]{2f, 0.065f * (float) (0.5 - 0.5 * Math.cos(turn))};
            // A weight curled in each hand in turn.
            case LIFT -> {
                float curl = (float) Math.max(0.0, Math.sin(turn + (left ? 0.0 : Math.PI)));
                yield new float[]{8f * curl, 0.05f * curl};
            }
            // Both arms out under a load.
            case CARRY -> new float[]{4f, 0.04f};
            case NONE, TREAT, SLEEP -> new float[]{0f, 0f};
        };
    }

    /**
     * The tool in the right hand for a motion that has one, placed at that hand
     * and turned with its forearm; the welding torch also throws sparks from its
     * tip onto the work.
     */
    private static void emitHeldTool(SpriteEmitter out, LayeredUnitAssets assets, WorkMotion motion,
                                     float[] rightArm, float phase, float actorX, float actorY,
                                     float swPx, float facingDeg, float alpha) {
        String key = WorkPropLayer.toolKey(motion);
        LayeredSpriteCache tool = key != null ? assets.specialEquipment(key) : null;
        if (tool == null) return;
        // The right hand, swung about the elbow with its forearm.
        float[] hand = rotate(RIGHT_HAND_FROM_ELBOW_X, RIGHT_HAND_FROM_ELBOW_Y, rightArm[0]);
        float handX = RIGHT_ELBOW_X + hand[0];
        float handY = RIGHT_ELBOW_Y + hand[1] + rightArm[1];
        float turn = rightArm[0] + WorkPropLayer.TOOL_TILT_DEG;
        float length = WorkPropLayer.toolLengthSw(motion);
        float width = length * tool.pxWidth / Math.max(1f, tool.pxHeight);
        // The grip sits in the hand and the working end reaches past it.
        float[] along = rotate(0f, length * 0.35f, turn);
        float[] at = worldPoint(actorX, actorY, handX + along[0], handY + along[1], swPx, facingDeg);
        out.add(tool, at[0], at[1], width * swPx, length * swPx, facingDeg + turn, 1f, 1f, 1f, alpha);
        if (motion != WorkMotion.WELD) return;
        LayeredSpriteCache sparks = assets.specialEquipment(WorkPropLayer.SPARKS);
        if (sparks == null) return;
        float[] tip = rotate(0f, length * 0.9f, turn);
        float[] work = worldPoint(actorX, actorY, handX + tip[0], handY + tip[1], swPx, facingDeg);
        int frame = Math.floorMod((int) Math.floor(phase * WorkPropLayer.SPARK_FRAMES),
                WorkPropLayer.SPARK_FRAMES);
        int frameWidth = sparks.pxWidth / WorkPropLayer.SPARK_COLUMNS;
        int frameHeight = sparks.pxHeight / WorkPropLayer.SPARK_ROWS;
        float size = WorkPropLayer.SPARK_SIZE_SW * swPx;
        out.frame(sparks, (frame % WorkPropLayer.SPARK_COLUMNS) * frameWidth,
                (frame / WorkPropLayer.SPARK_COLUMNS) * frameHeight, frameWidth, frameHeight,
                work[0], work[1], size, size, 0f, alpha);
    }

    /**
     * A dumbbell in each hand, across the grip, curled with the forearm. The hands are where the station arms put
     * them, so a family with arm art holds the weights in its hands; one without still holds them out in front.
     */
    private static void emitDumbbells(SpriteEmitter out, LayeredUnitAssets assets, float phase, float actorX,
                                      float actorY, float swPx, float facingDeg, float alpha) {
        LayeredSpriteCache weight = assets.specialEquipment(WorkPropLayer.DUMBBELL);
        if (weight == null) return;
        float length = WorkPropLayer.DUMBBELL_LENGTH_SW;
        float width = length * weight.pxHeight / Math.max(1f, weight.pxWidth);
        for (boolean left : new boolean[]{true, false}) {
            float[] arm = workArm(WorkMotion.LIFT, left, phase);
            float side = left ? -1f : 1f;
            float turn = side * arm[0];
            float[] hand = rotate(side * RIGHT_HAND_FROM_ELBOW_X, RIGHT_HAND_FROM_ELBOW_Y, turn);
            float[] at = worldPoint(actorX, actorY, side * RIGHT_ELBOW_X + hand[0],
                    RIGHT_ELBOW_Y + hand[1] + arm[1], swPx, facingDeg);
            out.add(weight, at[0], at[1], length * swPx, width * swPx, facingDeg + turn, 1f, 1f, 1f, alpha);
        }
    }

    /** How much larger a runner is drawn at the top of a stride than at a footfall: the bound toward the camera. */
    private static final float BOUND_RISE = 0.08f;
    /** How far a runner's weight rolls onto each stance foot, in shoulder widths. */
    private static final float BOUND_SWAY_SW = 0.06f;
    /** How far a runner surges forward at each push-off, in shoulder widths. */
    private static final float BOUND_SURGE_SW = 0.05f;
    /** How far a runner's shoulders twist against the arm swing, in degrees. */
    private static final float BOUND_TWIST_DEG = 6f;
    /** How far a rower slides fore and aft on the seat, in shoulder widths. */
    private static final float ROW_SLIDE_SW = 0.06f;
    /** Where a carried crate is held, in shoulder widths ahead of the actor. */
    private static final float CRATE_FORWARD_SW = 0.5f;
    /**
     * The right elbow and, from it, the right hand of the station-arm art at
     * rest, in shoulder widths of the actor's own frame. Read off the art: the
     * elbow is on the sleeve's outer edge halfway down the torso, and the forearm
     * reaches forward past the body canvas to the hand.
     */
    private static final float RIGHT_ELBOW_X = 0.4f, RIGHT_ELBOW_Y = -0.12f;
    private static final float RIGHT_HAND_FROM_ELBOW_X = -0.15f, RIGHT_HAND_FROM_ELBOW_Y = 0.52f;

    /** The body's placement when no clip authors one: centred 0.12 shoulder widths behind the actor. */
    private static final LayerTransform DEFAULT_BODY =
            new LayerTransform("body", null, 0f, -0.12f, 1f, 1f, 0f, 0.5f, 0.5f, 0, true);
    /** How far a forearm sweeps either way about its elbow while working, in degrees. */
    private static final float STATION_HAND_SWING_DEG = 7f;
    /** How far a hand reaches in at the top of its stroke, in shoulder widths. */
    private static final float STATION_HAND_REACH_SW = 0.035f;
    /**
     * Where the elbow sits, as a fraction of the body canvas: at the sleeve's
     * outer edge, halfway down the torso. The art is drawn to this point.
     */
    private static final float STATION_ELBOW_X = 0.1f, STATION_ELBOW_BODY_ROW = 0.5f;

    /**
     * A station arm placed exactly as the body is, then turned {@code swing}
     * degrees about its elbow and slid {@code reach} forward. The arm layer is
     * the body canvas extended forward, so the body canvas is its bottom rows and
     * the elbow is a fixed point of the body's own grid: the sleeve it leaves
     * stays put while the hand moves.
     */
    private static LayerTransform stationArmTransform(LayerTransform body, LayeredSpriteCache bodySprite,
                                                      LayeredSpriteCache arm, boolean left,
                                                      float swing, float reach) {
        float width = arm.pxWidth, bodyRows = bodySprite.pxHeight, armRows = arm.pxHeight;
        float bodyPivotY = (armRows - bodyRows + body.pivotY() * bodyRows) / armRows;
        float elbowX = left ? STATION_ELBOW_X : 1f - STATION_ELBOW_X;
        float elbowY = (armRows - bodyRows + STATION_ELBOW_BODY_ROW * bodyRows) / armRows;
        float[] toElbow = rotate((elbowX - body.pivotX()) * width / SOURCE_SHOULDER_PX * body.scaleX(),
                (bodyPivotY - elbowY) * armRows / SOURCE_SHOULDER_PX * body.scaleY(),
                body.angleDegrees());
        return new LayerTransform(body.id(), body.spritePath(),
                body.offsetX() + toElbow[0], body.offsetY() + toElbow[1] + reach,
                body.scaleX(), body.scaleY(), body.angleDegrees() + (left ? -swing : swing),
                elbowX, elbowY, body.z(), true);
    }

    private static LayerTransform layer(LayerPose pose, String id) {
        return pose != null ? pose.layer(id) : null;
    }

    private static EquipmentLayerComposer.Placement equipmentPlacement(
            EquipmentLayerDef equipment, boolean using, boolean firing, float phase,
            LayeredUnitAssets assets, LayerPose pose, float actorX, float actorY,
            float swPx, float facingDeg) {
        if (equipment.attachment() == EquipmentLayerDef.Attachment.ACTOR) {
            return EquipmentLayerComposer.resolve(equipment, using, firing, phase,
                    actorX, actorY, swPx, facingDeg);
        }
        LayerTransform body = layer(pose, "body");
        boolean left = equipment.attachment() == EquipmentLayerDef.Attachment.LEFT_SHOULDER;
        boolean right = equipment.attachment() == EquipmentLayerDef.Attachment.RIGHT_SHOULDER;
        LayeredSpriteCache shell = left ? assets.shoulderLeft : right ? assets.shoulderRight : null;
        // Shared authored poses may contain shoulder tracks for another armor family.
        // Only a family with that actual independent shell can supply its joint.
        LayerTransform parent = shell != null
                ? layer(pose, left ? "left-shoulder" : "right-shoulder") : null;
        float scaleX = parent != null ? parent.scaleX() : body != null ? body.scaleX() : 1f;
        float scaleY = parent != null ? parent.scaleY() : body != null ? body.scaleY() : 1f;
        float localAngle = parent != null ? parent.angleDegrees()
                : body != null ? body.angleDegrees() : 0f;
        float[] anchor;
        if (parent != null) {
            anchor = worldPoint(actorX, actorY, parent.offsetX(), parent.offsetY(), swPx, facingDeg);
        } else {
            float[] bodyPivot = worldPoint(actorX, actorY, body != null ? body.offsetX() : 0f,
                    body != null ? body.offsetY() : -0.12f, swPx, facingDeg);
            float bodyWidth = assets.body.pxWidth / SOURCE_SHOULDER_PX;
            float bodyHeight = assets.body.pxHeight / SOURCE_SHOULDER_PX;
            float localX = 0f;
            float localY = 0f;
            if (left || right) {
                // Inner pauldron hinge in the torso's normalized source canvas.
                // Convert from its center to the authored pivot before scaling.
                localX = (0.5f - (body != null ? body.pivotX() : 0.5f)) * bodyWidth;
                localY = ((body != null ? body.pivotY() : 0.5f) - 0.5f) * bodyHeight;
                localX += (left ? -0.234694f : 0.234694f) * bodyWidth;
                localY += 0.150124f * bodyHeight;
            }
            float[] offset = rotate(localX * scaleX * swPx, localY * scaleY * swPx,
                    facingDeg + localAngle);
            anchor = new float[]{bodyPivot[0] + offset[0], bodyPivot[1] + offset[1]};
        }
        return EquipmentLayerComposer.resolve(equipment, using, firing, phase,
                anchor[0], anchor[1], swPx, facingDeg + localAngle, scaleX, scaleY);
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

    /**
     * An item in its walking clip: the clip's {@code special} layer says where the hands hold it, the item's own
     * layer how big it is, so the carry and the aim draw the same launcher or rifle.
     */
    private static EquipmentLayerComposer.Placement carriedEquipmentPlacement(
            EquipmentLayerDef equipment, LayerTransform transform,
            float actorX, float actorY, float swPx, float facingDeg,
            EquipmentLayerDef.Occlusion occlusion) {
        float[] pivot = worldPoint(actorX, actorY, transform.offsetX(), transform.offsetY(), swPx, facingDeg);
        float width = equipment.widthShoulders() * transform.scaleX() * swPx;
        float height = equipment.heightShoulders() * transform.scaleY() * swPx;
        float angle = facingDeg + transform.angleDegrees();
        float[] centerOffset = rotate((0.5f - transform.pivotX()) * width,
                (transform.pivotY() - 0.5f) * height, angle);
        return new EquipmentLayerComposer.Placement(pivot[0] + centerOffset[0], pivot[1] + centerOffset[1],
                width, height, angle, occlusion);
    }

    /** {@code from} moved {@code t} of the way to {@code to}, turning the short way; {@code to}'s occlusion. */
    private static EquipmentLayerComposer.Placement blendPlacement(EquipmentLayerComposer.Placement from,
            EquipmentLayerComposer.Placement to, float t) {
        float turn = LayeredAppearance.wrapDegrees(to.angleDegrees() - from.angleDegrees());
        return new EquipmentLayerComposer.Placement(lerp(from.centerX(), to.centerX(), t),
                lerp(from.centerY(), to.centerY(), t), lerp(from.width(), to.width(), t),
                lerp(from.height(), to.height(), t), from.angleDegrees() + turn * t, to.occlusion());
    }

    /** The handset's size is its own authored art; the clip owns where the hand puts it. */
    private static EquipmentLayerComposer.Placement authoredHandsetPlacement(
            HandsetLayer handset, LayerTransform transform,
            float actorX, float actorY, float swPx, float facingDeg) {
        float[] pivot = worldPoint(actorX, actorY, transform.offsetX(),
                transform.offsetY(), swPx, facingDeg);
        float width = handset.widthShoulders() * transform.scaleX() * swPx;
        float height = handset.heightShoulders() * transform.scaleY() * swPx;
        float angle = facingDeg + transform.angleDegrees();
        float[] centerOffset = rotate((0.5f - transform.pivotX()) * width,
                (transform.pivotY() - 0.5f) * height, angle);
        return new EquipmentLayerComposer.Placement(pivot[0] + centerOffset[0],
                pivot[1] + centerOffset[1], width, height, angle,
                EquipmentLayerDef.Occlusion.OVER_BODY);
    }

    /** Stretches the cord sprite between the handset's port and the carried layer's port. */
    private static void emitCord(SpriteEmitter out, LayeredSpriteCache cord, HandsetLayer handset,
                                 EquipmentLayerComposer.Placement handsetPlacement,
                                 EquipmentLayerComposer.Placement pack, float swPx, float alpha) {
        float[] from = portPoint(handsetPlacement, handset.portX(), handset.portY());
        float[] to = portPoint(pack, handset.packPortX(), handset.packPortY());
        float dx = to[0] - from[0];
        float dy = to[1] - from[1];
        float length = (float) Math.hypot(dx, dy);
        if (length < 1e-3f) return;
        // The sprite points north (+y), so its bearing is the vector's angle from north.
        float angle = (float) Math.toDegrees(Math.atan2(-dx, dy));
        out.add(cord, (from[0] + to[0]) * 0.5f, (from[1] + to[1]) * 0.5f,
                handset.cordWidthShoulders() * swPx, length, angle, 1f, 1f, 1f, alpha);
    }

    private static float[] portPoint(EquipmentLayerComposer.Placement placement,
                                     float portX, float portY) {
        float[] offset = rotate((portX - 0.5f) * placement.width(),
                (0.5f - portY) * placement.height(), placement.angleDegrees());
        return new float[]{placement.centerX() + offset[0], placement.centerY() + offset[1]};
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
        emitEquipmentFlash(out, flash, placement, shoulderPx, 0.5f, 0f, alpha);
    }

    private static void emitEquipmentFlash(SpriteEmitter out, LayeredSpriteCache flash,
                                           EquipmentLayerComposer.Placement placement,
                                           float shoulderPx, float sourceMuzzleX,
                                           float sourceMuzzleY, float alpha) {
        if (flash == null) return;
        float[] muzzleOffset = rotate((sourceMuzzleX - .5f) * placement.width(),
                placement.height() * (.5f - sourceMuzzleY),
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

        /**
         * One frame of a sheet, in source pixels from the sheet's top left. An
         * emitter that cannot draw part of an image (a halo silhouette) leaves
         * it out, which is right for an effect such as sparks.
         */
        default void frame(LayeredSpriteCache sheet, int srcX, int srcY, int srcW, int srcH,
                           float centerX, float centerY, float width, float height,
                           float angleDegrees, float alpha) {
        }
    }

    /** The draw-list emitter every live and headless frame goes through. */
    private record DrawListEmitter(DrawList out) implements SpriteEmitter {
        @Override
        public void add(LayeredSpriteCache layer, float centerX, float centerY,
                        float width, float height, float angleDegrees,
                        float red, float green, float blue, float alpha) {
            out.addSprite(RenderLayer.UNITS, layer.sprite, centerX, centerY,
                    width, height, angleDegrees, red, green, blue, alpha);
        }

        @Override
        public void frame(LayeredSpriteCache sheet, int srcX, int srcY, int srcW, int srcH,
                          float centerX, float centerY, float width, float height,
                          float angleDegrees, float alpha) {
            out.addSheetQuad(RenderLayer.UNITS, sheet.sprite, srcX, srcY, srcW, srcH,
                    centerX, centerY, width, height, angleDegrees, 1f, 1f, 1f, alpha);
        }
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
