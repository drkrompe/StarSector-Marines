package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;

/** Copied own-force row available to a frame-only mission strategy. */
public record CommandSquadState(
        int squadId,
        Faction faction,
        int aliveMembers,
        float centroidX,
        float centroidY,
        int anchorCellX,
        int anchorCellY,
        int currentZoneId,
        UnitRole role,
        boolean localContact,
        boolean underFireRecently,
        boolean moraleBroken,
        String currentGoal,
        String currentAction,
        String executionSuspension,
        ObjectiveAssignment assignment,
        CommandDirective directive,
        int activePathMembers,
        int movingMembers,
        int coveredFromPrimaryMembers,
        int primaryEngageableMembers,
        int primaryEngageableFireTeams,
        String contactPosture,
        String contactDoctrine,
        String contactInitiative,
        int coolingDownMembers,
        int[] memberZoneIds,
        int[] memberCellXs,
        int[] memberCellYs) {

    public CommandSquadState {
        memberZoneIds = memberZoneIds.clone();
        memberCellXs = memberCellXs.clone();
        memberCellYs = memberCellYs.clone();
        if (memberCellXs.length != memberCellYs.length
                || memberCellXs.length != memberZoneIds.length) {
            throw new IllegalArgumentException(
                    "member position and zone arrays must have equal length");
        }
    }

    @Override public int[] memberZoneIds() {
        return memberZoneIds.clone();
    }

    @Override public int[] memberCellXs() { return memberCellXs.clone(); }

    @Override public int[] memberCellYs() { return memberCellYs.clone(); }

    /** Back-compatible construction seam for focused command fixtures. */
    public CommandSquadState(int squadId, Faction faction, int aliveMembers,
                             float centroidX, float centroidY, int anchorCellX,
                             int anchorCellY, int currentZoneId, UnitRole role,
                             boolean localContact, String executionSuspension,
                             ObjectiveAssignment assignment,
                             CommandDirective directive, int activePathMembers,
                             int[] memberZoneIds) {
        this(squadId, faction, aliveMembers, centroidX, centroidY, anchorCellX,
                anchorCellY, currentZoneId, role, localContact, false, false,
                null, null, executionSuspension, assignment, directive,
                activePathMembers, 0, -1, 0, 0, null, null, null, 0,
                memberZoneIds, new int[memberZoneIds.length],
                new int[memberZoneIds.length]);
    }
}
