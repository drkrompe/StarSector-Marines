package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.assault.AssaultTarget;
import com.dillon.starsectormarines.battle.assault.MachineAssault;
import com.dillon.starsectormarines.battle.assault.MachineAssaults;
import com.dillon.starsectormarines.battle.assault.MachineGuard;
import com.dillon.starsectormarines.battle.assault.MachineGuards;
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
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates exact-mech move requests and executes accepted movement without
 * suppressing the ordinary mech weapon pass.
 *
 * <p>Orders are applied after strategic command and before squad replanning.
 * Execution then intercepts only the selected member's unit update. Arrival
 * removes the override before ordinary doctrine executes, so handback has no
 * planless interval. Broken-morale survival temporarily suspends the order;
 * a hard withdrawal releases it.
 *
 * <p>A click on a garrisonable the side believes is enemy-held is an <b>assault</b> instead
 * ({@link MachineAssaults}): the destination is a stand-off outside the place, the order holds
 * the mech there once it arrives rather than handing back, and its fire goes first to a believed
 * occupant it can shoot and then to the wall sheltering one. The order ends when the side has
 * believed nobody inside for a few seconds.
 *
 * <p>A click on a garrisonable the side <em>mans</em> is a <b>guard</b> ({@link MachineGuards}): the destination is a
 * cell beside the place, off the garrison's lanes of fire, and the order holds the mech there, its hips on the
 * believed threat axis while it has nothing in its sights, its own targeting picking what to engage. It ends when the
 * garrison has gone for a few seconds. A commander may give the same guard to a lance of its own side
 * ({@link MechMoveOrderService#requestGuard}); a player's order on a chassis outranks a commander's.
 */
public final class MechMoveOrderSystem {

    private final MechMoveOrderService service;

    public MechMoveOrderSystem(MechMoveOrderService service) {
        this.service = service;
    }

    /** Accepts every valid queued request at the serialized command boundary. */
    public void tick(BattleSimulation sim) {
        for (var active : service.activeEntries()) {
            if (validLance(active.getKey(), active.getValue().commanded(), sim) == null) {
                service.release(active.getKey(), active.getValue());
            } else if (active.getValue().assault() != null) {
                serveAssault(active.getKey(), active.getValue(), sim);
            } else if (active.getValue().guard() != null) {
                serveGuard(active.getKey(), active.getValue(), sim);
            }
        }
        for (PendingOrder request : service.drainPending()) {
            Squad squad = validLance(request.mechId, request.commanded, sim);
            if (squad == null || !sim.movement().has(request.mechId)
                    || MechDamageDoctrine.crippled(
                            sim.world().mechLoadout(request.mechId).sections())) continue;
            ObjectiveAssignment assignment = squad.assignmentForExecution();
            if (assignment != null && assignment.kind() == AssignmentKind.WITHDRAW) {
                service.release(request.mechId);
                continue;
            }
            if (request.commanded) {
                // A commander's guard never displaces a player's order on the chassis, and is a guard or nothing.
                ActiveOrder current = service.activeOrder(request.mechId);
                if (current != null && !current.commanded()) continue;
                MachineGuard guard = planGuard(request.mechId, squad, request.cellX, request.cellY, sim);
                if (guard == null) continue;
                service.activate(request.mechId, new ActiveOrder(request.cellX, request.cellY,
                        guard.guardX(), guard.guardY(), Float.NaN, null, guard, true));
                sim.clearPath(request.mechId);
                continue;
            }
            MachineAssault assault = planAssault(request.mechId, squad,
                    request.cellX, request.cellY, sim);
            MachineGuard guard = assault == null
                    ? planGuard(request.mechId, squad, request.cellX, request.cellY, sim) : null;
            int[] destination = assault != null
                    ? new int[]{assault.standoffX(), assault.standoffY()}
                    : guard != null ? new int[]{guard.guardX(), guard.guardY()}
                    : nearestReachableCell(request.mechId, request.cellX, request.cellY, sim);
            if (destination == null) continue;
            ActiveOrder order = new ActiveOrder(request.cellX, request.cellY,
                    destination[0], destination[1], request.facingDegrees, assault, guard, false);
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
        if (validLance(mech, order.commanded(), sim) != squad || !sim.movement().has(mech)) {
            service.release(mech, order);
            return false;
        }
        if (MechDamageDoctrine.crippled(sim.world().mechLoadout(mech).sections())) {
            // Legs that cannot carry the chassis cannot carry the order either; release it
            // and let the crippled-chassis reflex hold the mech as a turret.
            service.release(mech, order);
            sim.clearPath(mech);
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
        if (order.assault() != null) {
            return executeAssault(mech, order, sim);
        }
        if (order.guard() != null) {
            return executeGuard(mech, order, sim);
        }
        if (sim.movement().atCell(mech,
                order.destinationX(), order.destinationY())) {
            service.release(mech, order);
            service.holdFacing(mech, order.facingDegrees());
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

    /** Route failures to one stand-off before the assault gives up. */
    static final int ASSAULT_ROUTE_FAILURES = 3;
    /** Guard cells a chassis could not route to before the guard gives up. */
    static final int GUARD_ROUTE_FAILURES = 5;

    /**
     * The assault a click is, or null when it is a move: a garrisonable under the click that the
     * side believes is enemy-held, with a stand-off for this chassis apart from its side's other machines' stand-offs and guard cells.
     */
    MachineAssault planAssault(long mech, Squad squad, int cellX, int cellY, BattleSimulation sim) {
        AssaultTarget target = MachineAssaults.targetUnderClick(sim, squad.faction, cellX, cellY);
        if (target == null) return null;
        int[] cell = MachineAssaults.chooseStandoff(sim, target, squad.faction,
                sim.world().cellX(mech), sim.world().cellY(mech), sim.world().attackRange(mech),
                lanceStandoffs(mech, squad, sim));
        return cell == null ? null : new MachineAssault(target, cell[0], cell[1], sim.getSimTickIndex());
    }

    /**
     * The serial half of a standing assault: ends it once the side believes nobody is inside, sets
     * this tick's fire directive from where the chassis stands, and chooses a new stand-off for a
     * chassis that has stood at its own for a while with nothing to fire at.
     */
    private void serveAssault(long mech, ActiveOrder order, BattleSimulation sim) {
        MachineAssault assault = order.assault();
        Squad squad = sim.squadOf(mech);
        if (!MachineAssaults.stillStanding(assault, sim, squad.faction)) {
            service.release(mech, order);
            sim.clearPath(mech);
            return;
        }
        MachineAssaults.aim(assault, sim, mech, squad.faction, sim.world().x(mech), sim.world().y(mech),
                sim.world().attackRange(mech), 0f, carriesWallDamage(sim.world().mechLoadout(mech)));
        boolean idle = assault.occupantId() == 0L && !assault.hasWall()
                && sim.movement().atCell(mech, order.destinationX(), order.destinationY());
        if (!MachineAssaults.dueToChooseAgain(assault, idle, sim.getSimTickIndex())) return;
        int[] cell = MachineAssaults.chooseStandoff(sim, assault.target(), squad.faction,
                sim.world().cellX(mech), sim.world().cellY(mech), sim.world().attackRange(mech),
                lanceStandoffs(mech, squad, sim));
        if (cell == null || (cell[0] == order.destinationX() && cell[1] == order.destinationY())) return;
        assault.moveStandoff(cell[0], cell[1]);
        service.activate(mech, new ActiveOrder(order.requestedX(), order.requestedY(), cell[0], cell[1],
                order.facingDegrees(), assault));
        sim.clearPath(mech);
    }

    /**
     * Where this chassis's side already fights from under an assault or a guard, so neither a lance nor
     * a group of one-mech squads sent at one place stacks on one cell.
     */
    private List<int[]> lanceStandoffs(long mech, Squad squad, BattleSimulation sim) {
        List<int[]> taken = new ArrayList<>();
        for (var entry : service.activeOrders()) {
            long other = entry.getKey();
            ActiveOrder order = entry.getValue();
            if (other == mech || order.assault() == null && order.guard() == null) continue;
            if (!sim.identity().has(other) || sim.identity().faction(other) != squad.faction) continue;
            taken.add(new int[]{order.destinationX(), order.destinationY()});
        }
        // Map order is not stable from run to run; the choosers read this as a set, but keep it sorted anyway.
        taken.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        return taken;
    }

    /**
     * The guard a click is, or null: a garrisonable under the click that the side mans and does not believe
     * enemy-held, with a cell beside it for this chassis apart from its side's other machines' stand-offs and guard cells.
     */
    MachineGuard planGuard(long mech, Squad squad, int cellX, int cellY, BattleSimulation sim) {
        AssaultTarget target = MachineGuards.targetUnderClick(sim, squad.faction, cellX, cellY);
        if (target == null) return null;
        float[] axis = MachineGuards.threatAxis(sim, squad.faction, target, sim.world().x(mech), sim.world().y(mech));
        int[] cell = MachineGuards.chooseGuardCell(sim, target, sim.world().cellX(mech), sim.world().cellY(mech),
                sim.world().attackRange(mech), axis[0], axis[1], lanceStandoffs(mech, squad, sim));
        return cell == null ? null
                : new MachineGuard(target, cell[0], cell[1], axis[0], axis[1], sim.getSimTickIndex());
    }

    /**
     * The serial half of a standing guard: ends it once the garrison has gone, and every few seconds reads the threat
     * axis again, moving the chassis to a cell chosen for the new one when the threat has swung round the place.
     */
    private void serveGuard(long mech, ActiveOrder order, BattleSimulation sim) {
        MachineGuard guard = order.guard();
        Squad squad = sim.squadOf(mech);
        if (!MachineGuards.stillStanding(guard, sim, squad.faction)) {
            service.release(mech, order);
            sim.clearPath(mech);
            return;
        }
        boolean unreachable = guard.takeChooseAgain();
        float[] axis = null;
        if (!unreachable) {
            if (!MachineGuards.dueForReview(guard, sim.getSimTickIndex())) return;
            axis = MachineGuards.believedAxis(sim, squad.faction, guard.target());
            if (axis == null || !MachineGuards.axisTurned(guard, axis[0], axis[1])) return;
        } else {
            axis = new float[] {guard.axisX(), guard.axisY()};
        }
        List<int[]> taken = lanceStandoffs(mech, squad, sim);
        taken.addAll(guard.unreachable());
        int[] cell = MachineGuards.chooseGuardCell(sim, guard.target(), sim.world().cellX(mech),
                sim.world().cellY(mech), sim.world().attackRange(mech), axis[0], axis[1], taken);
        if (cell == null) {
            if (unreachable) service.release(mech, order);
            return;
        }
        guard.moveGuard(cell[0], cell[1], axis[0], axis[1]);
        if (cell[0] == order.destinationX() && cell[1] == order.destinationY()) return;
        service.activate(mech, new ActiveOrder(order.requestedX(), order.requestedY(), cell[0], cell[1],
                order.facingDegrees(), null, guard, order.commanded()));
        sim.clearPath(mech);
    }

    /**
     * One unit update under a standing guard: ordinary targeting and fire, a walk to the guard cell, and a stand
     * there. With nothing in its sights the hips hold the threat axis, so the chassis's front armour and its
     * weapons' arcs point where the attack is believed to come from; with a target they turn onto it as ever.
     */
    private boolean executeGuard(long mech, ActiveOrder order, BattleControl sim) {
        MachineGuard guard = order.guard();
        fireWhileMoving(mech, sim);
        if (sim.movement().atCell(mech, order.destinationX(), order.destinationY())) {
            if (!Paths.isEmpty(sim.world().path(mech))) sim.clearPath(mech);
            if (sim.world().targetId(mech) == 0L) {
                service.holdFacing(mech, MechLocomotion.continuousFacing(guard.axisX(), guard.axisY()));
            } else {
                service.releaseHeldFacing(mech);
            }
            return true;
        }
        if (moveToward(mech, order, sim) == PathRequestStatus.FAILED) {
            sim.clearPath(mech);
            // The cell is set aside and the serial pass chooses another; after a few the guard gives up.
            if (guard.noteRouteFailure() >= GUARD_ROUTE_FAILURES) service.release(mech, order);
        }
        return true;
    }

    private static boolean carriesWallDamage(MechLoadoutComponent loadout) {
        for (MechWeaponMount mount : loadout.mounts()) {
            if (mount == null) continue;
            WeaponDef weapon = mount.weaponDef();
            if (weapon.wallDamage > 0) return true;
        }
        return false;
    }

    /**
     * One unit update under a standing assault: fire by the assault's priority, walk to the
     * stand-off, and hold there. The order owns the chassis's locomotion for as long as it stands,
     * so neither doctrine nor lance cohesion walks it into the place it is firing on.
     */
    private boolean executeAssault(long mech, ActiveOrder order, BattleControl sim) {
        MachineAssault assault = order.assault();
        fireOnAssault(mech, assault, sim);
        if (sim.movement().atCell(mech, order.destinationX(), order.destinationY())) {
            if (!Paths.isEmpty(sim.world().path(mech))) sim.clearPath(mech);
            return true;
        }
        if (moveToward(mech, order, sim) == PathRequestStatus.FAILED) {
            sim.clearPath(mech);
            if (assault.noteRouteFailure() >= ASSAULT_ROUTE_FAILURES) service.release(mech, order);
        }
        return true;
    }

    /**
     * A visible enemy inside the close-threat radius first, as ever; then the believed occupant the
     * serial pass named; then, when it named the sheltering wall instead, no body at all, so the
     * mounts are left for the wall pass ({@code WallBreachSystem}); otherwise the ordinary pick.
     */
    private static void fireOnAssault(long mech, MachineAssault assault, BattleControl sim) {
        long ordinary = MechTargeting.refreshTarget(mech, sim);
        long target;
        if (ordinary != 0L && TacticalScoring.cellDistance(sim.world().x(mech), sim.world().y(mech),
                sim.world().x(ordinary), sim.world().y(ordinary)) <= MechTargeting.CLOSE_THREAT_AGGRO_RANGE) {
            target = ordinary;
        } else if (assault.occupantId() != 0L && sim.world().isAlive(assault.occupantId())) {
            target = assault.occupantId();
        } else if (assault.hasWall()) {
            target = 0L;
        } else {
            target = ordinary;
        }
        sim.world().setTargetId(mech, target);
        if (target == 0L) return;
        float distance = TacticalScoring.cellDistance(
                sim.world().x(mech), sim.world().y(mech),
                sim.world().x(target), sim.world().y(target));
        if (distance > sim.world().attackRange(mech)) return;
        MechLoadoutComponent loadout = sim.world().mechLoadout(mech);
        boolean visible = sim.getTacticalScoring().hasClearShot(mech, target);
        MechCombatantBehavior.tryFireMechWeapons(mech, loadout, target, distance, sim, visible);
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

    /**
     * The lance a chassis under this order belongs to, or null when the order cannot stand: a player's order needs a
     * player's lance, a commander's guard a lance of the chassis's own side that the player is not driving.
     */
    private static Squad validLance(long mech, boolean commanded, BattleView sim) {
        if (!commanded) return validPlayerLance(mech, sim);
        if (!sim.world().isAlive(mech) || !sim.world().hasMechLoadout(mech) || !sim.identity().has(mech)) return null;
        Squad squad = sim.squadOf(mech);
        return squad == null || squad.faction != sim.identity().faction(mech)
                || !squad.isMechSquad() || squad.rescuePickupMech
                || squad.controlledMemberId() == mech
                ? null : squad;
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
