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
    public final LayeredSpriteCache ll3;
    public final LayeredSpriteCache pl2Ap;
    public final LayeredSpriteCache ac6;
    public final LayeredSpriteCache dc5;
    public final LayeredSpriteCache thermalLance;
    public final LayeredSpriteCache ac6Qs;
    public final LayeredSpriteCache dc4Qs;
    public final LayeredSpriteCache srm3QsPod;
    public final LayeredSpriteCache lightMachineGun;
    public final LayeredSpriteCache bl1;
    public final LayeredSpriteCache pl3;
    public final LayeredSpriteCache bl2;
    public final LayeredSpriteCache pl3Sweep;
    public final LayeredSpriteCache pl2;
    public final LayeredSpriteCache pl1;
    public final LayeredSpriteCache muzzleFlash;
    /**
     * Active protection's launchers and sensor panels on each chassis, in that chassis sprite's registration (null when
     * not loaded): bare fittings every livery shares.
     */
    public final ActiveProtection activeProtection;

    /** Active protection's overlay per chassis; any may be null, and that chassis then draws none. */
    public record ActiveProtection(LayeredSpriteCache bulwark, LayeredSpriteCache hound, LayeredSpriteCache warden) {
        public static final ActiveProtection NONE = new ActiveProtection(null, null, null);
    }

    public LayeredMechAssets(LayeredSpriteCache chassis, LayeredSpriteCache socketedChassis,
                             LayeredSpriteCache houndChassis,
                             LayeredSpriteCache wardenChassis,
                             LayeredSpriteCache foot, LayeredSpriteCache thighBone,
                             LayeredSpriteCache chaingunArm,
                             LayeredSpriteCache hc10, LayeredSpriteCache hc20,
                             LayeredSpriteCache srmPod,
                             LayeredSpriteCache lrmPod,
                             LayeredSpriteCache ll3,
                             LayeredSpriteCache pl2Ap,
                             LayeredSpriteCache ac6,
                             LayeredSpriteCache dc5,
                             LayeredSpriteCache thermalLance,
                             LayeredSpriteCache ac6Qs,
                             LayeredSpriteCache dc4Qs,
                             LayeredSpriteCache srm3QsPod,
                             LayeredSpriteCache lightMachineGun,
                             LayeredSpriteCache bl1,
                             LayeredSpriteCache pl3,
                             LayeredSpriteCache bl2,
                             LayeredSpriteCache pl3Sweep,
                             LayeredSpriteCache pl2,
                             LayeredSpriteCache pl1,
                             LayeredSpriteCache muzzleFlash) {
        this(chassis, socketedChassis, houndChassis, wardenChassis, foot, thighBone, chaingunArm, hc10, hc20,
                srmPod, lrmPod, ll3, pl2Ap, ac6, dc5, thermalLance, ac6Qs, dc4Qs, srm3QsPod, lightMachineGun,
                bl1, pl3, bl2, pl3Sweep, pl2, pl1, muzzleFlash, ActiveProtection.NONE);
    }

    public LayeredMechAssets(LayeredSpriteCache chassis, LayeredSpriteCache socketedChassis,
                             LayeredSpriteCache houndChassis,
                             LayeredSpriteCache wardenChassis,
                             LayeredSpriteCache foot, LayeredSpriteCache thighBone,
                             LayeredSpriteCache chaingunArm,
                             LayeredSpriteCache hc10, LayeredSpriteCache hc20,
                             LayeredSpriteCache srmPod,
                             LayeredSpriteCache lrmPod,
                             LayeredSpriteCache ll3,
                             LayeredSpriteCache pl2Ap,
                             LayeredSpriteCache ac6,
                             LayeredSpriteCache dc5,
                             LayeredSpriteCache thermalLance,
                             LayeredSpriteCache ac6Qs,
                             LayeredSpriteCache dc4Qs,
                             LayeredSpriteCache srm3QsPod,
                             LayeredSpriteCache lightMachineGun,
                             LayeredSpriteCache bl1,
                             LayeredSpriteCache pl3,
                             LayeredSpriteCache bl2,
                             LayeredSpriteCache pl3Sweep,
                             LayeredSpriteCache pl2,
                             LayeredSpriteCache pl1,
                             LayeredSpriteCache muzzleFlash,
                             ActiveProtection activeProtection) {
        this.activeProtection = activeProtection != null ? activeProtection : ActiveProtection.NONE;
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
        this.ll3 = ll3;
        this.pl2Ap = pl2Ap;
        this.ac6 = ac6;
        this.dc5 = dc5;
        this.thermalLance = thermalLance;
        this.ac6Qs = ac6Qs;
        this.dc4Qs = dc4Qs;
        this.srm3QsPod = srm3QsPod;
        this.lightMachineGun = lightMachineGun;
        this.bl1 = bl1;
        this.pl3 = pl3;
        this.bl2 = bl2;
        this.pl3Sweep = pl3Sweep;
        this.pl2 = pl2;
        this.pl1 = pl1;
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
                ll3, pl2Ap, ac6, dc5,
                thermalLance, ac6Qs, dc4Qs, srm3QsPod,
                lightMachineGun, bl1, pl3, bl2, pl3Sweep, pl2,
                pl1, muzzleFlash, activeProtection.bulwark(), activeProtection.hound(),
                activeProtection.warden());
    }
}
