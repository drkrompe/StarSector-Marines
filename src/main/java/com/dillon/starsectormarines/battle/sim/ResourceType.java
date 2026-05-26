package com.dillon.starsectormarines.battle.sim;

/**
 * Categories of per-faction battle resources accumulated over time and
 * spent by orchestration layers. Each type is produced by a specific
 * compound kind and consumed by its corresponding dispatch system.
 */
public enum ResourceType {
    /** Spent by {@link com.dillon.starsectormarines.battle.reinforcement.ReinforcementService} to dispatch a convoy or shuttle. Produced by alive ARMORYs. */
    REINFORCEMENT,
    /** Reserved for future air-strike dispatch. Produced by alive COMMAND_POSTs. */
    AIRSTRIKE
}
