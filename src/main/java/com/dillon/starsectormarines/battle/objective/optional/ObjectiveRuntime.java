package com.dillon.starsectormarines.battle.objective.optional;

/**
 * One rolled objective's battle-local state. Advanced serially by its
 * {@link ObjectiveBoard} until it reports a final status.
 */
public interface ObjectiveRuntime {

    enum Status {
        /** Still running. */
        ACTIVE,
        /** Done: the reward is paid. */
        SUCCEEDED,
        /** Failed: the cost is carried downstream. */
        FAILED,
        /** Its trigger never came: no reward and no cost. */
        LAPSED;

        public boolean isFinal() { return this != ACTIVE; }
    }

    /** Called once per board advance while this is {@link Status#ACTIVE}. */
    Status advance(ObjectiveContext ctx, int tick);

    /** The battle has ended with this still active. Must not return {@link Status#ACTIVE}. */
    default Status end(ObjectiveContext ctx) { return Status.LAPSED; }
}
