package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Typed Conquest extension over the common perspective frame. */
public final class ConquestCommandFrame extends CommandFrame {

    private final ConquestCommandFacts facts;

    private ConquestCommandFrame(CommandFrame common, ConquestCommandFacts facts) {
        super(common.tick(), common.perspective(), common.squads(),
                common.influence(), common.topology(), common.assignments());
        this.facts = facts;
    }

    static ConquestCommandFrame disclose(BattleView sim, Faction perspective,
                                         CommandTopology topology,
                                         CommandAssignmentSnapshot assignments) {
        return new ConquestCommandFrame(CommandFrame.freeze(sim, perspective,
                topology, assignments), ConquestCommandFacts.freeze(sim));
    }

    public ConquestCommandFacts facts() {
        return facts;
    }
}
