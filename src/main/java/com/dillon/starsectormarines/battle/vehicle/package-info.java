/**
 * Actor domain — ground-vehicle kinematics.
 *
 * <p>Category: actor domain (entity + kinematics + lifecycle).
 * <br>Charter:  the {@code GroundBody}/{@code BicycleBody} motion model,
 *           {@code PurePursuit} steering, cost-field + local Hybrid-A* routing,
 *           terminal Reeds-Shepp docking, convoy missions and control, and the
 *           component-native {@code VehicleType} model. Parked road vehicles
 *           are scenery, not vehicles: they are registry doodads.
 * <br>Boundary: a chassis as something to shoot at is not here: it is
 *           {@code ConvoyService}'s {@code BodyCarrier} implementation, and
 *           the durability law and damage route it feeds are shared with every
 *           other carried body ({@code ecs-nouns.md}).
 *           Ground kinematics use the bicycle model, NOT
 *           {@code air/AirBody}. {@code VehicleType.createBody()} is the
 *           extension seam for new chassis (tanks, etc.) — add a chassis
 *           there rather than branching the kinematics.
 *
 * <p>See {@code convoy-nouns.md} for the standing feature model and
 * {@link com.dillon.starsectormarines.battle} for the full taxonomy.
 */
package com.dillon.starsectormarines.battle.vehicle;
