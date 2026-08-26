package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Stateless disclosure of defender-owned Assault geometry and own reports. */
public final class AssaultDefenderCommandDisclosure
        implements CommandFrameDisclosure<AssaultDefenderCommandFrame> {
    public static final AssaultDefenderCommandDisclosure INSTANCE =
            new AssaultDefenderCommandDisclosure();

    private AssaultDefenderCommandDisclosure() { }

    @Override
    public AssaultDefenderCommandFrame freeze(
            BattleView sim, Faction perspective, CommandTopology topology,
            CommandAssignmentSnapshot assignments) {
        if (perspective != Faction.DEFENDER) {
            throw new IllegalArgumentException(
                    "Assault defender disclosure requires DEFENDER perspective");
        }
        return AssaultDefenderCommandFrame.disclose(sim, perspective,
                topology, assignments);
    }
}
