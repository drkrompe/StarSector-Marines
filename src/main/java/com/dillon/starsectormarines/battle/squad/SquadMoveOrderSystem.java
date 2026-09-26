package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.command.AssignmentArbiter;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.OrderCatalog;
import com.dillon.starsectormarines.battle.command.OrderCatalog.Arm;
import com.dillon.starsectormarines.battle.command.OrderCatalog.PlayerOrder;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.command.compound.CompoundService.Record;
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
 * Resolves a player world click into a leased squad assignment.
 * A ground click is an attack move, a click on a friendly vehicle with room is
 * a ride, an uncaptured Conquest compound is the ordinary secure-compound
 * action until capture completes, and Defend Area is placed where it was asked
 * for.
 *
 * <p><b>Every one of those goes through the arbiter as a lease.</b> The player
 * is an intervention authority, not a second assignment writer, so an accepted
 * click is a {@link com.dillon.starsectormarines.battle.command.CommandAuthority#PLAYER_INTERVENTION}
 * directive over the commander's, which the arbiter shelves and puts back when
 * the order ends. That is what makes the click provenanced like everything else
 * that points a squad somewhere, and what makes completion, expiry,
 * invalidation and hard withdrawal one door rather than four.
 *
 * <p>Which squads may be handed each of those, when each is over, and how long
 * each may stand are {@link OrderCatalog}'s to say — the same rows the
 * dispatchers read to decide what a squad holding one should be doing. What
 * stays here is the part that needs the map and the squad's own position:
 * snapping a click to reachable ground, keeping a compound bound to its live
 * zone, and following a vehicle that drives off while the squad walks.
 */
public final class SquadMoveOrderSystem {

    /** How far off a vehicle's centre a right-click still counts as pointing at it. */
    private static final float VEHICLE_CLICK_TOLERANCE_CELLS = 1.5f;

    /** The issuer every player lease is held under; the ledger names it as the owner. */
    public static final String PLAYER_ISSUER = "player";

    private final SquadMoveOrderService service;

    public SquadMoveOrderSystem(SquadMoveOrderService service) {
        this.service = service;
    }

    /** Validates requests, releases completed orders, and interrupts replans. */
    public void tick(BattleSimulation sim) {
        expireLeases(sim);
        for (var active : service.activeEntries()) {
            int squadId = active.getKey();
            ActiveOrder order = active.getValue();
            AssignmentKind kind = kindOf(order);
            Squad squad = validPlayerSquad(squadId, kind, sim);
            if (squad == null) {
                release(squadId, order, sim.getSquad(squadId), sim, "squad ineligible");
                continue;
            }
            if (withdrawing(squad, sim)) {
                release(squadId, order, squad, sim, "withdrawing");
                continue;
            }
            // Two orders keep a refresh of their own because their target can
            // move out from under them; the rest are asked of their row, so a
            // kind that completes is never persistent merely by being absent
            // from a list of tests here.
            if (order instanceof ActiveCaptureOrder capture) {
                String over = refreshCaptureOrder(squad, capture, sim);
                if (over != null) release(squadId, order, squad, sim, over);
                continue;
            }
            if (order instanceof ActiveMountOrder mount) {
                String over = refreshMountOrder(squadId, squad, mount, sim);
                if (over != null) release(squadId, order, squad, sim, over);
                continue;
            }
            if (complete(squad, kind, sim)) {
                release(squadId, order, squad, sim, "arrived");
            }
        }

        for (PendingOrder request : service.drainPending()) {
            // A move request has not chosen between its three answers yet — a
            // ride, a compound, or plain ground — but all three are infantry
            // orders, so ATTACK_MOVE settles eligibility for the whole branch:
            // a mount is an attack move onto a cell that can drive away, and a
            // compound click is refused for a lance in any case.
            Squad squad = validPlayerSquad(request.squadId,
                    request.kind == PendingOrder.Kind.DEFEND_AREA
                            ? AssignmentKind.DEFEND_AREA
                            : AssignmentKind.ATTACK_MOVE,
                    sim);
            if (squad == null || withdrawing(squad, sim)) continue;
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
                issue(squad, ObjectiveAssignment.secureCompound(
                        squad.id, targetZone, capture.node),
                        "secure compound", sim);
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
            issue(squad, ObjectiveAssignment.attackMove(
                    squad.id, destination[0], destination[1]),
                    "attack move to " + destination[0] + "," + destination[1], sim);
        }
    }

    /**
     * Takes the lease that puts {@code assignment} on the squad and clears the
     * plan standing on the old one.
     *
     * <p>The invalidation is what makes the click feel immediate: the replan
     * pass runs directly behind this system in the same tick, so the squad is
     * planning under the new order on the tick it was given rather than the
     * tick after. The handback side does the same thing for the same reason.
     */
    private void issue(Squad squad, ObjectiveAssignment assignment,
                       String reason, BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        arbiter(sim).lease(squad, assignment, PLAYER_ISSUER, reason, tick,
                OrderCatalog.playerOrder(assignment.kind()).leaseUntilTick(tick));
        invalidateExecution(squad, sim);
    }

    /**
     * Ends any lease whose bound has passed, and with it the order standing on
     * it. The mission underneath resumes on the same tick; nothing here has to
     * choose what the squad does next, because the arbiter already put back
     * what it was doing before.
     */
    private void expireLeases(BattleSimulation sim) {
        AssignmentArbiter arbiter = arbiter(sim);
        int tick = sim.getSimTickIndex();
        for (var active : service.activeEntries()) {
            Squad squad = sim.getSquad(active.getKey());
            if (squad == null || !arbiter.expireLease(squad, tick)) continue;
            service.release(active.getKey(), active.getValue());
            invalidateExecution(squad, sim);
        }
    }

    private static AssignmentArbiter arbiter(BattleSimulation sim) {
        return sim.getCommanderService().assignments();
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
        issue(squad, ObjectiveAssignment.attackMove(squad.id, vx, vy),
                "mount vehicle", sim);
    }

    /**
     * Keeps a mount order pointed at its vehicle and takes the squad aboard the
     * moment it is close enough.
     *
     * @return false when the order is finished or can no longer be carried out
     */
    private String refreshMountOrder(int squadId, Squad squad,
                                     ActiveMountOrder order, BattleSimulation sim) {
        long vehicleId = order.vehicleId();
        VehicleMission mission = sim.convoyMission(vehicleId);
        if (mission == null || mission.state == VehicleState.WRECKED
                || mission.state == VehicleState.GONE) {
            return "invalid target";   // the ride left, or died
        }
        if (sim.transport().mountSquad(vehicleId, squadId) > 0) {
            return "aboard";           // the order is done
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
            issue(squad, ObjectiveAssignment.attackMove(squad.id, vx, vy),
                    "mount vehicle", sim);
        }
        return null;
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
        issue(squad, ObjectiveAssignment.defendArea(
                squad.id, center[0], center[1], radius), "defend area", sim);
    }

    /**
     * Drops the order and hands the squad back to the directive the lease
     * shelved. {@code reason} is what the ledger records for the handback, so
     * an order that ended because the squad withdrew and one that ended because
     * it arrived are distinguishable afterwards.
     */
    private void release(int squadId, ActiveOrder order, Squad squad,
                         BattleSimulation sim, String reason) {
        service.release(squadId, order);
        if (squad == null) return;
        arbiter(sim).endLease(squad, PLAYER_ISSUER, reason, sim.getSimTickIndex());
        invalidateExecution(squad, sim);
    }

    /**
     * Whether the standing order has met its own objective, asked of the row
     * rather than answered here. An attack move is over when the squad has
     * stopped where {@link AttackMove#squadHasArrived} stops it — the action's
     * own footprint and not a centroid, which is the pair of rules the parked
     * squad taught us to keep as one. An area defence never says yes; the row
     * says so out loud rather than leaving persistence to be inferred from a
     * test nobody wrote.
     */
    private static boolean complete(Squad squad, AssignmentKind kind,
                                    BattleSimulation sim) {
        ObjectiveAssignment leased = squad.assignedObjective;
        return leased != null && OrderCatalog.playerOrder(kind).completion()
                .isComplete(squad, leased, sim);
    }

    /**
     * Keeps stable authored compound identity bound to the live capture zone.
     * Zone ids can change after a breach rebuild; completion and invalidation
     * are read from objective authority rather than inferred from arrival.
     */
    private String refreshCaptureOrder(Squad squad,
                                       ActiveCaptureOrder order,
                                       BattleSimulation sim) {
        CompoundService compounds = sim.getCompoundService();
        Record record = compounds.getRecord(order.targetNode());
        // Held or gone is the row's answer, the same one the arbiter and the
        // dispatcher read. What is left here is the half only the order system
        // can answer, because it needs the squad's own position: whether the
        // compound is still walkable to, and which live zone it is now.
        ObjectiveAssignment standing = squad.assignedObjective;
        if (record == null || standing == null
                || complete(squad, AssignmentKind.SECURE_COMPOUND, sim)) {
            return "captured";
        }
        int targetZone = compounds.captureZoneId(record, sim);
        int[] origin = origin(squad, sim);
        if (origin == null || !reachable(origin, targetZone, sim)) {
            return "invalid target";
        }
        if (standing.kind() != AssignmentKind.SECURE_COMPOUND
                || standing.targetNode() != order.targetNode()
                || standing.targetZoneId() != targetZone) {
            // A fresh lease over the standing one, which the arbiter treats as
            // the same intervention: the commander's directive stays on the
            // shelf the first click put it on rather than being replaced by the
            // rebind's own view of what the squad was doing.
            issue(squad, ObjectiveAssignment.secureCompound(
                    squad.id, targetZone, order.targetNode()),
                    "secure compound", sim);
        }
        return null;
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

    /**
     * A hard withdrawal cancels the order, wherever the withdrawal is written.
     *
     * <p>Two places, because there are two ways one arrives. A commander's goes
     * through the arbiter, which lets a withdrawal past a lease and so leaves it
     * on the squad's own field; a writer that has not migrated yet sets that
     * field directly, which lands on top of the player's order until the next
     * command pulse sorts it onto the shelf. Either is a withdrawal, so both are
     * read — and erring toward cancelling is the right direction for the one
     * order that means the squad is leaving.
     */
    private static boolean withdrawing(Squad squad, BattleSimulation sim) {
        if (isWithdrawal(squad.assignedObjective)) return true;
        CommandDirective shelved = sim.getShelvedSquadDirective(squad.id);
        return shelved != null && isWithdrawal(shelved.assignment());
    }

    private static boolean isWithdrawal(ObjectiveAssignment assignment) {
        return assignment != null && assignment.kind() == AssignmentKind.WITHDRAW;
    }

    private static int[] origin(Squad squad, BattleSimulation sim) {
        long member = firstLiveMember(squad, sim);
        return member == 0L ? null : new int[]{
                sim.world().cellX(member), sim.world().cellY(member)};
    }

    private static long firstLiveMember(Squad squad, BattleSimulation sim) {
        if (squad.controlledMemberId() != 0L) {
            long actingLeader = squad.autonomousLeader(sim);
            if (actingLeader != 0L) return actingLeader;
        }
        long leader = sim.resolveUnit(squad.leaderId);
        if (leader != 0L) return leader;
        for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
            long candidate = sim.resolveUnit(sim.squadMemberAt(squad.id, i));
            if (candidate != 0L) return candidate;
        }
        return 0L;
    }

    /**
     * The squad an order of {@code kind} may be given to, or null. Everything
     * here is about this squad — the right side, a body left to receive the
     * order, a formation not already spoken for. <b>Which arms the order
     * itself admits is {@link OrderCatalog}'s answer</b>, so a kind the player
     * gains later is eligible for the arms its row names and for no others,
     * without a second list here to keep in step.
     */
    private static Squad validPlayerSquad(int squadId, AssignmentKind kind,
                                          BattleSimulation sim) {
        Squad squad = sim.getSquad(squadId);
        // No shelter-guard term: the guard is allied, and the faction test
        // above is what keeps it out of the player's hands now.
        if (squad == null || squad.faction != Faction.MARINE
                || squad.isDroneSquad()) {
            return null;
        }
        long member = firstLiveMember(squad, sim);
        if (member == 0L || !sim.identity().has(member)) return null;
        var type = sim.identity().type(member);
        Arm arm;
        if (squad.isMechSquad()) {
            // A lance flying a rescue formation is the rescue commander's for
            // the duration; the player does not get to re-task it.
            if (squad.rescuePickupMech || !type.isMech()) return null;
            arm = Arm.MECH;
        } else if (type.usesInfantryTraining()) {
            arm = Arm.INFANTRY;
        } else {
            return null;
        }
        PlayerOrder player = OrderCatalog.playerOrder(kind);
        return player != null && player.allows(arm) ? squad : null;
    }

    /** Which row an accepted order stands on; a mount is an attack move at a moving cell. */
    private static AssignmentKind kindOf(ActiveOrder order) {
        if (order instanceof ActiveDefendAreaOrder) return AssignmentKind.DEFEND_AREA;
        if (order instanceof ActiveCaptureOrder) return AssignmentKind.SECURE_COMPOUND;
        return AssignmentKind.ATTACK_MOVE;
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
