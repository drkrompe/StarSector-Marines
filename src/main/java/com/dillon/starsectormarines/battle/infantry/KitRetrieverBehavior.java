package com.dillon.starsectormarines.battle.infantry;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.UnitBehavior;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.unit.Entity;
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
 * {@link BattleSimulation#processEquipmentDrops} sweeps every tick and
 * promotes whoever happens to be standing on a drop cell (this retriever
 * or any opportunist who walked over).
 */
public final class KitRetrieverBehavior implements UnitBehavior {

    public static final KitRetrieverBehavior INSTANCE = new KitRetrieverBehavior();

    private KitRetrieverBehavior() {}

    @Override
    public void update(Entity u, BattleSimulation sim) {
        EquipmentDrop drop = sim.task().equipmentDropTarget(u.entityId);
        if (drop == null || drop.consumed) {
            sim.role().setRole(u.entityId, UnitRole.COMBATANT);
            sim.task().clearEquipmentDropTarget(u.entityId);
            CombatantBehavior.INSTANCE.update(u, sim);
            return;
        }

        InfantryUnitPrep.tickCooldowns(u, sim.world());
        fireOpportunistically(u, sim);

        if (sim.world().moveProgress(u.entityId) == 0f) {
            sim.setPath(u, GridPathfinder.findPath(sim.getGrid(), sim.world().cellX(u.entityId), sim.world().cellY(u.entityId), drop.cellX, drop.cellY, sim.getOccupancyMap()));
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
    private static void fireOpportunistically(Entity u, BattleControl sim) {
        Entity target = sim.targetOf(u);
        if (target == null) {
            target = sim.getTacticalScoring().findBestTarget(u);
            sim.world().setTargetId(u.entityId, Entity.idOf(target));
        }
        if (target == null) return;
        float dist = TacticalScoring.cellDistance(sim.world().cellX(u.entityId), sim.world().cellY(u.entityId), sim.world().cellX(target.entityId), sim.world().cellY(target.entityId));
        boolean canFire = dist <= sim.world().attackRange(u.entityId)
                && sim.getGrid().hasLineOfSight(sim.world().cellX(u.entityId), sim.world().cellY(u.entityId), sim.world().cellX(target.entityId), sim.world().cellY(target.entityId));
        if (canFire) {
            // Retriever fires while pathing to a kit — MOVING accuracy penalty.
            // Authors intent; FiringSystem applies the cooldown gate and
            // executes the shot.
            sim.combat().setFireIntent(u.entityId, Entity.idOf(target), FireStance.MOVING, false);
        }
    }
}
