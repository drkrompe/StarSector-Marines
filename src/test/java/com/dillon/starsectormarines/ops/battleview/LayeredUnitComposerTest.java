package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.LayerPose;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.LayerTransform;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.marine.EquipmentLayerDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentPresentationDef;
import com.dillon.starsectormarines.marine.SpecialUsePose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayeredUnitComposerTest {
    @Test
    void optionalShouldersPreserveLegacyLayerOrder() {
        assertEquals(List.of("foot", "foot", "rifle", "body", "head"),
                draw(assets(false), null, 0).stream().map(Layer::id).toList());
        assertEquals(List.of("foot", "foot", "rifle", "body", "left", "right", "head"),
                draw(assets(true), null, 0).stream().map(Layer::id).toList());
    }

    @Test
    void overShoulderPrimaryDrawsAboveBothArmorShells() {
        assertEquals(List.of("foot", "foot", "body", "left", "right", "rifle", "head"),
                draw(assets(true), null, LayeredAppearance.FLAG_WEAPON_OVER_SHOULDER)
                        .stream().map(Layer::id).toList());
    }

    @Test
    void authoredJointUsesWorldBearingWithoutHelmetLook() {
        LayerTransform joint = new LayerTransform("left-shoulder", "left", -.3f, .2f,
                1f, 1f, 12f, .5f, .5f, 4, true);
        List<Layer> layers = draw(assets(true), new LayerPose(Map.of("left-shoulder", joint)), 0);
        Layer left = layers.stream().filter(layer -> layer.id().equals("left")).findFirst().orElseThrow();
        assertEquals(102f, left.angle(), .001f);
        assertEquals(70f, left.x(), .001f);
        assertEquals(155f, left.y(), .001f);
        assertEquals(59f, layers.get(layers.size() - 1).angle(), .001f);
    }

    @Test
    void specialOcclusionStraddlesTorsoAndShoulders() {
        for (EquipmentLayerDef.Occlusion occlusion : EquipmentLayerDef.Occlusion.values()) {
            LayeredUnitAssets assets = assets(true);
            assets.registerSpecialEquipment("special", sprite("special"));
            EquipmentLayerDef.State state = new EquipmentLayerDef.State(0f, 0f, 0f, occlusion);
            EquipmentLayerDef carrier = new EquipmentLayerDef("special", 1f, 1f, .5f, .5f,
                    state, state, 0f, occlusion, true, true);
            SpecialEquipmentPresentationDef presentation = new SpecialEquipmentPresentationDef(
                    null, null, SpecialUsePose.SHOULDER_LAUNCHER, carrier,
                    null, null, null, null, null);
            SpecialEquipmentDef special = new SpecialEquipmentDef("special", "Special", null,
                    null, null, null, null, null, 0, null, presentation,
                    null, null, null, null, null);
            List<String> layers = new ArrayList<>();
            LayeredUnitComposer.emit((sprite, x, y, width, height, angle, r, g, b, alpha) ->
                            layers.add(sprite.sourcePath),
                    assets, assets.head, null, true, special, EquipmentGrade.SERVICE,
                    0f, 0f, 150f, 0f, 0f, 0f, 0f,
                    LayeredAppearance.POSE_ROCKET_FIRE, 0, 1f);
            assertEquals(occlusion == EquipmentLayerDef.Occlusion.UNDER_BODY
                    ? List.of("foot", "foot", "special", "body", "left", "right", "head")
                    : List.of("foot", "foot", "body", "left", "right", "special", "head"), layers);
        }
    }

    @Test
    void atlasInventoryIncludesIndependentShells() {
        LayeredUnitAssets assets = assets(true);
        assertTrue(assets.layers().contains(assets.shoulderLeft));
        assertTrue(assets.layers().contains(assets.shoulderRight));
    }

    private static List<Layer> draw(LayeredUnitAssets assets, LayerPose pose, int flags) {
        List<Layer> layers = new ArrayList<>();
        LayeredUnitComposer.emit((sprite, x, y, width, height, angle, r, g, b, alpha) ->
                        layers.add(new Layer(sprite.sourcePath, x, y, angle)),
                assets, assets.head, null, true, null, EquipmentGrade.SERVICE,
                100f, 200f, 150f, 90f, -31f, 0f, 0f,
                LayeredAppearance.POSE_IDLE, flags, 1f, pose);
        return layers;
    }

    private static LayeredUnitAssets assets(boolean shoulders) {
        return new LayeredUnitAssets(sprite("body"), sprite("head"), sprite("foot"), null,
                sprite("rifle"), sprite("laser"), sprite("smg"), sprite("dmr"),
                sprite("rocket"), sprite("amr"), sprite("flash"), null, null,
                shoulders ? sprite("left") : null, shoulders ? sprite("right") : null);
    }

    private static LayeredSpriteCache sprite(String id) {
        return LayeredSpriteCache.headless(id, 150, 95);
    }

    private record Layer(String id, float x, float y, float angle) {}
}
