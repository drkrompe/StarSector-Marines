package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Frozen defender-only Assault command frame. */
public final class AssaultDefenderCommandFrame extends CommandFrame {
    private final AssaultDefenderCommandFacts facts;

    private AssaultDefenderCommandFrame(CommandFrame common,
                                        AssaultDefenderCommandFacts facts) {
        super(common.tick(), common.perspective(), common.squads(),
                common.influence(), common.topology(), common.assignments());
        this.facts = facts;
    }

    static AssaultDefenderCommandFrame disclose(
            BattleView sim, Faction perspective, CommandTopology topology,
            CommandAssignmentSnapshot assignments) {
        return new AssaultDefenderCommandFrame(CommandFrame.freeze(sim,
                perspective, topology, assignments),
                AssaultDefenderCommandFacts.freeze(sim, topology));
    }

    public AssaultDefenderCommandFacts facts() { return facts; }
}
