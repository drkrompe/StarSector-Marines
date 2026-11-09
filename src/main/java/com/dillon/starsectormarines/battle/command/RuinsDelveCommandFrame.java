package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** The company's frame for the Ruins delve: its own squads and the company's view of the vault. */
public final class RuinsDelveCommandFrame extends CommandFrame {
    private final RuinsObjectiveFacts facts;

    private RuinsDelveCommandFrame(CommandFrame common, RuinsObjectiveFacts facts) {
        super(common.tick(), common.perspective(), common.squads(),
                common.influence(), common.topology(), common.assignments());
        this.facts = facts;
    }

    static RuinsDelveCommandFrame disclose(BattleView sim, Faction perspective, CommandTopology topology,
                                           CommandAssignmentSnapshot assignments) {
        return new RuinsDelveCommandFrame(CommandFrame.freeze(sim, perspective, topology, assignments),
                RuinsObjectiveDisclosure.freeze(sim, perspective));
    }

    /** A frame over already-frozen common state and facts. */
    static RuinsDelveCommandFrame of(CommandFrame common, RuinsObjectiveFacts facts) {
        return new RuinsDelveCommandFrame(common, facts);
    }

    public RuinsObjectiveFacts facts() { return facts; }
}
