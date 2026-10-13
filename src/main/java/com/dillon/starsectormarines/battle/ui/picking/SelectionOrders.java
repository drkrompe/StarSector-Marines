package com.dillon.starsectormarines.battle.ui.picking;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Fans a world gesture out through the existing simulation-owned order mailboxes. */
public final class SelectionOrders {
    private SelectionOrders() { }

    public static void requestMove(BattleSimulation sim, Selection selection, int x, int y) {
        if (sim == null || selection == null || sim.directControl().active()
                || !sim.getGrid().inBounds(x, y)) return;
        if (selection.hasWarshipSelection()) {
            sim.shipCommands().requestMove(selection.getSelectedWarship(), x + .5f, y + .5f);
            return;
        }
        long vehicle = selection.getSelectedVehicleId();
        if (vehicle != 0L) {
            sim.getVehicleMoveOrderService().requestMove(vehicle, x, y);
            return;
        }
        long primary = selection.getSelectedUnitEntityId();
        if (selection.squadCount() == 1 && primary != 0L
                && sim.world().hasMechLoadout(primary)) {
            sim.getMechMoveOrderService().requestMove(primary, x, y);
            return;
        }
        for (int id : selection.getSelectedSquadIds()) {
            Squad squad = sim.getSquad(id);
            if (squad == null || squad.faction != Faction.MARINE || squad.rescuePickupMech) continue;
            if (squad.isMechSquad()) {
                for (int i = 0; i < sim.squadMemberCount(id); i++) {
                    long member = sim.squadMemberAt(id, i);
                    if (sim.world().isAlive(member) && sim.world().hasMechLoadout(member)) {
                        sim.getMechMoveOrderService().requestMove(member, x, y);
                    }
                }
            } else {
                sim.getSquadMoveOrderService().requestMove(id, x, y);
            }
        }
    }
}
