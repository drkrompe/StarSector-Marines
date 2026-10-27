package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.MountClass;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.spec.SpecSheet;
import com.dillon.starsectormarines.ui.spec.SpecSheetLayer;

import java.util.function.DoubleSupplier;

/**
 * The spec-sheet scene source for a marine primary: opens a {@link WeaponPreviewCanvas} for the
 * weapon and grade a weapon sheet names. One per document, holding that document's preview textures
 * until its screen is put away.
 */
public final class WeaponPreviewScenes implements SpecSheetLayer.SceneSource {

    private final ArmoryLoadoutPreviewComposer.Assets assets;
    private final Runnable release;

    /** The live game's source, loading through its own texture lease. */
    public WeaponPreviewScenes() {
        ArmoryPreviewAssets live = new ArmoryPreviewAssets();
        this.assets = live;
        this.release = live::release;
    }

    /** A source over supplied assets, such as a headless renderer's. */
    public WeaponPreviewScenes(ArmoryLoadoutPreviewComposer.Assets assets) {
        if (assets == null) throw new IllegalArgumentException("preview assets are required");
        this.assets = assets;
        this.release = () -> { };
    }

    @Override
    public CanvasProducer open(SpecSheet.Scene scene, DoubleSupplier seconds) {
        WeaponDef weapon = WeaponRegistry.installed().get(scene.subject());
        if (weapon == null || weapon.mount != MountClass.MARINE_PRIMARY) return null;
        return new WeaponPreviewCanvas(weapon, grade(scene.variant()), assets, seconds);
    }

    @Override
    public void release() {
        release.run();
    }

    private static EquipmentGrade grade(String variant) {
        for (EquipmentGrade grade : EquipmentGrade.values()) {
            if (grade.name().equals(variant)) return grade;
        }
        return EquipmentGrade.SERVICE;
    }
}
