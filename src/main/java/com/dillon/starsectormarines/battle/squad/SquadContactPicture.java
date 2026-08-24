package com.dillon.starsectormarines.battle.squad;

/**
 * Immutable squad-level interpretation of identified hostile beliefs. It is
 * published after perception and before GOAP, so parallel tactical readers
 * share one deterministic answer without consulting hidden enemy positions.
 */
public record SquadContactPicture(
        int tick,
        Posture posture,
        float axisX,
        float axisY,
        int contactCount,
        int directContactCount,
        float hostileStrength,
        int friendlyStrength,
        ForceBalance forceBalance,
        Sector dominantSector,
        Motion primaryMotion,
        long primaryContactId,
        int primaryCellX,
        int primaryCellY,
        float primaryConfidence,
        Doctrine doctrine,
        int primaryEngageableMembers,
        int liveMembers,
        int primaryEngageableFireTeams,
        int liveFireTeams,
        ContactInitiative contactInitiative) {

    public enum Posture { ADVANCING, DEFENDING, UNCOMMITTED }
    public enum Sector { NONE, FRONT, LEFT_FLANK, RIGHT_FLANK, REAR, UNKNOWN }
    public enum Motion { UNKNOWN, APPROACHING, LATERAL, WITHDRAWING }
    public enum ForceBalance { NONE, FAVORABLE, EVEN, UNFAVORABLE }
    public enum Doctrine { ADVANCE, HOLD, DISENGAGE }
    /** How an advancing HOLD doctrine is realized against the current contact. */
    public enum ContactInitiative { NONE, RECEIVE, PROSECUTE }

    public static final SquadContactPicture NONE = new SquadContactPicture(
            -1, Posture.UNCOMMITTED, 0f, 0f, 0, 0, 0f, 0,
            ForceBalance.NONE, Sector.NONE, Motion.UNKNOWN, 0L,
            -1, -1, 0f, Doctrine.ADVANCE, 0, 0, 0, 0,
            ContactInitiative.NONE);

    public boolean hasContacts() {
        return contactCount > 0;
    }
}
