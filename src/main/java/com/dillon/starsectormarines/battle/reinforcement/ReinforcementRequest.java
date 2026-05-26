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
 * <p>{@link #rallyX} / {@link #rallyY} of {@code -1} mean "dispatcher picks"
 * (nearest depleted tactical-node compound for the requesting side). v1
 * triggers always set them; the sentinel is reserved for future triggers
 * that don't know where reinforcements should land.
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

    public final Faction side;
    public final Reason reason;
    public final Strength strength;
    public final int rallyX;
    public final int rallyY;

    public ReinforcementRequest(Faction side, Reason reason, Strength strength,
                                int rallyX, int rallyY) {
        this.side = side;
        this.reason = reason;
        this.strength = strength;
        this.rallyX = rallyX;
        this.rallyY = rallyY;
    }

    public boolean hasRally() { return rallyX != RALLY_UNSET && rallyY != RALLY_UNSET; }

    @Override
    public String toString() {
        return "ReinforcementRequest{" + side + " " + reason + " " + strength
                + " rally=(" + rallyX + "," + rallyY + ")}";
    }
}
