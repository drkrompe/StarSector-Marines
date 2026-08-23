package com.dillon.starsectormarines.marine;

/** Outcome of one atomic template-card assignment to one fire team. */
public enum FireTeamTemplateResult {
    APPLIED,
    INVALID_FIRE_TEAM,
    TEAM_NOT_READY,
    STATIONED,
    UNKNOWN_CARD,
    LOCKED_RECIPE,
    INSUFFICIENT_PRIMARIES,
    INSUFFICIENT_ARMOR,
    INSUFFICIENT_SECONDARIES
}
