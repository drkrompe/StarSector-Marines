package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.SpecialEquipmentPresentationDef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArmoryLoadoutPreviewCanvasTest {

    private static final SpecialEquipmentPresentationDef.Preview USING =
            new SpecialEquipmentPresentationDef.Preview("using", 0.75f);

    @Test
    void specialPreviewStateUsesTheBattlefieldPoseVocabulary() {
        assertEquals(LayeredAppearance.POSE_ROCKET_AIM,
                ArmoryLoadoutPreviewComposer.poseFor(MarineSecondary.ROCKET_LAUNCHER, USING));
        assertEquals(LayeredAppearance.POSE_AMR_AIM,
                ArmoryLoadoutPreviewComposer.poseFor(MarineSecondary.ANTI_MATERIEL_RIFLE, USING));
        assertEquals(LayeredAppearance.POSE_SMOKE_THROW,
                ArmoryLoadoutPreviewComposer.poseFor(MarineSecondary.SMOKE_GRENADE, USING));
        assertEquals(LayeredAppearance.POSE_SATCHEL_PLANT,
                ArmoryLoadoutPreviewComposer.poseFor(MarineSecondary.SATCHEL_CHARGE, USING));
        assertEquals(LayeredAppearance.POSE_IDLE,
                ArmoryLoadoutPreviewComposer.poseFor(MarineSecondary.SATCHEL_CHARGE,
                        new SpecialEquipmentPresentationDef.Preview("carried", 1f)));
    }

    @Test
    void everyCampaignArmorMapsToItsBattlefieldLayerFamily() {
        for (MarineArmorPattern armor : MarineArmorPattern.values()) {
            LayeredArmorFamily family = ArmoryLoadoutPreviewComposer.armorFamily(armor);
            assertEquals(armor.name(), family.name());
        }
    }
}
