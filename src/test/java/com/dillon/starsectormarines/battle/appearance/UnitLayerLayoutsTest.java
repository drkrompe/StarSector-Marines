package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.AnimationClip;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.AnimationDriver;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.LayerPose;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.LayerTransform;
import com.dillon.starsectormarines.tools.layerauthoring.AuthoringDocument;
import com.dillon.starsectormarines.tools.layerauthoring.CompositionRenderer;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentPresentationDef.LayerClips;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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
        LayerPose walking = layouts.clip("marine-line", "rifle", "walking").sample(0.25f);

        LayerPose mastered = layouts.applyArmorMastering(walking,
                LayeredArmorFamily.PALATINE, LayeredArmorFamily.SPECTER_HEAVY);

        assertEquals(1.0f, mastered.layer("body").scaleX(), 0.000001f);
        assertEquals(1.0f, mastered.layer("head").scaleX(), 0.000001f);
        assertEquals(1.0f, mastered.layer("head").scaleY(), 0.000001f);
        assertEquals(walking.layer("head").offsetY() - 0.033625f,
                mastered.layer("head").offsetY(), 0.000001f);
        assertEquals(walking.layer("head").angleDegrees(), mastered.layer("head").angleDegrees());
        assertEquals(walking.layer("primary"), mastered.layer("primary"));
        assertEquals(walking.layer("left-foot"), mastered.layer("left-foot"));
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
    void specterShouldersAlternateStrideAndBraceIndependently() throws Exception {
        UnitLayerLayouts layouts = load();
        AnimationClip walking = layouts.clip("marine-line", "rifle", "walking");
        LayerPose leftStride = walking.sample(0f);
        LayerPose rightStride = walking.sample(0.5f);

        assertEquals(-7f, leftStride.layer("left-shoulder").angleDegrees()
                - leftStride.layer("body").angleDegrees(), 0.000001f);
        assertEquals(7f, rightStride.layer("left-shoulder").angleDegrees()
                - rightStride.layer("body").angleDegrees(), 0.000001f);
        assertEquals(7f, leftStride.layer("right-shoulder").angleDegrees()
                - leftStride.layer("body").angleDegrees(), 0.000001f);
        LayerPose braced = layouts.clip("marine-line", "anti-materiel", "firing").sample(0f);
        assertNotEquals(braced.layer("left-shoulder").angleDegrees(),
                braced.layer("right-shoulder").angleDegrees());
        LayerPose recoil = layouts.clip("marine-line", "rifle", "firing").sample(65f / 265f);
        assertEquals(8f, recoil.layer("right-shoulder").angleDegrees()
                - recoil.layer("body").angleDegrees(), 0.00001f);
    }

    @Test
    void shoulderMasteringFollowsTorsoRegistrationAndPreservesWeaponAndHelmet() throws Exception {
        Path path = Path.of("mod/data/appearance/unit-layer-layouts.appearance.json");
        JSONObject json = new JSONObject(Files.readString(path));
        var units = json.getJSONArray("units");
        for (int i = 0; i < units.length(); i++) {
            JSONObject unit = units.getJSONObject(i);
            if (!unit.getString("id").equals("armor-master-specter-heavy")) continue;
            var layers = unit.getJSONArray("variants").getJSONObject(0)
                    .getJSONArray("animations").getJSONObject(0)
                    .getJSONArray("frames").getJSONObject(0).getJSONArray("layers");
            for (int j = 0; j < layers.length(); j++) {
                JSONObject layer = layers.getJSONObject(j);
                if (layer.getString("id").equals("body")) layer.put("angleDegrees", 90);
                if (layer.getString("id").endsWith("-shoulder")) {
                    JSONArray offset = layer.getJSONArray("offset");
                    double x = offset.getDouble(0);
                    double y = offset.getDouble(1) + 0.06;
                    layer.put("offset", new JSONArray().put(-y).put(x - 0.06));
                    layer.put("angleDegrees", 90);
                }
            }
        }
        UnitLayerLayouts layouts = UnitLayerLayouts.parse(json);
        LayerPose original = layouts.clip("marine-line", "rifle", "walking").sample(0f);
        LayerPose mastered = layouts.applyArmorMastering(original,
                LayeredArmorFamily.SPECTER_HEAVY, LayeredArmorFamily.PALATINE);
        LayerTransform shoulder = original.layer("left-shoulder");
        LayerTransform originalBody = original.layer("body");
        LayerTransform body = mastered.layer("body");

        assertEquals(body.offsetX() - (shoulder.offsetY() - originalBody.offsetY()),
                mastered.layer("left-shoulder").offsetX(), 0.00001f);
        assertEquals(body.offsetY() + shoulder.offsetX() - originalBody.offsetX(),
                mastered.layer("left-shoulder").offsetY(), 0.00001f);
        assertEquals(shoulder.angleDegrees() + 90,
                mastered.layer("left-shoulder").angleDegrees(), 0.00001f);
        assertEquals(original.layer("primary"), mastered.layer("primary"));
        LayerPose helmetControl = layouts.applyArmorMastering(original,
                LayeredArmorFamily.PALATINE, LayeredArmorFamily.PALATINE);
        assertEquals(helmetControl.layer("head"), mastered.layer("head"));
        assertEquals(original.layer("left-foot"), mastered.layer("left-foot"));
    }

    @Test
    void rotatingOnlyMasterTorsoPreservesTheAbsoluteNeutralShoulderAssembly() throws Exception {
        Path path = Path.of("mod/data/appearance/unit-layer-layouts.appearance.json");
        JSONObject json = new JSONObject(Files.readString(path));
        JSONArray units = json.getJSONArray("units");
        for (int i = 0; i < units.length(); i++) {
            JSONObject unit = units.getJSONObject(i);
            if (!unit.getString("id").equals("armor-master-specter-heavy")) continue;
            JSONArray layers = unit.getJSONArray("variants").getJSONObject(0)
                    .getJSONArray("animations").getJSONObject(0)
                    .getJSONArray("frames").getJSONObject(0).getJSONArray("layers");
            for (int j = 0; j < layers.length(); j++) {
                JSONObject layer = layers.getJSONObject(j);
                if (layer.getString("id").equals("body")) layer.put("angleDegrees", 90);
            }
        }
        UnitLayerLayouts layouts = UnitLayerLayouts.parse(json);
        LayerPose neutral = layouts.clip("marine-line", "rifle", "idle").sample(0f);
        LayerPose mastered = layouts.applyArmorMastering(neutral,
                LayeredArmorFamily.SPECTER_HEAVY, LayeredArmorFamily.PALATINE);
        LayerPose master = layouts.clip("armor-master-specter-heavy", "field-loadout", "idle")
                .sample(0f);
        for (String id : new String[] {"left-shoulder", "right-shoulder"}) {
            assertEquals(master.layer(id).offsetX(), mastered.layer(id).offsetX(), 0.000001);
            assertEquals(master.layer(id).offsetY(), mastered.layer(id).offsetY(), 0.000001);
            assertEquals(master.layer(id).angleDegrees(), mastered.layer(id).angleDegrees(), 0.000001);
        }
    }

    @Test
    void specterShoulderWorkbenchPreviewMatchesRuntimeMastering() throws Exception {
        UnitLayerLayouts layouts = load();
        AuthoringDocument authoring = AuthoringDocument.load(Path.of("."));
        var specter = authoring.units().stream()
                .filter(unit -> unit.id().equals("armor-master-specter-heavy"))
                .findFirst().orElseThrow();
        var walking = specter.previewVariants().stream()
                .filter(variant -> variant.id().equals("rifle"))
                .flatMap(variant -> variant.animations().stream())
                .filter(animation -> animation.id().equals("walking"))
                .findFirst().orElseThrow();
        CompositionRenderer renderer = new CompositionRenderer(Path.of("."));
        for (float phase : new float[] {0f, 0.25f, 0.5f}) {
            var editor = renderer.composeAnimationPreview(specter,
                    renderer.samplePhase(walking, phase));
            LayerPose runtime = layouts.applyArmorMastering(
                    layouts.clip("marine-line", "rifle", "walking").sample(phase),
                    LayeredArmorFamily.SPECTER_HEAVY, LayeredArmorFamily.SPECTER_HEAVY);
            for (String id : new String[] {"left-shoulder", "right-shoulder"}) {
                var editorLayer = editor.layers().stream()
                        .filter(layer -> layer.id().equals(id)).findFirst().orElseThrow();
                LayerTransform runtimeLayer = runtime.layer(id);
                assertEquals(runtimeLayer.offsetX(), editorLayer.offsetX(), 0.000001);
                assertEquals(runtimeLayer.offsetY(), editorLayer.offsetY(), 0.000001);
                assertEquals(runtimeLayer.angleDegrees(), editorLayer.angleDegrees(), 0.000001);
                assertEquals(runtimeLayer.pivotX(), editorLayer.pivotX(), 0.000001);
                assertEquals(runtimeLayer.pivotY(), editorLayer.pivotY(), 0.000001);
            }
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
