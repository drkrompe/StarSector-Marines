package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.DerelictLegRig;
import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.mech.MechBodyHardpoints;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.render2d.BattleCamera;

/**
 * Draws a derelict walker's guns on its body (`mechs-nouns.md`, mount): each fitted mount's weapon, from the weapon's own
 * drawing, under a turret head at its body hardpoint ({@link MechBodyHardpoints}), turned together toward the walker's
 * aim as far as its arc lets it. The head is the derelict's own housing, one for a gun port and one for a rack, and the
 * weapon beneath it is whatever the mount holds, so any weapon the game has sits on a derelict mount with no art made for
 * that pairing (owner: turret heads over the weapons rather than custom art per weapon). The head covers the weapon's
 * breech and the barrel or tube box shows past it. The hardpoints ride the
 * drawn body ({@link DerelictLegRig#bodyX()}, its heading and its rise), never the legs, so a gun stays where it is
 * bolted while the body sways. Its flash is drawn at the tip of its barrel, where the battle fires its round from
 * ({@code MechHardpointGeometry.muzzle}).
 */
final class DerelictWalkerGuns {

    /** The gun port's head, and the rack's cradle, each drawn centred on its mount's pivot. */
    static final String PORT_HEAD = "graphics/battle/derelict-walker/turret-head-port.png";
    static final String RACK_HEAD = "graphics/battle/derelict-walker/turret-head-rack.png";
    /** A head's width as a share of its mount's gun-scale hull width. UNTUNED. */
    static final float PORT_HEAD_SHARE = 0.24f;
    static final float RACK_HEAD_SHARE = 0.36f;
    /** How far behind the pivot the rack's cradle sits, as a share of the rack's drawn length, so its arms flank the box. */
    static final float RACK_HEAD_BACK = 0.12f;
    /** The weapons beneath weathered toward the sentinel's plate (its torso drawing averages 106/92/71). UNTUNED. */
    static final float[] WEATHER = {0.90f, 0.84f, 0.72f};
    /** How wide a chaingun is drawn: the six-barrel drawing at half width, as on a centreline mount. */
    static final float CHAINGUN_WIDTH = 0.5f;

    private DerelictWalkerGuns() {
    }

    /**
     * @param guns        the weapon layers in the walker's faction's casing
     * @param portHead    the gun ports' head, or null to draw the weapons bare
     * @param rackHead    the racks' cradle, or null to draw the racks bare
     * @param appearance  each mount's drawing selector by {@link MechMountSlot} ordinal, 0 for an empty mount
     * @param aimDegrees  the walker's fire-control bearing
     * @param armKick     an arm gun's kick this frame, hull widths
     * @param podKick     a rack's kick this frame, hull widths
     */
    static void emit(DrawList out, LayeredMechAssets guns, LayeredSpriteCache portHead, LayeredSpriteCache rackHead,
                     DerelictLegRig rig,
                     BattleCamera cam, MechVariant variant, float renderScale, int[] appearance, float aimDegrees,
                     float armKick, float podKick, int flags, float alpha) {
        if (guns == null || !MechBodyHardpoints.has(variant)) return;
        // The hardpoints grow with the torso as it rises toward the camera.
        float hullWidth = LayeredMechAppearance.hullWidthCells(renderScale)
                * (1f + DerelictWalkerComposer.TORSO_HEIGHT_GROWTH * rig.bodyHeight());
        float ppc = cam.cellPxSize();
        // Racks under ports: the ports stand proud of the back.
        for (MechMountSlot slot : DRAW_ORDER) {
            MechBodyHardpoints.Hardpoint point = MechBodyHardpoints.of(variant, slot);
            int gun = appearance[slot.ordinal()];
            if (point == null || gun == 0) continue;
            LayeredSpriteCache sprite = LayeredMechComposer.mountSprite(guns, slot, gun);
            float length = MechBodyHardpoints.gunLengthCells(point, gun, hullWidth);
            if (sprite == null || sprite.sprite == null || length <= 0f) continue;
            float[] pivot = MechBodyHardpoints.pivot(rig.bodyX(), rig.bodyY(), rig.bodyFacing(), hullWidth, point);
            float facing = point.gunFacing(rig.bodyFacing(), aimDegrees);
            float gunHull = hullWidth * point.gunScale();
            float kick = (slot.isArm() ? (LayeredMechComposer.armKicks(gun) ? armKick : 0f) : podKick) * gunHull;
            float centre = (0.5f - MechBodyHardpoints.SEAT) * length - kick;
            float[] at = along(pivot, facing, centre);
            float widthScale = gun == LayeredMechAppearance.ARMS_CHAINGUN || gun == LayeredMechAppearance.ARMS_NOSE_CHAINGUN
                    ? CHAINGUN_WIDTH : 1f;
            float lengthPx = length * ppc;
            out.addSprite(RenderLayer.UNITS, sprite.sprite, cam.cellToScreenX(at[0]), cam.cellToScreenY(at[1]),
                    lengthPx * sprite.pxWidth / Math.max(1f, sprite.pxHeight) * widthScale, lengthPx, facing,
                    WEATHER[0], WEATHER[1], WEATHER[2], alpha);
            LayeredSpriteCache head = slot.isArm() ? portHead : rackHead;
            if (head != null && head.sprite != null) {
                float wide = (slot.isArm() ? PORT_HEAD_SHARE : RACK_HEAD_SHARE) * gunHull * ppc;
                float[] seat = slot.isArm() ? pivot : along(pivot, facing, -RACK_HEAD_BACK * length);
                out.addSprite(RenderLayer.UNITS, head.sprite, cam.cellToScreenX(seat[0]), cam.cellToScreenY(seat[1]),
                        wide, wide * head.pxHeight / Math.max(1f, head.pxWidth), facing, 1f, 1f, 1f, alpha);
            }
            if (flashing(slot, gun, flags) && guns.muzzleFlash != null && guns.muzzleFlash.sprite != null) {
                float[] tip = along(pivot, facing, (1f - MechBodyHardpoints.SEAT) * length - kick);
                float scale = gunHull * ppc / 208f;
                out.addSprite(RenderLayer.UNITS, guns.muzzleFlash.sprite, cam.cellToScreenX(tip[0]),
                        cam.cellToScreenY(tip[1]), guns.muzzleFlash.pxWidth * scale,
                        guns.muzzleFlash.pxHeight * scale, facing, 1f, 1f, 1f, alpha);
            }
        }
    }

    private static final MechMountSlot[] DRAW_ORDER = {MechMountSlot.LEFT_SHOULDER, MechMountSlot.RIGHT_SHOULDER,
            MechMountSlot.LEFT_ARM, MechMountSlot.RIGHT_ARM, MechMountSlot.ARMS};

    /** Whether the mount's flash shows: an arm gun with the guns' flash, a rack with its own track's. */
    static boolean flashing(MechMountSlot slot, int gun, int flags) {
        if (slot.isArm()) return (flags & LayeredMechAppearance.FLAG_CHAINGUN_FLASH) != 0;
        int track = LayeredMechComposer.srmPod(gun) ? LayeredMechAppearance.FLAG_SRM_FLASH
                : LayeredMechAppearance.FLAG_LRM_FLASH;
        return (flags & (track | LayeredMechAppearance.FLAG_LASER_FLASH)) != 0;
    }

    /** {@code from} moved {@code cells} along the sprite bearing {@code degrees}. */
    private static float[] along(float[] from, float degrees, float cells) {
        double radians = Math.toRadians(degrees);
        return new float[] {from[0] - (float) Math.sin(radians) * cells, from[1] + (float) Math.cos(radians) * cells};
    }
}
