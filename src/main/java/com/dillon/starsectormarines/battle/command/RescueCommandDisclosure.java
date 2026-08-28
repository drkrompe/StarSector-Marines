package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Trusted owning-side disclosure for Civilian Rescue. */
public final class RescueCommandDisclosure
        implements CommandFrameDisclosure<RescueCommandFrame> {
    public static final RescueCommandDisclosure INSTANCE =
            new RescueCommandDisclosure();

    private RescueCommandDisclosure() { }

    @Override
    public RescueCommandFrame freeze(
            BattleView sim, Faction perspective, CommandTopology topology,
            CommandAssignmentSnapshot assignments) {
        if (perspective != Faction.MARINE) {
            throw new IllegalArgumentException(
                    "civilian Rescue corridor requires MARINE perspective");
        }
        return RescueCommandFrame.disclose(sim, perspective, topology,
                assignments);
    }
}
