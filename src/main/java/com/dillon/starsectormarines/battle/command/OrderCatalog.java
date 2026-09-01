package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.action.AttackMove;
import com.dillon.starsectormarines.battle.infantry.SecureCompoundGoal;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One row per {@link AssignmentKind}: what a kind is made of, who may hand it
 * to a squad, and what a squad does with it when the player is the one who
 * did.
 *
 * <p>These facts were a set of switches that had to agree with each other
 * and did not have to: the arbiter's shape check, the arbiter's guess at who
 * owns an assignment nobody claimed, the infantry dispatcher's lookup of the
 * goal a player order must win with, the order system's two eligibility tests
 * and its three completion tests. Adding a kind the player could issue was a
 * five-file edit, and the one that was missed was silent. They are one table
 * now, and {@code OrderCatalogTest} fails the build for a kind without a row.
 *
 * <p><b>The table says which orders are lookups; it does not replace the
 * ladder.</b> A commander's assignment is served by whichever MISSION goal in
 * the arm's library scores it highest — a competition, deliberately, since
 * {@code HOLD_NODE} is served by two goals and a mech lance serves most kinds
 * through one generic goal. A player's order is different in kind: it must
 * win outright, or the click did nothing. So a {@link PlayerOrder} names the
 * goal, and the goal's own relevance still decides whether it is workable.
 */
public final class OrderCatalog {

    /** Which target slots an assignment of this kind must carry to be well-formed. */
    public enum Shape {
        /** No target at all — a standing posture such as {@link AssignmentKind#SUPPORT}. */
        NONE,
        /** {@link ObjectiveAssignment#targetZoneId()}. */
        ZONE,
        /** An exact, walkable {@link ObjectiveAssignment#targetCellX()}/{@code Y}. */
        CELL,
        /** {@link ObjectiveAssignment#targetNode()}. */
        NODE,
        /** {@link ObjectiveAssignment#objectiveId()} and an exact cell to reach it at. */
        OBJECTIVE_AT_CELL
    }

    /** The dispatchers a player order may be handed to. Drones take no player orders. */
    public enum Arm { INFANTRY, MECH }

    /**
     * Whether a player's order of this kind is done, read every tick by the
     * order system. Its own objective only: an order can also be
     * <em>invalidated</em> — its target unreachable, its squad withdrawn — and
     * that is the order system's business, since it needs the squad's origin
     * and a route to answer it.
     */
    @FunctionalInterface
    public interface Completion {
        boolean isComplete(Squad squad, ObjectiveAssignment order, BattleView sim);
    }

    /** Stands until superseded or withdrawn — a defence does not finish by being reached. */
    public static final Completion PERSISTENT = (squad, order, sim) -> false;

    /** The attack move's own arrival rule; see {@link AttackMove#squadHasArrived}. */
    public static final Completion ARRIVED = (squad, order, sim) ->
            AttackMove.squadHasArrived(squad, order.targetCellX(), order.targetCellY(), sim);

    /** The compound is held, or is no longer a compound the battle tracks. */
    public static final Completion CAPTURED = (squad, order, sim) -> {
        CompoundService.Record record = sim.getCompoundService().getRecord(order.targetNode());
        return record == null || record.state == CompoundService.CompoundState.MARINE_HELD;
    };

    /**
     * What the player's version of a kind means to the squad that receives it.
     *
     * @param goal       the MISSION goal that must win while the order stands;
     *                   must be registered in every listed arm's goal library
     * @param arms       which dispatchers may be handed this order by the player
     * @param completion when the order is over and the mission underneath resumes
     */
    public record PlayerOrder(Goal goal, Set<Arm> arms, Completion completion) {

        public PlayerOrder {
            Objects.requireNonNull(goal, "goal");
            Objects.requireNonNull(completion, "completion");
            if (arms == null || arms.isEmpty()) {
                throw new IllegalArgumentException("a player order needs at least one arm");
            }
            arms = Collections.unmodifiableSet(EnumSet.copyOf(arms));
        }

