package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.vehicle.ProgressiveVehicleField;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;

/**
 * A proved convoy journey: which gate the truck comes on at, where it sets its
 * payload down, which gate it leaves by, and the two drivable polylines between
 * them — plus the frozen progressive route view that the recovery ladder may
 * continue deriving mid-drive.
 *
 * <p>The product of a {@link RouteProofJob}, and the one thing
 * {@code ConvoyMeans.dispatch} needs from it. Nothing here is a world actor:
 * a plan exists precisely so that the actor can be created knowing the journey
 * is possible.
 */
record RoutePlan(RoadGraph.Node entry,
                 RoadGraph.Node destination,
                 RoadGraph.Node exit,
                 float[][] inbound,
                 float[][] outbound,
                 ProgressiveVehicleField fields) { }
