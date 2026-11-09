package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

/**
 * One roof over the whole megastructure ({@code mapgen-nouns.md}, Ruins ground). A roof is laid over a building's floor
 * and the walls beside it, so the interior's solid hull, more than a cell from any open cell, would show bare
 * between the roofs over its corridors and rooms. This stage makes that mass a building of its own: the hull cells
 * inside the interior that touch no open cell, even across a corner, are registered as one more building, in the
 * interior's kind and with the tint of the largest building under it, so the roof runs on unbroken. The cells stay
 * solid walls; only the roof over them is new. Entering the interior lifts its corridors' roof and leaves the mass
 * roofed, so the halls read as cut into it.
 *
 * <p>Runs after {@code FinalizeStage}, which discovers the ordinary buildings.
 */
public final class RuinsRoofStage implements GenStage {

    @Override
    public void run(GenContext ctx) {
        RuinsDelve delve = ctx.get(RuinsKeys.DELVE);
        Buildings buildings = ctx.get(BspKeys.BUILDINGS);
        if (delve == null || delve.interior() == null || buildings == null) return;
        RuinsDelve.Area box = delve.interior().footprint();
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        int count = 0;
        boolean[] solid = new boolean[ctx.width * ctx.height];
        for (int y = box.bottom(); y <= box.top(); y++) {
            for (int x = box.left(); x <= box.right(); x++) {
                if (!topology.isWall(x, y) || topology.getBuildingId(x, y) != 0
                        || topology.getBuildingKindHint(x, y) != RuinsDelveStage.HULL_KIND) continue;
                if (touchesOpen(grid, x, y)) continue;
                solid[y * ctx.width + x] = true;
                count++;
            }
        }
        if (count == 0) return;
        int[] cellsX = new int[count], cellsY = new int[count];
        int n = 0, id = 0;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (Building b : buildings.all()) id = Math.max(id, b.id);
        id++;
        for (int y = box.bottom(); y <= box.top(); y++) {
            for (int x = box.left(); x <= box.right(); x++) {
                if (!solid[y * ctx.width + x]) continue;
                cellsX[n] = x;
                cellsY[n] = y;
                n++;
                topology.setBuildingId(x, y, id);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        Building largest = null;
        for (Building b : buildings.all()) {
            if (b.kind != RuinsDelveStage.HULL_KIND || !box.contains(b.cellsX[0], b.cellsY[0])) continue;
            if (largest == null || b.cellCount() > largest.cellCount()) largest = b;
        }
        float r = largest == null ? 1f : largest.tintR, g = largest == null ? 1f : largest.tintG;
        float bl = largest == null ? 1f : largest.tintB;
        buildings.add(new Building(id, RuinsDelveStage.HULL_KIND, minX, maxX, minY, maxY, cellsX, cellsY, r, g, bl));
    }

    /** Whether any of the eight cells round {@code (x, y)} is open ground. */
    static boolean touchesOpen(NavigationGrid grid, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx != 0 || dy != 0) && grid.inBounds(x + dx, y + dy) && grid.isWalkable(x + dx, y + dy)) {
                    return true;
                }
            }
        }
        return false;
    }
}
