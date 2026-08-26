package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.ship.fit.RoomFit;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated deck draws through the game's own renderer.
 *
 * <p>Guards the drain contract rather than the picture. A world layer may emit a
 * command that only a live GL context can replay — the decal pass does — and
 * asking for such a layer fails loudly at draw time. That is the right
 * behaviour, but until this test existed the only thing that exercised it was
 * running the snapshot task by hand, so the failure surfaced as a broken
 * evidence run instead of a broken build.
 */
final class ShipDeckBattleSceneTest {

    private static final long SEED = 42L;
    private static final int CELL_PX = 16;

    @Test
    void deckDrawsThroughTheBattleRendererWithoutALiveContext() {
        Path modRoot = Paths.get("mod").toAbsolutePath().normalize();
        ShipDeckGenerator generator = new ShipDeckGenerator();
        MapResult deck = generator.generateDeck(
                new DeckSizing.DeckPlan(96, 28, List.of()), SEED, null, RoomFit.STANDARD);

        HeadlessBattleSceneRenderer scenes = new HeadlessBattleSceneRenderer(modRoot);
        HeadlessUiRenderer drain = new HeadlessUiRenderer(scenes, modRoot);

        BufferedImage image;
        try (ShipDeckBattleScene scene = new ShipDeckBattleScene(deck, SEED)) {
            int across = 24;
            int down = 16;
            image = drain.renderHostPass(
                    scene.pass(ShipDeckBattleScene.DeckView.over(
                            10, 6, across, down, CELL_PX)),
                    across * CELL_PX, down * CELL_PX);
        }

        assertNotNull(image, "the deck view produced no image");
        assertTrue(painted(image) > 0.5f,
                "the deck view came out mostly blank, so the world layers drew nothing");
    }

    /** Fraction of pixels the renderer actually put something opaque into. */
    private static float painted(BufferedImage image) {
        int opaque = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) > 0x80) opaque++;
            }
        }
        return opaque / (float) (image.getWidth() * image.getHeight());
    }
}
