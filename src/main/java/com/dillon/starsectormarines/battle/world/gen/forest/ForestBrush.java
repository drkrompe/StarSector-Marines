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
        stampTrees(field.trees(), grid, topology, doodads, reserved, false);
        return field;
    }

    /** Selected production crowns may stand on complete grassy summit cells, never on cliff faces. */
    public static void stampTrees(List<ForestField.Tree> trees, NavigationGrid grid, CellTopology topology,
                                  List<Doodad> doodads, boolean[] reserved, boolean allowSummits) {
        int w = grid.getWidth(), h = grid.getHeight();
        if (topology.getWidth() != w || topology.getHeight() != h || (reserved != null && reserved.length != w * h))
            throw new IllegalArgumentException("Forest and map dimensions differ");
        boolean[] blocked = new boolean[w * h];
        TileRegistry tiles = TileRegistry.installed();
        List<Doodad> additions = new ArrayList<>();
        for (var tree : trees) {
            if (tree.x() < 0 || tree.y() < 0 || tree.x() + tree.width() > w || tree.y() + tree.height() > h)
                throw new IllegalArgumentException("Tree extends beyond map");
            for (int y = tree.y(); y < tree.y() + tree.height(); y++)
                for (int x = tree.x(); x < tree.x() + tree.width(); x++)
                    if (tree.blocks(x, y)) blocked[y * w + x] = true;
            var def = tiles == null ? null : tiles.doodad(tree.canopy().id);
            if (def == null || def.footprintCellsX != tree.canopy().width || def.footprintCellsY != tree.canopy().height)
                throw new IllegalStateException("Missing or mismatched tree canopy " + tree.canopy().id);
            additions.add(new Doodad(tree.x(), tree.y(), def, tree.turns()));
        }
        // The entire canopy is protected, not just its trunk. Refusal is atomic.
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (blocked[y * w + x]) {
            boolean natural = switch (topology.getGroundKind(x, y)) { case GRASS, DIRT, STONE, SAND, SNOW -> true; default -> false; };
            boolean summit = allowSummits && isGrassySummit(grid, topology, x, y);
            if (!natural || (!grid.isWalkable(x, y) && !summit) || grid.isDoorway(x, y) || topology.isWall(x, y)
                    || (topology.isFixture(x, y) && !summit) || topology.getBuildingId(x, y) != 0 || topology.getSurface(x, y) != 0
                    || (topology.roadSurface() != null && topology.roadSurface().underlay(x, y) != null)
                    || (reserved != null && reserved[y * w + x]))
                throw new IllegalArgumentException("Forest overlaps protected ground at " + x + "," + y + "; erase or move this stroke");
        }
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (blocked[y * w + x]) {
            grid.setWalkable(x, y, false); grid.setSeeThrough(x, y, false);
            grid.setTag(x, y, CellTag.NATURAL_SOLID, true); grid.setWallHp(x, y, 0);
            topology.setFixture(x, y, true); topology.setNatureOverlayIndex(x, y, -1);
        }
        doodads.addAll(additions);
    }

    /** A full uncut grass cap; a partial cap would let the sprite spill down the cliff. */
    public static boolean isGrassySummit(NavigationGrid grid, CellTopology topology, int x, int y) {
        if (!grid.hasTag(x, y, CellTag.NATURAL_SOLID) || topology.terrainSurface() == null) return false;
        var patch = topology.terrainSurface().patch(x, y, 6);
        return patch != null && patch.surface() == 0 && topology.terrainSurface().isFullSummit(x, y);
    }
}
