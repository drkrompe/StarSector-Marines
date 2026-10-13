package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.LayerPose;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.LayerTransform;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.marine.EquipmentLayerDef;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
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
    void committedPodLaunchUsesIndependentBearingAndPreservesPrimaryLayers() {
        LayeredUnitAssets assets = assets(true);
        assets.registerIntegralSystem("system", sprite("mount"));
        EquipmentLayerDef.State carried = new EquipmentLayerDef.State(0f, 0f, 0f,
                EquipmentLayerDef.Occlusion.OVER_BODY);
        EquipmentLayerDef carrier = new EquipmentLayerDef("mount", .32f, .48f, .5f, .5f,
                carried, carried, 0f, null, true, false,
                EquipmentLayerDef.Attachment.LEFT_SHOULDER, .5f, 20f / 192f);
        IntegralSystemDef system = new IntegralSystemDef("system", "System", null,
                null, null, null, 0f, 0f, 0, null, null, null, null, null, carrier);
        List<Layer> layers = new ArrayList<>();
        LayeredUnitComposer.emit((sprite, x, y, width, height, angle, r, g, b, alpha) ->
                        layers.add(new Layer(sprite.sourcePath, x, y, angle)),
                assets, assets.head, null, true, null, EquipmentGrade.SERVICE,
                100f, 200f, 150f, 0f, 37f, 0f, .9f,
                LayeredAppearance.POSE_IDLE, 0, 1f, null, system,
                new LayeredUnitComposer.IntegralLaunch(85f, 220f, -90f, 0f));
        Layer mount = layers.stream().filter(l -> l.id().equals("mount")).findFirst().orElseThrow();
        assertEquals(-90f, mount.angle());
        assertEquals(85f - 72f * (0.5f - 20f / 192f), mount.x(), .001f);
        assertEquals(220f, mount.y(), .001f);
        assertTrue(layers.stream().anyMatch(l -> l.id().equals("rifle")));
        assertTrue(layers.stream().anyMatch(l -> l.id().equals("flash")));
    }

    @Test
    void integralMountFollowsActualShoulderBearingAndIgnoresHelmetLook() {
        LayeredUnitAssets assets = assets(true);
        LayerTransform shoulder = new LayerTransform("left-shoulder", "left", -.3f, .2f,
                2f, 1.5f, 90f, .5f, .5f, 4, true);
        LayerPose pose = new LayerPose(Map.of("left-shoulder", shoulder));
        Layer mount = integralDraw(assets, pose, 0f, 37f, 0f, 0).stream()
                .filter(layer -> layer.id().equals("mount")).findFirst().orElseThrow();
        // Parent pivot (70,155), with scaled local offsets rotated through 180 degrees.
        assertEquals(100f, mount.x(), .001f);
        assertEquals(150.5f, mount.y(), .001f);
        assertEquals(187f, mount.angle(), .001f);
        assertEquals(mount, integralDraw(assets, pose, 0f, -73f, .8f, 0).stream()
                .filter(layer -> layer.id().equals("mount")).findFirst().orElseThrow());
    }

    @Test
    void monolithicMountUsesScaledBodyAndRejectsOtherFamilyShoulderTrack() {
        LayerTransform body = new LayerTransform("body", "body", .1f, -.2f,
                2f, 3f, -90f, .5f, .5f, 3, true);
        LayerTransform foreign = new LayerTransform("left-shoulder", "left", 8f, 9f,
                7f, 7f, 37f, .5f, .5f, 4, true);
        LayerPose pose = new LayerPose(Map.of("body", body, "left-shoulder", foreign));
        Layer mount = integralDraw(assets(false), pose, 0f, 44f, 0f, 0).stream()
                .filter(layer -> layer.id().equals("mount")).findFirst().orElseThrow();
        assertEquals(130f - .234694f * 300f - 30f, mount.x(), .001f);
        assertEquals(215f + .150124f * 285f + 9f, mount.y(), .001f);
        assertEquals(7f, mount.angle(), .001f);
    }

    @Test
    void equippedHardwareIsStaticAndCoexistsWithForegroundPrimary() {
        LayeredUnitAssets assets = assets(true);
        List<Layer> first = integralDraw(assets, null, 1f, 0f, 0f,
                LayeredAppearance.FLAG_WEAPON_OVER_SHOULDER);
        List<Layer> later = integralDraw(assets, null, 1f, 0f, .7f,
                LayeredAppearance.FLAG_WEAPON_OVER_SHOULDER);
        assertEquals(List.of("foot", "foot", "body", "left", "right", "mount", "rifle", "head"),
                first.stream().map(Layer::id).toList());
        assertEquals(first.stream().filter(layer -> layer.id().equals("mount")).toList(),
                later.stream().filter(layer -> layer.id().equals("mount")).toList());
    }

    @Test
    void integralUnderBodyOcclusionPlacesHardwareBehindTorso() {
        List<Layer> layers = integralDraw(assets(true), null, 0f, 0f, 0f, 0,
                EquipmentLayerDef.Occlusion.UNDER_BODY);
        assertEquals(List.of("foot", "foot", "mount", "rifle", "body", "left", "right", "head"),
                layers.stream().map(Layer::id).toList());
    }

    @Test
    void missingHardwareSpritePreservesExistingComposition() {
        LayeredUnitAssets assets = assets(true);
        EquipmentLayerDef.State state = new EquipmentLayerDef.State(0f, 0f, 0f,
                EquipmentLayerDef.Occlusion.OVER_BODY);
        EquipmentLayerDef carrier = new EquipmentLayerDef("unloaded", 1f, 1f, .5f, .5f,
                state, state, 0f, null, true, false);
        IntegralSystemDef system = new IntegralSystemDef("no-art", "No art", null,
                null, null, null, 0f, 0f, 0, null, null, null, null, null, carrier);
        List<String> ids = new ArrayList<>();
        LayeredUnitComposer.emit((sprite, x, y, width, height, angle, r, g, b, alpha) ->
                        ids.add(sprite.sourcePath), assets, assets.head, null, true, null,
                EquipmentGrade.SERVICE, 0f, 0f, 150f, 0f, 0f, 0f, 0f,
                LayeredAppearance.POSE_IDLE, 0, 1f, null, system);
        assertEquals(List.of("foot", "foot", "rifle", "body", "left", "right", "head"), ids);
    }

    private static List<Layer> integralDraw(LayeredUnitAssets assets, LayerPose pose,
                                           float recoil, float look, float phase, int flags) {
        return integralDraw(assets, pose, recoil, look, phase, flags,
                EquipmentLayerDef.Occlusion.OVER_BODY);
    }

    private static List<Layer> integralDraw(LayeredUnitAssets assets, LayerPose pose,
                                           float recoil, float look, float phase, int flags,
                                           EquipmentLayerDef.Occlusion occlusion) {
        assets.registerIntegralSystem("system", sprite("mount"));
        EquipmentLayerDef.State carried = new EquipmentLayerDef.State(-.1f, .02f, 7f,
                occlusion);
        EquipmentLayerDef.State using = new EquipmentLayerDef.State(4f, 5f, 80f,
                EquipmentLayerDef.Occlusion.UNDER_BODY);
        EquipmentLayerDef carrier = new EquipmentLayerDef("mount", .24f, .4f, .5f, .5f,
                carried, using, recoil, null, false, true, EquipmentLayerDef.Attachment.LEFT_SHOULDER);
        IntegralSystemDef system = new IntegralSystemDef("system", "System", null,
                null, null, null, 0f, 0f, 0, null, null, null, null, null, carrier);
        List<Layer> layers = new ArrayList<>();
        LayeredUnitComposer.emit((sprite, x, y, width, height, angle, r, g, b, alpha) ->
                        layers.add(new Layer(sprite.sourcePath, x, y, angle)),
                assets, assets.head, null, true, null, EquipmentGrade.SERVICE,
                100f, 200f, 150f, 90f, look, 0f, phase,
                LayeredAppearance.POSE_IDLE, flags, 1f, pose, system);
        return layers;
    }

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
