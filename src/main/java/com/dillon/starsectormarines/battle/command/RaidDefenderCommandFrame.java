package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Typed defender frame that deliberately excludes attacker egress geometry. */
public final class RaidDefenderCommandFrame extends CommandFrame {
    private final RaidDefenderCommandFacts facts;

    private RaidDefenderCommandFrame(CommandFrame common,
                                     RaidDefenderCommandFacts facts) {
        super(common.tick(), common.perspective(), common.squads(),
                common.influence(), common.topology(), common.assignments());
        this.facts = facts;
    }

    static RaidDefenderCommandFrame disclose(BattleView sim, Faction perspective,
                                             CommandTopology topology,
                                             CommandAssignmentSnapshot assignments) {
        return new RaidDefenderCommandFrame(CommandFrame.freeze(sim, perspective,
                topology, assignments), RaidDefenderCommandFacts.freeze(sim));
    }

    public RaidDefenderCommandFacts facts() { return facts; }
}
