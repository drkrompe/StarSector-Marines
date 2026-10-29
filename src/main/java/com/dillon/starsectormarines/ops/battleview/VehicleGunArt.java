package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleGun;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

/**
 * How a hull's fitted mech weapon is drawn (mechs-nouns.md, vehicle gun card): the weapon's own top-down drawing, the
 * one a mech carries, at the length it fires from ({@link VehicleGun}), turning on the hull's turret ring in place of
 * the hull's own turret. Stock hulls are unaffected.
 */
final class VehicleGunArt {

    private VehicleGunArt() { }

    /** The weapon's drawing; null where the hull fires its own gun or the weapon has none. */
    static LayeredSpriteCache sprite(LayeredMechAssets assets, VehicleType type, MechWeaponComponent weapon) {
        if (assets == null || weapon == null || VehicleGun.lengthCells(type, weapon) <= 0f) return null;
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
