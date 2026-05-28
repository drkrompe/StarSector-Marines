/**
 * Actor domain — ground-vehicle kinematics.
 *
 * <p>Category: actor domain (entity + kinematics + lifecycle).
 * <br>Charter:  the {@code GroundBody}/{@code BicycleBody} motion model,
 *           {@code PurePursuit} steering, Hybrid-A* + Reeds-Shepp planning
 *           ({@code HybridAStarPlanner}, {@code ReedsShepp}, {@code Pose}),
 *           convoys ({@code ConvoyPlanner}, {@code GroundSystem}), and the
 *           {@code Vehicle}/{@code VehicleType}/{@code MapVehicle} model.
 * <br>Boundary: ground kinematics use the bicycle model, NOT
 *           {@code air/AirBody}. {@code VehicleType.createBody()} is the
 *           extension seam for new chassis (tanks, etc.) — add a chassis
 *           there rather than branching the kinematics.
 *
 * <p>See {@code roadmap/battle-reorg/overview.md} for the full taxonomy.
 */
package com.dillon.starsectormarines.battle.vehicle;
