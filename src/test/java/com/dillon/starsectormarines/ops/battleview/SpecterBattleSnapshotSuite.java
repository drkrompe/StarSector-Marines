package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.infantry.SpecterBattleScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/** One actual replay, at the live default camera and four real wheel notches closer. */
public final class SpecterBattleSnapshotSuite implements SnapshotSuite {
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final EnumSet<RenderLayer> LAYERS = EnumSet.of(
            RenderLayer.GROUND, RenderLayer.DECALS, RenderLayer.DOODADS,
            RenderLayer.UNIT_SHADOWS, RenderLayer.UNITS, RenderLayer.HAZARDS,
            RenderLayer.SMOKE, RenderLayer.SHOTS);

    @Override public String id() { return "specter-battle"; }
    @Override public String label() { return "Specter alongside Legionaries and Hound at normal battle zoom"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        HeadlessUiRenderer renderer = new HeadlessUiRenderer(
                HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                new HeadlessBattleSceneRenderer(context.modRoot(), true));
        ReviewImpactFx impacts = new ReviewImpactFx(context.modRoot());
        List<BufferedImage> normal = new ArrayList<>();
        List<BufferedImage> closer = new ArrayList<>();
        List<SceneReport> reports = new SpecterBattleScene().play(new FrameSink() {
            @Override public void frame(String loop, BattleSimulation sim, int tick, String caption) {
                try {
                    normal.add(frameAt(renderer, sim, impacts, tick, caption, 0));
                    closer.add(frameAt(renderer, sim, impacts, tick, caption, 4));
                    if (tick % 90 == 0) System.out.println("    Specter battle frame at " + tick / 30f + "s");
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            }
            @Override public void advanced(String loop, BattleSimulation sim, int tick) {
                impacts.feed(sim);
            }
        });
        SceneReport report = reports.get(0);
        System.out.println("    Specter battle: " + report.metrics());
        if (!report.passed()) throw new IllegalStateException("Battle review failed: " + report.verdicts());
        int firstShot = report.metrics().get("firstShotTick").intValue();
        int walking = Math.max(1, Math.min(20, (firstShot - 3) / SpecterBattleScene.FRAME_EVERY_TICKS));
        int contact = Math.min(normal.size() - 1, firstShot / SpecterBattleScene.FRAME_EVERY_TICKS + 20);
        return List.of(
                SnapshotArtifact.colorAnimation("normal-zoom.gif", normal, 100),
                new SnapshotArtifact("normal-walking.png", normal.get(walking)),
                new SnapshotArtifact("normal-contact.png", normal.get(contact)),
                SnapshotArtifact.colorAnimation("closer-four-notches.gif", closer, 100),
                new SnapshotArtifact("closer-walking.png", closer.get(walking)),
                new SnapshotArtifact("closer-contact.png", closer.get(contact)));
    }

    private BufferedImage frameAt(HeadlessUiRenderer renderer, BattleSimulation sim,
                                  ReviewImpactFx impacts, int tick, String caption, int notches) throws IOException {
        BattleCamera camera = camera(sim, WIDTH, HEIGHT, notches);
        BattleSceneHostPass pass = new BattleSceneHostPass() {
            @Override public BattleSceneFrame prepare(CanvasHostViewport viewport, float alpha) {
                RenderContext context = new RenderContext(sim, camera, null, alpha, 0f, false,
                        new HighlightOverlay(), new Selection(), BattleRenderHostProfile.EMBEDDED_SCENE,
                        tick / 30f);
                return new BattleSceneFrame(context, LAYERS);
            }
            @Override public void draw(CanvasHostViewport viewport, float alpha) {
                throw new IllegalStateException("Offline review requires the headless battle drain");
            }
        };
        BufferedImage raw = renderer.renderHostPass(pass, WIDTH, HEIGHT);
        impacts.paint(raw, camera);
        BufferedImage output = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = output.createGraphics();
        g.drawImage(raw, 0, 0, null);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        band(g, 0, 65);
        label(g, notches == 0 ? "NORMAL BATTLE ZOOM" : "CLOSER VIEW / FOUR WHEEL NOTCHES", 24, 27, 21);
        label(g, String.format(Locale.ROOT,
                "Zoom %.4fx  |  %.2f px/cell  |  112 x 64 Relief-size map  |  1920 x 1080, 100%% UI scale  |  Real-time playback",
                camera.zoom(), camera.cellPxSize()), 24, 52, 16);
        laneLabel(g, camera, "SPECTER", SpecterBattleScene.ORIGIN_Y + 14);
        laneLabel(g, camera, "LEGIONARY", SpecterBattleScene.ORIGIN_Y + 26);
        laneLabel(g, camera, "HOUND", SpecterBattleScene.ORIGIN_Y + 35);
        band(g, HEIGHT - 57, 57);
        label(g, caption, 24, HEIGHT - 32, 17);
        label(g, "Controlled production battle simulation / Java2D capture; no selection discs, HUD, or GL terrain relief. Same unit scales in both views.",
                24, HEIGHT - 10, 15);
        g.dispose();
        return output;
    }

    private static BattleCamera camera(BattleSimulation sim, float width, float height, int notches) {
        int columns = sim.getGrid().getWidth(), rows = sim.getGrid().getHeight();
        BattleCamera camera = new BattleCamera(columns, rows);
        // BattleLayout's live cover fit, not the review renderer's floored contain fit.
        camera.setViewport(0, 0, width, height, Math.max(width / columns, height / rows));
        camera.zoomAt(notches, width / 2, height / 2);
        if (notches > 0) camera.centerOn(SpecterBattleScene.ORIGIN_X + 40, SpecterBattleScene.ORIGIN_Y + 24);
        return camera;
    }

    private static void laneLabel(Graphics2D g, BattleCamera camera, String text, float y) {
        int screenY = Math.round(HEIGHT - camera.cellToScreenY(y));
        g.setColor(new Color(10, 18, 24, 210));
        g.fillRoundRect(18, screenY - 20, 135, 29, 5, 5);
        label(g, text, 28, screenY, 16);
    }

    private static void band(Graphics2D g, int y, int height) {
        g.setColor(new Color(10, 18, 24, 225));
        g.fillRect(0, y, WIDTH, height);
    }

    private static void label(Graphics2D g, String text, int x, int y, int points) {
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, points));
        g.setColor(new Color(225, 234, 238));
        g.drawString(text, x, y);
    }
}
