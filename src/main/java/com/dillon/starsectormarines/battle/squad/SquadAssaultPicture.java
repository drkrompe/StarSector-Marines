package com.dillon.starsectormarines.battle.squad;

/**
 * Immutable per-squad answer to "what is my part in this attack?" — published
 * once per tick for squads under an attack-move order, after contact pictures
 * and before the replan reads them.
 *
 * <p>This is the cooperation seam between squads. A squad's own
 * {@link SquadContactPicture} says what it can see and how it should posture;
 * this says whether a sibling squad is already fixing the same enemy, and
 * therefore whether forming a second frontal line is the useful thing to do or
 * the wasteful one.
 *
 * <p>It carries no authority. Command still owns where a squad is going, and
 * local doctrine still owns whether the squad is advancing, holding, or
 * breaking. A role only chooses the <em>manner</em> of an order the squad
 * already has, which is why a squad may hold a role and still be pulled off it
 * by morale, a lost fire team, or a new assignment.
 */
public record SquadAssaultPicture(
        int tick,
        Role role,
        long sharedContactId,
        int partnerSquadId,
        float axisX,
        float axisY) {

    /**
     * What this squad does about a contact its cooperating group shares.
     *
     * <p>The split is the fire-team fix-and-flank pattern lifted one level: a
     * squad is to a cooperating group of squads what a fire team is to its
     * squad. The vocabulary is deliberately the same so the two read alike.
     */
    public enum Role {
        /** No cooperation applies — advance and fight under ordinary doctrine. */
        NONE,
        /** Hold the contact's attention from the current ground so a sibling can move. */
        BASE_OF_FIRE,
        /** Move onto a bearing off the fixing squad's axis while it holds. */
        MANEUVER
    }

    public static final SquadAssaultPicture NONE =
            new SquadAssaultPicture(-1, Role.NONE, 0L, -1, 0f, 0f);

    /** True when a sibling squad is fixing this contact and this squad is the one moving. */
    public boolean isManeuvering() {
        return role == Role.MANEUVER && sharedContactId != 0L;
    }

    /** True when this squad owes a sibling the fire that lets it move. */
    public boolean isBaseOfFire() {
        return role == Role.BASE_OF_FIRE && sharedContactId != 0L;
    }
}
