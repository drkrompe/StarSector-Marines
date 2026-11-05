package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.vehicle.TurretMountStyle;
import com.dillon.starsectormarines.battle.vehicle.VehicleGun;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.CampaignVehicle;
import com.dillon.starsectormarines.ui.retained.CanvasBlend;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.CanvasSpriteRegion;

import java.awt.Color;
import java.util.function.BooleanSupplier;

/**
 * Static thumbnail of one vehicle hull and its turret, nose to the left as the sheet draws it,
 * on the same dark pad the chassis previews use so a lance reads as one row of machines. A hull with
 * an empty gun card stands with a bare turret ring, as it does in a battle.
 */
public final class VehiclePreviewCanvas implements CanvasProducer {

    private static final Color BACKGROUND = new Color(0x08, 0x12, 0x1B);
    private static final Color PAD = new Color(0x0D, 0x20, 0x2C);
    /** A sheet whose size cannot be read draws its frames at the APC sheets' proportion. */
    private static final float DEFAULT_FRAME_ASPECT = 0.75f;
    /** The canvas turn that brings the sheet's left-pointing nose down the screen, as a parked hull faces down its berth. */
    private static final float ON_PAD_DEGREES = 90f;

    private static final String ART_ROOT = "graphics/battle/vehicles/";
    private static final String COLLAR_TEXTURE = ART_ROOT + "turret-collar.png";

    private final VehicleType type;
    /** Read on every draw: fitting a gun to a campaign hull changes what its standing canvas shows. */
    private final BooleanSupplier bareRing;
    /** Resolved on the first draw, from whichever host is drawing; NaN until then. */
    private float frameAspect = Float.NaN;

    /** The hull in its stock fit, its own turret on it: a debug loadout's machine. */
    public VehiclePreviewCanvas(VehicleType type) {
        this(type, () -> false);
    }

    private VehiclePreviewCanvas(VehicleType type, BooleanSupplier bareRing) {
        if (type == null) throw new IllegalArgumentException("vehicle type required");
        this.type = type;
        this.bareRing = bareRing;
    }

    /** The hull as it is bought, with nothing in its gun card: a pattern on offer. */
    public static VehiclePreviewCanvas asBought(VehicleType type) {
        return new VehiclePreviewCanvas(type, () -> true);
    }

    /** A campaign hull as it stands now, its ring bare while its gun card is empty. */
    public static VehiclePreviewCanvas of(CampaignVehicle vehicle) {
        if (vehicle == null) throw new IllegalArgumentException("vehicle required");
        return new VehiclePreviewCanvas(vehicle.type(), () -> vehicle.gun() == null
                && (!vehicle.hasOwnGun() || vehicle.type().hasModularTurret()));
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        float inset = Math.max(3f, Math.min(width, height) * 0.055f);
        context.fillRect(0f, 0f, width, height, BACKGROUND);
        context.fillRect(inset, inset, width - inset * 2f, height - inset * 2f, PAD);

        float aspect = frameAspect(context);
        float hullWidth = Math.min((width - inset * 2f) * 0.92f, (height - inset * 2f) * 0.92f / aspect);
        drawAt(context, width * 0.5f, height * 0.5f, hullWidth / type.drawnLengthCells, false, 1f);
    }

    /**
     * Draws the hull and turret at a canvas point. The sheet's nose points left; {@code onPad}
     * turns the whole vehicle a quarter so it faces down its pad as the parked hull does. A hull
     * with an empty gun card carries a bare ring where its turret stands; a hull that never takes a
     * gun keeps whatever turret its sheet was drawn with, as in a battle.
     */
    void drawAt(CanvasContext context, float centerX, float centerY, float cellPx, boolean onPad, float alpha) {
        float hullWidth = type.drawnLengthCells * cellPx;
        float aspect = frameAspect(context);
        float hullHeight = hullWidth * aspect;
        float angle = onPad ? ON_PAD_DEGREES : 0f;
        float[] tint = type.hullTint();
        Color hullColor = tint == null ? new Color(1f, 1f, 1f, alpha)
                : new Color(clamp(tint[0]), clamp(tint[1]), clamp(tint[2]), alpha);
        context.sprite(type.spritePath, null, centerX, centerY, hullWidth, hullHeight, angle, hullColor,
                CanvasSpriteRegion.frame(type.frameCount, 1, type.spriteFrame), CanvasBlend.NORMAL);
        if (type.turretFrame >= 0) {
            float turretWidth = type.turretVisualCells * cellPx;
            // The chassis' forward (+Y) runs along the nose and its right (+X) a quarter clockwise of it:
            // forward left and right up on the sheet; forward down and right to the left when turned.
            float turretX = onPad ? centerX - type.turretMountX * cellPx : centerX - type.turretMountY * cellPx;
            float turretY = onPad ? centerY + type.turretMountY * cellPx : centerY - type.turretMountX * cellPx;
            if (type.hasTurretWeapon() && bareRing.getAsBoolean()) {
                drawBareRing(context, turretX, turretY, cellPx, angle, alpha);
                return;
            }
            VehicleType.GunLayer gun = type.gunLayer(null);
            if (gun != null) {
                // A head's gun is its own layer under the housing ({@link VehicleType.GunLayer}), ahead of its centre.
                float ahead = turretWidth * gun.centreAheadShare();
                float strip = VehicleType.GunLayer.STRIP_SHARE;
                context.sprite(type.spritePath, null, onPad ? turretX : turretX - ahead,
                        onPad ? turretY + ahead : turretY, turretWidth * gun.cutShare(),
                        turretWidth * aspect * strip, angle, new Color(1f, 1f, 1f, alpha),
                        new CanvasSpriteRegion((float) gun.frame() / type.frameCount,
                                Math.min(gun.strip() * strip, 1f - strip),
                                gun.cutShare() / type.frameCount, strip), CanvasBlend.NORMAL);
            }
            context.sprite(type.spritePath, null, turretX, turretY, turretWidth, turretWidth * aspect, angle,
                    new Color(1f, 1f, 1f, alpha), CanvasSpriteRegion.frame(type.frameCount, 1, type.turretFrame),
                    CanvasBlend.NORMAL);
        }
    }

    /** The ring in the hull's roof, and the white ring's collar in it, as {@link ConvoyRenderSystem} draws them. */
    private void drawBareRing(CanvasContext context, float x, float y, float cellPx, float angle, float alpha) {
        float ringPx = VehicleGun.mountCells(type) * cellPx;
        Color tint = new Color(1f, 1f, 1f, alpha);
        context.sprite(ART_ROOT + type.mountStyle().ringTexture, null, x, y, ringPx, ringPx, 0f, tint);
        if (type.mountStyle() == TurretMountStyle.WHITE) {
            float collarPx = ringPx * ConvoyRenderSystem.COLLAR_SHARE_OF_RING;
            context.sprite(COLLAR_TEXTURE, null, x, y, collarPx, collarPx, angle, tint);
        }
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /** One frame's height over its width, as the drawing host sizes the sheet. */
    private float frameAspect(CanvasContext context) {
        if (Float.isNaN(frameAspect)) {
            float[] size = context.imageSize(type.spritePath);
            frameAspect = size != null && size[0] > 0f && size[1] > 0f
                    ? size[1] / (size[0] / type.frameCount) : DEFAULT_FRAME_ASPECT;
        }
        return frameAspect;
    }
}
