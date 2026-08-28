package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

/** Headless image entry point for a generated map's ordinary battle scene. */
public final class HeadlessBattleMapRenderer {

    private final HeadlessUiRenderer drain;

    public HeadlessBattleMapRenderer(Path modRoot) {
        Path normalized = modRoot.toAbsolutePath().normalize();
        drain = new HeadlessUiRenderer(new HeadlessBattleSceneRenderer(normalized), normalized);
    }

    /**
     * Renders the complete map at a fixed battle-cell scale through the
     * production ground and doodad collectors.
     */
    public BufferedImage render(MapResult map, long seed, int cellPx) {
        if (cellPx <= 0) throw new IllegalArgumentException("cell size must be positive");
        try (MapBattleScene scene = new MapBattleScene(map, seed)) {
            return drain.renderHostPass(
                    scene.pass(MapBattleScene.MapView.whole(map, cellPx)),
                    map.grid.getWidth() * cellPx, map.grid.getHeight() * cellPx);
        }
    }
}
