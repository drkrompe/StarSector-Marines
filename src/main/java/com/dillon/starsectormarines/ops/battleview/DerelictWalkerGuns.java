package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.DerelictLegRig;
import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.mech.MechBodyHardpoints;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.render2d.BattleCamera;

/**
 * Draws a derelict walker's guns on its body (`mechs-nouns.md`, mount): each fitted mount's weapon, from the company's
 * own weapon drawings in the walker's faction's casing, standing on a turret ring at its body hardpoint
 * ({@link MechBodyHardpoints}) and turned toward the walker's aim as far as its arc lets it. The hardpoints ride the
 * drawn body ({@link DerelictLegRig#bodyX()}, its heading and its rise), never the legs, so a gun stays where it is
 * bolted while the body sways. Its flash is drawn at the tip of its barrel, where the battle fires its round from
 * ({@code MechHardpointGeometry.muzzle}).
 */
final class DerelictWalkerGuns {

    /** A mount's turret ring across, as a share of its gun scale's hull width. */
    static final float RING_SHARE = 0.30f;
    /** The ring a shade darker than painted, so it reads as the hull's own collar rather than a lid. */
    static final float RING_SHADE = 0.72f;
    /** How wide a chaingun is drawn: the six-barrel drawing at half width, as on a centreline mount. */
    static final float CHAINGUN_WIDTH = 0.5f;

    private DerelictWalkerGuns() {
    }

    /**
     * @param guns        the weapon layers in the walker's faction's casing
     * @param ring        the turret ring drawn under each gun, or null for none
     * @param appearance  each mount's drawing selector by {@link MechMountSlot} ordinal, 0 for an empty mount
     * @param aimDegrees  the walker's fire-control bearing
     * @param armKick     an arm gun's kick this frame, hull widths
     * @param podKick     a rack's kick this frame, hull widths
     */
    static void emit(DrawList out, LayeredMechAssets guns, LayeredSpriteCache ring, DerelictLegRig rig,
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
            if (ring != null && ring.sprite != null) {
                float across = RING_SHARE * gunHull * ppc;
                out.addSprite(RenderLayer.UNITS, ring.sprite, cam.cellToScreenX(pivot[0]),
                        cam.cellToScreenY(pivot[1]), across, across, facing, RING_SHADE, RING_SHADE, RING_SHADE,
                        alpha);
            }
            float kick = (slot.isArm() ? (LayeredMechComposer.armKicks(gun) ? armKick : 0f) : podKick) * gunHull;
            float centre = (0.5f - MechBodyHardpoints.SEAT) * length - kick;
            float[] at = along(pivot, facing, centre);
            float widthScale = gun == LayeredMechAppearance.ARMS_CHAINGUN || gun == LayeredMechAppearance.ARMS_NOSE_CHAINGUN
                    ? CHAINGUN_WIDTH : 1f;
            float lengthPx = length * ppc;
            out.addSprite(RenderLayer.UNITS, sprite.sprite, cam.cellToScreenX(at[0]), cam.cellToScreenY(at[1]),
                    lengthPx * sprite.pxWidth / Math.max(1f, sprite.pxHeight) * widthScale, lengthPx, facing,
                    1f, 1f, 1f, alpha);
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
