package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.AnimationClip;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.AnimationDriver;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.LayerPose;
import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument;
import com.dillon.starsectormarines.tools.layerauthoring.CompositionRenderer;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UnitLayerLayoutsTest {

    @Test
    void walkingClipIsExplicitlyDrivenByProceduralLocomotionPhase() throws Exception {
        UnitLayerLayouts layouts = load();

        AnimationClip walking = layouts.clip("marine-line", "rifle", "walking");

        assertNotNull(walking);
        assertEquals(AnimationDriver.LOCOMOTION_PHASE, walking.driver());
        assertEquals(440, walking.totalDurationMs());
    }

    @Test
    void actionClipClampsAtItsAuthoredEndPose() throws Exception {
        AnimationClip aiming = load().clip("marine-line", "rifle", "aiming");

        assertNotNull(aiming);
        assertEquals(AnimationDriver.ACTION_PHASE, aiming.driver());
        assertFalse(aiming.loop());
        assertEquals(aiming.frames().get(aiming.frames().size() - 1).pose(),
                aiming.sample(1f));
    }

    @Test
    void runtimeAndEditorSampleTheSameDurationWeightedPhase() throws Exception {
        UnitLayerLayouts layouts = load();
        AnimationClip runtimeClip = layouts.clip("marine-line", "rifle", "walking");
        LayerPose runtimePose = runtimeClip.sample(0.25f);

        AuthoringDocument authoring = AuthoringDocument.load(Path.of("."));
        var editorClip = authoring.units().get(0).variants().get(0).animations().stream()
                .filter(animation -> animation.id().equals("walking"))
                .findFirst().orElseThrow();
        var editorPose = new CompositionRenderer(Path.of("."))
                .samplePhase(editorClip, 0.25);
        var editorLeftFoot = editorPose.layers().stream()
                .filter(layer -> layer.id().equals("left-foot"))
                .findFirst().orElseThrow();

        assertEquals(editorLeftFoot.offsetX(),
                runtimePose.layer("left-foot").offsetX(), 0.000001);
        assertEquals(editorLeftFoot.offsetY(),
                runtimePose.layer("left-foot").offsetY(), 0.000001);
        assertEquals(editorLeftFoot.angleDegrees(),
                runtimePose.layer("left-foot").angleDegrees(), 0.000001);
    }

    @Test
    void lightMechThighScaleIsSampledFromAuthoredWalkingClip() throws Exception {
        AnimationClip walking = load().clip("mech-hound", "field-loadout", "walking");

        LayerPose midpoint = walking.sample(0.25f);

        assertEquals(0.3365f, midpoint.layer("left-thigh").scaleY(), 0.000001f);
        assertEquals(118.25f, midpoint.layer("left-thigh").angleDegrees(), 0.00001f);
    }

    private static UnitLayerLayouts load() throws Exception {
        Path path = Path.of("mod/data/appearance/unit-layer-layouts.appearance.json");
        return UnitLayerLayouts.parse(new JSONObject(Files.readString(path)));
    }
}
