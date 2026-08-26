package com.dillon.starsectormarines.battle.command.reinforcement;

/** Outcome of one means provider's attempt to commit a reinforcement delivery. */
public enum ReinforcementDispatchResult {
    /** A delivery actor or squad now exists and owns the request objective. */
    COMMITTED,
    /** This means cannot commit the request; the dispatcher may try the next means. */
    REJECTED,
    /** Transient state prevented commitment; keep the request for a later tick. */
    RETRYABLE
}
