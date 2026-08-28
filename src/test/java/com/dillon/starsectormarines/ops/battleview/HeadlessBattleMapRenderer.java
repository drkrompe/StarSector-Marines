package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

/** Headless image entry point for a generated map's ordinary battle scene. */
public final class HeadlessBattleMapRenderer {

    private final HeadlessUiRenderer drain;

    public HeadlessBattleMapRenderer(Path modRoot) {
        this(List.of(modRoot));
    }

    /**
     * Resolve sprites against several roots, earliest first.
     *
     * <p>Lets a caller shadow individual shipped sheets with its own copies — an
     * authoring preview swapping candidate art into a tile's cells — without
     * writing anything into the mod folder. The scene renderer keeps the shipped
     * root, because that is where the catalogs it installs live.
     */
    public HeadlessBattleMapRenderer(List<Path> resourceRoots) {
        if (resourceRoots == null || resourceRoots.isEmpty()) {
            throw new IllegalArgumentException("at least one resource root is required");
        }
        List<Path> roots = resourceRoots.stream()
                .map(root -> root.toAbsolutePath().normalize())
                .toList();
        Path shipped = roots.get(roots.size() - 1);
        drain = new HeadlessUiRenderer(roots, new HeadlessBattleSceneRenderer(shipped));
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
