package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;

import java.util.Collections;
import java.util.EnumSet;

/**
 * Ordinary battle-render host for a generated map without a mission HUD.
 *
 * <p>Authoring tools and embedded application views use this scene instead of
 * reconstructing tiles with a second painter. The map is promoted through
 * {@link BattleSetup} to the same {@link BattleSimulation} consumed in battle,
 * then {@link BattleRenderer} collects the selected production render systems.
 * A live host supplies {@link BattleSprites}; a headless evidence host leaves
 * them null and substitutes only the final command drain.
 */
public final class MapBattleScene implements AutoCloseable {

    /** Terrain and authored props, without mission-only actors or concealment. */
    public static final EnumSet<RenderLayer> AUTHORING_LAYERS = EnumSet.of(
            RenderLayer.GROUND, RenderLayer.DOODADS);

    private final BattleSimulation simulation;
    private final BattleRenderer renderer;
    private final HighlightOverlay highlights = new HighlightOverlay();
    private final Selection selection = new Selection();

    /** Scene model only; a tooling drain brings its own renderer and assets. */
    public MapBattleScene(MapResult map, long seed) {
        this(map, seed, null);
    }

    /** Scene with an optional live graphics renderer. */
    public MapBattleScene(MapResult map, long seed, BattleSprites sprites) {
        if (map == null) throw new IllegalArgumentException("a generated map is required");
        simulation = BattleSetup.buildMap(map, Collections.emptyList(),
                Collections.emptyList(), seed).sim();
        simulation.setMissionCompletionEnabled(false);
        simulation.getFogOfWar().tick(0, simulation.getRoster());
        renderer = sprites == null ? null : new BattleRenderer(sprites);
        if (renderer != null) renderer.buildTileBatches();
    }

    public BattleSimulation simulation() {
        return simulation;
    }

    public BattleSceneHostPass pass(MapView view) {
        return pass(view, AUTHORING_LAYERS);
    }

    public BattleSceneHostPass pass(MapView view, EnumSet<RenderLayer> layers) {
        if (view == null) throw new IllegalArgumentException("a map framing is required");
        if (layers == null || layers.isEmpty()) {
            throw new IllegalArgumentException("at least one render layer is required");
        }
        EnumSet<RenderLayer> selected = EnumSet.copyOf(layers);
        return new BattleSceneHostPass() {
            @Override
            public BattleSceneFrame prepare(CanvasHostViewport viewport, float alphaMult) {
                if (viewport.width() <= 0f || viewport.height() <= 0f) {
                    throw new IllegalArgumentException("a map scene requires a visible viewport");
                }
                BattleCamera camera = new BattleCamera(
                        simulation.getGrid().getWidth(), simulation.getGrid().getHeight());
                camera.setViewport(viewport.screenX(), viewport.screenY(),
                        viewport.width(), viewport.height(), view.cellPx());
                camera.centerOn(view.centerCellX(), view.centerCellY());
                RenderContext context = new RenderContext(simulation, camera, null,
                        alphaMult, 0f, false, highlights, selection,
                        BattleRenderHostProfile.EMBEDDED_SCENE);
                return new BattleSceneFrame(context, selected);
            }

            @Override
            public void draw(CanvasHostViewport viewport, float alphaMult) {
                if (renderer == null) {
                    throw new IllegalStateException("This map scene has no live renderer");
                }
                BattleSceneFrame frame = prepare(viewport, alphaMult);
                renderer.renderWorld(frame.context(), frame.layers());
            }
        };
    }

    @Override
    public void close() {
        simulation.close();
    }

    /** Camera center and authored pixels per battle cell. */
    public record MapView(float centerCellX, float centerCellY, float cellPx) {

        public MapView {
            if (!(cellPx > 0f)) throw new IllegalArgumentException("cell size must be positive");
        }

        /** Frame the complete generated map at the requested cell scale. */
        public static MapView whole(MapResult map, float cellPx) {
            if (map == null) throw new IllegalArgumentException("a generated map is required");
            return new MapView(map.grid.getWidth() * 0.5f,
                    map.grid.getHeight() * 0.5f, cellPx);
        }
    }
}
