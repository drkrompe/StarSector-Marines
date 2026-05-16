package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.EquipmentDrop;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.UnitRole;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

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

        PlanterBehavior.fireOpportunistically(u, sim);

        if (u.moveProgress == 0f) {
            sim.setPath(u, GridPathfinder.findPath(sim.getGrid(), u.cellX, u.cellY, drop.cellX, drop.cellY, sim.getOccupancyMap()));
        }
        sim.advanceMovement(u);
    }
}
