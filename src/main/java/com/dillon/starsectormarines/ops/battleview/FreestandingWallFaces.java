package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoofCover;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.VisibleCellRect;

import java.util.List;

/**
 * Freestanding walls stood up ({@link FreestandingWalls}): each run's faces turned to the view's centre, from their
 * foot on the ground to the run's height at its lift about the view's centre, lit by their side, then the run's cells
 * lifted to that height as its cap. Drawn in the roof layer before the buildings' faces, so a building stands over a
 * wall beside it; above the fog, so each carries its cell's fog itself, as a roof does. The runs are settled once per
 * topology change: a breached cell is no wall, and its run re-forms without it.
 */
final class FreestandingWallFaces {

    private final PolyMesh mesh = new PolyMesh(256);
    private CellTopology runsOf;
    private long runsAt = -1;
    private List<FreestandingWalls.Run> runs = List.of();

    /**
     * This frame's walls into the roof layer.
     *
     * @param fogDrawn whether the frame draws the fog; a frame without it (a look shot) shows every wall in light
     */
    void collect(BattleSimulation sim, DrawList out, float alphaMult, BattleCamera camera, boolean fogDrawn) {
        CellTopology topology = sim.getTopology();
        if (topology == null) return;
        if (topology != runsOf || topology.changeCount() != runsAt) {
            runs = runs(topology);
            runsOf = topology;
            runsAt = topology.changeCount();
        }
        if (runs.isEmpty()) return;
        int gridW = topology.getWidth();
        FogOfWarService vis = sim.getFogOfWar();
        boolean[] shown = !fogDrawn || vis == null || !vis.isInitialized() ? null : vis.cellShownArray();
        int gridH = topology.getHeight();
        float unseen = FogField.roofShade(1f);
        float viewX = camera.panCellX(), viewY = camera.panCellY();
        float sx = camera.cellToScreenX(viewX), sy = camera.cellToScreenY(viewY);
        VisibleCellRect view = camera.visibleCells(VisibleCellRect.GEOMETRY_MARGIN_CELLS + 6, gridW,
                topology.getHeight());
        mesh.reset();
        for (FreestandingWalls.Run run : runs) {
            if (!view.intersects(run.minX(), run.maxX(), run.minY(), run.maxY())) continue;
            float lift = BuildingFaces.lift(run.meters());
            FreestandingWalls.Kind kind = run.kind();
            float[] segs = run.segments();
            for (int e = 0; e < segs.length; e += BuildingFaces.SEG) {
                int cell = (int) segs[e + 6];
                if (!view.contains(cell % gridW, cell / gridW)) continue;
                float x0 = segs[e], y0 = segs[e + 1], x1 = segs[e + 2], y1 = segs[e + 3];
                float nx = segs[e + 4], ny = segs[e + 5];
                if (!BuildingFaces.facing(nx, ny, (x0 + x1) / 2f, (y0 + y1) / 2f, viewX, viewY)) continue;
                float fog = seen(shown, cell, gridW, gridH) ? 1f : unseen;
                float light = BuildingFaces.shade(nx, ny) * fog;
                float ax = camera.cellToScreenX(x0), ay = camera.cellToScreenY(y0);
                float bx = camera.cellToScreenX(x1), by = camera.cellToScreenY(y1);
                mesh.appendQuad(ax, ay, bx, by,
                        sx + (bx - sx) * lift, sy + (by - sy) * lift,
                        sx + (ax - sx) * lift, sy + (ay - sy) * lift,
                        kind.r * light, kind.g * light, kind.b * light, alphaMult);
            }
            for (int cell : run.cells()) {
                int cx = cell % gridW, cy = cell / gridW;
                if (!view.contains(cx, cy)) continue;
                float fog = seen(shown, cell, gridW, gridH) ? 1f : unseen;
                // A cap faces the sky: the kind's own tint, brighter than any face.
                float light = fog;
                float ax = sx + (camera.cellToScreenX(cx) - sx) * lift;
                float bx = sx + (camera.cellToScreenX(cx + 1) - sx) * lift;
                float ay = sy + (camera.cellToScreenY(cy) - sy) * lift;
                float by = sy + (camera.cellToScreenY(cy + 1) - sy) * lift;
                mesh.appendQuad(ax, ay, bx, ay, bx, by, ax, by,
                        kind.r * light, kind.g * light, kind.b * light, alphaMult);
            }
        }
        if (!mesh.isEmpty()) out.addPoly(RenderLayer.ROOFS, mesh);
    }

    /**
     * Whether a wall cell is in sight: a wall blocks the sight that reveals it, so it is seen where the ground beside
     * it is, across one of its sides. No fog, all seen.
     */
    static boolean seen(boolean[] shown, int cell, int gridW, int gridH) {
        if (shown == null || shown.length < gridW * gridH) return true;
        if (shown[cell]) return true;
        int x = cell % gridW, y = cell / gridW;
        return x > 0 && shown[cell - 1] || x < gridW - 1 && shown[cell + 1]
                || y > 0 && shown[cell - gridW] || y < gridH - 1 && shown[cell + gridW];
    }

    /** The map's runs of freestanding wall: wall cells no building owns, not out over the void. */
    private static List<FreestandingWalls.Run> runs(CellTopology topology) {
        int gridW = topology.getWidth();
        return FreestandingWalls.runs(gridW, topology.getHeight(), at -> {
            int x = at % gridW, y = at / gridW;
            return topology.isWall(x, y) && !topology.isWindow(x, y) && RoofCover.owner(topology, x, y) == 0
                    && topology.getGroundKind(x, y) != CellTopology.GroundKind.VOID;
        }, at -> FreestandingWalls.kind(topology.getSurfaceId(at % gridW, at / gridW),
                topology.getWorksStyle(at % gridW, at / gridW)));
    }
}
