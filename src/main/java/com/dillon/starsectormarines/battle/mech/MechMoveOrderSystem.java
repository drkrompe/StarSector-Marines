package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.mech.MechMoveOrderService.ActiveOrder;
import com.dillon.starsectormarines.battle.mech.MechMoveOrderService.PendingOrder;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.nav.PathRequestStatus;
import com.dillon.starsectormarines.battle.nav.ReachableCellResolver;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * Validates exact-mech move requests and executes accepted movement without
 * suppressing the ordinary mech weapon pass.
 *
 * <p>Orders are applied after strategic command and before squad replanning.
 * Execution then intercepts only the selected member's unit update. Arrival
 * removes the override before ordinary doctrine executes, so handback has no
 * planless interval. Broken-morale survival temporarily suspends the order;
 * a hard withdrawal releases it.
 */
public final class MechMoveOrderSystem {

    private final MechMoveOrderService service;

    public MechMoveOrderSystem(MechMoveOrderService service) {
        this.service = service;
    }

    /** Accepts every valid queued request at the serialized command boundary. */
    public void tick(BattleSimulation sim) {
        for (var active : service.activeEntries()) {
            if (validPlayerLance(active.getKey(), sim) == null) {
                service.release(active.getKey(), active.getValue());
            }
        }
        for (PendingOrder request : service.drainPending()) {
            Squad squad = validPlayerLance(request.mechId, sim);
            if (squad == null || !sim.movement().has(request.mechId)) continue;
            ObjectiveAssignment assignment = squad.assignmentForExecution();
            if (assignment != null && assignment.kind() == AssignmentKind.WITHDRAW) {
                service.release(request.mechId);
                continue;
            }
            int[] destination = nearestReachableCell(request.mechId,
                    request.cellX, request.cellY, sim);
            if (destination == null) continue;
            ActiveOrder order = new ActiveOrder(request.cellX, request.cellY,
                    destination[0], destination[1]);
            service.activate(request.mechId, order);
            sim.clearPath(request.mechId);
        }
    }

    /**
     * Runs the active override for one mech.
     *
     * @return true when the move order consumed this unit update; false when
     *         ordinary squad-plan execution should continue this tick
     */
    public boolean executeIfActive(long mech, Squad squad,
                                   BattleControl sim) {
        ActiveOrder order = service.activeOrder(mech);
        if (order == null) return false;
        if (validPlayerLance(mech, sim) != squad || !sim.movement().has(mech)) {
            service.release(mech, order);
            return false;
        }
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        if (assignment != null && assignment.kind() == AssignmentKind.WITHDRAW) {
            service.release(mech, order);
            sim.clearPath(mech);
            return false;
        }
        if (squad.moraleBroken) {
            return false;
        }
        if (sim.movement().atCell(mech,
                order.destinationX(), order.destinationY())) {
            service.release(mech, order);
            if (!Paths.isEmpty(sim.world().path(mech))) sim.clearPath(mech);
            return false;
        }

        fireWhileMoving(mech, sim);
        if (moveToward(mech, order, sim) == PathRequestStatus.FAILED) {
            service.release(mech, order);
            sim.clearPath(mech);
            return false;
        }
        return true;
    }

    /**
     * Selects a provisional click cell in the coarse navigation component.
     * The later circular route proof must reach a legal point inside that cell;
     * refusal releases the override. Coarse connectivity alone is insufficient.
     */
    static int[] nearestReachableCell(long mech, int requestedX, int requestedY,
                                      BattleSimulation sim) {
        int startX = sim.world().cellX(mech);
        int startY = sim.world().cellY(mech);
        return ReachableCellResolver.nearest(sim.getGrid(), startX, startY,
                requestedX, requestedY);
    }

    private static PathRequestStatus moveToward(long mech, ActiveOrder order,
                                   BattleControl sim) {
        return MechRouteIntent.forMember(mech, MechMoveOrderSystem.class,
                MechRouteIntent.cellKey(order.destinationX(), order.destinationY()), sim)
                .moveToward(mech, order.destinationX(), order.destinationY(), sim);
    }

    private static void fireWhileMoving(long mech, BattleControl sim) {
        long target = MechTargeting.refreshTarget(mech, sim);
        sim.world().setTargetId(mech, target);
        if (target == 0L) return;
        float distance = TacticalScoring.cellDistance(
                sim.world().x(mech), sim.world().y(mech),
                sim.world().x(target), sim.world().y(target));
        if (distance > sim.world().attackRange(mech)) return;
        MechLoadoutComponent loadout = sim.world().mechLoadout(mech);
        boolean visible = sim.getTacticalScoring().hasClearShot(mech, target);
        MechCombatantBehavior.tryFireMechWeapons(
                mech, loadout, target, distance, sim, visible);
    }

    private static Squad validPlayerLance(long mech, BattleView sim) {
        if (!sim.world().isAlive(mech)
                || !sim.world().hasMechLoadout(mech)
                || !sim.identity().has(mech)
                || sim.identity().faction(mech) != Faction.MARINE) {
            return null;
        }
        Squad squad = sim.squadOf(mech);
        return squad == null || squad.faction != Faction.MARINE
                || !squad.isMechSquad() || squad.rescuePickupMech
                || squad.controlledMemberId() == mech
                ? null : squad;
    }
}
