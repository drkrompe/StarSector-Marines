package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Stateless disclosure for the Marine Assault search picture. */
public final class AssaultCommandDisclosure
        implements CommandFrameDisclosure<AssaultCommandFrame> {

    public static final AssaultCommandDisclosure INSTANCE =
            new AssaultCommandDisclosure();

    private AssaultCommandDisclosure() { }

    @Override
    public AssaultCommandFrame freeze(BattleView sim, Faction perspective,
                                      CommandTopology topology,
                                      CommandAssignmentSnapshot assignments) {
        if (perspective != Faction.MARINE) {
            throw new IllegalArgumentException(
                    "Assault attacker disclosure requires MARINE perspective");
        }
        return AssaultCommandFrame.disclose(sim, perspective, topology,
                assignments);
    }
}