        public boolean allows(Arm arm) {
            return arms.contains(arm);
        }
    }

    /**
     * @param kind              the row key
     * @param shape             which slots an assignment of this kind must carry
     * @param externalAuthority who is assumed to own an assignment of this kind
     *                          that was written without provenance, or null when
     *                          that depends on whether a mission commander exists
     * @param player            the player's version of this kind, or null when the
     *                          player cannot issue it
     */
    public record Row(AssignmentKind kind, Shape shape,
                      CommandAuthority externalAuthority, PlayerOrder player) {

        public Row {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(shape, "shape");
        }

        public boolean playerIssuable() {
            return player != null;
        }
    }

    private static final Map<AssignmentKind, Row> ROWS = new EnumMap<>(AssignmentKind.class);

    static {
        row(AssignmentKind.CLEAR_ZONE, Shape.ZONE, null, null);
        row(AssignmentKind.SWEEP_SECTOR, Shape.CELL, null, null);
        row(AssignmentKind.DEFEND_TRACK, Shape.CELL, null, null);
        row(AssignmentKind.DEFEND_SITE, Shape.CELL, null, null);
        // The one order both arms take from the player; a deliberate area
        // must beat role-specific mech mission goals, which the goal's own
        // relevance arranges.
        row(AssignmentKind.DEFEND_AREA, Shape.CELL, null, new PlayerOrder(
                DefendAssignedAreaGoal.INSTANCE, EnumSet.of(Arm.INFANTRY, Arm.MECH), PERSISTENT));
        row(AssignmentKind.ADVANCE_TRACK, Shape.CELL, null, null);
        // Mechs take a per-chassis move order instead, which is not an
        // assignment at all; see MechMoveOrderSystem.
        row(AssignmentKind.ATTACK_MOVE, Shape.CELL, null, new PlayerOrder(
                AttackMoveGoal.INSTANCE, EnumSet.of(Arm.INFANTRY), ARRIVED));
        row(AssignmentKind.SECURE_COMPOUND, Shape.ZONE, null, new PlayerOrder(
                SecureCompoundGoal.INSTANCE, EnumSet.of(Arm.INFANTRY), CAPTURED));
        // A held node with no issuer is a garrison's: born garrisons hold
        // nodes and nobody else writes HOLD_NODE without provenance.
        row(AssignmentKind.HOLD_NODE, Shape.NODE, CommandAuthority.GARRISON, null);
        row(AssignmentKind.RUSH_OBJECTIVE, Shape.OBJECTIVE_AT_CELL, null, null);
        row(AssignmentKind.WITHDRAW, Shape.CELL, null, null);
        row(AssignmentKind.SUPPORT, Shape.NONE, null, null);
        // An escort with no issuer belongs to the payload it walks beside.
        row(AssignmentKind.ESCORT, Shape.CELL, CommandAuthority.PAYLOAD, null);
    }

    private OrderCatalog() {}

    private static void row(AssignmentKind kind, Shape shape,
                            CommandAuthority externalAuthority, PlayerOrder player) {
        if (ROWS.put(kind, new Row(kind, shape, externalAuthority, player)) != null) {
            throw new IllegalStateException("duplicate order row for " + kind);
        }
    }

    /** The row for {@code kind}; every kind has one, which {@code OrderCatalogTest} pins. */
    public static Row row(AssignmentKind kind) {
        Row row = ROWS.get(Objects.requireNonNull(kind, "kind"));
        if (row == null) throw new IllegalStateException("no order row for " + kind);
        return row;
    }

    /** The player's version of {@code kind}, or null when the player cannot issue it. */
    public static PlayerOrder playerOrder(AssignmentKind kind) {
        return row(kind).player();
    }

    /** Every row, in {@link AssignmentKind} order. */
    public static List<Row> rows() {
        return List.copyOf(ROWS.values());
    }
}
