package com.dillon.starsectormarines.battle.turret;
import com.dillon.starsectormarines.battle.decision.UnitBehavior;
import com.dillon.starsectormarines.battle.infantry.CombatantBehavior;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.TurretStateService;
import com.dillon.starsectormarines.battle.unit.Entity;

/**
 * Static-defense behavior. Delegates the aim/fire loop to {@link TurretAim} so
 * the same logic powers shuttle-mounted turrets in
 * {@link com.dillon.starsectormarines.battle.air.AirSystem}; this class is
 * just the {@link UnitBehavior} adapter that ferries a turret's
 * {@code TURRET_STATE} fields in and out of the shared {@link TurretAim.State}
 * carrier.
 *
 * <p>Pulled out of {@link CombatantBehavior} rather than reused because the
 * cohesion / repositioning / pathfinding branches don't apply, and bolting
 * facing-tracking into that class would pollute the mobile-unit path.
 */
public final class TurretBehavior implements UnitBehavior {

    public static final TurretBehavior INSTANCE = new TurretBehavior();

    private TurretBehavior() {}

    @Override
    public void update(long u, BattleSimulation sim) {
        long id = u;
        TurretStateService turretState = sim.turretState();

        // Age the per-shot recoil timer every tick; reset to 0 on each fired
        // round below. The renderer reads this to drive barrel slide so a
        // burst recoils per round, not just on the trigger pull.
        turretState.setRecoilTimer(id, turretState.recoilTimer(id) + BattleSimulation.TICK_DT);

        // Drop a stale burst if its victim died — frees the mount to
        // re-acquire, same shape as the shuttle-mounted equivalent in
        // AirSystem.tickShuttleTurrets. Reads the TURRET_STATE-shadow
        // burstTargetId (not the inherited COMBAT one — see the
        // BattleComponents#TURRET_STATE class doc).
        int burstRemaining = turretState.burstRemaining(id);
        Entity currentBurstTarget = sim.resolveUnit(turretState.burstTargetId(id));
        if (burstRemaining > 0 && currentBurstTarget == null) {
            burstRemaining = 0;
            turretState.setBurstRemaining(id, 0);
            turretState.setBurstTargetId(id, 0L);
        }
        // Pin slew target during a burst so the barrel tracks the salvo
        // victim instead of drifting toward a fresh acquisition mid-burst.
        // Direct id-to-id copy (not setTarget) — both fields are already
        // entity ids in the same id space, no null encoding to apply.
        if (burstRemaining > 0) {
            sim.world().setTargetId(id, turretState.burstTargetId(id));
        }

        TurretKind kind = turretState.kind(id);
        TurretAim.State s = new TurretAim.State();
        s.originCellX = sim.world().cellX(id);
        s.originCellY = sim.world().cellY(id);
        s.originX = sim.world().cellX(id) + 0.5f;
        s.originY = sim.world().cellY(id) + 0.5f;
        s.faction = sim.identity().faction(u);
        s.squadId = sim.squad().hasSquad(id) ? sim.squad().squadId(id) : Entity.NO_SQUAD;
        s.excludeFromCrowding = u;
        s.facingDegrees = turretState.facingDegrees(id);
        s.turnRateDegPerSec = kind.turnRateDegPerSec;
        s.attackRange = sim.world().attackRange(id);
        s.minRange = kind.minRange;
        s.cooldownTimer = sim.world().cooldownTimer(id);
        s.attackCooldown = sim.combat().attackCooldown(id);
        s.target = sim.targetOf(u);
        s.indirectFire = kind.indirectFire;

        TurretAim.tick(s, sim.getTacticalScoring(), sim.getGrid(), sim.world(), sim.vision(), BattleSimulation.TICK_DT);

        turretState.setFacingDegrees(id, s.facingDegrees);
        sim.world().setCooldownTimer(id, s.cooldownTimer);
        sim.world().setTargetId(id, Entity.idOf(s.target));

        // Burst continuation runs ahead of fresh trigger pulls. A committed
        // salvo finishes its rounds before the aim loop kicks another.
        if (burstRemaining > 0) {
            float burstTimer = turretState.burstTimer(id) - BattleSimulation.TICK_DT;
            if (burstTimer <= 0f) {
                // Recompute LoS for each burst round — target moves, LoS state
                // can flip mid-salvo. Indirect-fire kinds use this to switch
                // the per-rocket accuracy between full and no-LoS-multiplier.
                // Direct-fire kinds gate LoS in the aim loop so by the time
                // the burst started, LoS was good; the renderer keeps firing
                // even if LoS breaks mid-burst, matching the existing behavior.
                boolean hasLos = sim.getGrid().hasLineOfSight(
                        sim.world().cellX(id), sim.world().cellY(id), sim.world().cellX(currentBurstTarget.entityId), sim.world().cellY(currentBurstTarget.entityId));
                sim.fireShotFrom(sim.world().cellX(id) + 0.5f, sim.world().cellY(id) + 0.5f, sim.identity().faction(u), kind, currentBurstTarget,
                        /*aerialShooter*/ false, hasLos);
                turretState.setRecoilTimer(id, 0f);
                burstRemaining--;
                turretState.setBurstRemaining(id, burstRemaining);
                turretState.setBurstTimer(id, kind.burstSpacing);
                if (burstRemaining == 0) turretState.setBurstTargetId(id, 0L);
            } else {
                turretState.setBurstTimer(id, burstTimer);
            }
            return;
        }

        if (s.fireThisTick) {
            if (kind.burstCount > 1) {
                // Burst kinds route through fireShotFrom so the scatter / AoE /
                // raycast pipeline applies. Latch the remaining rounds for the
                // pump to drain.
                sim.fireShotFrom(sim.world().cellX(id) + 0.5f, sim.world().cellY(id) + 0.5f, sim.identity().faction(u), kind, s.target,
                        /*aerialShooter*/ false, s.lastFireHadLos);
                turretState.setRecoilTimer(id, 0f);
                if (s.target != null) {
                    turretState.setBurstRemaining(id, kind.burstCount - 1);
                    turretState.setBurstTimer(id, kind.burstSpacing);
                    turretState.setBurstTargetId(id, s.target.entityId);
                }
            } else {
                // Single-shot kinds keep the existing Entity-vs-Entity fire path
                // so morale impact + ShotEvent tagging stay correct for the
                // unchanged ground turrets (Arbalest, Hephaestus, etc.).
                sim.fireShot(u, s.target.entityId);
                turretState.setRecoilTimer(id, 0f);
            }
        }
    }
}
