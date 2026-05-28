package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.equipment.EquipmentDrop;
import com.dillon.starsectormarines.battle.unit.Unit;
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
    public void update(Unit u, BattleSimulation sim) {
        EquipmentDrop drop = u.equipmentDropTarget;
        if (drop == null || drop.consumed) {
            u.role = UnitRole.COMBATANT;
            u.equipmentDropTarget = null;
            CombatantBehavior.INSTANCE.update(u, sim);
            return;
        }

        fireOpportunistically(u, sim);

        if (u.getMoveProgress() == 0f) {
            sim.setPath(u, GridPathfinder.findPath(sim.getGrid(), u.getCellX(), u.getCellY(), drop.cellX, drop.cellY, sim.getOccupancyMap()));
        }
        sim.advanceMovement(u);
    }

    /**
     * Inline opportunistic fire — was a shared helper on {@code PlanterBehavior}
     * when planters had a bespoke path. Now that planters route through GOAP,
     * the kit-retriever is the last role still on a per-unit dispatch that
     * fires while moving, so the helper lives here. Decrements the cooldown
     * itself (unlike the GOAP path where {@code InfantryUnitPrep.tickCooldowns}
     * does it before {@code Action.execute}).
     */
    private static void fireOpportunistically(Unit u, BattleSimulation sim) {
        Unit target = sim.targetOf(u);
        if (target == null) {
            target = sim.getTacticalScoring().findBestTarget(u);
            u.setTarget(target);
        }
        if (u.getCooldownTimer() > 0f) u.setCooldownTimer(u.getCooldownTimer() - BattleSimulation.TICK_DT);
        if (target == null) return;
        float dist = TacticalScoring.cellDistance(u.getCellX(), u.getCellY(), target.getCellX(), target.getCellY());
        boolean canFire = dist <= u.getAttackRange()
                && sim.getGrid().hasLineOfSight(u.getCellX(), u.getCellY(), target.getCellX(), target.getCellY())
                && u.getCooldownTimer() <= 0f;
        if (canFire) {
            // Retriever fires while pathing to a kit — MOVING accuracy penalty.
            sim.fireShot(u, target, FireStance.MOVING);
            u.setCooldownTimer(u.attackCooldown);
            u.beginBurst(target);
        }
    }
}
