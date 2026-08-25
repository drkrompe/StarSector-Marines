package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Stateless, mission-owned disclosure adapter for Conquest command facts. */
public final class ConquestCommandDisclosure
        implements CommandFrameDisclosure<ConquestCommandFrame> {

    public static final ConquestCommandDisclosure INSTANCE =
            new ConquestCommandDisclosure();

    private ConquestCommandDisclosure() { }

    @Override
    public ConquestCommandFrame freeze(BattleView sim, Faction perspective,
                                       CommandTopology topology,
                                       CommandAssignmentSnapshot assignments) {
        return ConquestCommandFrame.disclose(sim, perspective, topology,
                assignments);
    }
}
