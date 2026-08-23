package com.dillon.starsectormarines.battle.command;

/**
 * Categories of per-faction battle resources accumulated over time and
 * spent by orchestration layers. Each type is produced by a specific
 * compound kind and consumed by its corresponding dispatch system.
 */
public enum ResourceType {
    /** Spent by the reinforcement dispatcher for any ordinary means. Produced by held ARMORYs. */
    REINFORCEMENT,
    /** Reserved for future air-strike dispatch. Produced by alive COMMAND_POSTs. */
    AIRSTRIKE
}
