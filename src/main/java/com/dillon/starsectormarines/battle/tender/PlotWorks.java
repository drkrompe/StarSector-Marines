package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Material;
import com.dillon.starsectormarines.battle.world.gen.road.RoadRasterizer;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

/**
 * Puts one stage of a {@link BuildPlot} on the live map, through the same cell writes a fort's pad and the redoubts'
 * mid-battle works make: paving is the fort pad's concrete with the ground scatter cleared, and the rim and stores are
 * walkable wearable cover, so rounds wear them down like any other cover prop. Nothing is laid on a cell that is no
 * longer open ground or already carries a prop, and nothing is laid without the art.
 */
final class PlotWorks {

    /** Seed of the concrete's wear; any fixed value, since the wear is hashed per cell. */
    private static final long WEAR_SEED = 0x10C057L;

    private PlotWorks() {}

    static void lay(BattleSimulation sim, BuildPlot plot, BuildPlot.Stage stage, int cx, int cy, int[] facing) {
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        TileRegistry registry = TileRegistry.installed();
        for (BuildPlot.Piece piece : plot.pieces(stage, cx, cy, facing[0], facing[1])) {
            int x = piece.x(), y = piece.y();
            if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || grid.isDoorway(x, y) || topology.isWall(x, y)) {
                continue;
            }
            if (piece.doodad() == null) {
                topology.setGroundKind(x, y, GroundKind.COURTYARD);
                topology.setSurface(x, y, topology.surfaceIndex(
                        Material.CONCRETE.block(RoadRasterizer.age(.55, WEAR_SEED, x, y))));
                sim.clearNatureOverlay(x, y);
                continue;
            }
            DoodadDef def = registry == null ? null : registry.doodad(piece.doodad());
            if (def == null || dressed(sim, x, y)) continue;
            grid.setSeeThrough(x, y, true);
            topology.setFixture(x, y, true);
            sim.addDoodad(new Doodad(x, y, def));
        }
    }

    /** Whether a prop already stands on the cell; a stage steps round it rather than stacking on it. */
    private static boolean dressed(BattleSimulation sim, int x, int y) {
        for (Doodad d : sim.getDoodads()) {
            if (x >= d.cellX && x < d.cellX + d.footprintCellsX
                    && y >= d.cellY && y < d.cellY + d.footprintCellsY) return true;
        }
        return false;
    }
}
