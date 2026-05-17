package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.MapTurret;
import com.dillon.starsectormarines.battle.Unit;

/**
 * Static-defense behavior. Delegates the aim/fire loop to {@link TurretAim} so
 * the same logic powers shuttle-mounted turrets in
 * {@link com.dillon.starsectormarines.battle.air.AirSystem}; this class is
 * just the {@link UnitBehavior} adapter that ferries {@link MapTurret} fields
 * in and out of the shared {@link TurretAim.State} carrier.
 *
 * <p>Pulled out of {@link CombatantBehavior} rather than reused because the
 * cohesion / repositioning / pathfinding branches don't apply, and bolting
 * facing-tracking into that class would pollute the mobile-unit path.
 */
public final class TurretBehavior implements UnitBehavior {

    public static final TurretBehavior INSTANCE = new TurretBehavior();

    private TurretBehavior() {}

    @Override
    public void update(Unit u, BattleSimulation sim) {
        MapTurret t = (MapTurret) u;

        TurretAim.State s = new TurretAim.State();
        s.originCellX = t.cellX;
        s.originCellY = t.cellY;
        s.originX = t.cellX + 0.5f;
        s.originY = t.cellY + 0.5f;
        s.faction = t.faction;
        s.squadId = t.squadId;
        s.excludeFromCrowding = t;
        s.facingDegrees = t.facingDegrees;
        s.turnRateDegPerSec = t.kind.turnRateDegPerSec;
        s.attackRange = t.attackRange;
        s.cooldownTimer = t.cooldownTimer;
        s.attackCooldown = t.attackCooldown;
        s.target = t.target;

        TurretAim.tick(s, sim, BattleSimulation.TICK_DT);

        t.facingDegrees = s.facingDegrees;
        t.cooldownTimer = s.cooldownTimer;
        t.target = s.target;

        if (s.fireThisTick) {
            sim.fireShot(t, s.target);
        }
    }
}
