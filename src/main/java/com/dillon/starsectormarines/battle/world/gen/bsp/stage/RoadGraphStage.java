package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.TrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraphBuilder;
import com.dillon.starsectormarines.battle.world.gen.road.RoadReservation;

/**
 * Step 2c — extract the vehicle-navigation skeleton from the road mask before
 * fillers run. The graph is built once over the trunk + BSP-frame road cells
 * produced so far; fillers paint inside their own leaves and never touch road
 * cells, so the skeleton is stable through the rest of the pipeline. It flows
 * into {@code MapResult} for runtime ground-vehicle pathing.
 *
 * <p>{@link BspKeys#ROAD_RESERVATION} is the cell mask of every graph node +
 * edge cell. Every stamper that runs AFTER the graph reads it so they don't
 * clobber a centerline — defense posts, fortress wall, beach shoreline,
 * compound walls/bridges. Without this, a turret pad or perimeter wall lands on
 * a road cell, the convoy graph is intact in memory but the cell underneath is
 * non-walkable, and the truck visually drives "through" the stamped structure.
 *
 * <p>Reads {@link BspKeys#TRUNK_PLAN}; binds {@link BspKeys#ROAD_CELLS},
 * {@link BspKeys#ROAD_RESERVATION}, {@link BspKeys#ROAD_GRAPH}.
 */
public final class RoadGraphStage implements GenStage {

    @Override
    public void run(GenContext ctx) {
        TrunkPlan.Plan plan = ctx.get(BspKeys.TRUNK_PLAN);
        RoadGraph roadGraph = RoadGraphBuilder.build(plan.roadCells, plan);
        boolean[][] roadReservation = RoadReservation.mask(roadGraph, ctx.width, ctx.height);
        ctx.put(BspKeys.ROAD_CELLS, plan.roadCells);
        ctx.put(BspKeys.ROAD_RESERVATION, roadReservation);
        ctx.put(BspKeys.ROAD_GRAPH, roadGraph);
    }
}
