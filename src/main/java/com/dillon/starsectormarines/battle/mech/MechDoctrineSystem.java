package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.MechDoctrineService.PendingOverride;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Serial command-phase consumer for {@link MechDoctrineService}. */
public final class MechDoctrineSystem {

    private final MechDoctrineService service;

    public MechDoctrineSystem(MechDoctrineService service) {
        this.service = service;
    }

    /** Applies every queued request before the squad replan pass. */
    public void tick(BattleSimulation sim) {
        for (PendingOverride request : service.drainPending()) {
            apply(request, sim);
        }
    }

    private static void apply(PendingOverride request, BattleSimulation sim) {
        long mech = request.mechId;
        if (!sim.world().isAlive(mech)
                || !sim.world().hasMechLoadout(mech)
                || !sim.identity().has(mech)
                || sim.identity().faction(mech) != Faction.MARINE) {
            return;
        }
        Squad squad = sim.squadOf(mech);
        if (squad == null || squad.rescuePickupMech) return;

        MechLoadoutComponent loadout = sim.world().mechLoadout(mech);
        if (!loadout.applyBattleOverride(request.role)) return;

        if (sim.movement().has(mech)) sim.clearPath(mech);
        synchronized (squad.lock) {
            squad.currentPlan = null;
            squad.currentGoal = null;
        }
    }
}
