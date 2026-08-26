package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Stateless, mission-owned disclosure adapter for Sabotage command facts. */
public final class SabotageCommandDisclosure
        implements CommandFrameDisclosure<SabotageCommandFrame> {

    public static final SabotageCommandDisclosure INSTANCE =
            new SabotageCommandDisclosure();

    private SabotageCommandDisclosure() { }

    @Override
    public SabotageCommandFrame freeze(BattleView sim, Faction perspective,
                                       CommandTopology topology,
                                       CommandAssignmentSnapshot assignments) {
        if (perspective != Faction.MARINE) {
            throw new IllegalArgumentException(
                    "attacker Sabotage disclosure requires MARINE perspective");
        }
        return SabotageCommandFrame.disclose(sim, perspective, topology,
                assignments);
    }
}
