package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;

import java.util.Locale;

/**
 * How a hull's fitted mech weapon is drawn on its turret (mechs-nouns.md, vehicle gun card): the weapon's own top-down
 * drawing, the one a mech carries, turning on the hull's turret mount. Stock hulls are unaffected.
 */
final class VehicleGunArt {

    /**
     * The look under review: {@code stock} keeps the hull's own turret whatever is fitted, {@code replace} draws the
     * weapon where the turret stood, {@code overlay} draws it smaller on top of the hull's own turret. Temporary:
     * remove the property and the losing modes once the owner has chosen.
     */
    static final String PROPERTY = "battle.vehicle.fittedTurretArt";

    enum Mode { STOCK, REPLACE, OVERLAY }

    private static final Mode MODE = Mode.valueOf(System.getProperty(PROPERTY, "replace").toUpperCase(Locale.ROOT));

    /** The share of the hull's width the weapon takes where the turret stood, and on top of the turret. */
    private static final float FILL_SHARE = .62f;
    private static final float OVERLAY_SHARE = .4f;

    private VehicleGunArt() { }

    static Mode mode() { return MODE; }

    /** The weapon's drawing; null where the hull fires its own gun, the mode is stock, or the weapon has none. */
    static LayeredSpriteCache sprite(LayeredMechAssets assets, MechWeaponComponent weapon) {
        if (assets == null || weapon == null || MODE == Mode.STOCK) return null;
        LayeredSpriteCache sprite = LayeredMechComposer.weaponSprite(assets, weapon.appearanceSelector);
        return sprite != null && sprite.sprite != null ? sprite : null;
    }

    /**
     * Draws {@code gun} centred at the screen point, its barrel toward {@code facingDeg}, as wide as a share of the hull
     * ({@code hullWidthPx}): neither a turret's frame nor its length says how big the turret is, since the frame is
     * the whole cell and a cannon's barrel makes most of the length.
     */
    static void draw(DrawList out, LayeredSpriteCache gun, float x, float y, float hullWidthPx, float facingDeg,
                     float alpha) {
        float scale = hullWidthPx * (MODE == Mode.OVERLAY ? OVERLAY_SHARE : FILL_SHARE) / gun.pxWidth;
        out.addSprite(RenderLayer.CONVOY, gun.sprite, x, y, gun.pxWidth * scale, gun.pxHeight * scale, facingDeg,
                1f, 1f, 1f, alpha);
    }
}
