package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayeredMechComposerTest {

    @Test
    void previewSinkReceivesCanonicalBulwarkLayerOrderAndScale() {
        LayeredMechAssets assets = MechLabDollCanvas.headlessAssets();
        List<Layer> layers = new ArrayList<>();
        MechVariant variant = MechVariant.BULWARK;

        LayeredMechComposer.emit((sprite, x, y, width, height, angle, alpha) ->
                        layers.add(new Layer(sprite.sourcePath, x, y, width, height, angle)),
                assets, 400f, 300f, 208f,
                0f, 0f, 0f, 0f, 0f, 0f, 0,
                variant.chassisAppearance, variant.arms.appearanceSelector,
                variant.leftShoulder.appearanceSelector,
                variant.rightShoulder.appearanceSelector, 1f);

        assertEquals(List.of(
                        "foot.png", "foot.png", "chaingun-arm.png", "chaingun-arm.png",
                        "chassis.png", "lrm-pod.png", "lrm-pod.png"),
                layers.stream().map(layer -> fileName(layer.path())).toList());
        assertEquals(208f, layers.get(4).width(), 0.001f);
        assertEquals(31f, layers.get(2).width(), 0.001f,
                "Bulwark arm width uses the battle compositor's half-width mount rule");
    }

    @Test
    void absentShoulderDoesNotCreatePreviewLayer() {
        LayeredMechAssets assets = MechLabDollCanvas.headlessAssets();
        List<String> layers = new ArrayList<>();
        MechVariant variant = MechVariant.HOUND;

        LayeredMechComposer.emit((sprite, x, y, width, height, angle, alpha) ->
                        layers.add(fileName(sprite.sourcePath)),
                assets, 400f, 300f, 208f,
                0f, 0f, 0f, 0f, 0f, 0f, 0,
                variant.chassisAppearance, variant.arms.appearanceSelector,
                variant.leftShoulder.appearanceSelector,
                LayeredMechAppearance.POD_NONE, 1f);

        assertEquals(List.of("foot.png", "foot.png", "thigh-bone.png", "thigh-bone.png"),
                layers.subList(0, 4),
                "the static fallback also keeps both thighs below every upper layer");
        assertEquals(1, layers.stream().filter("srm-pod.png"::equals).count());
    }

    @Test
    void walkingShiftsTheUpperMassButLeavesFeetPlanted() {
        LayeredMechAssets assets = MechLabDollCanvas.headlessAssets();
        List<Layer> layers = new ArrayList<>();
        MechVariant variant = MechVariant.BULWARK;

        LayeredMechComposer.emit((sprite, x, y, width, height, angle, alpha) ->
                        layers.add(new Layer(sprite.sourcePath, x, y, width, height, angle)),
                assets, 400f, 300f, 208f,
                0f, 0f, 0.40f, 0f, 0f, 0f,
                LayeredMechAppearance.FLAG_MOVING,
                variant.chassisAppearance, variant.arms.appearanceSelector,
                variant.leftShoulder.appearanceSelector,
                variant.rightShoulder.appearanceSelector, 1f);

        assertEquals(400f, (layers.get(0).x() + layers.get(1).x()) * 0.5f, 0.001f,
                "the planted feet remain centered on the physical actor position");
        assertEquals(400f + 0.035f * 208f, layers.get(4).x(), 0.001f,
                "the chassis shifts laterally over the supporting foot");
    }

    @Test
    void proceduralGaitUsesSolvedFootAnchorsYawAndWaist() {
        LayeredMechAssets assets = MechLabDollCanvas.headlessAssets();
        List<Layer> layers = new ArrayList<>();
        MechVariant variant = MechVariant.HOUND;
        LayeredMechComposer.GaitPose gait = new LayeredMechComposer.GaitPose(
                350f, 280f, 15f,
                445f, 285f, -12f,
                410f, 310f, 1f, 0f);

        LayeredMechComposer.emit((sprite, x, y, width, height, angle, alpha) ->
                        layers.add(new Layer(sprite.sourcePath, x, y, width, height, angle)),
                assets, 400f, 300f, 208f,
                0f, 0f, 0.4f, 0f, 0f, 0f,
                LayeredMechAppearance.FLAG_MOVING,
                variant.chassisAppearance, variant.arms.appearanceSelector,
                variant.leftShoulder.appearanceSelector,
                LayeredMechAppearance.POD_NONE, 1f, null, gait);

        assertEquals(List.of(
                        "foot.png", "foot.png", "thigh-bone.png", "thigh-bone.png",
                        "chaingun-arm.png", "chassis-hound.png", "srm-pod.png"),
                layers.stream().map(layer -> fileName(layer.path())).toList(),
                "feet and thighs must be fully emitted before the upper assembly");
        assertEquals(350f, layers.get(0).x(), 0.001f);
        assertEquals(280f, layers.get(0).y(), 0.001f);
        assertEquals(15f, layers.get(0).angle(), 0.001f);
        assertEquals(445f, layers.get(1).x(), 0.001f);
        assertEquals(-12f, layers.get(1).angle(), 0.001f);
        Layer chassis = layers.stream()
                .filter(layer -> fileName(layer.path()).equals("chassis-hound.png"))
                .findFirst().orElseThrow();
        assertEquals(410f, chassis.x(), 0.001f);
        assertEquals(310f, chassis.y(), 0.001f,
                "the complete upper assembly follows the solved waist position");
        assertEquals(0f, chassis.angle(), 0.001f,
                "the procedural waist replaces the old phase-authored chassis wobble");
        assertTrue(layers.get(0).width() > layers.get(1).width(),
                "swing lift has a subtle top-down scale cue");
    }

    @Test
    void dualChaingunFlashUsesOnlyTheSelectedPosedArm() {
        LayeredMechAssets assets = MechLabDollCanvas.headlessAssets();
        MechVariant variant = MechVariant.BULWARK;
        List<Layer> first = emitFlashingBulwark(assets, variant,
                LayeredMechAppearance.FLAG_CHAINGUN_FLASH);
        List<Layer> second = emitFlashingBulwark(assets, variant,
                LayeredMechAppearance.FLAG_CHAINGUN_FLASH
                        | LayeredMechAppearance.FLAG_SECONDARY_ARMS_MUZZLE);

        Layer firstFlash = first.stream()
                .filter(layer -> fileName(layer.path()).equals("marine-muzzle-flash.png"))
                .findFirst().orElseThrow();
        Layer secondFlash = second.stream()
                .filter(layer -> fileName(layer.path()).equals("marine-muzzle-flash.png"))
                .findFirst().orElseThrow();
        assertEquals(1, first.stream()
                .filter(layer -> fileName(layer.path()).equals("marine-muzzle-flash.png"))
                .count());
        assertEquals(400f - 0.37f * 208f, firstFlash.x(), 0.001f);
        assertEquals(400f + 0.37f * 208f, secondFlash.x(), 0.001f);
        assertEquals(300f + 0.39f * 208f, firstFlash.y(), 0.001f);
    }

    @Test
    void missileFlashUsesOnlyTheFiringShoulderPod() {
        LayeredMechAssets assets = MechLabDollCanvas.headlessAssets();
        MechVariant variant = MechVariant.BULWARK;
        List<Layer> layers = emitFlashingBulwark(assets, variant,
                LayeredMechAppearance.FLAG_SRM_FLASH
                        | LayeredMechAppearance.FLAG_LEFT_SHOULDER_FLASH);

        List<Layer> flashes = layers.stream()
                .filter(layer -> fileName(layer.path()).equals("marine-muzzle-flash.png"))
                .toList();
        assertEquals(1, flashes.size());
        assertEquals(400f - 0.40f * 208f, flashes.get(0).x(), 0.001f);
        assertEquals(300f + 0.16f * 208f, flashes.get(0).y(), 0.001f);
    }

    private static List<Layer> emitFlashingBulwark(LayeredMechAssets assets,
                                                    MechVariant variant, int flags) {
        List<Layer> layers = new ArrayList<>();
        LayeredMechComposer.emit((sprite, x, y, width, height, angle, alpha) ->
                        layers.add(new Layer(sprite.sourcePath, x, y, width, height, angle)),
                assets, 400f, 300f, 208f,
                0f, 0f, 0f, 0f, 0f, 0f, flags,
                variant.chassisAppearance, variant.arms.appearanceSelector,
                variant.leftShoulder.appearanceSelector,
                variant.rightShoulder.appearanceSelector, 1f);
        return layers;
    }

    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private record Layer(String path, float x, float y, float width, float height,
                         float angle) { }
}
