package com.dillon.starsectormarines.ops.battleview;

/** Complete modular texture set shared by the live mech family. */
public final class LayeredMechAssets {
    public final LayeredSpriteCache chassis;
    public final LayeredSpriteCache socketedChassis;
    public final LayeredSpriteCache houndChassis;
    public final LayeredSpriteCache siroccoChassis;
    public final LayeredSpriteCache foot;
    public final LayeredSpriteCache chaingunArm;
    public final LayeredSpriteCache linearCannon;
    public final LayeredSpriteCache srmPod;
    public final LayeredSpriteCache lrmPod;
    public final LayeredSpriteCache muzzleFlash;

    public LayeredMechAssets(LayeredSpriteCache chassis, LayeredSpriteCache socketedChassis,
                             LayeredSpriteCache houndChassis,
                             LayeredSpriteCache siroccoChassis,
                             LayeredSpriteCache foot, LayeredSpriteCache chaingunArm,
                             LayeredSpriteCache linearCannon, LayeredSpriteCache srmPod,
                             LayeredSpriteCache lrmPod, LayeredSpriteCache muzzleFlash) {
        this.chassis = chassis;
        this.socketedChassis = socketedChassis;
        this.houndChassis = houndChassis;
        this.siroccoChassis = siroccoChassis;
        this.foot = foot;
        this.chaingunArm = chaingunArm;
        this.linearCannon = linearCannon;
        this.srmPod = srmPod;
        this.lrmPod = lrmPod;
        this.muzzleFlash = muzzleFlash;
    }
}
