package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Frozen Marine view of one authored civilian-rescue corridor. */
public final class RescueCommandFrame extends CommandFrame {
    private final RescueCommandFacts facts;

    private RescueCommandFrame(CommandFrame common, RescueCommandFacts facts) {
        super(common.tick(), common.perspective(), common.squads(),
                common.influence(), common.topology(), common.assignments());
        this.facts = facts;
    }

    static RescueCommandFrame disclose(
            BattleView sim, Faction perspective, CommandTopology topology,
            CommandAssignmentSnapshot assignments) {
        return new RescueCommandFrame(CommandFrame.freeze(sim, perspective,
                topology, assignments), RescueCommandFacts.freeze(sim));
    }

    public RescueCommandFacts facts() { return facts; }
}
