/**
 * Actor domain — flying-entity kinematics.
 *
 * <p>Category: actor domain (entity + kinematics + lifecycle).
 * <br>Charter:  the {@code AirBody} motion model + {@code AirHandling} /
 *           {@code AirSystem}, shuttle missions ({@code ShuttleMission},
 *           {@code ShuttleType}, {@code ShuttleAssignment}), mounted
 *           turrets ({@code MountedTurret}, {@code TurretMount}),
 *           steering ({@code SteeringMode}), and engine slots
 *           ({@code engine/}). Shuttles and (planned) fighters share
 *           {@code AirBody}. An aircraft on its wheels is also a combat
 *           target: {@code AirTargetService} says which craft ground fire can
 *           reach and {@code AirDamageResolver} applies the shared durability
 *           law to one, both on {@code ConvoyService}'s terms.
 * <br>Boundary: motion is <em>composed</em>, not inherited — sync a unit's
 *           cell/render position from its {@code AirBody} each tick, or
 *           shots fire from the spawn point while sprites orbit.
 *           Fighters already use {@code AirBody} motion but retain legacy
 *           lifecycle/render ownership in {@code flyby/}; their final fold is
 *           tracked by {@code fighter-air-entities.md}. See
 *           {@code air-nouns.md} for the domain model.
 *
 * <p>See {@link com.dillon.starsectormarines.battle} for the full taxonomy.
 */
package com.dillon.starsectormarines.battle.air;
