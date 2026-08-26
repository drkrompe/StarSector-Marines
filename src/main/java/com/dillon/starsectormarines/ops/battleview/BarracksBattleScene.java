package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;

import java.util.EnumSet;

/** Bounded battle-renderer host for the flagship's marine quarters. */
public final class BarracksBattleScene implements AutoCloseable {

    static final int GRID_WIDTH = BarracksSceneLayout.WIDTH;
    static final int GRID_HEIGHT = BarracksSceneLayout.HEIGHT;
    private static final EnumSet<RenderLayer> ROOM_LAYERS = EnumSet.of(
            RenderLayer.GROUND, RenderLayer.DOODADS);

    private final BattleRenderer renderer;
    private final HighlightOverlay highlights = new HighlightOverlay();
    private final Selection selection = new Selection();
    private final BattleCamera camera = new BattleCamera(GRID_WIDTH, GRID_HEIGHT);
    private BattleSimulation simulation;

    public BarracksBattleScene(BattleSprites sprites) {
        if (sprites == null) throw new IllegalArgumentException("battle sprites are required");
        renderer = new BattleRenderer(sprites);
        renderer.buildTileBatches();
    }

    public void renderBackdrop(CanvasHostViewport viewport, float alphaMult) {
        if (viewport.width() <= 0f || viewport.height() <= 0f) return;
        ensureSimulation();
        configureCamera(camera, viewport.screenX(), viewport.screenY(),
                viewport.width(), viewport.height());
        RenderContext context = new RenderContext(simulation, camera, null,
                alphaMult, 0f, false, highlights, selection,
                BattleRenderHostProfile.EMBEDDED_SCENE);
        renderer.renderWorld(context, ROOM_LAYERS);
    }

    static BattleCamera cameraForSurface(float width, float height) {
        BattleCamera result = new BattleCamera(GRID_WIDTH, GRID_HEIGHT);
        configureCamera(result, 0f, 0f, width, height);
        return result;
    }

    private static void configureCamera(BattleCamera camera, float x, float y,
                                        float width, float height) {
        float cell = Math.min(width / GRID_WIDTH, height / GRID_HEIGHT);
        camera.setViewport(x, y, width, height, cell);
        camera.centerOn(GRID_WIDTH * 0.5f, GRID_HEIGHT * 0.5f);
    }

    private void ensureSimulation() {
        if (simulation != null) return;
        simulation = buildSimulation();
    }

    static BattleSimulation buildSimulation() {
        NavigationGrid grid = new NavigationGrid(GRID_WIDTH, GRID_HEIGHT);
        CellTopology topology = new CellTopology(GRID_WIDTH, GRID_HEIGHT);
        for (int y = 0; y < GRID_HEIGHT; y++) {
            for (int x = 0; x < GRID_WIDTH; x++) {
                topology.setGroundKind(x, y, BarracksSceneLayout.groundKind(x, y));
                if (BarracksSceneLayout.wall(x, y)) topology.setWall(x, y, true);
                else grid.setWalkableFloor(x, y);
            }
        }
        for (int y = 0; y < GRID_HEIGHT; y++) {
            for (int x = 0; x < GRID_WIDTH; x++) grid.recomputeCoverAt(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, topology, 0x4241525241434B53L);
        for (BarracksSceneLayout.FloorOverlayPlacement placement
                : BarracksSceneLayout.floorOverlays()) {
            sim.addDoodad(prop(placement.cellX(), placement.cellY(),
                    placement.tileColumn(), placement.tileRow()));
        }
        for (BarracksSceneLayout.PropPlacement placement : BarracksSceneLayout.PROPS) {
            sim.addDoodad(prop(placement.cellX(), placement.cellY(),
                    placement.tileColumn(), placement.tileRow()));
        }
        sim.getFogOfWar().tick(0, sim.getRoster());
        return sim;
    }

    private static Doodad prop(int x, int y, int column, int row) {
        return new Doodad(x, y, new TileManifest.TileFrame(column, row),
                TileManifest.SHEET, Doodad.COVER_NONE);
    }

    @Override
    public void close() {
        if (simulation != null) {
            simulation.close();
            simulation = null;
        }
    }
}
