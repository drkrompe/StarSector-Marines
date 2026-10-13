package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.fixture.MarineSeatCommitment;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Authored opening position, never a campaign snapshot of last battle's survivors. */
public final class GroundMarineArrival {
    public final float x, y;
    public final SquadCommandClaim commandClaim;
    private boolean deployed;

    public GroundMarineArrival(float x, float y, SquadCommandClaim commandClaim) {
        this.x = x;
        this.y = y;
        this.commandClaim = Objects.requireNonNull(commandClaim, "commandClaim");
    }

    /** The launch roster is the only personnel authority; empty or anonymous forces fail closed. */
    public void deploy(List<MarineSeatCommitment> seats, Consumer<MarineLoadout> deploy) {
        if (deployed) throw new IllegalStateException("Company ground arrival already applied");
        List<MarineLoadout> loadouts = seats.stream().map(MarineSeatCommitment::toLoadout).toList();
        if (loadouts.isEmpty() || loadouts.stream().anyMatch(loadout ->
                loadout.campaignSoldierId == null || loadout.campaignSquad == null))
            throw new IllegalStateException("Ground arrival requires selected named squads");
        // Validate the entire force before any body is created.
        loadouts.forEach(deploy);
        deployed = true;
    }
}
