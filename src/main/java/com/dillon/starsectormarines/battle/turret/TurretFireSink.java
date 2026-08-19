package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.unit.Faction;

@FunctionalInterface
public interface TurretFireSink {

    void fire(long shooterId, float fromX, float fromY, Faction shooterFaction,
              TurretKind kind, long target, boolean aerialShooter, boolean hasLos);

    default void fire(float fromX, float fromY, Faction shooterFaction,
                      TurretKind kind, long target, boolean aerialShooter) {
        fire(0L, fromX, fromY, shooterFaction, kind, target, aerialShooter, true);
    }

    default void fire(float fromX, float fromY, Faction shooterFaction,
                      TurretKind kind, long target, boolean aerialShooter, boolean hasLos) {
        fire(0L, fromX, fromY, shooterFaction, kind, target, aerialShooter, hasLos);
    }
}
