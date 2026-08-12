package com.dillon.starsectormarines.battle.infantry;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.UnitBehavior;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.combat.FireStance;

/**
 * Kit-retriever: head for the assigned drop, fire opportunistically along
 * the way. If the drop has been consumed by someone else (or the pointer
 * is null), demote back to combatant — the unit will pick up combat
 * targeting normally next tick.
 *
 * <p>Pickup itself isn't handled here:
 * {@link EquipmentDropSystem#tick()} sweeps every tick and
 * promotes whoever happens to be standing on a drop cell (this retriever
 * or any opportunist who walked over).
 */
public final class KitRetrieverBehavior implements UnitBehavior {

    public static final KitRetrieverBehavior INSTANCE = new KitRetrieverBehavior();

    private KitRetrieverBehavior() {}

    @Override
    public void update(long u, BattleSimulation sim) {
        EquipmentDrop drop = sim.task().equipmentDropTarget(u);
        if (drop == null || drop.consumed) {
            sim.role().setRole(u, UnitRole.COMBATANT);
            sim.task().clearEquipmentDropTarget(u);
            CombatantBehavior.INSTANCE.update(u, sim);
            return;
        }

        InfantryUnitPrep.tickCooldowns(u, sim.world());
        fireOpportunistically(u, sim);

        if (sim.movement().mayRepath(u)) {
            sim.setPath(u, GridPathfinder.findPath(sim.getGrid(), sim.world().cellX(u), sim.world().cellY(u), drop.cellX, drop.cellY, sim.getOccupancyMap()));
        }
        sim.advanceMovement(u);
    }

    /**
     * Inline opportunistic fire — was a shared helper on {@code PlanterBehavior}
     * when planters had a bespoke path. Now that planters route through GOAP,
     * the kit-retriever is the last role still on a per-unit dispatch that
     * fires while moving, so the helper lives here. {@code update()} now
     * routes cooldown ticking through {@code InfantryUnitPrep.tickCooldowns}
     * like the GOAP path does before {@code Action.execute} — a deliberate
     * change from the old inline (primary-only) decrement: secondary and
     * reposition cooldowns now tick during retrieval too.
     */
    private static void fireOpportunistically(long u, BattleControl sim) {
        long target = sim.targetOf(u);
        if (target == 0L) {
            target = sim.getTacticalScoring().findBestTarget(u);
            sim.world().setTargetId(u, target);
        }
        if (target == 0L) return;
        float dist = TacticalScoring.cellDistance(sim.world().cellX(u), sim.world().cellY(u), sim.world().cellX(target), sim.world().cellY(target));
        boolean canFire = dist <= sim.world().attackRange(u)
                && sim.getGrid().hasLineOfSight(sim.world().cellX(u), sim.world().cellY(u), sim.world().cellX(target), sim.world().cellY(target));
        if (canFire) {
            // Retriever fires while pathing to a kit — MOVING accuracy penalty.
            // Authors intent; FiringSystem applies the cooldown gate and
            // executes the shot.
            sim.combat().setFireIntent(u, target, FireStance.MOVING, false);
        }
    }
}
