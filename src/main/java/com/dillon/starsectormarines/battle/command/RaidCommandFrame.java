package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Typed attacker frame for Raid strategy planning. */
public final class RaidCommandFrame extends CommandFrame {
    private final RaidCommandFacts facts;

    private RaidCommandFrame(CommandFrame common, RaidCommandFacts facts) {
        super(common.tick(), common.perspective(), common.squads(),
                common.influence(), common.topology(), common.assignments());
        this.facts = facts;
    }

    static RaidCommandFrame disclose(BattleView sim, Faction perspective,
                                     CommandTopology topology,
                                     CommandAssignmentSnapshot assignments) {
        return new RaidCommandFrame(CommandFrame.freeze(sim, perspective,
                topology, assignments), RaidCommandFacts.freeze(sim));
    }

    public RaidCommandFacts facts() { return facts; }
}
