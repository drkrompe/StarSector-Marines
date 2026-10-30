package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredWeaponFamily;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.marine.SpecialEquipmentPresentationDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentPresentationDef.HandsetLayer;

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
    /** Optional independent armor shells; absent for monolithic armor families. */
    public final LayeredSpriteCache shoulderLeft;
    public final LayeredSpriteCache shoulderRight;
    public final LayeredSpriteCache foot;
    public final LayeredSpriteCache foreClaw;
    public final LayeredSpriteCache rifle;
    public final LayeredSpriteCache laserGun;
    public final LayeredSpriteCache smg;
    public final LayeredSpriteCache dmr;
    public final LayeredSpriteCache srm1;
    public final LayeredSpriteCache antiMaterielRifle;
    public final LayeredSpriteCache muzzleFlash;
    private final EnumMap<LayeredWeaponFamily, EnumMap<EquipmentGrade, LayeredSpriteCache>>
            gradeWeapons = new EnumMap<>(LayeredWeaponFamily.class);
    private final Map<String, LayeredSpriteCache> specialEquipment = new LinkedHashMap<>();
    private final Map<String, LayeredSpriteCache> integralSystems = new LinkedHashMap<>();
    /** Lying-body poses in pose order; empty for a family whose fallen lie some other way. */
    private final List<LayeredSpriteCache> downed = new ArrayList<>();
    /** Forearms and hands out in front, drawn while at a console; null for a family without them. */
    private LayeredSpriteCache stationArmLeft, stationArmRight;

    public LayeredUnitAssets(LayeredSpriteCache body, LayeredSpriteCache head,
                             LayeredSpriteCache foot, LayeredSpriteCache foreClaw,
                             LayeredSpriteCache rifle,
                             LayeredSpriteCache laserGun,
                             LayeredSpriteCache smg,
                             LayeredSpriteCache dmr,
                             LayeredSpriteCache srm1,
                             LayeredSpriteCache antiMaterielRifle,
                             LayeredSpriteCache muzzleFlash,
                             LayeredSpriteCache surplusRifle,
                             LayeredSpriteCache masterworkDmr) {
        this(body, head, foot, foreClaw, rifle, laserGun, smg, dmr,
                srm1, antiMaterielRifle, muzzleFlash, surplusRifle,
                masterworkDmr, null, null);
    }

    public LayeredUnitAssets(LayeredSpriteCache body, LayeredSpriteCache head,
                             LayeredSpriteCache foot, LayeredSpriteCache foreClaw,
                             LayeredSpriteCache rifle, LayeredSpriteCache laserGun,
                             LayeredSpriteCache smg, LayeredSpriteCache dmr,
                             LayeredSpriteCache srm1,
                             LayeredSpriteCache antiMaterielRifle,
                             LayeredSpriteCache muzzleFlash,
                             LayeredSpriteCache surplusRifle,
                             LayeredSpriteCache masterworkDmr,
                             LayeredSpriteCache shoulderLeft,
                             LayeredSpriteCache shoulderRight) {
        this.shoulderLeft = shoulderLeft;
        this.shoulderRight = shoulderRight;
        this.body = body;
        this.head = head;
        this.foot = foot;
        this.foreClaw = foreClaw;
        this.rifle = rifle;
        this.laserGun = laserGun;
        this.smg = smg;
        this.dmr = dmr;
        this.srm1 = srm1;
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
     * Registers an item's hand-held part and its cord beside its carried layer, so the
     * atlas inventory ({@link #layers()}) sees them with every other equipment sprite.
     */
    public void registerHandset(String equipmentId, LayeredSpriteCache handset,
                                LayeredSpriteCache cord) {
        registerSpecialEquipment(HandsetLayer.assetKey(equipmentId), handset);
        registerSpecialEquipment(HandsetLayer.cordAssetKey(equipmentId), cord);
    }

    public LayeredSpriteCache handset(String equipmentId) {
        return specialEquipment.get(HandsetLayer.assetKey(equipmentId));
    }

    /** The item stowed on the wearer while not in use, or null when it has no packed art. */
    public LayeredSpriteCache packed(String equipmentId) {
        return specialEquipment.get(SpecialEquipmentPresentationDef.packedAssetKey(equipmentId));
    }

    public LayeredSpriteCache handsetCord(String equipmentId) {
        return specialEquipment.get(HandsetLayer.cordAssetKey(equipmentId));
    }

    public void registerIntegralSystem(String systemId, LayeredSpriteCache sprite) {
        if (systemId != null && sprite != null) integralSystems.put(systemId, sprite);
    }

    public LayeredSpriteCache integralSystem(String systemId) {
        return integralSystems.get(systemId);
    }

    /** Lying-body pose count a family with downed art ships ({@code downed-0..3.png}). */
    public static final int DOWNED_POSES = 4;

    /** Appends the next lying-body pose; poses are registered in pose order. */
    public void registerDowned(LayeredSpriteCache pose) {
        if (pose != null) downed.add(pose);
    }

    /**
     * The lying body for a corpse's death-pose roll, wrapped onto the poses this family has,
     * or null when it has none and the corpse is laid down by its rig or its unit type's corpse sheet.
     */
    public void registerStationArms(LayeredSpriteCache left, LayeredSpriteCache right) {
        if (left == null || right == null) return;
        stationArmLeft = left;
        stationArmRight = right;
    }

    /**
     * The forearm-and-hand layer for one side, drawn while the actor works a
     * fixture with its hands, or null when this family has none. It shares the
     * body layer's grid, extended forward: the body canvas is its lower half.
     */
    public LayeredSpriteCache stationArm(boolean left) {
        return left ? stationArmLeft : stationArmRight;
    }

    public LayeredSpriteCache downed(int poseIndex) {
        return downed.isEmpty() ? null : downed.get(Math.floorMod(poseIndex, downed.size()));
    }

    /**
     * Every image this family can draw, for a consumer that has to see all of
     * them rather than pick one — the {@link UnitAtlas}'s layout, which needs
     * the whole set before any of it is drawn.
     */
    public List<LayeredSpriteCache> layers() {
        List<LayeredSpriteCache> all = new ArrayList<>(List.of());
        Collections.addAll(all, body, head, foot, foreClaw, shoulderLeft, shoulderRight, rifle, laserGun, smg, dmr,
                srm1, antiMaterielRifle, muzzleFlash);
        for (EnumMap<EquipmentGrade, LayeredSpriteCache> variants : gradeWeapons.values()) {
            all.addAll(variants.values());
        }
        all.addAll(specialEquipment.values());
        all.addAll(integralSystems.values());
        all.addAll(downed);
        if (stationArmLeft != null) Collections.addAll(all, stationArmLeft, stationArmRight);
        return all;
    }

    private void registerGrade(LayeredWeaponFamily family, EquipmentGrade grade,
                               LayeredSpriteCache sprite) {
        if (sprite == null) return;
        gradeWeapons.computeIfAbsent(family, ignored -> new EnumMap<>(EquipmentGrade.class))
                .put(grade, sprite);
    }
}
