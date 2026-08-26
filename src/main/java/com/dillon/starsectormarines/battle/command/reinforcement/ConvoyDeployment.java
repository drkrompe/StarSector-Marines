package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.command.SquadCommandClaim;

/** Mission-authored inputs that convoy routing resolves into one vehicle journey. */
public record ConvoyDeployment(
        int hintX,
        int hintY,
        int minimumDefenderForward,
        boolean strictDefenderRearEntry,
        boolean commandOwnsObjective,
        SquadCommandClaim squadClaim) {

    public static ConvoyDeployment legacy(ReinforcementRequest request) {
        return new ConvoyDeployment(request.rallyX, request.rallyY, -1,
                false, false,
                SquadCommandClaim.reinforcement(request.reason.name()));
    }
}
