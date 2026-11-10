package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.Habitat;
import com.dillon.starsectormarines.battle.world.gen.HabitatZoning;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

/**
 * Builds the finished town for the world it stands on ({@link Habitat}): every town wall in the habitat's material,
 * and on a world nobody lives on in the open a marked threshold outside each outside door, the lock's
 * decontamination or airlock apron. Runs once the town is placed and its open ground settled, so it sees every wall
 * and door. Inert for the open town, which is the map it was.
 *
 * <p>Presentation only. A wall keeps its cell, tags and hit points and changes only its surface, and its doors and
 * windows are drawn from their jamb walls, so they follow. A fort lot's walls are its works' (`fort-works.md`) and are
 * left to them. A threshold is a floor marking with no footprint or cover, laid only on open ground nothing else
 * stands on.
 */
public final class HabitatStage implements GenStage {

    /** The floor marking laid outside an enclosed habitat's outside doors. */
    static final String THRESHOLD = "doodad.fl-striped-yellow";

    @Override public void run(GenContext c) {
        apply(c, HabitatZoning.forProfile(c.get(BspKeys.MARKET_PROFILE)));
    }

    /**
     * Builds a placed town in {@code habitat}: the call for a site that lays its own buildings with its own recipe, after
     * they are stamped. Inert for the open town.
     */
    public static void apply(GenContext c, Habitat habitat) {
        if (habitat == null || habitat == Habitat.OPEN) return;
        rebuild(c.topology, habitat);
        if (habitat.enclosed()) markThresholds(c);
    }

    /** Every town wall, in the habitat's material. */
    static void rebuild(CellTopology topology, Habitat habitat) {
        for (int y = 0; y < topology.getHeight(); y++) {
            for (int x = 0; x < topology.getWidth(); x++) {
                if (topology.getWorksStyle(x, y) != 0) continue;
                String id = topology.getSurfaceId(x, y);
                String built = habitat.wallFor(id);
                if (built != null && !built.equals(id)) topology.setSurface(x, y, topology.surfaceIndex(built));
            }
        }
    }

    private static void markThresholds(GenContext c) {
        TileRegistry registry = TileRegistry.installed();
        DoodadDef marking = registry == null ? null : registry.doodad(THRESHOLD);
        if (marking == null) return;
        NavigationGrid grid = c.grid;
        CellTopology topology = c.topology;
        boolean[] taken = new boolean[c.width * c.height];
        for (Doodad d : c.doodads) {
            for (int yy = d.cellY; yy < d.cellY + d.footprintCellsY; yy++)
                for (int xx = d.cellX; xx < d.cellX + d.footprintCellsX; xx++)
                    if (grid.inBounds(xx, yy)) taken[yy * c.width + xx] = true;
        }
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int y = 0; y < c.height; y++) {
            for (int x = 0; x < c.width; x++) {
                if (!grid.isDoorway(x, y) || topology.getWorksStyle(x, y) != 0) continue;
                for (int[] s : sides) {
                    int ix = x - s[0], iy = y - s[1], ox = x + s[0], oy = y + s[1];
                    if (!grid.inBounds(ix, iy) || !grid.inBounds(ox, oy)) continue;
                    // An outside door: a building's floor on one side, open ground on the other.
                    if (topology.getBuildingId(ix, iy) == 0 || topology.getBuildingId(ox, oy) != 0) continue;
                    if (!grid.isWalkable(ox, oy) || grid.isDoorway(ox, oy) || topology.isFixture(ox, oy)
                            || topology.isWater(ox, oy) || taken[oy * c.width + ox]) continue;
                    c.doodads.add(new Doodad(ox, oy, marking));
                    taken[oy * c.width + ox] = true;
                }
            }
        }
    }
}
