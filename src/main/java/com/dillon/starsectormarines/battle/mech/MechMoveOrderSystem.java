package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.assault.AssaultTarget;
import com.dillon.starsectormarines.battle.assault.MachineAssault;
import com.dillon.starsectormarines.battle.assault.MachineAssaults;
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
            } else if (active.getValue().assault() != null) {
                serveAssault(active.getKey(), active.getValue(), sim);
            }
        }
        for (PendingOrder request : service.drainPending()) {
            Squad squad = validPlayerLance(request.mechId, sim);
            if (squad == null || !sim.movement().has(request.mechId)
                    || MechDamageDoctrine.crippled(
                            sim.world().mechLoadout(request.mechId).sections())) continue;
            ObjectiveAssignment assignment = squad.assignmentForExecution();
            if (assignment != null && assignment.kind() == AssignmentKind.WITHDRAW) {
                service.release(request.mechId);
                continue;
            }
            MachineAssault assault = planAssault(request.mechId, squad,
                    request.cellX, request.cellY, sim);
            int[] destination = assault != null
                    ? new int[]{assault.standoffX(), assault.standoffY()}
                    : nearestReachableCell(request.mechId, request.cellX, request.cellY, sim);
            if (destination == null) continue;
            ActiveOrder order = new ActiveOrder(request.cellX, request.cellY,
                    destination[0], destination[1], assault);
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

    /** Route failures to one stand-off before the assault gives up. */
    static final int ASSAULT_ROUTE_FAILURES = 3;

    /**
     * The assault a click is, or null when it is a move: a garrisonable under the click that the
     * side believes is enemy-held, with a stand-off for this chassis apart from its lance-mates'.
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
        service.activate(mech, new ActiveOrder(order.requestedX(), order.requestedY(), cell[0], cell[1], assault));
        sim.clearPath(mech);
    }

    /** Where this chassis's lance-mates already fight from, so a lance does not stack on one cell. */
    private List<int[]> lanceStandoffs(long mech, Squad squad, BattleSimulation sim) {
        List<int[]> taken = new ArrayList<>();
        for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
            long mate = sim.squadMemberAt(squad.id, i);
            if (mate == mech) continue;
            ActiveOrder other = service.activeOrder(mate);
            if (other != null && other.assault() != null) {
                taken.add(new int[]{other.destinationX(), other.destinationY()});
            }
        }
        return taken;
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
