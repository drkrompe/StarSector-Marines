package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.world.model.NightLighting;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.ContrailStyle;
import com.dillon.starsectormarines.render2d.ContrailTrail;
import com.dillon.starsectormarines.render2d.DrawCommand;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the headless review drain does with the presentation commands the production collection cannot give it:
 * a contrail ribbon drawn through the shared ribbon geometry, and an aircraft's engine plumes recognisable as a
 * named pass rather than an anonymous custom draw the drain has to skip.
 */
class ReviewDrainOverlayTest {

    private static final int W = 40;
    private static final int H = 20;
    private static final int PIXELS_W = 200;
    private static final int PIXELS_H = 100;

    private static HeadlessUiRenderer ui;

    @BeforeAll
    static void drain() {
        Path root = Path.of("mod").toAbsolutePath().normalize();
        ui = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(root),
                new HeadlessBattleSceneRenderer(root, true));
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    private static BattleCamera camera() {
        BattleCamera camera = new BattleCamera(W, H);
        camera.setViewport(0f, 0f, PIXELS_W, PIXELS_H, 5f);
        return camera;
    }

    private static RenderContext context(BattleSimulation sim) {
        return new RenderContext(sim, camera(), null, 1f, 0f, false,
                new HighlightOverlay(), new Selection(), BattleRenderHostProfile.EMBEDDED_SCENE);
    }

    private static final class OverlayPass implements BattleSceneHostPass, HeadlessBattleSceneRenderer.ReviewOverlayPass {
        private final BattleSimulation sim;
        private final DrawList overlay;
        private final EnumSet<RenderLayer> layers;

        OverlayPass(BattleSimulation sim, DrawList overlay) {
            this(sim, overlay, EnumSet.of(RenderLayer.SHOTS));
        }

        OverlayPass(BattleSimulation sim, DrawList overlay, EnumSet<RenderLayer> layers) {
            this.sim = sim;
            this.overlay = overlay;
            this.layers = layers;
        }

        @Override
        public BattleSceneFrame prepare(CanvasHostViewport viewport, float alphaMult) {
            return new BattleSceneFrame(context(sim), layers);
        }

        @Override
        public DrawList overlay(BattleCamera ignored) {
            return overlay;
        }

        @Override
        public void draw(CanvasHostViewport viewport, float alphaMult) {
            throw new IllegalStateException("headless only");
        }
    }

    private static DrawList contrailOverlay() {
        ContrailStyle style = new ContrailStyle(1f, 0.5f, 0.5f, 1f, 0.8f, 0.2f, 1f, 1f, 0.8f, 0.2f, 1f, 0.05f, false);
        ContrailTrail trail = new ContrailTrail(style, 16);
        for (float x = 4f; x <= 30f; x += 2f) trail.pushSample(x, 10f);
        DrawList list = new DrawList();
        list.addRibbon(RenderLayer.SHOTS, trail, 1f);
        return list;
    }

