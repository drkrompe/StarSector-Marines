package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.LayerPose;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UnitRenderServiceLayerPoseTest {

    @Test
    void secondaryDefinitionsSelectTheirAuthoredActionClips() throws Exception {
        UnitLayerLayouts layouts = UnitLayerLayouts.parse(new JSONObject(Files.readString(
                Path.of("mod/data/appearance/unit-layer-layouts.appearance.json"))));

        assertSpecial(layouts, SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID),
                LayeredAppearance.POSE_ROCKET_AIM, "rocket-launcher");
        assertSpecial(layouts, SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID),
                LayeredAppearance.POSE_AMR_FIRE, "special");
        assertSpecial(layouts, SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SMOKE_GRENADE_ID),
                LayeredAppearance.POSE_SMOKE_THROW, "special");
        assertSpecial(layouts, SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID),
                LayeredAppearance.POSE_SATCHEL_PLANT, "special");
    }

    @Test
    void secondaryEntrySettlesOutOfThePreservedStridePose() throws Exception {
        UnitLayerLayouts layouts = UnitLayerLayouts.parse(new JSONObject(Files.readString(
                Path.of("mod/data/appearance/unit-layer-layouts.appearance.json"))));
        float locomotionPhase = 0.25f;
        LayerPose stride = layouts.clip("marine-line", "rifle", "walking")
                .sample(locomotionPhase);

        LayerPose entry = UnitRenderService.infantryPoseDef(layouts, true,
                SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID), LayeredAppearance.POSE_AMR_AIM,
                locomotionPhase, 0f, LayeredAppearance.FLAG_ACTION_FROM_MOVING);
        LayerPose settled = UnitRenderService.infantryPoseDef(layouts, true,
                SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID), LayeredAppearance.POSE_AMR_AIM,
                locomotionPhase, LayeredAppearance.ACTION_ENTRY_BLEND_PHASE,
                LayeredAppearance.FLAG_ACTION_FROM_MOVING);

        assertEquals(stride.layer("body").angleDegrees(),
                entry.layer("body").angleDegrees(), 0.000001f);
        assertEquals(stride.layer("left-foot").offsetY(),
                entry.layer("left-foot").offsetY(), 0.000001f);
        assertNotNull(entry.layer("special"),
                "the destination action keeps its equipment-specific layer");
        assertEquals(layouts.clip("marine-line", "anti-materiel", "aiming")
                        .sample(LayeredAppearance.ACTION_ENTRY_BLEND_PHASE)
                        .layer("body").angleDegrees(),
                settled.layer("body").angleDegrees(), 0.000001f);
    }

    @Test
    void movingPrimaryActionKeepsDistanceDrivenStrideFeet() throws Exception {
        UnitLayerLayouts layouts = UnitLayerLayouts.parse(new JSONObject(Files.readString(
                Path.of("mod/data/appearance/unit-layer-layouts.appearance.json"))));
        float locomotionPhase = 0.125f;
        float actionPhase = 0.5f;
        LayerPose stride = layouts.clip("marine-line", "rifle", "walking")
                .sample(locomotionPhase);
        LayerPose aimed = layouts.clip("marine-line", "rifle", "aiming")
                .sample(actionPhase);

        LayerPose movingAim = UnitRenderService.infantryPoseDef(layouts, true, null,
                LayeredAppearance.POSE_AIMED, locomotionPhase, actionPhase,
                LayeredAppearance.FLAG_MOVING);

        assertEquals(stride.layer("left-foot"), movingAim.layer("left-foot"));
        assertEquals(stride.layer("right-foot"), movingAim.layer("right-foot"));
        assertEquals(aimed.layer("body"), movingAim.layer("body"),
                "primary action retains upper-body authority");
        assertEquals(aimed.layer("primary"), movingAim.layer("primary"));
        assertNotEquals(aimed.layer("left-foot"), movingAim.layer("left-foot"),
                "moving aim must not collapse to planted action feet");
    }

    private static void assertSpecial(UnitLayerLayouts layouts,
                                      SpecialEquipmentDef secondary, int pose,
                                      String layerId) {
        LayerPose sampled = UnitRenderService.infantryPoseDef(layouts, true, secondary,
                pose, 0f, 0.5f, 0);

        assertNotNull(sampled, secondary.id());
        assertNotNull(sampled.layer(layerId), secondary.id());
    }
}
