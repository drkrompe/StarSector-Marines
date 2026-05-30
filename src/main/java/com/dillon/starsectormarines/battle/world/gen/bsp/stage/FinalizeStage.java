package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.BuildingFloodFill;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.apache.log4j.Logger;

/**
 * Step 4 — finalize: HP on walls, cover bake, wall flag, then flood-fill
 * building interiors from the stamped kind hints. The flood-fill runs after
 * {@code tagDefaultWalls} so the wall predicate it reads is authoritative; its
 * result is the {@link Buildings} registry that drives the roof-render and
 * fog-of-war visibility passes, bound under {@link BspKeys#BUILDINGS}.
 *
 * <p>Closes with a diagnostic: any road-graph centerline cell that ended up
 * non-walkable means a stamper trampled the graph despite the reservation —
 * logged once per generation so a regression surfaces in {@code starsector.log}.
 */
public final class FinalizeStage implements GenStage {

    private static final Logger LOG = Logger.getLogger(FinalizeStage.class);

    /** Default starting wall HP — matches legacy {@code UrbanMapGenerator.WALL_HP_DEFAULT}. */
    private static final int WALL_HP_DEFAULT = 100;

    @Override
    public void run(GenContext ctx) {
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;

        seedWallHp(grid, topology);
        bakeCoverFromWalls(grid);
        topology.tagDefaultWalls(grid);

        Buildings buildings = BuildingFloodFill.populate(topology, ctx.seed);
        ctx.put(BspKeys.BUILDINGS, buildings);

        verifyRoadGraphWalkable(grid, ctx.get(BspKeys.ROAD_GRAPH));
    }

    /** Every non-walkable, non-water cell gets a starting HP. Water is non-walkable but isn't a destructible wall. */
    private static void seedWallHp(NavigationGrid grid, CellTopology topology) {
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!grid.isWalkable(x, y) && !topology.isWater(x, y)) {
                    grid.setWallHp(x, y, WALL_HP_DEFAULT);
                }
            }
        }
    }

    /** Per-facing cardinal-wall bake. Each facing reads 1 if a wall sits there, else 0. */
    private static void bakeCoverFromWalls(NavigationGrid grid) {
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                grid.recomputeCoverAt(x, y);
            }
        }
    }

    private static void verifyRoadGraphWalkable(NavigationGrid grid, RoadGraph graph) {
        if (graph == null || graph.edges().isEmpty()) return;
        int blocked = 0;
        int firstX = -1, firstY = -1;
        for (RoadGraph.Edge e : graph.edges()) {
            for (int i = 0; i < e.cellsX.length; i++) {
                int x = e.cellsX[i];
                int y = e.cellsY[i];
                if (!grid.inBounds(x, y)) continue;
                if (grid.isWalkable(x, y)) continue;
                if (blocked == 0) { firstX = x; firstY = y; }
                blocked++;
            }
        }
        if (blocked > 0) {
            LOG.warn("BspCityGenerator: " + blocked + " road-graph cell(s) ended up non-walkable"
                    + " (first at " + firstX + "," + firstY + ") — a stamper bypassed the reservation");
        }
    }
}
