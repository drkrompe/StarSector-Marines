package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Unit;

@FunctionalInterface
public interface TurretFireSink {

    void fire(float fromX, float fromY, Faction shooterFaction,
              TurretKind kind, Unit target, boolean aerialShooter, boolean hasLos);

    default void fire(float fromX, float fromY, Faction shooterFaction,
                      TurretKind kind, Unit target, boolean aerialShooter) {
        fire(fromX, fromY, shooterFaction, kind, target, aerialShooter, true);
    }
}
