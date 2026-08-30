package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.MechDoctrineService.PendingOverride;
import com.dillon.starsectormarines.battle.mech.MechDoctrineService.PendingLanceOrder;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * Serial command-phase consumer for exact-mech doctrine requests and
 * lance-wide cohesion orders queued through {@link MechDoctrineService}.
 */
public final class MechDoctrineSystem {

    private final MechDoctrineService service;

    public MechDoctrineSystem(MechDoctrineService service) {
        this.service = service;
    }

    /** Applies every queued request before the squad replan pass. */
    public void tick(BattleSimulation sim) {
        for (PendingOverride request : service.drainPending()) {
            applyOverride(request, sim);
        }
        for (PendingLanceOrder request : service.drainPendingLanceOrders()) {
            applyLanceOrder(request, sim);
        }
    }

    private static void applyOverride(PendingOverride request, BattleSimulation sim) {
        long mech = request.mechId;
        Squad squad = validPlayerLance(mech, sim);
        if (squad == null) return;

        MechLoadoutComponent loadout = sim.world().mechLoadout(mech);
        if (!loadout.applyBattleOverride(request.role)) return;

        if (sim.movement().has(mech)) sim.clearPath(mech);
        invalidatePlan(squad);
    }

    private static void applyLanceOrder(PendingLanceOrder request,
                                        BattleSimulation sim) {
        Squad squad = validPlayerLance(request.mechId, sim);
        if (squad == null || request.order == null
                || !squad.applyLanceOrder(request.order)) return;

        for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
            long member = sim.squadMemberAt(squad.id, i);
            if (sim.world().isAlive(member)
                    && sim.world().hasMechLoadout(member)
                    && sim.movement().has(member)) {
                sim.clearPath(member);
            }
        }
        invalidatePlan(squad);
    }

    private static Squad validPlayerLance(long mech, BattleSimulation sim) {
        if (!sim.world().isAlive(mech)
                || !sim.world().hasMechLoadout(mech)
                || !sim.identity().has(mech)
                || sim.identity().faction(mech) != Faction.MARINE) {
            return null;
        }
        Squad squad = sim.squadOf(mech);
        return squad == null || squad.faction != Faction.MARINE
                || !squad.isMechSquad() || squad.rescuePickupMech
                ? null : squad;
    }

    private static void invalidatePlan(Squad squad) {
        synchronized (squad.lock) {
            squad.currentPlan = null;
            squad.currentGoal = null;
        }
    }
}
