package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;
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

        assertEquals(3, collectGround(sim, cam),
                "backing fill + window frame + glass");

        assertTrue(sim.damageEdgeBarrier(0, 0, Direction.E, 40));
        assertEquals(1, collectGround(sim, cam),
                "destroyed window leaves only the floor backing");
    }

    private static int collectGround(BattleSimulation sim, BattleCamera cam) {
        GroundRenderSystem system = new GroundRenderSystem(new BattleSprites());
        DrawList out = new DrawList();
        system.collect(new RenderContext(sim, cam, null, 1f, 0f, false,
                new HighlightOverlay(), new Selection()), out);
        return out.count(RenderLayer.GROUND);
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
