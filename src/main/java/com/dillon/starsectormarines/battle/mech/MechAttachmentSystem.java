package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.mech.MechAttachmentService.Request;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.PathRequestStatus;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies and serves <b>attachments</b> ({@code mechs-nouns.md}, attachment): a player mech squad
 * that follows one player infantry squad, standing by its effective doctrine relative to it.
 *
 * <p><b>Like the tactical move, an attachment owns locomotion, not the assignment.</b> It is a reflex
 * over the lance's plan step ({@link MechReflexes#ATTACHED}), so the mission assignment, shared plan,
 * doctrine and contact picture stay where they were and resume on detach without a planless tick.
 * It is not a lease: its target is a moving squad, which no assignment shape carries, and like the
 * tactical move it must win outright over every mission goal or the click did nothing.
 *
 * <p>The serial pass ({@link #tick}) is the only place the relation changes. It ends an attachment
 * whose mech squad or anchor is gone, withdrawing or driven; it ends one whose mech squad has since
 * been given a <b>direct order</b> of its own — a tactical move, an assault or guard click, or a
 * Defend Area — which is the rule that a direct order reaching a follower without its squad
 * detaches it; it accepts queued requests, cancelling any direct order the mech stood under; and it
 * rebuilds each anchor's axis and its followers' ranks for the parallel unit pass to read.
 */
public final class MechAttachmentSystem {

    /** An attached mech this far from its slot cell re-picks it; under it, a moving squad does not re-route the mech every tick. */
    static final float SLOT_DRIFT_CELLS = 2f;
    /** Cells the slot search spirals out to for ground a chassis can stand on. */
    private static final int SLOT_SEARCH_RADIUS = 4;
    /** An axis shorter than this, in cells, is no axis: the squad is on its destination or its threat. */
    private static final float MIN_AXIS_CELLS = 1.5f;

    private final MechAttachmentService service;

    public MechAttachmentSystem(MechAttachmentService service) {
        this.service = service;
    }

    /** Releases stale attachments, applies queued requests, and publishes each anchor's axis and ranks. */
    public void tick(BattleSimulation sim) {
        for (Map.Entry<Integer, Integer> entry : new ArrayList<>(service.attachments().entrySet())) {
            String over = overReason(entry.getKey(), entry.getValue(), sim);
            if (over != null) detach(entry.getKey(), sim);
        }
        for (Request request : service.drainPending()) {
            if (request.anchorSquadId() == MechAttachmentService.NONE) {
                if (service.isAttached(request.mechSquadId())) detach(request.mechSquadId(), sim);
                continue;
            }
            Squad mechSquad = validMechSquad(request.mechSquadId(), sim);
            Squad anchor = validAnchor(request.anchorSquadId(), sim);
            if (mechSquad == null || anchor == null || withdrawing(mechSquad)) continue;
            if (service.anchorOf(mechSquad.id) == anchor.id) continue;
            attach(mechSquad, anchor, sim);
        }
        publish(sim);
    }

    /**
     * Why a standing attachment is over, or {@code null} while it stands. Package-private so the
     * detach rule can be read without a sim tick.
     */
    String overReason(int mechSquadId, int anchorSquadId, BattleSimulation sim) {
        Squad mechSquad = validMechSquad(mechSquadId, sim);
        if (mechSquad == null) return "mech squad gone";
        Squad anchor = validAnchor(anchorSquadId, sim);
        if (anchor == null) return "anchor gone";
        if (withdrawing(mechSquad)) return "withdrawing";
        if (withdrawing(anchor)) return "anchor withdrawing";
        if (mechSquad.controlledMemberId() != 0L) return "direct control";
        if (sim.getSquadMoveOrderService().activeOrder(mechSquadId) != null) return "direct order";
        for (int i = 0, n = sim.squadMemberCount(mechSquadId); i < n; i++) {
            MechMoveOrderService.ActiveOrder order =
                    sim.getMechMoveOrderService().activeOrder(sim.squadMemberAt(mechSquadId, i));
            if (order != null && !order.commanded()) return "direct order";
        }
        return null;
    }

    private void attach(Squad mechSquad, Squad anchor, BattleSimulation sim) {
        // The attachment replaces whatever direct order the mech stood under, so the relation is
        // not ended on the next pass by the order it was meant to supersede.
        sim.getSquadMoveOrderSystem().cancel(mechSquad.id, sim, "attached");
        for (int i = 0, n = sim.squadMemberCount(mechSquad.id); i < n; i++) {
            long member = sim.squadMemberAt(mechSquad.id, i);
            sim.getMechMoveOrderService().cancel(member);
            service.setSlot(member, null);
        }
        service.attach(mechSquad.id, anchor.id);
        invalidateExecution(mechSquad, sim);
    }

    private void detach(int mechSquadId, BattleSimulation sim) {
        service.detach(mechSquadId);
        for (int i = 0, n = sim.squadMemberCount(mechSquadId); i < n; i++) {
            service.setSlot(sim.squadMemberAt(mechSquadId, i), null);
        }
        Squad squad = sim.getSquad(mechSquadId);
        if (squad != null) invalidateExecution(squad, sim);
    }

    /** Followers' ranks by role, and each anchor's axis, for the parallel pass to read. */
    private void publish(BattleSimulation sim) {
        Map<Long, Integer> ranks = new HashMap<>();
        Map<Long, Integer> marchRanks = new HashMap<>();
        Map<Integer, Integer> marchCounts = new HashMap<>();
        Set<Integer> anchors = new HashSet<>();
        Map<Integer, Map<MechRole, Integer>> counts = new HashMap<>();
        // Mech squad id order, then member order: deterministic, and stable as followers come and go.
        for (Map.Entry<Integer, Integer> entry : service.attachments().entrySet()) {
            int anchorId = entry.getValue();
            anchors.add(anchorId);
            Map<MechRole, Integer> roleCounts = counts.computeIfAbsent(anchorId, id -> new EnumMap<>(MechRole.class));
            for (int i = 0, n = sim.squadMemberCount(entry.getKey()); i < n; i++) {
                long member = sim.squadMemberAt(entry.getKey(), i);
                // The player's attachment outranks a commander's guard on the chassis, as a player's move does.
                sim.getMechMoveOrderService().standDownGuard(member);
                MechLoadoutComponent loadout = sim.world().mechLoadout(member);
                if (loadout == null || !sim.world().isAlive(member)) continue;
                MechRole role = loadout.effectiveRole();
                int rank = roleCounts.getOrDefault(role, 0);
                roleCounts.put(role, rank + 1);
                ranks.put(member, rank);
                int marchRank = marchCounts.getOrDefault(anchorId, 0);
                marchCounts.put(anchorId, marchRank + 1);
                marchRanks.put(member, marchRank);
            }
        }
        service.setRanks(ranks, marchRanks);
        service.retainAxes(anchors::contains);
        for (int anchorId : anchors) {
            Squad anchor = sim.getSquad(anchorId);
            float[] axis = anchor == null ? null : axis(anchor, sim);
            if (axis != null) service.setAxis(anchorId, axis);
        }
    }

    /**
     * The way the squad faces: toward the threat it believes in, else toward where it is going — its
     * order's cell or node, else its leader's route — or {@code null} when neither is far enough off
     * to be a direction. Package-private for tests.
     */
    static float[] axis(Squad anchor, BattleView sim) {
        float cx = anchor.centroidX;
        float cy = anchor.centroidY;
        SquadContactPicture picture = anchor.contactPicture;
        if (picture != null && picture.hasContacts()
                && picture.primaryCellX() >= 0 && picture.primaryCellY() >= 0) {
            float[] axis = unit(picture.primaryCellX() + .5f - cx, picture.primaryCellY() + .5f - cy);
            if (axis != null) return axis;
        }
        if (anchor.alertLevel != SquadAlertLevel.UNAWARE
                && anchor.lastSeenEnemyX >= 0 && anchor.lastSeenEnemyY >= 0) {
            float[] axis = unit(anchor.lastSeenEnemyX + .5f - cx, anchor.lastSeenEnemyY + .5f - cy);
            if (axis != null) return axis;
        }
        ObjectiveAssignment order = anchor.assignmentForExecution();
        if (order != null) {
            if (order.targetCellX() >= 0 && order.targetCellY() >= 0) {
                float[] axis = unit(order.targetCellX() + .5f - cx, order.targetCellY() + .5f - cy);
                if (axis != null) return axis;
            }
            TacticalNode node = order.targetNode();
            if (node != null) {
                float[] axis = unit(node.anchorX + .5f - cx, node.anchorY + .5f - cy);
                if (axis != null) return axis;
            }
        }
        long leader = sim.resolveUnit(anchor.leaderId);
        if (leader != 0L) {
            int[] path = sim.world().path(leader);
            if (!Paths.isEmpty(path)) {
                return unit(Paths.destX(path) + .5f - cx, Paths.destY(path) + .5f - cy);
            }
        }
        return null;
    }

    private static float[] unit(float dx, float dy) {
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        return length < MIN_AXIS_CELLS ? null : new float[]{dx / length, dy / length};
    }

    /**
     * One unit update for an attached chassis: ordinary targeting and fire, and a walk to its slot.
     *
     * @return true when the attachment consumed this unit update; false when the plan step should run
     */
    public boolean executeIfAttached(long mech, Squad squad, BattleControl sim) {
        int anchorId = service.anchorOf(squad.id);
        if (anchorId == MechAttachmentService.NONE) return false;
        Squad anchor = sim.getSquad(anchorId);
        if (anchor == null || anchor.aliveMembers <= 0 || squad.moraleBroken
                || squad.controlledMemberId() == mech || withdrawing(squad)) return false;
        MechLoadoutComponent loadout = sim.world().mechLoadout(mech);
        if (loadout == null || !sim.movement().has(mech)) return false;

        fire(mech, loadout, sim);
        int[] slot = slot(mech, loadout, anchor, sim);
        if (slot == null) {
            if (!Paths.isEmpty(sim.world().path(mech))) sim.clearPath(mech);
            return true;
        }
        MechRouteIntent route = MechRouteIntent.forMember(mech, MechAttachmentSystem.class, anchorId, sim);
        route.refreshCandidates(MechRouteIntent.cellKey(slot[0], slot[1]));
        if (route.moveToward(mech, slot[0], slot[1], sim) == PathRequestStatus.FAILED) {
            service.setSlot(mech, null);
        }
        return true;
    }

    /** The cell this chassis walks to: its cached slot while the ideal stays near it, else a fresh one. */
    private int[] slot(long mech, MechLoadoutComponent loadout, Squad anchor, BattleView sim) {
        float[] axis = service.axisOf(anchor.id);
        float[] ideal = MechAttachmentSlot.point(loadout.effectiveRole(), service.rankOf(mech),
                service.marchRankOf(mech), inContact(anchor),
                anchor.centroidX, anchor.centroidY,
                axis == null ? Float.NaN : axis[0], axis == null ? Float.NaN : axis[1],
                sim.world().x(mech), sim.world().y(mech));
        int[] cached = service.slotOf(mech);
        if (cached != null && TacticalScoring.cellDistance(cached[0] + .5f, cached[1] + .5f,
                ideal[0], ideal[1]) <= SLOT_DRIFT_CELLS
                && MechRouteIntent.candidate(mech, cached[0], cached[1], sim)) {
            return cached;
        }
        int[] fresh = standableNear(mech, ideal[0], ideal[1], sim);
        service.setSlot(mech, fresh);
        return fresh;
    }

    /** The cell nearest ({@code x}, {@code y}) the chassis can stand on, within {@link #SLOT_SEARCH_RADIUS}. */
    private static int[] standableNear(long mech, float x, float y, BattleView sim) {
        NavigationGrid grid = sim.getGrid();
        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        for (int r = 0; r <= SLOT_SEARCH_RADIUS; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int px = cx + dx;
                    int py = cy + dy;
                    if (!grid.inBounds(px, py) || !MechRouteIntent.candidate(mech, px, py, sim)) continue;
                    return new int[]{px, py};
                }
            }
        }
        return null;
    }

    private static void fire(long mech, MechLoadoutComponent loadout, BattleControl sim) {
        long target = MechTargeting.refreshTarget(mech, sim);
        sim.world().setTargetId(mech, target);
        if (target == 0L) return;
        float distance = TacticalScoring.cellDistance(sim.world().x(mech), sim.world().y(mech),
                sim.world().x(target), sim.world().y(target));
        if (distance > sim.world().attackRange(mech)) return;
        boolean visible = sim.getTacticalScoring().hasClearShot(mech, target);
        MechCombatantBehavior.tryFireMechWeapons(mech, loadout, target, distance, sim, visible);
    }

    /**
     * Whether the squad is fighting: it holds contacts or engaged this tick. Only then do its followers
     * take their doctrine's places; otherwise they walk with it. Package-private for tests.
     */
    static boolean inContact(Squad anchor) {
        return anchor.contactPicture != null && anchor.contactPicture.hasContacts() || anchor._engagedThisTick;
    }

    /** A live player mech squad that may follow: not a rescue payload. */
    static Squad validMechSquad(int squadId, BattleView sim) {
        Squad squad = sim.getSquad(squadId);
        return squad != null && squad.faction == Faction.MARINE && squad.isMechSquad()
                && !squad.rescuePickupMech && squad.aliveMembers > 0 ? squad : null;
    }

    /** A live player infantry squad a mech may follow. */
    public static Squad validAnchor(int squadId, BattleView sim) {
        Squad squad = sim.getSquad(squadId);
        return squad != null && squad.faction == Faction.MARINE && !squad.isMechSquad()
                && !squad.isDroneSquad() && squad.aliveMembers > 0 ? squad : null;
    }

    private static boolean withdrawing(Squad squad) {
        ObjectiveAssignment assignment = squad.assignmentForExecution();
        return assignment != null && assignment.kind() == AssignmentKind.WITHDRAW;
    }

    /** Drops the plan standing over the old relation so the squad plans afresh on this tick. */
    private static void invalidateExecution(Squad squad, BattleSimulation sim) {
        synchronized (squad.lock) {
            squad.currentPlan = null;
            squad.currentGoal = null;
        }
        for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
            long member = sim.squadMemberAt(squad.id, i);
            if (sim.resolveUnit(member) == 0L) continue;
            sim.clearPath(member);
            MechLoadoutComponent loadout = sim.world().mechLoadout(member);
            if (loadout != null) loadout.routeIntent.reset();
        }
    }
}
