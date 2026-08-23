package com.dillon.starsectormarines.marine;

/** Outcome of one atomic template assignment to a fire team or whole squad. */
public enum FireTeamTemplateResult {
    APPLIED,
    INVALID_FIRE_TEAM,
    TEAM_NOT_READY,
    STATIONED,
    UNKNOWN_TEMPLATE,
    UNKNOWN_ARRANGEMENT,
    LOCKED_RECIPE,
    INSUFFICIENT_PRIMARIES,
    INSUFFICIENT_ARMOR,
    INSUFFICIENT_SECONDARIES
}
