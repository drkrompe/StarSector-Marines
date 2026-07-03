package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.unit.Faction;

@FunctionalInterface
public interface TurretFireSink {

    void fire(float fromX, float fromY, Faction shooterFaction,
              TurretKind kind, long target, boolean aerialShooter, boolean hasLos);

    default void fire(float fromX, float fromY, Faction shooterFaction,
                      TurretKind kind, long target, boolean aerialShooter) {
        fire(fromX, fromY, shooterFaction, kind, target, aerialShooter, true);
    }
}
