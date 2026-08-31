package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.command.compound.CompoundService.Record;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.decision.goap.action.AttackMove;
import com.dillon.starsectormarines.battle.nav.ReachableCellResolver;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveCaptureOrder;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveMoveOrder;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveOrder;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.PendingOrder;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * Resolves a player world click into a temporary infantry execution assignment.
 * Ground uses attack-move; an uncaptured Conquest compound uses the ordinary
 * secure-compound action until capture completes. Mission ownership remains in
 * {@link Squad#assignedObjective} throughout.
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
            if (withdrawing(squad)) {
                release(squadId, order, squad, sim);
                continue;
            }
            if (order instanceof ActiveMoveOrder move && arrived(squad, move)) {
                release(squadId, order, squad, sim);
                continue;
            }
            if (order instanceof ActiveCaptureOrder capture
                    && !refreshCaptureOrder(squad, capture, sim)) {
                release(squadId, order, squad, sim);
            }
        }

        for (PendingOrder request : service.drainPending()) {
            Squad squad = validPlayerInfantrySquad(request.squadId, sim);
            if (squad == null || withdrawing(squad)) continue;
            int[] origin = origin(squad, sim);
            if (origin == null) continue;

            Record capture = uncapturedCompoundAt(
                    request.cellX, request.cellY, sim);
            if (capture != null) {
                int targetZone = sim.getCompoundService()
                        .captureZoneId(capture, sim);
                if (!reachable(origin, targetZone, sim)) continue;
                ActiveCaptureOrder order = new ActiveCaptureOrder(
                        request.cellX, request.cellY,
                        capture.captureCellX, capture.captureCellY,
                        capture.node);
                service.activate(squad.id, order);
                squad.applyPlayerTacticalOrder(
                        ObjectiveAssignment.secureCompound(
                                squad.id, targetZone, capture.node));
                invalidateExecution(squad, sim);
                continue;
            }

            int[] destination = ReachableCellResolver.nearest(sim.getGrid(),
                    origin[0], origin[1], request.cellX, request.cellY);
            if (destination == null) continue;

            ActiveMoveOrder order = new ActiveMoveOrder(
                    request.cellX, request.cellY,
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
        squad.clearPlayerTacticalOrder();
        invalidateExecution(squad, sim);
    }

    private static boolean arrived(Squad squad, ActiveMoveOrder order) {
        return TacticalScoring.cellDistance(squad.centroidX, squad.centroidY,
                order.destinationX() + 0.5f,
                order.destinationY() + 0.5f) <= AttackMove.ARRIVAL_RADIUS;
    }

    /**
     * Keeps stable authored compound identity bound to the live capture zone.
     * Zone ids can change after a breach rebuild; completion and invalidation
     * are read from objective authority rather than inferred from arrival.
     */
    private static boolean refreshCaptureOrder(Squad squad,
                                               ActiveCaptureOrder order,
                                               BattleSimulation sim) {
        CompoundService compounds = sim.getCompoundService();
        Record record = compounds.getRecord(order.targetNode());
        if (record == null
                || record.state == CompoundService.CompoundState.MARINE_HELD) {
            return false;
        }
        int targetZone = compounds.captureZoneId(record, sim);
        int[] origin = origin(squad, sim);
        if (origin == null || !reachable(origin, targetZone, sim)) return false;
        ObjectiveAssignment current = squad.playerTacticalOrder();
        if (current == null
                || current.kind() != AssignmentKind.SECURE_COMPOUND
                || current.targetNode() != order.targetNode()
                || current.targetZoneId() != targetZone) {
            squad.applyPlayerTacticalOrder(ObjectiveAssignment.secureCompound(
                    squad.id, targetZone, order.targetNode()));
            invalidateExecution(squad, sim);
        }
        return true;
    }

    private static Record uncapturedCompoundAt(int cellX, int cellY,
                                               BattleSimulation sim) {
        Record closest = null;
        int closestDistance = Integer.MAX_VALUE;
        for (Record record : sim.getCompoundService().getRecords()) {
            if (record.state == CompoundService.CompoundState.MARINE_HELD) continue;
            var node = record.node;
            if (cellX < node.left || cellX > node.right
                    || cellY < node.top || cellY > node.bottom) continue;
            int dx = 2 * cellX - node.left - node.right;
            int dy = 2 * cellY - node.top - node.bottom;
            int distance = dx * dx + dy * dy;
            if (distance < closestDistance) {
                closest = record;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private static boolean reachable(int[] origin, int targetZone,
                                     BattleSimulation sim) {
        if (targetZone < 0) return false;
        int originZone = sim.getZoneGraph().zoneIdAt(origin[0], origin[1]);
        return originZone == targetZone || originZone >= 0
                && ZoneQueries.zonePathBfs(originZone, targetZone, sim).size() >= 2;
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
