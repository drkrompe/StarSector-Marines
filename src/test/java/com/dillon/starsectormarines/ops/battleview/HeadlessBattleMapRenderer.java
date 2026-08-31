package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

/** Headless image entry point for a generated map's ordinary battle scene. */
public final class HeadlessBattleMapRenderer {

    /** {@code FogOfWarService.COHORT_COUNT}, which is private to it. Over-ticking is harmless; under-ticking hides units. */
    private static final int FOG_COHORTS = 6;

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

    /**
     * Renders a camera-framed portion of the map without first allocating a
     * complete-map image. The selected cells still come from the ordinary
     * scene collectors; only the host viewport is smaller.
     */
    public BufferedImage renderView(MapResult map, long seed,
                                    float centerCellX, float centerCellY,
                                    int widthCells, int heightCells, int cellPx) {
        if (widthCells <= 0 || heightCells <= 0) {
            throw new IllegalArgumentException("view dimensions must be positive");
        }
        if (cellPx <= 0) throw new IllegalArgumentException("cell size must be positive");
        return renderView(map, seed, centerCellX, centerCellY, widthCells, heightCells, cellPx,
                null, MapBattleScene.AUTHORING_LAYERS);
    }

    /**
     * The same framing, with bodies on the map and a chosen set of layers.
     *
     * <p>Everything above renders terrain with nobody standing on it, because
     * {@link MapBattleScene} spawns nothing and the default layer set stops at
     * ground and props. That was fine while headless evidence was about
     * <em>terrain</em>, and it is the reason a render system that draws
     * <em>bodies</em> — a shadow, a marker, a pose — had no way to be looked at
     * without launching the game.
     *
     * <p>{@code populate} is handed the scene's own live simulation, so a caller
     * spawns through the ordinary {@code sim.spawn(EntitySpec)} path rather than
     * through anything this class invents. Fog is re-ticked afterwards: the
     * scene ticks it once at construction, when the roster is empty, and a unit
     * spawned after that is hidden from every visibility gate in the renderer —
     * which looks exactly like a render system that does not work.
     *
     * @param populate spawns into the scene's simulation, or {@code null} for
     *                 an empty map
     * @param layers   which production render systems to collect
     */
    public BufferedImage renderView(MapResult map, long seed,
                                    float centerCellX, float centerCellY,
                                    int widthCells, int heightCells, int cellPx,
                                    Consumer<BattleSimulation> populate,
                                    EnumSet<RenderLayer> layers) {
        if (widthCells <= 0 || heightCells <= 0) {
            throw new IllegalArgumentException("view dimensions must be positive");
        }
        if (cellPx <= 0) throw new IllegalArgumentException("cell size must be positive");
        try (MapBattleScene scene = new MapBattleScene(map, seed)) {
            if (populate != null) {
                populate.accept(scene.simulation());
                // Once per cohort, not once. Contributors are round-robined
                // across FogOfWarService's cohorts and a tick refreshes one of
                // them, so a single tick leaves most of a freshly spawned force
                // casting no vision and therefore hidden from every visibility
                // gate in the renderer -- which looks exactly like a render
                // system that does not work, and cost an hour saying so.
                for (int cohort = 0; cohort < FOG_COHORTS; cohort++) {
                    scene.simulation().getFogOfWar()
                            .tick(0, scene.simulation().getRoster());
                }
            }
            return drain.renderHostPass(
                    scene.pass(new MapBattleScene.MapView(
                            centerCellX, centerCellY, cellPx), layers),
                    widthCells * cellPx, heightCells * cellPx);
        }
    }
}
