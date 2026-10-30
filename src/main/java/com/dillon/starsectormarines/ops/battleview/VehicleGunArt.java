package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleGun;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

/**
 * How a hull's fitted mech weapon is drawn (mechs-nouns.md, vehicle gun card): a drawing made for a hull's roof where
 * the weapon has one ({@link #vehicleDrawingPath}), otherwise the weapon's own top-down drawing, the one a mech carries,
 * at the length it fires from ({@link VehicleGun}), turning on the hull's turret ring in place of the hull's own turret.
 * Stock hulls are unaffected.
 */
final class VehicleGunArt {

    /**
     * The light machine gun on a roof pintle: barrel, receiver and cradle on the sprite's centre column, the drum hanging
     * to the right, muzzle at the top edge and the rear of the grips at the bottom (art-source/vehicles/pintle-lmg). The
     * mech's drawing is an arm gun whose drum sits on the barrel's side of the sprite box, so drawn centred on a turret its
     * barrel ran off the plate's slot and the whole gun swung off-centre as it turned.
     */
    static final String PINTLE_LMG = "graphics/battle/vehicles/pintle-lmg.png";

    private VehicleGunArt() { }

    /**
     * The drawing a hull carries {@code weapon} in instead of the mech's, its path under the mod; null where the hull
     * draws the mech's own. Every such drawing is cut with its barrel on the sprite's centre column, so it turns on the
     * ring with its muzzle where the gun's shots leave ({@link VehicleGun#fit}).
     */
    static String vehicleDrawingPath(MechWeaponComponent weapon) {
        return weapon == MechWeaponComponent.LIGHT_MACHINE_GUN ? PINTLE_LMG : null;
    }

    /** The weapon's drawing on a hull; null where the hull fires its own gun or the weapon has none. */
    static LayeredSpriteCache sprite(BattleSprites sprites, VehicleType type, MechWeaponComponent weapon) {
        if (weapon == null || VehicleGun.lengthCells(type, weapon) <= 0f) return null;
        LayeredSpriteCache own = sprites.vehicleGunDrawing(weapon);
        if (own != null && own.sprite != null) return own;
        LayeredMechAssets assets = sprites.layeredMechSprites();
        if (assets == null) return null;
        LayeredSpriteCache sprite = LayeredMechComposer.weaponSprite(assets, weapon);
        return sprite != null && sprite.sprite != null ? sprite : null;
    }

    /**
     * Draws {@code gun} {@code lengthPx} long (its width following the drawing's own proportions) centred at the screen
     * point, its barrel toward {@code facingDeg}; {@code shade} 1 is the drawing as painted, 0 a shadow of it.
     */
    static void draw(DrawList out, LayeredSpriteCache gun, float x, float y, float lengthPx, float facingDeg,
                     float lengthScale, float widthScale, float shade, float alpha) {
        float aspect = (float) gun.pxWidth / (float) gun.pxHeight;
        out.addSprite(RenderLayer.CONVOY, gun.sprite, x, y, lengthPx * aspect * widthScale, lengthPx * lengthScale,
                facingDeg, shade, shade, shade, alpha);
    }
}
