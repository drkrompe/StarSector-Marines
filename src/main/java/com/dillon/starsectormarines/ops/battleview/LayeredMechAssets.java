package com.dillon.starsectormarines.ops.battleview;

import java.util.Arrays;
import java.util.List;

/** Complete modular texture set shared by the live mech family. */
public final class LayeredMechAssets {
    public final LayeredSpriteCache chassis;
    public final LayeredSpriteCache socketedChassis;
    public final LayeredSpriteCache houndChassis;
    public final LayeredSpriteCache siroccoChassis;
    public final LayeredSpriteCache foot;
    public final LayeredSpriteCache thighBone;
    public final LayeredSpriteCache chaingunArm;
    public final LayeredSpriteCache linearCannon;
    public final LayeredSpriteCache heavyCannon;
    public final LayeredSpriteCache srmPod;
    public final LayeredSpriteCache lrmPod;
    public final LayeredSpriteCache shoulderLaser;
    public final LayeredSpriteCache pulseLaserArm;
    public final LayeredSpriteCache bastionAutocannon;
    public final LayeredSpriteCache demolitionCannon;
    public final LayeredSpriteCache thermalLance;
    public final LayeredSpriteCache musterAutogun;
    public final LayeredSpriteCache quarryBreakerCannon;
    public final LayeredSpriteCache pioneerRocketCradle;
    public final LayeredSpriteCache muzzleFlash;

    public LayeredMechAssets(LayeredSpriteCache chassis, LayeredSpriteCache socketedChassis,
                             LayeredSpriteCache houndChassis,
                             LayeredSpriteCache siroccoChassis,
                             LayeredSpriteCache foot, LayeredSpriteCache thighBone,
                             LayeredSpriteCache chaingunArm,
                             LayeredSpriteCache linearCannon, LayeredSpriteCache heavyCannon,
                             LayeredSpriteCache srmPod,
                             LayeredSpriteCache lrmPod,
                             LayeredSpriteCache shoulderLaser,
                             LayeredSpriteCache pulseLaserArm,
                             LayeredSpriteCache bastionAutocannon,
                             LayeredSpriteCache demolitionCannon,
                             LayeredSpriteCache thermalLance,
                             LayeredSpriteCache musterAutogun,
                             LayeredSpriteCache quarryBreakerCannon,
                             LayeredSpriteCache pioneerRocketCradle,
                             LayeredSpriteCache muzzleFlash) {
        this.chassis = chassis;
        this.socketedChassis = socketedChassis;
        this.houndChassis = houndChassis;
        this.siroccoChassis = siroccoChassis;
        this.foot = foot;
        this.thighBone = thighBone;
        this.chaingunArm = chaingunArm;
        this.linearCannon = linearCannon;
        this.heavyCannon = heavyCannon;
        this.srmPod = srmPod;
        this.lrmPod = lrmPod;
        this.shoulderLaser = shoulderLaser;
        this.pulseLaserArm = pulseLaserArm;
        this.bastionAutocannon = bastionAutocannon;
        this.demolitionCannon = demolitionCannon;
        this.thermalLance = thermalLance;
        this.musterAutogun = musterAutogun;
        this.quarryBreakerCannon = quarryBreakerCannon;
        this.pioneerRocketCradle = pioneerRocketCradle;
        this.muzzleFlash = muzzleFlash;
    }

    /**
     * Every image this set can draw, for a consumer that has to see all of them
     * rather than pick one — the {@link UnitAtlas}'s layout, which needs the
     * whole set before any of it is drawn.
     */
    public List<LayeredSpriteCache> layers() {
        return Arrays.asList(chassis, socketedChassis, houndChassis, siroccoChassis,
                foot, thighBone, chaingunArm, linearCannon, heavyCannon, srmPod, lrmPod,
                shoulderLaser, pulseLaserArm, bastionAutocannon, demolitionCannon,
                thermalLance, musterAutogun, quarryBreakerCannon, pioneerRocketCradle,
                muzzleFlash);
    }
}
