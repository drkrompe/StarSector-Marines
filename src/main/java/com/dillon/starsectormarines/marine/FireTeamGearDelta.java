package com.dillon.starsectormarines.marine;

/** One catalog line in a prospective fire-team refit transaction. */
public record FireTeamGearDelta(Kind kind, String label, int free,
                                int returned, int required) {

    /** Sentinel for fleet issue that is not inventory-limited. */
    public static final int UNLIMITED = -1;

    public enum Kind {
        PRIMARY,
        ARMOR,
        SPECIAL
    }

    public boolean unlimited() {
        return free == UNLIMITED;
    }

    /** Positive means stores issue gear; negative means the refit returns a surplus. */
    public int netIssue() {
        return required - returned;
    }

    public int availableAfterReturns() {
        return unlimited() ? UNLIMITED : free + returned;
    }

    public boolean sufficient() {
        return unlimited() || availableAfterReturns() >= required;
    }
}
