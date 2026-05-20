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

        // Age the per-shot recoil timer every tick; reset to 0 on each fired
        // round below. The renderer reads this to drive barrel slide so a
        // burst recoils per round, not just on the trigger pull.
        t.recoilTimer += BattleSimulation.TICK_DT;

        // Drop a stale burst if its victim died — frees the mount to
        // re-acquire, same shape as the shuttle-mounted equivalent in
        // AirSystem.tickShuttleTurrets.
        if (t.burstRemaining > 0 && (t.burstTarget == null || !t.burstTarget.isAlive())) {
            t.burstRemaining = 0;
            t.burstTarget = null;
        }
        // Pin slew target during a burst so the barrel tracks the salvo
        // victim instead of drifting toward a fresh acquisition mid-burst.
        if (t.burstRemaining > 0) {
            t.target = t.burstTarget;
        }

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
        s.minRange = t.kind.minRange;
        s.cooldownTimer = t.cooldownTimer;
        s.attackCooldown = t.attackCooldown;
        s.target = t.target;

        TurretAim.tick(s, sim, BattleSimulation.TICK_DT);

        t.facingDegrees = s.facingDegrees;
        t.cooldownTimer = s.cooldownTimer;
        t.target = s.target;

        // Burst continuation runs ahead of fresh trigger pulls. A committed
        // salvo finishes its rounds before the aim loop kicks another.
        if (t.burstRemaining > 0) {
            t.burstTimer -= BattleSimulation.TICK_DT;
            if (t.burstTimer <= 0f) {
                sim.fireShotFrom(t.cellX + 0.5f, t.cellY + 0.5f, t.faction, t.kind, t.burstTarget, /*aerialShooter*/ false);
                t.recoilTimer = 0f;
                t.burstRemaining--;
                t.burstTimer = t.kind.burstSpacing;
                if (t.burstRemaining == 0) t.burstTarget = null;
            }
            return;
        }

        if (s.fireThisTick) {
            if (t.kind.burstCount > 1) {
                // Burst kinds route through fireShotFrom so the scatter / AoE /
                // raycast pipeline applies. Latch the remaining rounds for the
                // pump to drain.
                sim.fireShotFrom(t.cellX + 0.5f, t.cellY + 0.5f, t.faction, t.kind, s.target, /*aerialShooter*/ false);
                t.recoilTimer = 0f;
                if (s.target != null && s.target.isAlive()) {
                    t.burstRemaining = t.kind.burstCount - 1;
                    t.burstTimer = t.kind.burstSpacing;
                    t.burstTarget = s.target;
                }
            } else {
                // Single-shot kinds keep the existing Unit-vs-Unit fire path
                // so morale impact + ShotEvent tagging stay correct for the
                // unchanged ground turrets (Arbalest, Hephaestus, etc.).
                sim.fireShot(t, s.target);
                t.recoilTimer = 0f;
            }
        }
    }
}
