package com.dillon.starsectormarines.ops.battleview;

/** Loaded modular armor family and shared equipment layers for one unit type. */
public final class LayeredUnitAssets {
    public final LayeredSpriteCache body;
    public final LayeredSpriteCache head;
    public final LayeredSpriteCache foot;
    public final LayeredSpriteCache rifle;
    public final LayeredSpriteCache laserGun;
    public final LayeredSpriteCache rocketLauncher;
    public final LayeredSpriteCache muzzleFlash;

    public LayeredUnitAssets(LayeredSpriteCache body, LayeredSpriteCache head,
                             LayeredSpriteCache foot, LayeredSpriteCache rifle,
                             LayeredSpriteCache laserGun,
                             LayeredSpriteCache rocketLauncher,
                             LayeredSpriteCache muzzleFlash) {
        this.body = body;
        this.head = head;
        this.foot = foot;
        this.rifle = rifle;
        this.laserGun = laserGun;
        this.rocketLauncher = rocketLauncher;
        this.muzzleFlash = muzzleFlash;
    }
}
