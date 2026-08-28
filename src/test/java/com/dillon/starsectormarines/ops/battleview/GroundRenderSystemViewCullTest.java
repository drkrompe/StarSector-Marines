package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.DrawCommand;
import com.dillon.starsectormarines.render2d.VisibleCellRect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless pin that {@link GroundRenderSystem} range-loops the camera view
 * instead of the whole grid. Uses the no-sheet fallback (solid wall fills) so
 * the suite needs no OpenGL context.
 */
class GroundRenderSystemViewCullTest {

    private static final int GRID = 40;

    @Test
    void zoomOneEmitsEveryNonWalkableCell() {
        BattleSimulation sim = emptyWalls();
        BattleCamera cam = fittedCamera();
        int emitted = collectGround(sim, cam);

        assertEquals(1 + GRID * GRID, emitted, "backing fill + one solid per cell");
    }

    @Test
    void zoomedInEmitsOnlyTheVisibleSlice() {
        BattleSimulation sim = emptyWalls();
        BattleCamera cam = fittedCamera();
        cam.zoomAt(20f, 200f, 200f);
        VisibleCellRect view = cam.visibleCells(
                VisibleCellRect.GEOMETRY_MARGIN_CELLS, GRID, GRID);

        int emitted = collectGround(sim, cam);

        assertEquals(1 + view.width() * view.height(), emitted);
        assertTrue(emitted < 1 + GRID * GRID / 4,
                "zoomed collect should skip most of the grid, was " + emitted);
        assertTrue(view.width() * view.height() < GRID * GRID);
        assertTrue(view.contains((int) cam.panCellX(), (int) cam.panCellY()));
    }

    @Test
    void intactWindowEdgeEmitsFrameAndGlassThenDisappears() {
        NavigationGrid grid = new NavigationGrid(2, 1);
        grid.setWalkableFloor(0, 0);
        grid.setWalkableFloor(1, 0);
        grid.placeEdgeBarrier(0, 0, Direction.E,
                SharedEdgeBarrier.Kind.WINDOW);
        BattleSimulation sim = new BattleSimulation(grid,
                new CellTopology(2, 1));
        BattleCamera cam = new BattleCamera(2, 1);
        cam.setViewport(0f, 0f, 200f, 100f, 100f);

        DrawList intact = collectGroundCommands(sim, cam);
        assertEquals(3, intact.count(RenderLayer.GROUND),
                "backing fill + window frame + glass");
        DrawCommand frame = intact.buffer(RenderLayer.GROUND)[1];
        DrawCommand glass = intact.buffer(RenderLayer.GROUND)[2];
        float cell = cam.cellPxSize();
        float edgeX = cam.cellToScreenX(1f);
        assertEquals(edgeX, frame.width(), 0.001f,
                "frame exterior must stop on the shared edge");
        assertEquals(edgeX - cell * 0.30f, frame.centerX(), 0.001f,
                "frame must project into its structural-owner cell");
        assertEquals(edgeX - cell * 0.15f,
                (glass.centerX() + glass.width()) * 0.5f, 0.001f,
                "pane must remain centered inside the inward frame");
        assertEquals(cell * 0.30f, frame.width() - frame.centerX(), 0.001f,
                "edge frame is widened for distance readability");
        assertEquals(cell * 0.12f, glass.width() - glass.centerX(), 0.001f,
                "glass stays visibly narrower than its frame");
        assertEquals(cell, frame.height() - frame.centerY(), 0.001f,
                "frame must meet the neighboring wall at both ends");
        assertEquals(cell * 0.84f, glass.height() - glass.centerY(), 0.001f);

        assertTrue(sim.damageEdgeBarrier(0, 0, Direction.E, 40));
        assertEquals(1, collectGround(sim, cam),
                "destroyed window leaves only the floor backing");
    }

    @Test
    void northWindowEdgeUsesTheSameReadableGeometryRotated() {
        NavigationGrid grid = new NavigationGrid(1, 2);
        grid.setWalkableFloor(0, 0);
        grid.setWalkableFloor(0, 1);
        grid.placeEdgeBarrier(0, 1, Direction.S,
                SharedEdgeBarrier.Kind.WINDOW);
        BattleSimulation sim = new BattleSimulation(grid,
                new CellTopology(1, 2));
        BattleCamera cam = new BattleCamera(1, 2);
        cam.setViewport(0f, 0f, 100f, 200f, 100f);

        DrawList out = collectGroundCommands(sim, cam);
        DrawCommand frame = out.buffer(RenderLayer.GROUND)[1];
        DrawCommand glass = out.buffer(RenderLayer.GROUND)[2];
        float cell = cam.cellPxSize();
        float edgeY = cam.cellToScreenY(1f);
        assertEquals(edgeY, frame.centerY(), 0.001f,
                "frame exterior must stop on the shared edge");
        assertEquals(edgeY + cell * 0.30f, frame.height(), 0.001f,
                "south-facing window must project north into its owner cell");
        assertEquals(edgeY + cell * 0.15f,
                (glass.centerY() + glass.height()) * 0.5f, 0.001f);
        assertEquals(cell * 0.30f, frame.height() - frame.centerY(), 0.001f);
        assertEquals(cell * 0.12f, glass.height() - glass.centerY(), 0.001f);
        assertEquals(cell, frame.width() - frame.centerX(), 0.001f);
        assertEquals(cell * 0.84f, glass.width() - glass.centerX(), 0.001f);
    }

    private static int collectGround(BattleSimulation sim, BattleCamera cam) {
        return collectGroundCommands(sim, cam).count(RenderLayer.GROUND);
    }

    private static DrawList collectGroundCommands(BattleSimulation sim, BattleCamera cam) {
        GroundRenderSystem system = new GroundRenderSystem(new BattleSprites());
        DrawList out = new DrawList();
        system.collect(new RenderContext(sim, cam, null, 1f, 0f, false,
                new HighlightOverlay(), new Selection()), out);
        return out;
    }

    private static BattleSimulation emptyWalls() {
        return new BattleSimulation(new NavigationGrid(GRID, GRID), new CellTopology(GRID, GRID));
    }

    private static BattleCamera fittedCamera() {
        BattleCamera cam = new BattleCamera(GRID, GRID);
        cam.setViewport(0f, 0f, 400f, 400f, 10f);
        return cam;
    }
}
