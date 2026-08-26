package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Typed Sabotage extension over the common perspective command frame. */
public final class SabotageCommandFrame extends CommandFrame {

    private final SabotageCommandFacts facts;

    private SabotageCommandFrame(CommandFrame common, SabotageCommandFacts facts) {
        super(common.tick(), common.perspective(), common.squads(),
                common.influence(), common.topology(), common.assignments());
        this.facts = facts;
    }

    static SabotageCommandFrame disclose(BattleView sim, Faction perspective,
                                         CommandTopology topology,
                                         CommandAssignmentSnapshot assignments) {
        return new SabotageCommandFrame(CommandFrame.freeze(sim, perspective,
                topology, assignments), SabotageCommandFacts.freeze(sim));
    }

    public SabotageCommandFacts facts() {
        return facts;
    }
}
