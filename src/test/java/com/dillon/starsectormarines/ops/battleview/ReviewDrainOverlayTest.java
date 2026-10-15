package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.Airframe;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

        OverlayPass(BattleSimulation sim, DrawList overlay) {
            this.sim = sim;
            this.overlay = overlay;
        }

        @Override
        public BattleSceneFrame prepare(CanvasHostViewport viewport, float alphaMult) {
            return new BattleSceneFrame(context(sim), EnumSet.of(RenderLayer.SHOTS));
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
        sim.world().mission(craft).state = ShuttleState.LOADING;
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

        int plumes = 0;
        for (int i = 0; i < out.count(RenderLayer.SHUTTLES); i++) {
            DrawCommand command = out.buffer(RenderLayer.SHUTTLES)[i];
            if (command.kind() != DrawCommand.Kind.CUSTOM) continue;
            assertInstanceOf(EngineFxPass.class, command.customDraw());
            plumes++;
        }
        assertEquals(1, plumes);
    }
}
