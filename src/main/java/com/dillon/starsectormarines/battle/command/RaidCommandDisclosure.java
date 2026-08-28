package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Trusted attacker disclosure for the authored Raid contract. */
public final class RaidCommandDisclosure
        implements CommandFrameDisclosure<RaidCommandFrame> {
    public static final RaidCommandDisclosure INSTANCE = new RaidCommandDisclosure();
    private RaidCommandDisclosure() { }

    @Override
    public RaidCommandFrame freeze(BattleView sim, Faction perspective,
                                   CommandTopology topology,
                                   CommandAssignmentSnapshot assignments) {
        if (perspective != Faction.MARINE) {
            throw new IllegalArgumentException("Raid attacker requires MARINE perspective");
        }
        return RaidCommandFrame.disclose(sim, perspective, topology, assignments);
    }
}
