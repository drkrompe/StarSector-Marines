package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

/**
 * The CPU review drain: the production layers collected and replayed through Java2D by
 * {@link HeadlessBattleSceneRenderer}, in fixed bands so every machine draws the same pixels. GL-only custom passes
 * are drawn by its CPU models or not at all; see the table in {@code snapshot-suites.md} under "What a Java2D review frame draws".
 */
final class Java2dReviewBackend implements ReviewSceneBackend {

    private final HeadlessUiRenderer renderer;

    Java2dReviewBackend(Path modRoot) {
        Path root = modRoot.toAbsolutePath().normalize();
        // The canvas reads the same roots the scene does — mod first, then the
        // installed game — so a hull loaded out of the install can be painted
        // as well as collected.
        this.renderer = new HeadlessUiRenderer(
                HeadlessBattleSceneRenderer.resourceRoots(root),
                new HeadlessBattleSceneRenderer(root, true));
    }

    @Override
    public BufferedImage draw(Scene scene, int width, int height) {
        return renderer.renderHostPass(pass(scene), width, height);
    }

    private static BattleSceneHostPass pass(Scene scene) {
        return new ReviewHostPass() {
            @Override
            public DrawList overlay(BattleCamera camera) {
                return scene.impacts() == null ? null : scene.impacts().overlay(camera);
            }

            @Override
            public BattleSceneFrame prepare(CanvasHostViewport viewport, float alphaMult) {
                RenderContext context = new RenderContext(scene.simulation(), scene.camera(),
                        null, alphaMult, 0f, false,
                        new HighlightOverlay(), new Selection(),
                        BattleRenderHostProfile.OBSERVER_SCENE, scene.clockSeconds());
                return new BattleSceneFrame(context, scene.layers());
            }

            @Override
            public void draw(CanvasHostViewport viewport, float alphaMult) {
                throw new IllegalStateException("Visual review requires the headless scene drain");
            }
        };
    }

    /** The scene pass, with the presentation services a feed owns brought into the same drain. */
    private interface ReviewHostPass extends BattleSceneHostPass, HeadlessBattleSceneRenderer.ReviewOverlayPass { }
}
