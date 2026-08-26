package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Mission-owned defender disclosure that exposes only installation alarms. */
public final class SabotageDefenderCommandDisclosure
        implements CommandFrameDisclosure<SabotageDefenderCommandFrame> {
    public static final SabotageDefenderCommandDisclosure INSTANCE =
            new SabotageDefenderCommandDisclosure();

    private SabotageDefenderCommandDisclosure() { }

    @Override
    public SabotageDefenderCommandFrame freeze(
            BattleView sim, Faction perspective, CommandTopology topology,
            CommandAssignmentSnapshot assignments) {
        if (perspective != Faction.DEFENDER) {
            throw new IllegalArgumentException(
                    "defender Sabotage disclosure requires DEFENDER perspective");
        }
        return SabotageDefenderCommandFrame.disclose(sim, perspective,
                topology, assignments);
    }
}
