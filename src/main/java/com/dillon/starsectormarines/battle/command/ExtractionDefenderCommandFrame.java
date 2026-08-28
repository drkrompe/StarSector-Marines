package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Frozen defender frame that contains no hidden Extraction corridor truth. */
public final class ExtractionDefenderCommandFrame extends CommandFrame {
    private final ExtractionDefenderCommandFacts facts;

    private ExtractionDefenderCommandFrame(CommandFrame common,
                                            ExtractionDefenderCommandFacts facts) {
        super(common.tick(), common.perspective(), common.squads(),
                common.influence(), common.topology(), common.assignments());
        this.facts = facts;
    }

    static ExtractionDefenderCommandFrame disclose(
            BattleView sim, Faction perspective, CommandTopology topology,
            CommandAssignmentSnapshot assignments) {
        return new ExtractionDefenderCommandFrame(CommandFrame.freeze(sim,
                perspective, topology, assignments),
                ExtractionDefenderCommandFacts.freeze(sim));
    }

    public ExtractionDefenderCommandFacts facts() { return facts; }
}
