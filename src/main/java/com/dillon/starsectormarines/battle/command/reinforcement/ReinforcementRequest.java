package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * A request for "more units" posted by a {@link ReinforcementTrigger} and
 * offered in priority order until a {@link ReinforcementMeans} commits it.
 * See {@code reinforcement-nouns.md}.
 *
 * <p>Side capability: the request type can represent defenders or attackers,
 * while production means currently dispatch defenders only. The
 * {@link #reason} is dispatch-neutral context and never selects a means.
 *
 * <p>Delivery vs. objective — the two-coordinate split (see
 * {@code reinforcement-nouns.md}):
 * <ul>
 *   <li>{@link #rallyX} / {@link #rallyY} is the <b>delivery hint</b> —
 *       where the {@link ReinforcementMeans} should try to land troops (a
 *       safe LZ/entry in defender-held territory). {@code -1} means
 *       "dispatcher picks".</li>
 *   <li>{@link #objectiveX} / {@link #objectiveY} is the <b>squad
 *       assignment</b> — the contested position the deboarded squad should
 *       advance to and re-man. {@code -1} means no explicit tactical-node
 *       assignment.</li>
 * </ul>
 * The 5-arg constructor defaults objective = rally, matching the legacy
 * behavior where the rally served double duty.
 *
 * <p>Prepaid requests (see {@link #prepaid}) are the bulge counterattack's
 * ({@code reinforcement-nouns.md}) up-front ticket
 * earmark: the poster already debited the cost in one lump at muster, so
 * {@link ReinforcementSystem#tick} must neither debit nor refund on dispatch
 * — the earmark is a sunk bet, win or lose.
 */
public final class ReinforcementRequest {

    /** Why the request was posted. Informational; means providers ignore it. */
    public enum Reason {
        GARRISON_DEPLETED,
        OBJECTIVE_LOST,
        SCRIPTED_TIMER,
        /**
         * The staged bulge counterattack's massed wave — see
         * {@code reinforcement-nouns.md} and
         * {@link CounterattackSystem}. Informational like the others; it does
         * not branch dispatch logic. Production counterattack requests are
         * posted as {@link #prepaid}.
         */
        COUNTERATTACK
    }

    /**
     * How much reinforcement to send. Each means provider scales its own
     * spawn count from this. The coordinated open contract is tracked by
     * {@code reinforcement-strength-scaling.md}.
     */
    public enum Strength { SMALL, MEDIUM, LARGE }

    public static final int RALLY_UNSET = -1;
    public static final int OBJECTIVE_UNSET = -1;

    public final Faction side;
    public final Reason reason;
    public final Strength strength;
    public final int rallyX;
    public final int rallyY;
    public final int objectiveX;
    public final int objectiveY;

    /**
     * True when the poster already paid the ticket cost in a single up-front
     * lump (the bulge counterattack's earmark — see {@link Reason#COUNTERATTACK}
     * and {@code reinforcement-nouns.md}), rather than
     * per-dispatch. {@link ReinforcementSystem#tick} skips both the debit on
     * dispatch and the refund on the no-means path for a prepaid request: the
     * commitment was the point, so a wave request no means can deliver still
     * burns its ticket. Defaults to {@code false} for every non-widest
     * constructor — the steady per-dispatch debit is unchanged.
     */
    public final boolean prepaid;
    private final ReinforcementDispatchReservation dispatchReservation;

    public ReinforcementRequest(Faction side, Reason reason, Strength strength,
                                int rallyX, int rallyY) {
        this(side, reason, strength, rallyX, rallyY, rallyX, rallyY);
    }

    public ReinforcementRequest(Faction side, Reason reason, Strength strength,
                                int rallyX, int rallyY,
                                int objectiveX, int objectiveY) {
        this(side, reason, strength, rallyX, rallyY, objectiveX, objectiveY, false);
    }

    public ReinforcementRequest(Faction side, Reason reason, Strength strength,
                                int rallyX, int rallyY,
                                int objectiveX, int objectiveY,
                                boolean prepaid) {
        this(side, reason, strength, rallyX, rallyY,
                objectiveX, objectiveY, prepaid, null);
    }

    private ReinforcementRequest(Faction side, Reason reason, Strength strength,
                                 int rallyX, int rallyY,
                                 int objectiveX, int objectiveY,
                                 boolean prepaid,
                                 ReinforcementDispatchReservation reservation) {
        this.side = side;
        this.reason = reason;
        this.strength = strength;
        this.rallyX = rallyX;
        this.rallyY = rallyY;
        this.objectiveX = objectiveX;
        this.objectiveY = objectiveY;
        this.prepaid = prepaid;
        this.dispatchReservation = reservation;
    }

    /** Returns the same request data carrying provisional trigger state. */
    ReinforcementRequest withDispatchReservation(
            ReinforcementDispatchReservation reservation) {
        return new ReinforcementRequest(side, reason, strength,
                rallyX, rallyY, objectiveX, objectiveY, prepaid, reservation);
    }

    /** Release provisional trigger state after every means rejects. */
    void releaseDispatchReservation() {
        if (dispatchReservation != null) dispatchReservation.release();
    }

    public boolean hasRally() { return rallyX != RALLY_UNSET && rallyY != RALLY_UNSET; }

    public boolean hasObjective() {
        return objectiveX != OBJECTIVE_UNSET && objectiveY != OBJECTIVE_UNSET;
    }

    @Override
    public String toString() {
        return "ReinforcementRequest{" + side + " " + reason + " " + strength
                + " rally=(" + rallyX + "," + rallyY + ")"
                + " objective=(" + objectiveX + "," + objectiveY + ")}";
    }
}
