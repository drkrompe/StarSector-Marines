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
        UnitRole role,
        boolean localContact,
        ObjectiveAssignment assignment,
        CommandDirective directive) {
}
