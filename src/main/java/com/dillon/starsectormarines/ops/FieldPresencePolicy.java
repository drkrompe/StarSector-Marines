package com.dillon.starsectormarines.ops;

/** Mission-owned limit on persistent player squads concurrently in theatre. */
public enum FieldPresencePolicy {

    /** Every committed squad may arrive under the mission's ordinary schedule. */
    UNRESTRICTED(0),
    /** One squad operates while the remainder wait off-map as reserves. */
    INFILTRATION(1),
    /** A two-squad capture team operates while the remainder wait as reserves. */
    CAPTURE_TEAM(2);

    private final int activeSquadLimit;

    FieldPresencePolicy(int activeSquadLimit) {
        this.activeSquadLimit = Math.max(0, activeSquadLimit);
    }

    public int activeSquadLimit() {
        return activeSquadLimit;
    }

    public boolean limited() {
        return activeSquadLimit > 0;
    }

    public String briefingText() {
        if (!limited()) return "Unrestricted field presence";
        return activeSquadLimit + (activeSquadLimit == 1
                ? " squad active · remaining commitment held in reserve"
                : " squads active · remaining commitment held in reserve");
    }

    /** Current production defaults; authored missions may override in their builder. */
    public static FieldPresencePolicy defaultFor(MissionType type) {
        if (type == MissionType.SABOTAGE) return INFILTRATION;
        if (type == MissionType.RAID) return CAPTURE_TEAM;
        return UNRESTRICTED;
    }
}
