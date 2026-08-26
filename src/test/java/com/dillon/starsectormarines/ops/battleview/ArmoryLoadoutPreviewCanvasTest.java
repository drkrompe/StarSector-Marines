package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.FireTeamBillet;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.SpecialEquipmentPresentationDef;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmoryLoadoutPreviewCanvasTest {

    private static final SpecialEquipmentPresentationDef.Preview USING =
            new SpecialEquipmentPresentationDef.Preview("using", 0.75f);

    @Test
    void specialPreviewStateUsesTheBattlefieldPoseVocabulary() {
        assertEquals(LayeredAppearance.POSE_ROCKET_AIM,
                ArmoryLoadoutPreviewComposer.poseForDef(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID), USING));
        assertEquals(LayeredAppearance.POSE_AMR_AIM,
                ArmoryLoadoutPreviewComposer.poseForDef(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID), USING));
        assertEquals(LayeredAppearance.POSE_SMOKE_THROW,
                ArmoryLoadoutPreviewComposer.poseForDef(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID), USING));
        assertEquals(LayeredAppearance.POSE_SATCHEL_PLANT,
                ArmoryLoadoutPreviewComposer.poseForDef(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID), USING));
        assertEquals(LayeredAppearance.POSE_IDLE,
                ArmoryLoadoutPreviewComposer.poseForDef(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID),
                        new SpecialEquipmentPresentationDef.Preview("carried", 1f)));
    }

    @Test
    void everyCampaignArmorMapsToItsBattlefieldLayerFamily() {
        for (MarineArmorPattern armor : MarineArmorPattern.values()) {
            LayeredArmorFamily family = ArmoryLoadoutPreviewComposer.armorFamily(armor);
            assertEquals(armor.name(), family.name());
        }
    }

    @Test
    void selectedMarinePreviewCyclesTheAuthoredIdleClip() {
        HeadlessArmoryPreviewRenderer renderer =
                new HeadlessArmoryPreviewRenderer(Path.of("mod"));

        var settled = ArmoryLoadoutPreviewComposer.idlePose(
                renderer.assets().unitLayerLayouts(), 0f);
        var breathing = ArmoryLoadoutPreviewComposer.idlePose(
                renderer.assets().unitLayerLayouts(), 0.9f);
        var looped = ArmoryLoadoutPreviewComposer.idlePose(
                renderer.assets().unitLayerLayouts(), 1.8f);

        assertNotNull(settled);
        assertNotNull(breathing);
        assertNotEquals(settled.layer("head"), breathing.layer("head"));
        assertEquals(settled.layer("head"), looped.layer("head"));
    }

    @Test
    void everyArmorCatalogEntryRendersThroughTheLiveLoadoutComposition() {
        HeadlessArmoryPreviewRenderer renderer =
                new HeadlessArmoryPreviewRenderer(Path.of("mod"));

        MarineArmorCatalogRegistry.installed().all().forEach(armor -> {
            BufferedImage image = renderer.render(new FireTeamBillet(
                    "Catalog proof", WeaponRegistry.STARTER_PRIMARY_ID,
                    EquipmentGrade.SERVICE, null, armor.id()));
            int opaquePixels = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) >>> 24) != 0) opaquePixels++;
                }
            }
            assertTrue(opaquePixels > 1_000, armor.id());
        });
    }
}
