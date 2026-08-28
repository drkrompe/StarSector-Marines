package com.dillon.starsectormarines.battle.command;

/** Narrow mission facts that may invalidate a directive before its stability floor. */
public enum CommandStabilityBreak {
    NONE("ordinary replanning"),
    OBJECTIVE_COMPLETED("objective completed"),
    TARGET_UNREACHABLE("target became unreachable"),
    TOPOLOGY_REBOUND("target rebound to rebuilt topology"),
    CONTEXT_INVALIDATED("command context invalidated");

    private final String description;

    CommandStabilityBreak(String description) {
        this.description = description;
    }

    public boolean permitsEarlySupersession() {
        return this != NONE;
    }

    public String description() {
        return description;
    }
}
