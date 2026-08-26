package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Frozen Marine perspective used by the Assault search commander. */
public final class AssaultCommandFrame extends CommandFrame {

    private AssaultCommandFrame(CommandFrame common) {
        super(common.tick(), common.perspective(), common.squads(),
                common.influence(), common.topology(), common.assignments());
    }

    static AssaultCommandFrame disclose(BattleView sim, Faction perspective,
                                        CommandTopology topology,
                                        CommandAssignmentSnapshot assignments) {
        return new AssaultCommandFrame(CommandFrame.freeze(sim, perspective,
                topology, assignments));
    }
}
