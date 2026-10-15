package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.PlacementGuards;
import com.dillon.starsectormarines.battle.world.gen.fit.ConsoleBankLayout;
import com.dillon.starsectormarines.battle.world.gen.fit.ConsoleBankLayout.Cell;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayList;
import java.util.List;

/** Complete computer banks and optional walkable mess stools, shared by shells and authored stamps. */
final class BuildingConsoleBanks {
    private BuildingConsoleBanks() {}

    static boolean place(NavigationGrid grid, CellTopology topology, List<Cell> cells,
                         boolean operatorRight, List<Doodad> doodads) {
        var pieces = ConsoleBankLayout.resolveWorld(cells, false, operatorRight);
        var registry = TileRegistry.installed();
        for (var piece : pieces) {
            var def = registry.doodad(piece.id());
            if (def == null || def.footprintCellsX != 1 || def.footprintCellsY != 1
                    || !free(grid, topology, piece.cell(), doodads)) return false;
        }
        List<Cell> seats = new ArrayList<>();
        int station = 0;
        for (int i = 0; i < cells.size(); i++) {
            if (pieces.get(i).id().endsWith("corner")) continue;
            Cell at = cells.get(i), before = cells.get(i == 0 ? 0 : i - 1);
            Cell after = cells.get(i == 0 ? 1 : i);
            int sign = operatorRight ? 1 : -1;
            int dx = (after.y() - before.y()) * sign, dy = (before.x() - after.x()) * sign;
            // The stool occupies the first row visually; the second is the service aisle.
            for (int depth = 1; depth <= 2; depth++) {
                Cell work = new Cell(at.x() + dx * depth, at.y() + dy * depth);
                if (cells.contains(work) || !open(grid, topology, work, doodads)) return false;
            }
            if ((cells.size() == 2 && i == 0) || (i > 0 && i < cells.size() - 1 && station++ % 2 == 0))
                seats.add(new Cell(at.x() + dx, at.y() + dy));
        }
        // Prove the real sparse path one cell at a time; roll back a refused bank in full.
        List<Cell> blocked = new ArrayList<>();
        for (Cell cell : cells) {
            if (PlacementGuards.wouldStrandGround(grid, cell.x(), cell.y(), 1, 1)) {
                for (Cell undo : blocked) grid.setWalkable(undo.x(), undo.y(), true);
                return false;
            }
            grid.setWalkable(cell.x(), cell.y(), false);
            blocked.add(cell);
        }
        for (var piece : pieces) {
            int x = piece.cell().x(), y = piece.cell().y();
            grid.setSeeThrough(x, y, true);
            topology.setFixture(x, y, true);
            doodads.add(new Doodad(x, y, registry.doodad(piece.id()), -piece.clockwiseTurns()));
        }
        var stool = registry.doodad("mess-galley.stool");
        if (stool != null) for (Cell seat : seats)
            if (free(grid, topology, seat, doodads)) doodads.add(new Doodad(seat.x(), seat.y(), stool));
        return true;
    }

    private static boolean free(NavigationGrid grid, CellTopology topology, Cell cell, List<Doodad> doodads) {
        int x = cell.x(), y = cell.y();
        return open(grid, topology, cell, doodads)
                && !PlacementGuards.touchesDoorway(grid, x, y) && !PlacementGuards.touchesEdgeBarrier(grid, x, y);
    }

    private static boolean open(NavigationGrid grid, CellTopology topology, Cell cell, List<Doodad> doodads) {
        int x = cell.x(), y = cell.y();
        return grid.inBounds(x, y) && grid.isWalkable(x, y) && !topology.isFixture(x, y)
                && doodads.stream().noneMatch(d -> d.occupiesCell(x, y));
    }
}