    private static int painted(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) count++;
            }
        }
        return count;
    }

    @Test
    void aContrailOverlayIsDrawnWhereTheTrailLies() {
        BattleSimulation sim = openSim();
        BufferedImage bare = ui.renderHostPass(new OverlayPass(sim, new DrawList()), PIXELS_W, PIXELS_H);
        BufferedImage trailed = ui.renderHostPass(new OverlayPass(sim, contrailOverlay()), PIXELS_W, PIXELS_H);

        assertEquals(0, painted(bare));
        // 26 cells at 5px, half a cell (2.5px) either side of the axis: a band some 130 by 5 pixels.
        assertTrue(painted(trailed) > 400, "ribbon painted " + painted(trailed) + " pixels");
        int axisRow = PIXELS_H - Math.round(10f * 5f);
        assertTrue((trailed.getRGB(70, axisRow) >>> 24) != 0, "nothing on the trail's axis");
    }

    @Test
    void theSameOverlayDrawsTheSamePixelsTwice() {
        BattleSimulation sim = openSim();
        BufferedImage first = ui.renderHostPass(new OverlayPass(sim, contrailOverlay()), PIXELS_W, PIXELS_H);
        BufferedImage second = ui.renderHostPass(new OverlayPass(sim, contrailOverlay()), PIXELS_W, PIXELS_H);

        for (int y = 0; y < PIXELS_H; y++) {
            for (int x = 0; x < PIXELS_W; x++) assertEquals(first.getRGB(x, y), second.getRGB(x, y));
        }
    }

    @Test
    void anAircraftsEnginePlumesAreANamedPassTheReviewDrainCanRecognise() {
        BattleSimulation sim = openSim();
        long craft = sim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.MARINE, 20f, 10f, 2f, 2f, 2f, 2f, 0f);
        sim.world().mission(craft).state = ShuttleState.INCOMING;
        sim.world().setAltitudeT(craft, 1f);
        sim.world().kinematics(craft).teleport(20f, 10f, 0f);
        SpriteAPI token = (SpriteAPI) Proxy.newProxyInstance(
                SpriteAPI.class.getClassLoader(), new Class<?>[]{SpriteAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
        Map<Airframe, ShuttleSpriteCache> hulls = new LinkedHashMap<>();
        hulls.put(ShuttleType.AEROSHUTTLE, new ShuttleSpriteCache(token, 1f, 64, 64));
        BattleSprites sprites = new BattleSprites() {
            @Override
            public Map<Airframe, ShuttleSpriteCache> airframeSprites() {
                return hulls;
            }
        };
        DrawList out = new DrawList();

        new ShuttleRenderSystem(sprites).collect(context(sim), out);

        assertEquals(1, enginePasses(out, RenderLayer.AIRCRAFT_LIGHTS));
        assertEquals(1, hullLightPasses(out));
        assertEquals(0, enginePasses(out, RenderLayer.SHUTTLES), "No duplicate engine draw beneath the night shade");
        assertTrue(RenderLayer.NIGHT_LIGHTING.ordinal() < RenderLayer.AIRCRAFT_LIGHTS.ordinal());
        assertTrue(RenderLayer.DECK_LIGHTING.ordinal() < RenderLayer.AIRCRAFT_LIGHTS.ordinal());

        var mission = sim.world().mission(craft);
        for (ShuttleState state : List.of(ShuttleState.LOADING, ShuttleState.PENDING, ShuttleState.GONE)) {
            mission.state = state;
            DrawList suppressed = new DrawList();
            new ShuttleRenderSystem(sprites).collect(context(sim), suppressed);
            assertEquals(0, enginePasses(suppressed, RenderLayer.AIRCRAFT_LIGHTS), state.name());
            assertEquals(0, hullLightPasses(suppressed), state.name());
        }
        mission.state = ShuttleState.INCOMING;
        mission.destroyed = true;
        DrawList destroyed = new DrawList();
        new ShuttleRenderSystem(sprites).collect(context(sim), destroyed);
        assertEquals(0, enginePasses(destroyed, RenderLayer.AIRCRAFT_LIGHTS));
        assertEquals(0, hullLightPasses(destroyed));
        sim.close();
    }

    private static int hullLightPasses(DrawList out) {
        int count = 0;
        for (int i = 0; i < out.count(RenderLayer.AIRCRAFT_LIGHTS); i++) {
            DrawCommand command = out.buffer(RenderLayer.AIRCRAFT_LIGHTS)[i];
            if (command.customDraw() instanceof EngineHullLightPass light) {
                count++;
                DrawCommand hull = out.buffer(RenderLayer.SHUTTLES)[0];
                assertEquals(hull.sprite(), light.hullSprite());
                assertEquals(hull.centerX(), light.centerX());
                assertEquals(hull.centerY(), light.centerY());
                assertEquals(hull.width(), light.widthPx());
                assertEquals(hull.height(), light.heightPx());
                assertEquals(hull.angleDegrees(), light.angleDegrees());
            }
        }
        return count;
    }

    private static int enginePasses(DrawList out, RenderLayer layer) {
        int count = 0;
        for (int i = 0; i < out.count(layer); i++) {
            DrawCommand command = out.buffer(layer)[i];
            if (command.kind() == DrawCommand.Kind.CUSTOM && command.customDraw() instanceof EngineFxPass) count++;
        }
        return count;
    }

    @Test
    void nightDarkensTheHullWithoutDimmingItsExteriorEngineEmission() {
        InstalledHullSpecs.install();
        try (BattleSimulation sim = openSim()) {
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++)
                sim.getTopology().setGroundKind(x, y, CellTopology.GroundKind.STONE);
            long craft = sim.spawnSortie(FighterProfile.TALON, Faction.MARINE,
                    20, 10, 20, 10, 20, 10, 0);
            sim.world().mission(craft).state = ShuttleState.INCOMING;
            sim.world().kinematics(craft).teleport(20, 10, 0);
            sim.world().setAltitudeT(craft, 1);
            BufferedImage coverage = aircraftFrame(sim, false, false, false);
            BufferedImage dayHull = aircraftFrame(sim, false, false, true);
            BufferedImage dayLit = aircraftFrame(sim, false, true, true);
            BufferedImage nightHull = aircraftFrame(sim, true, false, true);
            BufferedImage nightLit = aircraftFrame(sim, true, true, true);
            int darkenedHullPixels = 0, emissionPixels = 0;
            for (int y = 0; y < PIXELS_H; y++) for (int x = 0; x < PIXELS_W; x++) {
                int hull = dayHull.getRGB(x, y);
                if ((coverage.getRGB(x, y) >>> 24) > 100 && rgbSum(nightHull.getRGB(x, y)) < rgbSum(hull) - 20)
                    darkenedHullPixels++;
                if ((coverage.getRGB(x, y) >>> 24) != 0) continue; // Compare the plume outside the hull, where additive clipping cannot differ.
                int dayContribution = rgbSum(dayLit.getRGB(x, y)) - rgbSum(hull);
                int nightContribution = rgbSum(nightLit.getRGB(x, y)) - rgbSum(nightHull.getRGB(x, y));
                if (dayContribution > 6) {
                    emissionPixels++;
                    assertEquals(dayContribution, nightContribution, 4, "Night must not shade the engine contribution");
                }
            }
            assertTrue(darkenedHullPixels > 5, "The ordinary aircraft hull still receives night shade");
            assertTrue(emissionPixels > 2, "The shared review drain must actually draw the new engine masks");
        }
    }

    private static int rgbSum(int pixel) {
        return (pixel >>> 16 & 255) + (pixel >>> 8 & 255) + (pixel & 255);
    }

    private static BufferedImage aircraftFrame(BattleSimulation sim, boolean night, boolean lights, boolean backing) {
        sim.getTopology().setNightLighting(night ? new NightLighting(.8f, List.of(), Set.of()) : null);
        EnumSet<RenderLayer> layers = EnumSet.of(RenderLayer.SHUTTLES, RenderLayer.NIGHT_LIGHTING);
        if (lights) layers.add(RenderLayer.AIRCRAFT_LIGHTS);
        DrawList overlay = new DrawList();
        // An opaque backing enters before the hull and night shade, matching a real battle framebuffer.
        if (backing) overlay.addSolidRect(RenderLayer.SHUTTLES, 0, 0, PIXELS_W, PIXELS_H, 0, 0, 0, 1);
        return ui.renderHostPass(new OverlayPass(sim, overlay, layers), PIXELS_W, PIXELS_H);
    }
}
