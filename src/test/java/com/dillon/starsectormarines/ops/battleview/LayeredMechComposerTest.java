package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayeredMechComposerTest {

    @Test
    void previewSinkReceivesCanonicalBulwarkLayerOrderAndScale() {
        LayeredMechAssets assets = MechLabDollCanvas.headlessAssets();
        List<Layer> layers = new ArrayList<>();
        MechVariant variant = MechVariant.BULWARK;

        LayeredMechComposer.emit((sprite, x, y, width, height, angle, alpha) ->
                        layers.add(new Layer(sprite.sourcePath, width, height)),
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

        assertEquals(1, layers.stream().filter("srm-pod.png"::equals).count());
    }

    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private record Layer(String path, float width, float height) { }
}
