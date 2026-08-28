package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Trusted defender disclosure for Raid target security. */
public final class RaidDefenderCommandDisclosure
        implements CommandFrameDisclosure<RaidDefenderCommandFrame> {
    public static final RaidDefenderCommandDisclosure INSTANCE =
            new RaidDefenderCommandDisclosure();
    private RaidDefenderCommandDisclosure() { }

    @Override
    public RaidDefenderCommandFrame freeze(BattleView sim, Faction perspective,
                                           CommandTopology topology,
                                           CommandAssignmentSnapshot assignments) {
        if (perspective != Faction.DEFENDER) {
            throw new IllegalArgumentException("Raid defender requires DEFENDER perspective");
        }
        return RaidDefenderCommandFrame.disclose(sim, perspective, topology,
                assignments);
    }
}
