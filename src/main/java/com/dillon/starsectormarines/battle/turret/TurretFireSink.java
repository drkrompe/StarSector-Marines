package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.weapon.BeamWander;

@FunctionalInterface
public interface TurretFireSink {

    /** Fires one round, returning false when the gun's crew held it rather than hit a friend. */
    boolean fire(long shooterId, float fromX, float fromY, Faction shooterFaction,
                 StructureDef structure, long target, boolean aerialShooter, boolean hasLos,
                 float mountFacingDegrees, int releaseIndex);

    /**
     * One pulse of a crawling beam ({@link BeamWander}): as {@link #fire(long, float, float, Faction, StructureDef,
     * long, boolean, boolean, float, int)}, its aim read off {@code wander}'s path when non-null.
     */
    default boolean fire(long shooterId, float fromX, float fromY, Faction shooterFaction,
                         StructureDef structure, long target, boolean aerialShooter, boolean hasLos,
                         float mountFacingDegrees, int releaseIndex, BeamWander wander) {
        return fire(shooterId, fromX, fromY, shooterFaction, structure, target, aerialShooter, hasLos,
                mountFacingDegrees, releaseIndex);
    }

    /** One point pulse of a crawling beam: as {@link #firePoint}, its aim read off {@code wander}'s path. */
    default boolean firePoint(long shooterId, float fromX, float fromY, Faction shooterFaction,
                              StructureDef structure, PointFireAim aim,
                              float mountFacingDegrees, int releaseIndex, BeamWander wander) {
        return firePoint(shooterId, fromX, fromY, shooterFaction, structure, aim, mountFacingDegrees, releaseIndex);
    }

    /** Emits a modeled ground point round, returning whether a round was accepted. */
    default boolean firePoint(long shooterId, float fromX, float fromY, Faction shooterFaction,
                              StructureDef structure, PointFireAim aim,
                              float mountFacingDegrees, int releaseIndex) {
        return false;
    }

    default void fire(long shooterId, float fromX, float fromY, Faction shooterFaction,
                      StructureDef structure, long target, boolean aerialShooter,
                      boolean hasLos) {
        fire(shooterId, fromX, fromY, shooterFaction, structure, target,
                aerialShooter, hasLos, Float.NaN, 0);
    }

    default void fire(float fromX, float fromY, Faction shooterFaction,
                      StructureDef structure, long target, boolean aerialShooter) {
        fire(0L, fromX, fromY, shooterFaction, structure, target, aerialShooter, true);
    }

    default void fire(float fromX, float fromY, Faction shooterFaction,
                      StructureDef structure, long target, boolean aerialShooter, boolean hasLos) {
        fire(0L, fromX, fromY, shooterFaction, structure, target, aerialShooter, hasLos);
    }
}
