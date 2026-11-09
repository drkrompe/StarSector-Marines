package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Trusted company disclosure for the Ruins delve. */
public final class RuinsDelveCommandDisclosure implements CommandFrameDisclosure<RuinsDelveCommandFrame> {
    public static final RuinsDelveCommandDisclosure INSTANCE = new RuinsDelveCommandDisclosure();

    private RuinsDelveCommandDisclosure() { }

    @Override
    public RuinsDelveCommandFrame freeze(BattleView sim, Faction perspective, CommandTopology topology,
                                         CommandAssignmentSnapshot assignments) {
        if (perspective != Faction.MARINE) {
            throw new IllegalArgumentException("the delve is the company's command");
        }
        return RuinsDelveCommandFrame.disclose(sim, perspective, topology, assignments);
    }
}
