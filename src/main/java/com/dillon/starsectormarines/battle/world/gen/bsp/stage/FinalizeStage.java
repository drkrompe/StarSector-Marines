package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.BuildingFloodFill;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.gen.road.VehicleCorridor;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.WallMasks;
import org.apache.log4j.Logger;

/**
 * Step 4 — finalize: HP on walls, cover bake, wall flag, then flood-fill
 * building interiors from the stamped kind hints. The flood-fill runs after
 * {@code tagDefaultWalls} so the wall predicate it reads is authoritative; its
 * result is the {@link Buildings} registry that drives the roof-render and
 * fog-of-war visibility passes, bound under {@link BspKeys#BUILDINGS}.
 *
 * <p>Closes with two diagnostics: any road-graph centerline cell that ended up
 * non-walkable, and any pinch that took the vehicle corridor below a drivable
 * width. Both mean a stamper trampled a route despite the reservation, and both
 * are logged once per generation so a regression surfaces in
 * {@code starsector.log}.
 */
public final class FinalizeStage implements GenStage {

    private static final Logger LOG = Logger.getLogger(FinalizeStage.class);

    /** Default starting wall HP — matches legacy {@code UrbanMapGenerator.WALL_HP_DEFAULT}. */
    private static final int WALL_HP_DEFAULT = 100;

    /**
     * Drivable lines the vehicle corridor must keep across its narrowest
     * section. An intact {@link VehicleCorridor#WIDTH}-wide band leaves three;
     * a building crowding one kerb leaves two, which still drives. One is the
     * failure {@link VehicleCorridor} exists to prevent.
     */
    private static final int MIN_DRIVABLE_LINES = 2;

    @Override
    public void run(GenContext ctx) {
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;

        seedWallHp(grid, topology);
        bakeCoverFromWalls(grid);
        topology.tagDefaultWalls(grid);
        // A wall nobody stamped draws the block's transparent centre, which is
        // to say nothing at all. Buildings stamp their own; ship decks never
        // did, so every bulkhead on every deck was invisible.
        WallMasks.stampUnclaimed(topology);

        Buildings buildings = BuildingFloodFill.populate(topology, ctx.seed);
        ctx.put(BspKeys.BUILDINGS, buildings);

        verifyRoadGraphWalkable(grid, ctx.get(BspKeys.ROAD_GRAPH));
        verifyVehicleCorridorDrivable(grid, ctx.get(BspKeys.VEHICLE_CORRIDOR));
    }

    /** Structural non-walkable cells get starting HP; water and non-structural fixtures are not destructible walls. */
    private static void seedWallHp(NavigationGrid grid, CellTopology topology) {
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!grid.isWalkable(x, y) && !topology.isWater(x, y)
                        && !topology.isFixture(x, y)) {
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

    /**
     * The same alarm for the vehicle corridor, which the one above cannot
     * raise: it walks a one-cell centerline, so a band whittled from five cells
     * to one reads as perfectly intact there. What matters to a convoy is how
     * many lines across the band a {@code HEAVY_APC} still fits on, and one
     * line is not a road — a single wreck closes it and nothing can pass.
     */
    private static void verifyVehicleCorridorDrivable(NavigationGrid grid, VehicleCorridor corridor) {
        if (corridor == null) return;
        int radius = VehicleClearance.radiusForWidth(VehicleType.HEAVY_APC.visualWidthCells);
        VehicleClearance clearance = VehicleClearance.erode(grid, radius);

        boolean alongX = corridor.axis == TraversalAxis.WEST_TO_EAST;
        int sections = alongX ? grid.getWidth() : grid.getHeight();
        int across = alongX ? grid.getHeight() : grid.getWidth();
        int narrowest = Integer.MAX_VALUE;
        int narrowestAt = -1;
        // The outermost sections are skipped rather than measured: erosion
        // reads the cells beyond the map border as solid, so the band is
        // impassable there by construction and would report a false pinch.
        for (int s = radius; s < sections - radius; s++) {
            int lines = 0;
            for (int a = 0; a < across; a++) {
                int x = alongX ? s : a;
                int y = alongX ? a : s;
                if (corridor.contains(x, y) && clearance.isPassable(x, y)) lines++;
            }
            if (lines < narrowest) {
                narrowest = lines;
                narrowestAt = s;
            }
        }
        if (narrowest < MIN_DRIVABLE_LINES) {
            LOG.warn("BspCityGenerator: vehicle corridor narrows to " + narrowest
                    + " drivable line(s) at " + (alongX ? "x=" : "y=") + narrowestAt
                    + " — a stamper bypassed the reservation");
        }
    }
}
