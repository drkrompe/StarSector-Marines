package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.UnitRole;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.objective.ChargeSiteObjective;

/**
 * Planter: head to the assigned charge site, dwell on the anchor cell so
 * {@link ChargeSiteObjective#tick} accumulates plant progress, fire
 * opportunistically at any visible enemy in range. If the assigned
 * objective is null or complete, demotes to {@link UnitRole#COMBATANT}
 * (clearing {@code assignedObjective}) so the unit becomes available for
 * combat AI and the kit-reassignment pass.
 */
public final class PlanterBehavior implements UnitBehavior {

    public static final PlanterBehavior INSTANCE = new PlanterBehavior();

    private PlanterBehavior() {}

    @Override
    public void update(Unit u, BattleSimulation sim) {
        if (!(u.assignedObjective instanceof ChargeSiteObjective)
                || u.assignedObjective.isComplete()) {
            u.role = UnitRole.COMBATANT;
            u.assignedObjective = null;
            CombatantBehavior.INSTANCE.update(u, sim);
            return;
        }
        ChargeSiteObjective site = (ChargeSiteObjective) u.assignedObjective;
        int sx = site.cellX();
        int sy = site.cellY();

        fireOpportunistically(u, sim);

        if (u.cellX == sx && u.cellY == sy) {
            if (!u.pathEmpty()) sim.clearPath(u);
            u.moveProgress = 0f;
            u.renderX = u.cellX;
            u.renderY = u.cellY;
            return;
        }

        if (u.moveProgress == 0f) {
            sim.setPath(u, GridPathfinder.findPath(sim.getGrid(), u.cellX, u.cellY, sx, sy, sim.getOccupancyMap()));
        }
        sim.advanceMovement(u);
    }

    /**
     * Shared cooldown + range + LOS check + fire call, identical for planters
     * and kit retrievers. Both roles fire if they happen to have a clean shot
     * but never break path to engage — the objective is the goal.
     */
    static void fireOpportunistically(Unit u, BattleSimulation sim) {
        if (u.target == null || !u.target.isAlive()) {
            u.target = TacticalScoring.findBestTarget(u, sim);
        }
        if (u.cooldownTimer > 0f) u.cooldownTimer -= BattleSimulation.TICK_DT;
        if (u.target == null) return;
        float dist = TacticalScoring.cellDistance(u.cellX, u.cellY, u.target.cellX, u.target.cellY);
        boolean canFire = dist <= u.attackRange
                && sim.getGrid().hasLineOfSight(u.cellX, u.cellY, u.target.cellX, u.target.cellY)
                && u.cooldownTimer <= 0f;
        if (canFire) {
            sim.fireShot(u, u.target);
            u.cooldownTimer = u.attackCooldown;
        }
    }
}
