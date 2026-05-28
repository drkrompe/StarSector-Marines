/**
 * Actor domain — flying-entity kinematics.
 *
 * <p>Category: actor domain (entity + kinematics + lifecycle).
 * <br>Charter:  the {@code AirBody} motion model + {@code AirHandling} /
 *           {@code AirSystem}, shuttles ({@code Shuttle},
 *           {@code ShuttleType}, {@code ShuttleAssignment}), mounted
 *           turrets ({@code MountedTurret}, {@code TurretMount}),
 *           steering ({@code SteeringMode}), and engine slots
 *           ({@code engine/}). Shuttles and (planned) fighters share
 *           {@code AirBody}.
 * <br>Boundary: motion is <em>composed</em>, not inherited — sync a unit's
 *           cell/render position from its {@code AirBody} each tick, or
 *           shots fire from the spawn point while sprites orbit.
 *           Forward-looking: {@code flyby/} fighters will be rebuilt on
 *           {@code AirBody} and fold into this package (see
 *           {@code roadmap/backlog.md}).
 *
 * <p>See {@code roadmap/battle-reorg/overview.md} for the full taxonomy.
 */
package com.dillon.starsectormarines.battle.air;
