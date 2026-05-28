package com.dillon.starsectormarines.battle.reinforcement;

import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * A request for "more units" posted by a {@link ReinforcementTrigger} and
 * fulfilled by the first {@link ReinforcementMeans} that returns
 * {@code canFulfill = true}. The full design lives in
 * {@code roadmap/reinforcement/architecture.md}.
 *
 * <p>Side neutrality: defenders and attackers both post through the same
 * type. The {@link #reason} is informational/diegetic — it surfaces in
 * briefings and after-action reports but never branches dispatch logic.
 *
 * <p>Delivery vs. objective — the two-coordinate split (see
 * {@code roadmap/conquest/stories/progressive-reinforcement.md}):
 * <ul>
 *   <li>{@link #rallyX} / {@link #rallyY} is the <b>delivery hint</b> —
 *       where the {@link ReinforcementMeans} should try to land troops (a
 *       safe LZ/entry in defender-held territory). {@code -1} means
 *       "dispatcher picks".</li>
 *   <li>{@link #objectiveX} / {@link #objectiveY} is the <b>squad
 *       assignment</b> — the contested position the deboarded squad should
 *       advance to and re-man. {@code -1} means "no objective" (overflow →
 *       patrol the delivery slice).</li>
 * </ul>
 * The 5-arg constructor defaults objective = rally, matching the legacy
 * behavior where the rally served double duty.
 */
public final class ReinforcementRequest {

    /** Why the request was posted. Informational; means providers ignore it. */
    public enum Reason {
        GARRISON_DEPLETED,
        OBJECTIVE_LOST,
        SCRIPTED_TIMER
    }

    /**
     * How much reinforcement to send. Each means provider scales its own
     * spawn count from this — see the strength scaling table in
     * {@code roadmap/reinforcement/architecture.md}.
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

    public ReinforcementRequest(Faction side, Reason reason, Strength strength,
                                int rallyX, int rallyY) {
        this(side, reason, strength, rallyX, rallyY, rallyX, rallyY);
    }

    public ReinforcementRequest(Faction side, Reason reason, Strength strength,
                                int rallyX, int rallyY,
                                int objectiveX, int objectiveY) {
        this.side = side;
        this.reason = reason;
        this.strength = strength;
        this.rallyX = rallyX;
        this.rallyY = rallyY;
        this.objectiveX = objectiveX;
        this.objectiveY = objectiveY;
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
