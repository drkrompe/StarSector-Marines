package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.LayerPose;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class UnitRenderServiceLayerPoseTest {

    @Test
    void secondaryDefinitionsSelectTheirAuthoredActionClips() throws Exception {
        UnitLayerLayouts layouts = UnitLayerLayouts.parse(new JSONObject(Files.readString(
                Path.of("mod/data/appearance/unit-layer-layouts.appearance.json"))));

        assertSpecial(layouts, MarineSecondary.ROCKET_LAUNCHER,
                LayeredAppearance.POSE_ROCKET_AIM, "rocket-launcher");
        assertSpecial(layouts, MarineSecondary.ANTI_MATERIEL_RIFLE,
                LayeredAppearance.POSE_AMR_FIRE, "special");
        assertSpecial(layouts, MarineSecondary.SMOKE_GRENADE,
                LayeredAppearance.POSE_SMOKE_THROW, "special");
        assertSpecial(layouts, MarineSecondary.SATCHEL_CHARGE,
                LayeredAppearance.POSE_SATCHEL_PLANT, "special");
    }

    private static void assertSpecial(UnitLayerLayouts layouts,
                                      MarineSecondary secondary, int pose,
                                      String layerId) {
        LayerPose sampled = UnitRenderService.infantryPose(layouts, true, secondary,
                pose, 0f, 0.5f, 0);

        assertNotNull(sampled, secondary.name());
        assertNotNull(sampled.layer(layerId), secondary.name());
    }
}
