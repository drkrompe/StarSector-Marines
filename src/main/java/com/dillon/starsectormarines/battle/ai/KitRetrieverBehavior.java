package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.EquipmentDrop;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.UnitRole;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.weapons.FireStance;

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

        if (u.moveProgress == 0f) {
            sim.setPath(u, GridPathfinder.findPath(sim.getGrid(), u.cellX, u.cellY, drop.cellX, drop.cellY, sim.getOccupancyMap()));
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
            // Retriever fires while pathing to a kit — MOVING accuracy penalty.
            sim.fireShot(u, u.target, FireStance.MOVING);
            u.cooldownTimer = u.attackCooldown;
            u.beginBurst(u.target);
        }
    }
}
