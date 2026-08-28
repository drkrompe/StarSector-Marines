package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Typed Marine frame for generic Extraction corridor planning. */
public final class ExtractionCommandFrame extends CommandFrame {
    private final ExtractionCommandFacts facts;

    private ExtractionCommandFrame(CommandFrame common,
                                   ExtractionCommandFacts facts) {
        super(common.tick(), common.perspective(), common.squads(),
                common.influence(), common.topology(), common.assignments());
        this.facts = facts;
    }

    static ExtractionCommandFrame disclose(
            BattleView sim, Faction perspective, CommandTopology topology,
            CommandAssignmentSnapshot assignments) {
        return new ExtractionCommandFrame(CommandFrame.freeze(sim, perspective,
                topology, assignments), ExtractionCommandFacts.freeze(sim));
    }

    public ExtractionCommandFacts facts() { return facts; }
}
