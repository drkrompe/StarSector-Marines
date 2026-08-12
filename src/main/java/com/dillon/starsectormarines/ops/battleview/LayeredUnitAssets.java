package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredWeaponFamily;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;

import java.util.EnumMap;

/** Loaded modular armor family and shared equipment layers for one unit type. */
public final class LayeredUnitAssets {
    public final LayeredSpriteCache body;
    public final LayeredSpriteCache head;
    public final LayeredSpriteCache foot;
    public final LayeredSpriteCache rifle;
    public final LayeredSpriteCache laserGun;
    public final LayeredSpriteCache smg;
    public final LayeredSpriteCache dmr;
    public final LayeredSpriteCache rocketLauncher;
    public final LayeredSpriteCache muzzleFlash;
    private final EnumMap<LayeredWeaponFamily, EnumMap<EquipmentGrade, LayeredSpriteCache>>
            gradeWeapons = new EnumMap<>(LayeredWeaponFamily.class);

    public LayeredUnitAssets(LayeredSpriteCache body, LayeredSpriteCache head,
                             LayeredSpriteCache foot, LayeredSpriteCache rifle,
                             LayeredSpriteCache laserGun,
                             LayeredSpriteCache smg,
                             LayeredSpriteCache dmr,
                             LayeredSpriteCache rocketLauncher,
                             LayeredSpriteCache muzzleFlash,
                             LayeredSpriteCache surplusRifle,
                             LayeredSpriteCache masterworkDmr) {
        this.body = body;
        this.head = head;
        this.foot = foot;
        this.rifle = rifle;
        this.laserGun = laserGun;
        this.smg = smg;
        this.dmr = dmr;
        this.rocketLauncher = rocketLauncher;
        this.muzzleFlash = muzzleFlash;
        registerGrade(LayeredWeaponFamily.RIFLE, EquipmentGrade.SURPLUS, surplusRifle);
        registerGrade(LayeredWeaponFamily.DMR, EquipmentGrade.MASTERWORK, masterworkDmr);
    }

    /** Exact grade art when authored, otherwise the family's neutral source. */
    public LayeredSpriteCache weapon(LayeredWeaponFamily family, EquipmentGrade grade) {
        EnumMap<EquipmentGrade, LayeredSpriteCache> variants = gradeWeapons.get(family);
        LayeredSpriteCache exact = variants != null && grade != null ? variants.get(grade) : null;
        if (exact != null) return exact;
        return switch (family) {
            case RIFLE -> rifle;
            case LASER_GUN -> laserGun;
            case SMG -> smg;
            case DMR -> dmr;
        };
    }

    private void registerGrade(LayeredWeaponFamily family, EquipmentGrade grade,
                               LayeredSpriteCache sprite) {
        if (sprite == null) return;
        gradeWeapons.computeIfAbsent(family, ignored -> new EnumMap<>(EquipmentGrade.class))
                .put(grade, sprite);
    }
}
