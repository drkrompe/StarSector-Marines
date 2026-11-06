package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.vehicle.TurretHead;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent.HardpointType;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.vehicle.TurretMountStyle;
import com.dillon.starsectormarines.battle.vehicle.VehicleGun;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.CampaignVehicle;
import com.dillon.starsectormarines.ui.retained.CanvasBlend;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.CanvasSpriteRegion;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;
import java.util.function.Supplier;

/**
 * Static thumbnail of one vehicle hull and its turret, nose to the left as the sheet draws it,
 * on the same dark pad the chassis previews use so a lance reads as one row of machines. A campaign
 * hull shows what is in its gun card as {@link ConvoyRenderSystem} draws it in a battle: a bare turret
 * ring for an empty card, and a fitted weapon as its head on the hull's sheet or its own drawing on the ring.
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
    private static final String PLATE_TEXTURE = ART_ROOT + "gun-plate.png";
    private static final String BASE_TEXTURE = ART_ROOT + "turret-base-medium.png";
    private static final String LIGHT_BASE_TEXTURE = ART_ROOT + "turret-base-light.png";
    /** A gun's drawing points up the canvas; this turns it to where the unturned sheet's nose points. */
    private static final float GUN_TO_NOSE_DEGREES = 90f;

    /** What stands on the hull's turret ring: nothing, a fitted weapon, or (neither) the hull's own turret. */
    private record GunCard(boolean bare, MechWeaponComponent weapon) {
        static final GunCard OWN_TURRET = new GunCard(false, null);
        static final GunCard EMPTY = new GunCard(true, null);
    }

    /** A weapon's drawing on a hull, wherever its art comes from. */
    private record GunArt(String path, SpriteAPI live, float widthOverLength) { }

    private final VehicleType type;
    /** Read on every draw: fitting a gun to a campaign hull changes what its standing canvas shows. */
    private final Supplier<GunCard> gunCard;
    /** Where a fitted weapon's mech drawing comes from; may yield null where no machine art is loaded. */
    private final Supplier<LayeredMechAssets> assets;
    /** Resolved on the first draw, from whichever host is drawing; NaN until then. */
    private float frameAspect = Float.NaN;

    /** The hull in its stock fit, its own turret on it: a debug loadout's machine. */
    public VehiclePreviewCanvas(VehicleType type) {
        this(type, () -> GunCard.OWN_TURRET, () -> null);
    }

    private VehiclePreviewCanvas(VehicleType type, Supplier<GunCard> gunCard, Supplier<LayeredMechAssets> assets) {
        if (type == null) throw new IllegalArgumentException("vehicle type required");
        this.type = type;
        this.gunCard = gunCard;
        this.assets = assets;
    }

    /** The hull as it is bought, with nothing in its gun card: a pattern on offer. */
    public static VehiclePreviewCanvas asBought(VehicleType type) {
        return new VehiclePreviewCanvas(type, () -> GunCard.EMPTY, () -> null);
    }

    /** A campaign hull as it stands now, with whatever is in its gun card. */
    public static VehiclePreviewCanvas of(CampaignVehicle vehicle, Supplier<LayeredMechAssets> assets) {
        if (vehicle == null) throw new IllegalArgumentException("vehicle required");
        return new VehiclePreviewCanvas(vehicle.type(), () -> gunCardOf(vehicle), assets);
    }

    /** The battle's own reading of a gun card ({@code ConvoyService}): an older save's hull keeps its own gun until replaced. */
    private static GunCard gunCardOf(CampaignVehicle vehicle) {
        VehicleType type = vehicle.type();
        MechWeaponComponent weapon = vehicle.gun() != null ? vehicle.gun()
                : vehicle.hasOwnGun() ? type.ownGun() : null;
        if (weapon != null) return new GunCard(false, weapon);
        return !vehicle.hasOwnGun() || type.hasModularTurret() ? GunCard.EMPTY : GunCard.OWN_TURRET;
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
     * that never takes a gun keeps whatever turret its sheet was drawn with, as in a battle.
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
            GunCard card = type.hasTurretWeapon() ? gunCard.get() : GunCard.OWN_TURRET;
            TurretHead family = type.turretHead(headPoint(card));
            boolean openHead = family != null && !family.closed;
            drawHead(context, turretX, turretY, cellPx, onPad, alpha, true);
            if (card.bare()) {
                if (!openHead) drawRing(context, turretX, turretY, cellPx, angle, alpha);
                drawHead(context, turretX, turretY, cellPx, onPad, alpha, false);
                return;
            }
            MechWeaponComponent weapon = card.weapon();
            GunArt art = weapon == null ? null : gunArt(context, weapon);
            if (weapon != null && !openHead && (art != null || type.hasModularTurret())) {
                drawRing(context, turretX, turretY, cellPx, angle, alpha);
            }
            if (art != null) {
                drawFittedGun(context, weapon, art, turretX, turretY, cellPx, onPad, alpha);
                return;
            }
            context.sprite(type.spritePath, null, turretX, turretY, turretWidth, turretWidth * aspect, angle,
                    new Color(1f, 1f, 1f, alpha),
                    CanvasSpriteRegion.frame(type.frameCount, 1, type.headFrame(weapon, type.turretFrame)),
                    CanvasBlend.NORMAL);
        }
    }

    /** The ring in the hull's roof, and the white ring's collar in it, as {@link ConvoyRenderSystem} draws them. */
    private void drawRing(CanvasContext context, float x, float y, float cellPx, float angle, float alpha) {
        float ringPx = VehicleGun.mountCells(type) * cellPx;
        Color tint = new Color(1f, 1f, 1f, alpha);
        context.sprite(ART_ROOT + type.mountStyle().ringTexture, null, x, y, ringPx, ringPx, 0f, tint);
        if (type.mountStyle() == TurretMountStyle.WHITE) {
            float collarPx = ringPx * ConvoyRenderSystem.COLLAR_SHARE_OF_RING;
            context.sprite(COLLAR_TEXTURE, null, x, y, collarPx, collarPx, angle, tint);
        }
    }

    /** A weapon's drawing, with the laser base or gun plate it stands on, on the ring and pointing down the nose. */
    private void drawFittedGun(CanvasContext context, MechWeaponComponent weapon, GunArt art,
                               float x, float y, float cellPx, boolean onPad, float alpha) {
        float gunAngle = (onPad ? ON_PAD_DEGREES : 0f) + GUN_TO_NOSE_DEGREES;
        Color tint = new Color(1f, 1f, 1f, alpha);
        if (type.hasModularTurret() && type.mountStyle() == TurretMountStyle.WHITE) {
            float basePx = VehicleGun.baseCells(weapon) * cellPx;
            context.sprite(weapon.footprintRows < 2 ? LIGHT_BASE_TEXTURE : BASE_TEXTURE, null,
                    x, y, basePx, basePx, gunAngle, tint);
        }
        float platePx = VehicleGun.plateCells(type, weapon) * cellPx;
        if (platePx > 0f) context.sprite(PLATE_TEXTURE, null, x, y, platePx, platePx, gunAngle, tint);
        float lengthPx = VehicleGun.lengthCells(type, weapon) * cellPx;
        float ahead = (.5f - VehicleGun.RING_FROM_REAR) * lengthPx;
        context.sprite(art.path(), art.live(), onPad ? x : x - ahead, onPad ? y + ahead : y,
                lengthPx * art.widthOverLength(), lengthPx, gunAngle, tint);
        drawHead(context, x, y, cellPx, onPad, alpha, false);
    }

    /**
     * The hull's family head on its ring, front down the nose, as {@link ConvoyRenderSystem} draws it: an open barbette
     * when {@code under} (before the weapon), a closed head when not (over it).
     */
    private void drawHead(CanvasContext context, float x, float y, float cellPx, boolean onPad, float alpha,
                          boolean under) {
        HardpointType headPoint = headPoint(gunCard.get());
        TurretHead family = type.turretHead(headPoint);
        if (family == null || family.closed == under) return;
        float headPx = VehicleGun.headCells(type, headPoint) * cellPx;
        context.sprite(ART_ROOT + family.texture, null, x, y, headPx, headPx,
                (onPad ? ON_PAD_DEGREES : 0f) + GUN_TO_NOSE_DEGREES, new Color(1f, 1f, 1f, alpha));
    }

    /** The colour the head is chosen by: the fitted weapon's; null with none fitted, the hull's own head. */
    private static HardpointType headPoint(GunCard card) {
        return card.weapon() != null ? card.weapon().hardpointType : null;
    }

    /** The weapon's drawing on this hull, as {@link VehicleGunArt#sprite} picks it; null for one that wears a head on the sheet. */
    private GunArt gunArt(CanvasContext context, MechWeaponComponent weapon) {
        if (VehicleGun.lengthCells(type, weapon) <= 0f) return null;
        String own = VehicleGunArt.vehicleDrawingPath(type, weapon);
        float[] size = own == null ? null : context.imageSize(own);
        if (size != null && size[0] > 0f && size[1] > 0f) return new GunArt(own, null, size[0] / size[1]);
        LayeredMechAssets mechArt = assets.get();
        LayeredSpriteCache drawing = mechArt == null ? null : LayeredMechComposer.weaponSprite(mechArt, weapon);
        return drawing == null ? null
                : new GunArt(drawing.sourcePath, drawing.sprite, (float) drawing.pxWidth / drawing.pxHeight);
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
