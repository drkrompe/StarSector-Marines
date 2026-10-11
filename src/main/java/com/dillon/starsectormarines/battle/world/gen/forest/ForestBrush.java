package com.dillon.starsectormarines.battle.world.gen.forest;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import java.util.ArrayList;
import java.util.List;

/** Generation-time forest construction. Editing replays the plan on clean ground, never a live battle. */
public final class ForestBrush {
    private ForestBrush() {}
    public static ForestField stamp(ForestPlan plan, NavigationGrid grid, CellTopology topology,
                                    List<Doodad> doodads, boolean[] reserved) {
        if (grid.getWidth() != plan.width() || grid.getHeight() != plan.height()
                || topology.getWidth() != plan.width() || topology.getHeight() != plan.height()
                || (reserved != null && reserved.length != plan.width() * plan.height()))
            throw new IllegalArgumentException("Forest and map dimensions differ");
        ForestField field = ForestField.build(plan);
        TileRegistry tiles = TileRegistry.installed();
        List<Doodad> additions = new ArrayList<>();
        for (var tree : field.trees()) {
            var def = tiles == null ? null : tiles.doodad(tree.canopy().id);
            if (def == null || def.footprintCellsX != tree.canopy().width || def.footprintCellsY != tree.canopy().height)
                throw new IllegalStateException("Missing or mismatched tree canopy " + tree.canopy().id);
            additions.add(new Doodad(tree.x(), tree.y(), def, tree.turns()));
        }
        // The entire canopy is protected, not just its trunk. Refusal is atomic.
        for (int y = 0; y < plan.height(); y++) for (int x = 0; x < plan.width(); x++) if (field.blocked(x, y)) {
            boolean natural = switch (topology.getGroundKind(x, y)) { case GRASS, DIRT, STONE, SAND, SNOW -> true; default -> false; };
            if (!natural || !grid.isWalkable(x, y) || grid.isDoorway(x, y) || topology.isWall(x, y)
                    || topology.isFixture(x, y) || topology.getBuildingId(x, y) != 0 || topology.getSurface(x, y) != 0
                    || (topology.roadSurface() != null && topology.roadSurface().underlay(x, y) != null)
                    || (reserved != null && reserved[y * plan.width() + x]))
                throw new IllegalArgumentException("Forest overlaps protected ground at " + x + "," + y + "; erase or move this stroke");
        }
        for (int y = 0; y < plan.height(); y++) for (int x = 0; x < plan.width(); x++) if (field.blocked(x, y)) {
            grid.setWalkable(x, y, false); grid.setSeeThrough(x, y, false);
            grid.setTag(x, y, CellTag.NATURAL_SOLID, true); grid.setWallHp(x, y, 0);
            topology.setFixture(x, y, true); topology.setNatureOverlayIndex(x, y, -1);
        }
        doodads.addAll(additions);
        return field;
    }
}
