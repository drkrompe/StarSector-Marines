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
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveMountOrder;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveDefendAreaOrder;
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

    /** How far off a vehicle's centre a right-click still counts as pointing at it. */
    private static final float VEHICLE_CLICK_TOLERANCE_CELLS = 1.5f;

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
                continue;
            }
            if (order instanceof ActiveMountOrder mount
                    && !refreshMountOrder(squadId, squad, mount, sim)) {
                release(squadId, order, squad, sim);
            }
        }

        for (PendingOrder request : service.drainPending()) {
            Squad squad = validPlayerInfantrySquad(request.squadId, sim);
            if (squad == null || withdrawing(squad)) continue;
            int[] origin = origin(squad, sim);
            if (origin == null) continue;

            // A friendly vehicle with room under the click is not a patch of
            // ground to walk to — it is a ride. Resolved before the compound
            // and the plain move, because the APC is the more specific answer
            // to what the player pointed at.
            if (request.kind != PendingOrder.Kind.DEFEND_AREA) {
                long ride = sim.transport().mountableVehicleFor(request.cellX, request.cellY,
                        squad.faction, sim.squadMemberCount(squad.id));
                if (ride != 0L) {
                    activateMount(request, squad, ride, sim);
                    continue;
                }
            }

            Record capture = uncapturedCompoundAt(
                    request.cellX, request.cellY, sim);
            if (capture != null) {
                if (request.kind == PendingOrder.Kind.DEFEND_AREA) {
                    activateDefendArea(request, squad, origin, sim);
                    continue;
                }
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

            if (request.kind == PendingOrder.Kind.DEFEND_AREA) {
                activateDefendArea(request, squad, origin, sim);
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

    /**
     * A vehicle of the squad's own side, within a click's tolerance of
     * ({@code cellX}, {@code cellY}), with seats for the whole squad. Zero when
     * the click was at ordinary ground — or at a vehicle that cannot take them,
     * which is the same thing as far as the order is concerned.
     */
    private void activateMount(PendingOrder request, Squad squad, long vehicleId,
                               BattleSimulation sim) {
        GroundBody body = sim.convoy().body(vehicleId);
        int vx = (int) Math.floor(body.x);
        int vy = (int) Math.floor(body.y);
        ActiveMountOrder order = new ActiveMountOrder(
                request.cellX, request.cellY, vx, vy, vehicleId);
        service.activate(squad.id, order);
        squad.applyTacticalMoveOrder(vx, vy);
        invalidateExecution(squad, sim);
    }

    /**
     * Keeps a mount order pointed at its vehicle and takes the squad aboard the
     * moment it is close enough.
     *
     * @return false when the order is finished or can no longer be carried out
     */
    private boolean refreshMountOrder(int squadId, Squad squad,
                                      ActiveMountOrder order, BattleSimulation sim) {
        long vehicleId = order.vehicleId();
        VehicleMission mission = sim.convoyMission(vehicleId);
        if (mission == null || mission.state == VehicleState.WRECKED
                || mission.state == VehicleState.GONE) {
            return false;   // the ride left, or died
        }
        if (sim.transport().mountSquad(vehicleId, squadId) > 0) {
            return false;   // aboard; the order is done
        }
        // Not in reach yet. A vehicle is not a cell — it can drive while the
        // squad walks — so the destination follows it rather than staying where
        // the click landed.
        GroundBody body = sim.convoy().body(vehicleId);
        int vx = (int) Math.floor(body.x);
        int vy = (int) Math.floor(body.y);
        if (vx != order.destinationX() || vy != order.destinationY()) {
            service.activate(squadId, new ActiveMountOrder(
                    order.requestedX(), order.requestedY(), vx, vy, vehicleId));
            squad.applyTacticalMoveOrder(vx, vy);
            invalidateExecution(squad, sim);
        }
        return true;
    }

    private void activateDefendArea(PendingOrder request, Squad squad,
                                    int[] origin, BattleSimulation sim) {
        int[] center = ReachableCellResolver.nearest(sim.getGrid(),
                origin[0], origin[1], request.cellX, request.cellY);
        if (center == null) return;
        int radius = SquadMoveOrderService.DEFEND_AREA_RADIUS_CELLS;
        ActiveDefendAreaOrder order = new ActiveDefendAreaOrder(
                request.cellX, request.cellY, center[0], center[1], radius);
        service.activate(squad.id, order);
        squad.applyPlayerTacticalOrder(ObjectiveAssignment.defendArea(
                squad.id, center[0], center[1], radius));
        invalidateExecution(squad, sim);
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
