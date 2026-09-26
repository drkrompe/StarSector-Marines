package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.combat.PointFireAim;

@FunctionalInterface
public interface TurretFireSink {

    void fire(long shooterId, float fromX, float fromY, Faction shooterFaction,
              StructureDef structure, long target, boolean aerialShooter, boolean hasLos,
              float mountFacingDegrees, int releaseIndex);

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
