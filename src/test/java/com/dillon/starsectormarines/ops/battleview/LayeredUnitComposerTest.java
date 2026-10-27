package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.LayerPose;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.LayerTransform;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.marine.EquipmentLayerDef;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentPresentationDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentPresentationDef.HandsetLayer;
import com.dillon.starsectormarines.marine.SpecialUsePose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                null, null, null, 0f, 0f, 0, null, null, null, carrier);
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
                null, null, null, 0f, 0f, 0, null, null, null, carrier);
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
                null, null, null, 0f, 0f, 0, null, null, null, carrier);
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
                    null, SpecialUsePose.SHOULDER_LAUNCHER, carrier,
                    null, null, null, null, null);
            SpecialEquipmentDef special = new SpecialEquipmentDef("special", "Special", null,
                    null, null, null, null, null, 0, null, presentation,
                    null, null, null);
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
    void aPackedLauncherRidesTheBackUntilItIsUsedThenTheHeldOneReplacesIt() {
        LayeredUnitAssets assets = assets(false);
        assets.registerSpecialEquipment("special", sprite("launcher"));
        assets.registerSpecialEquipment(SpecialEquipmentPresentationDef.packedAssetKey("special"),
                sprite("packed"));
        EquipmentLayerDef.State held = new EquipmentLayerDef.State(0f, 0f, 0f,
                EquipmentLayerDef.Occlusion.UNDER_BODY);
        EquipmentLayerDef carrier = new EquipmentLayerDef("launcher", 1f, 1f, .5f, .5f,
                held, held, 0f, EquipmentLayerDef.Occlusion.OVER_BODY, false, true);
        EquipmentLayerDef.State slung = new EquipmentLayerDef.State(0f, -.03f, -35f,
                EquipmentLayerDef.Occlusion.OVER_BODY);
        EquipmentLayerDef packed = new EquipmentLayerDef("packed", .3f, .96f, .5f, .5f,
                slung, slung, 0f, null, true, false, EquipmentLayerDef.Attachment.BODY);
        SpecialEquipmentPresentationDef presentation = new SpecialEquipmentPresentationDef(
                null, SpecialUsePose.SHOULDER_LAUNCHER, carrier,
                null, null, null, null, null, null, packed);
        SpecialEquipmentDef special = new SpecialEquipmentDef("special", "Special", null,
                null, null, null, null, null, 0, null, presentation,
                null, null, null);
        for (int pose : new int[]{LayeredAppearance.POSE_IDLE, LayeredAppearance.POSE_FIRING}) {
            List<String> layers = new ArrayList<>();
            LayeredUnitComposer.emit((sprite, x, y, width, height, angle, r, g, b, alpha) ->
                            layers.add(sprite.sourcePath),
                    assets, assets.head, null, true, special, EquipmentGrade.SERVICE,
                    0f, 0f, 150f, 0f, 0f, 0f, 0f, pose, 0, 1f);
            assertEquals(List.of("foot", "foot", "rifle", "body", "packed", "head"), layers,
                    "carried, the launcher is slung over the body under the head (pose " + pose + ")");
        }
        List<String> using = new ArrayList<>();
        LayeredUnitComposer.emit((sprite, x, y, width, height, angle, r, g, b, alpha) ->
                        using.add(sprite.sourcePath),
                assets, assets.head, null, true, special, EquipmentGrade.SERVICE,
                0f, 0f, 150f, 0f, 0f, 0f, 1f, LayeredAppearance.POSE_ROCKET_AIM, 0, 1f);
        assertTrue(using.contains("launcher"), "aimed, the held launcher is drawn: " + using);
        assertFalse(using.contains("packed"), "aimed, nothing is left on the back: " + using);
    }

    @Test
    void aTreatingMedicHoldsTheKitInPlaceOfTheirOwnSpecial() {
        LayeredUnitAssets assets = assets(false);
        assets.registerSpecialEquipment("special", sprite("special"));
        assets.registerSpecialEquipment(TreatmentKitLayer.ASSET_KEY, sprite("kit"));
        EquipmentLayerDef.State state = new EquipmentLayerDef.State(0f, 0f, 0f,
                EquipmentLayerDef.Occlusion.OVER_BODY);
        EquipmentLayerDef carrier = new EquipmentLayerDef("special", 1f, 1f, .5f, .5f,
                state, state, 0f, EquipmentLayerDef.Occlusion.OVER_BODY, false, true);
        SpecialEquipmentPresentationDef presentation = new SpecialEquipmentPresentationDef(
                null, SpecialUsePose.PLANT, carrier, null, null, null, null, null);
        SpecialEquipmentDef special = new SpecialEquipmentDef("special", "Special", null,
                null, null, null, null, null, 0, null, presentation,
                null, null, null);
        List<String> layers = new ArrayList<>();
        LayeredUnitComposer.emit((sprite, x, y, width, height, angle, r, g, b, alpha) ->
                        layers.add(sprite.sourcePath),
                assets, assets.head, null, true, special, EquipmentGrade.SERVICE,
                0f, 0f, 150f, 0f, 0f, 0f, LayeredAppearance.TREATING_PHASE,
                LayeredAppearance.POSE_SATCHEL_PLANT, LayeredAppearance.FLAG_TREATING, 1f);
        assertTrue(layers.contains("kit"), "the kit is drawn while treating: " + layers);
        assertFalse(layers.contains("special"), "the medic's own special is not planted: " + layers);
    }

    @Test
    void radioPackStaysOnTheBackInEveryPoseAndDrawsOverTheBodyUnderTheHead() {
        int[] poses = {LayeredAppearance.POSE_IDLE, LayeredAppearance.POSE_AIMED,
                LayeredAppearance.POSE_FIRING, LayeredAppearance.POSE_SATCHEL_PLANT};
        for (int pose : poses) {
            for (int flags : new int[]{0, LayeredAppearance.FLAG_MOVING}) {
                List<String> ids = radioDraw(pose, flags, null);
                assertEquals(List.of("foot", "foot", "rifle", "body", "left", "right", "pack", "head"),
                        ids, "pose " + pose + " flags " + flags);
            }
        }
    }

    @Test
    void handsetAndCordAppearOnlyWhileCallingAndCordJoinsPackToHand() {
        LayerTransform handset = new LayerTransform("handset", "handset", -.24f, .06f,
                1f, 1f, 6f, .5f, .5f, 36, true);
        LayerPose calling = new LayerPose(Map.of("handset", handset));
        // Raised to the ear: cord runs under the head, handset is the topmost layer.
        assertEquals(List.of("foot", "foot", "rifle", "body", "left", "right", "pack", "cord",
                        "head", "handset"),
                radioDraw(LayeredAppearance.POSE_SATCHEL_PLANT, 0, calling));
        // The same authored layer is ignored outside the call, and hidden frames draw nothing.
        assertEquals(List.of("foot", "foot", "rifle", "body", "left", "right", "pack", "head"),
                radioDraw(LayeredAppearance.POSE_IDLE, 0, calling));
        LayerTransform hidden = new LayerTransform("handset", "handset", -.24f, .06f,
                1f, 1f, 6f, .5f, .5f, 36, false);
        assertEquals(List.of("foot", "foot", "rifle", "body", "left", "right", "pack", "head"),
                radioDraw(LayeredAppearance.POSE_SATCHEL_PLANT, 0,
                        new LayerPose(Map.of("handset", hidden))));
    }

    @Test
    void cordSpansFromHandsetPortToPackPort() {
        LayerTransform handset = new LayerTransform("handset", "handset", -.24f, .06f,
                1f, 1f, 0f, .5f, .5f, 36, true);
        List<float[]> cord = new ArrayList<>();
        radioEmit(LayeredAppearance.POSE_SATCHEL_PLANT, 0,
                new LayerPose(Map.of("handset", handset)), (sprite, x, y, width, height, angle,
                        r, g, b, alpha) -> {
                    if (sprite.sourcePath.equals("cord")) cord.add(new float[]{x, y, width, height, angle});
                });
        assertEquals(1, cord.size());
        float[] c = cord.get(0);
        // Handset centred at (-.24,.06) sw from the actor at (0,0), facing north, 100 px per sw:
        // port (.5,.99) is .49 of the 40px height below centre.
        float handX = -24f;
        float handY = 6f - .49f * 40f;
        // The pack centre sits .17 behind the body pivot (-.12): (0,-29); its port is (.07,.37).
        float packX = (.07f - .5f) * 40f;
        float packY = -29f + (.5f - .37f) * 42f;
        assertEquals((handX + packX) / 2f, c[0], .01f);
        assertEquals((handY + packY) / 2f, c[1], .01f);
        assertEquals((float) Math.hypot(packX - handX, packY - handY), c[3], .01f);
        assertEquals(5f, c[2], .001f);
    }

    private static List<String> radioDraw(int pose, int flags, LayerPose authored) {
        List<String> ids = new ArrayList<>();
        radioEmit(pose, flags, authored, (sprite, x, y, width, height, angle, r, g, b, alpha) ->
                ids.add(sprite.sourcePath));
        return ids;
    }

    private static void radioEmit(int pose, int flags, LayerPose authored,
                                  LayeredUnitComposer.SpriteEmitter out) {
        LayeredUnitAssets assets = assets(true);
        assets.registerSpecialEquipment("radio", sprite("pack"));
        assets.registerHandset("radio", sprite("handset"), sprite("cord"));
        EquipmentLayerDef.State state = new EquipmentLayerDef.State(0f, -.17f, 0f,
                EquipmentLayerDef.Occlusion.OVER_BODY);
        EquipmentLayerDef carrier = new EquipmentLayerDef("pack", .4f, .42f, .5f, .5f,
                state, state, 0f, null, true, false, EquipmentLayerDef.Attachment.BODY);
        HandsetLayer handset = new HandsetLayer("handset", "cord", .16f, .4f, .05f,
                .5f, .99f, .07f, .37f, false);
        SpecialEquipmentPresentationDef presentation = new SpecialEquipmentPresentationDef(
                null, SpecialUsePose.PLANT, carrier, null, null, null, null, null, handset);
        SpecialEquipmentDef radio = new SpecialEquipmentDef("radio", "Radio", null,
                null, null, null, null, null, 0, null, presentation,
                null, null, null);
        LayeredUnitComposer.emit(out, assets, assets.head, null, true, radio,
                EquipmentGrade.SERVICE, 0f, 0f, 100f, 0f, 0f, 0f, 0f, pose, flags, 1f, authored);
    }

    @Test
    void atlasInventoryIncludesIndependentShells() {
        LayeredUnitAssets assets = assets(true);
        assertTrue(assets.layers().contains(assets.shoulderLeft));
        assertTrue(assets.layers().contains(assets.shoulderRight));
    }

    @Test
    void atAStationTheArmsReplaceTheRifleUnderTheTorsoOnTheBodysGrid() {
        LayeredUnitAssets assets = assets(false);
        assets.registerStationArms(LayeredSpriteCache.headless("arm-left", 150, 190),
                LayeredSpriteCache.headless("arm-right", 150, 190));
        List<Layer> working = draw(assets, null, LayeredAppearance.FLAG_AT_STATION);
        assertEquals(List.of("foot", "foot", "arm-left", "arm-right", "body", "head"),
                working.stream().map(Layer::id).toList());
        // The body canvas is the arm canvas's bottom rows: the arm's centre sits
        // half the extra height forward of the body's, at shoulder width 150 px.
        Layer body = working.get(4), arm = working.get(2);
        assertEquals((190 - 95) / 2f, (float) Math.hypot(arm.x() - body.x(), arm.y() - body.y()), 0.01f);

        assertEquals(List.of("foot", "foot", "rifle", "body", "head"),
                draw(assets, null, 0).stream().map(Layer::id).toList(), "no arms away from a station");
        assertEquals(List.of("foot", "foot", "rifle", "body", "head"),
                draw(assets(false), null, LayeredAppearance.FLAG_AT_STATION).stream().map(Layer::id).toList(),
                "a family without station arms keeps its rifle");
    }

    @Test
    void aWorkingHandSweepsAboutAnElbowThatStaysInTheSleeve() {
        LayeredUnitAssets assets = assets(false);
        assets.registerStationArms(LayeredSpriteCache.headless("arm-left", 150, 190),
                LayeredSpriteCache.headless("arm-right", 150, 190));
        Layer still = armAt(assets, 0f), swept = armAt(assets, 0.25f);
        assertEquals(7f, Math.abs(swept.angle() - still.angle()), 0.01f, "the forearm turns");
        // The elbow is 0.1 of the width in and halfway down the body rows: 60 px
        // left of the layer's centre and 47.5 px behind it on a 150x190 arm over a 95-row body.
        float[] a = elbow(still), b = elbow(swept);
        assertEquals(a[0], b[0], 0.01f);
        assertEquals(a[1], b[1], 0.01f);
    }

    private static Layer armAt(LayeredUnitAssets assets, float phase) {
        List<Layer> layers = new ArrayList<>();
        LayeredUnitComposer.emit((sprite, x, y, width, height, angle, r, g, b, alpha) ->
                        layers.add(new Layer(sprite.sourcePath, x, y, angle)),
                assets, assets.head, null, true, null, EquipmentGrade.SERVICE,
                100f, 200f, 150f, 90f, -31f, 0f, phase,
                LayeredAppearance.POSE_IDLE, LayeredAppearance.FLAG_AT_STATION, 1f, null);
        return layers.stream().filter(layer -> layer.id().equals("arm-left")).findFirst().orElseThrow();
    }

    private static float[] elbow(Layer arm) {
        double rad = Math.toRadians(arm.angle());
        float x = -60f, y = -47.5f;
        return new float[]{arm.x() + (float) (x * Math.cos(rad) - y * Math.sin(rad)),
                arm.y() + (float) (x * Math.sin(rad) + y * Math.cos(rad))};
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
