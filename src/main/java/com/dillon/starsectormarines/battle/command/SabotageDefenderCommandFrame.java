package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Defender-only Sabotage frame; deliberately excludes attacker task facts. */
public final class SabotageDefenderCommandFrame extends CommandFrame {
    private final SabotageDefenderCommandFacts facts;

    private SabotageDefenderCommandFrame(CommandFrame common,
                                         SabotageDefenderCommandFacts facts) {
        super(common.tick(), common.perspective(), common.squads(),
                common.influence(), common.topology(), common.assignments());
        this.facts = facts;
    }

    static SabotageDefenderCommandFrame disclose(
            BattleView sim, Faction perspective, CommandTopology topology,
            CommandAssignmentSnapshot assignments) {
        return new SabotageDefenderCommandFrame(CommandFrame.freeze(sim,
                perspective, topology, assignments),
                SabotageDefenderCommandFacts.freeze(sim));
    }

    public SabotageDefenderCommandFacts facts() { return facts; }
}
