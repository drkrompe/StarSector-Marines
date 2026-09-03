package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredWeaponFamily;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loaded modular armor family and shared equipment layers for one unit type. */
public final class LayeredUnitAssets {
    public final LayeredSpriteCache body;
    public final LayeredSpriteCache head;
    public final LayeredSpriteCache foot;
    public final LayeredSpriteCache foreClaw;
    public final LayeredSpriteCache rifle;
    public final LayeredSpriteCache laserGun;
    public final LayeredSpriteCache smg;
    public final LayeredSpriteCache dmr;
    public final LayeredSpriteCache rocketLauncher;
    public final LayeredSpriteCache antiMaterielRifle;
    public final LayeredSpriteCache muzzleFlash;
    private final EnumMap<LayeredWeaponFamily, EnumMap<EquipmentGrade, LayeredSpriteCache>>
            gradeWeapons = new EnumMap<>(LayeredWeaponFamily.class);
    private final Map<String, LayeredSpriteCache> specialEquipment = new LinkedHashMap<>();

    public LayeredUnitAssets(LayeredSpriteCache body, LayeredSpriteCache head,
                             LayeredSpriteCache foot, LayeredSpriteCache foreClaw,
                             LayeredSpriteCache rifle,
                             LayeredSpriteCache laserGun,
                             LayeredSpriteCache smg,
                             LayeredSpriteCache dmr,
                             LayeredSpriteCache rocketLauncher,
                             LayeredSpriteCache antiMaterielRifle,
                             LayeredSpriteCache muzzleFlash,
                             LayeredSpriteCache surplusRifle,
                             LayeredSpriteCache masterworkDmr) {
        this.body = body;
        this.head = head;
        this.foot = foot;
        this.foreClaw = foreClaw;
        this.rifle = rifle;
        this.laserGun = laserGun;
        this.smg = smg;
        this.dmr = dmr;
        this.rocketLauncher = rocketLauncher;
        this.antiMaterielRifle = antiMaterielRifle;
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

    public void registerSpecialEquipment(String equipmentId, LayeredSpriteCache sprite) {
        if (equipmentId != null && sprite != null) specialEquipment.put(equipmentId, sprite);
    }

    public LayeredSpriteCache specialEquipment(String equipmentId) {
        return specialEquipment.get(equipmentId);
    }

    /**
     * Every image this family can draw, for a consumer that has to see all of
     * them rather than pick one — the {@link UnitAtlas}'s layout, which needs
     * the whole set before any of it is drawn.
     */
    public List<LayeredSpriteCache> layers() {
        List<LayeredSpriteCache> all = new ArrayList<>(List.of());
        Collections.addAll(all, body, head, foot, foreClaw, rifle, laserGun, smg, dmr,
                rocketLauncher, antiMaterielRifle, muzzleFlash);
        for (EnumMap<EquipmentGrade, LayeredSpriteCache> variants : gradeWeapons.values()) {
            all.addAll(variants.values());
        }
        all.addAll(specialEquipment.values());
        return all;
    }

    private void registerGrade(LayeredWeaponFamily family, EquipmentGrade grade,
                               LayeredSpriteCache sprite) {
        if (sprite == null) return;
        gradeWeapons.computeIfAbsent(family, ignored -> new EnumMap<>(EquipmentGrade.class))
                .put(grade, sprite);
    }
}
