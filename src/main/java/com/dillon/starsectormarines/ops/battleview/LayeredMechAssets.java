package com.dillon.starsectormarines.ops.battleview;

import java.util.Arrays;
import java.util.List;

/** Complete modular texture set shared by the live mech family. */
public final class LayeredMechAssets {
    public final LayeredSpriteCache chassis;
    public final LayeredSpriteCache socketedChassis;
    public final LayeredSpriteCache houndChassis;
    public final LayeredSpriteCache wardenChassis;
    public final LayeredSpriteCache foot;
    public final LayeredSpriteCache thighBone;
    public final LayeredSpriteCache chaingunArm;
    public final LayeredSpriteCache hc10;
    public final LayeredSpriteCache hc20;
    public final LayeredSpriteCache srmPod;
    public final LayeredSpriteCache lrmPod;
    public final LayeredSpriteCache shoulderLaser;
    public final LayeredSpriteCache pulseLaserArm;
    public final LayeredSpriteCache ac6;
    public final LayeredSpriteCache dc5;
    public final LayeredSpriteCache thermalLance;
    public final LayeredSpriteCache ac6Qs;
    public final LayeredSpriteCache dc4Qs;
    public final LayeredSpriteCache srm3QsPod;
    public final LayeredSpriteCache lightMachineGun;
    public final LayeredSpriteCache lightPulseLaser;
    public final LayeredSpriteCache burstLaser;
    public final LayeredSpriteCache pdLaser;
    public final LayeredSpriteCache heavyPdLaser;
    public final LayeredSpriteCache mediumBurstLaser;
    public final LayeredSpriteCache lightBurstLaser;
    public final LayeredSpriteCache muzzleFlash;

    public LayeredMechAssets(LayeredSpriteCache chassis, LayeredSpriteCache socketedChassis,
                             LayeredSpriteCache houndChassis,
                             LayeredSpriteCache wardenChassis,
                             LayeredSpriteCache foot, LayeredSpriteCache thighBone,
                             LayeredSpriteCache chaingunArm,
                             LayeredSpriteCache hc10, LayeredSpriteCache hc20,
                             LayeredSpriteCache srmPod,
                             LayeredSpriteCache lrmPod,
                             LayeredSpriteCache shoulderLaser,
                             LayeredSpriteCache pulseLaserArm,
                             LayeredSpriteCache ac6,
                             LayeredSpriteCache dc5,
                             LayeredSpriteCache thermalLance,
                             LayeredSpriteCache ac6Qs,
                             LayeredSpriteCache dc4Qs,
                             LayeredSpriteCache srm3QsPod,
                             LayeredSpriteCache lightMachineGun,
                             LayeredSpriteCache lightPulseLaser,
                             LayeredSpriteCache burstLaser,
                             LayeredSpriteCache pdLaser,
                             LayeredSpriteCache heavyPdLaser,
                             LayeredSpriteCache mediumBurstLaser,
                             LayeredSpriteCache lightBurstLaser,
                             LayeredSpriteCache muzzleFlash) {
        this.chassis = chassis;
        this.socketedChassis = socketedChassis;
        this.houndChassis = houndChassis;
        this.wardenChassis = wardenChassis;
        this.foot = foot;
        this.thighBone = thighBone;
        this.chaingunArm = chaingunArm;
        this.hc10 = hc10;
        this.hc20 = hc20;
        this.srmPod = srmPod;
        this.lrmPod = lrmPod;
        this.shoulderLaser = shoulderLaser;
        this.pulseLaserArm = pulseLaserArm;
        this.ac6 = ac6;
        this.dc5 = dc5;
        this.thermalLance = thermalLance;
        this.ac6Qs = ac6Qs;
        this.dc4Qs = dc4Qs;
        this.srm3QsPod = srm3QsPod;
        this.lightMachineGun = lightMachineGun;
        this.lightPulseLaser = lightPulseLaser;
        this.burstLaser = burstLaser;
        this.pdLaser = pdLaser;
        this.heavyPdLaser = heavyPdLaser;
        this.mediumBurstLaser = mediumBurstLaser;
        this.lightBurstLaser = lightBurstLaser;
        this.muzzleFlash = muzzleFlash;
    }

    /**
     * Every image this set can draw, for a consumer that has to see all of them
     * rather than pick one — the {@link UnitAtlas}'s layout, which needs the
     * whole set before any of it is drawn.
     */
    public List<LayeredSpriteCache> layers() {
        return Arrays.asList(chassis, socketedChassis, houndChassis, wardenChassis,
                foot, thighBone, chaingunArm, hc10, hc20, srmPod, lrmPod,
                shoulderLaser, pulseLaserArm, ac6, dc5,
                thermalLance, ac6Qs, dc4Qs, srm3QsPod,
                lightMachineGun, lightPulseLaser, burstLaser, pdLaser, heavyPdLaser, mediumBurstLaser,
                lightBurstLaser, muzzleFlash);
    }
}
