package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.action.AttackMove;
import com.dillon.starsectormarines.battle.nav.ReachableCellResolver;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveOrder;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.PendingOrder;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * Applies a player move as a temporary infantry execution assignment.
 * Mission ownership remains in {@link Squad#assignedObjective}; the ordinary
 * attack-move plan supplies formation, contact behavior, and fire on the way.
 */
public final class SquadMoveOrderSystem {

    private final SquadMoveOrderService service;

    public SquadMoveOrderSystem(SquadMoveOrderService service) {
        this.service = service;
    }

    /** Validates requests, releases completed orders, and interrupts replans. */
    public void tick(BattleSimulation sim) {
        for (var active : service.activeEntries()) {
            int squadId = active.getKey();
            ActiveOrder order = active.getValue();
            Squad squad = validPlayerInfantrySquad(squadId, sim);
            if (squad == null) {
                release(squadId, order, sim.getSquad(squadId), sim);
                continue;
            }
            if (withdrawing(squad) || arrived(squad, order)) {
                release(squadId, order, squad, sim);
            }
        }

        for (PendingOrder request : service.drainPending()) {
            Squad squad = validPlayerInfantrySquad(request.squadId, sim);
            if (squad == null || withdrawing(squad)) continue;
            int[] origin = origin(squad, sim);
            if (origin == null) continue;
            int[] destination = ReachableCellResolver.nearest(sim.getGrid(),
                    origin[0], origin[1], request.cellX, request.cellY);
            if (destination == null) continue;

            ActiveOrder order = new ActiveOrder(request.cellX, request.cellY,
                    destination[0], destination[1]);
            service.activate(squad.id, order);
            squad.applyTacticalMoveOrder(destination[0], destination[1]);
            invalidateExecution(squad, sim);
        }
    }

    private void release(int squadId, ActiveOrder order, Squad squad,
                         BattleSimulation sim) {
        service.release(squadId, order);
        if (squad == null) return;
        squad.clearTacticalMoveOrder();
        invalidateExecution(squad, sim);
    }

    private static boolean arrived(Squad squad, ActiveOrder order) {
        return TacticalScoring.cellDistance(squad.centroidX, squad.centroidY,
                order.destinationX() + 0.5f,
                order.destinationY() + 0.5f) <= AttackMove.ARRIVAL_RADIUS;
    }

    private static boolean withdrawing(Squad squad) {
        ObjectiveAssignment assignment = squad.assignedObjective;
        return assignment != null && assignment.kind() == AssignmentKind.WITHDRAW;
    }

    private static int[] origin(Squad squad, BattleSimulation sim) {
        long member = firstLiveMember(squad, sim);
        return member == 0L ? null : new int[]{
                sim.world().cellX(member), sim.world().cellY(member)};
    }

    private static long firstLiveMember(Squad squad, BattleSimulation sim) {
        long leader = sim.resolveUnit(squad.leaderId);
        if (leader != 0L) return leader;
        for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
            long candidate = sim.resolveUnit(sim.squadMemberAt(squad.id, i));
            if (candidate != 0L) return candidate;
        }
        return 0L;
    }

    private static Squad validPlayerInfantrySquad(int squadId,
                                                   BattleSimulation sim) {
        Squad squad = sim.getSquad(squadId);
        if (squad == null || squad.faction != Faction.MARINE
                || squad.isMechSquad() || squad.isDroneSquad()
                || squad.rescuePickupGuard || squad.rescueShelterGuard) {
            return null;
        }
        long member = firstLiveMember(squad, sim);
        return member == 0L || !sim.identity().has(member)
                || !sim.identity().type(member).usesInfantryTraining()
                ? null : squad;
    }

    private static void invalidateExecution(Squad squad,
                                            BattleSimulation sim) {
        synchronized (squad.lock) {
            squad.currentPlan = null;
            squad.currentGoal = null;
            squad.clearBoundingOverwatch();
            squad.clearEngagementDisciplineHold();
        }
        for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
            long member = sim.squadMemberAt(squad.id, i);
            if (sim.resolveUnit(member) != 0L) sim.clearPath(member);
        }
    }
}
