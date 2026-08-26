package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.AnimationClip;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.AnimationDriver;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.LayerPose;
import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument;
import com.dillon.starsectormarines.tools.layerauthoring.CompositionRenderer;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentPresentationDef.LayerClips;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
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

    @Test
    void factionArmorMasteringScalesMixedBodyAndHelmetOverAnimation() throws Exception {
        UnitLayerLayouts layouts = load();
        LayerPose idle = layouts.clip("marine-line", "rifle", "idle").sample(0f);

        LayerPose mastered = layouts.applyArmorMastering(idle,
                LayeredArmorFamily.PALATINE, LayeredArmorFamily.SPECTER_HEAVY);

        assertEquals(idle.layer("body").scaleX(),
                mastered.layer("body").scaleX(), 0.000001f);
        assertEquals(idle.layer("head").scaleX() * 1.8f,
                mastered.layer("head").scaleX(), 0.000001f);
        assertEquals(idle.layer("head").scaleY() * 1.5f,
                mastered.layer("head").scaleY(), 0.000001f);
        assertEquals(idle.layer("head").offsetY() + 0.016f,
                mastered.layer("head").offsetY(), 0.000001f);
        assertEquals(idle.layer("primary"), mastered.layer("primary"));
    }

    @Test
    void everyFactionArmorMasterIsExposedAsAWorkbenchUnit() throws Exception {
        UnitLayerLayouts layouts = load();
        String[] ids = {
                "armor-master-aegis", "armor-master-palatine",
                "armor-master-furnace-line", "armor-master-reaver",
                "armor-master-specter-heavy", "armor-master-bulwark-heavy",
                "armor-master-reliquary-heavy", "armor-master-lions-mantle",
                "armor-master-foundry-breaker"
        };
        for (String id : ids) {
            AnimationClip clip = layouts.clip(id, "field-loadout", "idle");
            assertNotNull(clip, id);
            assertNotNull(clip.sample(0f).layer("body"), id);
            assertNotNull(clip.sample(0f).layer("head"), id);
        }
    }

    @Test
    void secondaryUseClipsCoverEveryExistingActionPose() throws Exception {
        UnitLayerLayouts layouts = load();

        AnimationClip rocketFire = layouts.clip("marine-line", "rocket", "firing");
        AnimationClip amrAim = layouts.clip("marine-line", "anti-materiel", "aiming");
        AnimationClip amrFire = layouts.clip("marine-line", "anti-materiel", "firing");
        AnimationClip smoke = layouts.clip("marine-line", "smoke", "throwing");
        AnimationClip satchel = layouts.clip("marine-line", "satchel", "planting");

        assertEquals(AnimationDriver.ACTION_PHASE, amrAim.driver());
        assertEquals(400, rocketFire.totalDurationMs());
        assertEquals(400, amrAim.totalDurationMs());
        assertEquals(400, amrFire.totalDurationMs());
        assertEquals(800, smoke.totalDurationMs());
        assertEquals(900, satchel.totalDurationMs());
        assertNotNull(amrFire.sample(0.5f).layer("special"));
        assertFalse(smoke.sample(0.5f).layer("primary").visible());
        assertEquals(45f, rocketFire.sample(1f).layer("rocket-launcher")
                .angleDegrees(), 0.000001f);
        assertEquals(45f, amrFire.sample(1f).layer("special")
                .angleDegrees(), 0.000001f);
        assertFalse(smoke.sample(1f).layer("special").visible());
        assertEquals(-0.08f, satchel.sample(1f).layer("special")
                .offsetY(), 0.000001f);
    }

    @Test
    void everyEquipmentAuthoredClipReferenceResolves() throws Exception {
        UnitLayerLayouts layouts = load();

        for (SpecialEquipmentDef equipment : SpecialEquipmentRegistry.installed().all()) {
            LayerClips references = equipment.presentation().layerClips();
            assertNotNull(layouts.clip("marine-line", references.variant(),
                    references.using()), equipment.id());
            if (references.firing() != null) {
                assertNotNull(layouts.clip("marine-line", references.variant(),
                        references.firing()), equipment.id());
            }
        }
    }

    private static UnitLayerLayouts load() throws Exception {
        Path path = Path.of("mod/data/appearance/unit-layer-layouts.appearance.json");
        return UnitLayerLayouts.parse(new JSONObject(Files.readString(path)));
    }
}
