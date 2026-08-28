package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.Objects;

/** Trusted projection of one authored opening-operation place into a side frame. */
public final class OpeningOperationCommandDisclosure
        implements CommandFrameDisclosure<OpeningOperationCommandFrame> {
    private final OpeningOperationCommandFacts facts;

    public OpeningOperationCommandDisclosure(OpeningOperationCommandFacts facts) {
        this.facts = Objects.requireNonNull(facts, "facts");
    }

    @Override
    public OpeningOperationCommandFrame freeze(
            BattleView sim, Faction perspective, CommandTopology topology,
            CommandAssignmentSnapshot assignments) {
        if (perspective != Faction.MARINE && perspective != Faction.DEFENDER) {
            throw new IllegalArgumentException(
                    "opening operation requires a battle-side perspective");
        }
        return OpeningOperationCommandFrame.disclose(sim, perspective,
                topology, assignments, facts);
    }
}
