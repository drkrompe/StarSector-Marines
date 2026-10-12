package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Public freight dispatch hold, frozen beside territory facts; no cargo or enemy positions. */
public final class ThunderRunCommandFrame extends CommandFrame {
    private final ConquestCommandFacts facts;
    private final int holdSite;

    private ThunderRunCommandFrame(ConquestCommandFrame common, int holdSite) {
        super(common.tick(), common.perspective(), common.squads(), common.influence(),
                common.topology(), common.assignments());
        facts = common.facts();
        this.holdSite = holdSite;
    }

    public static ThunderRunCommandFrame disclose(BattleView view, Faction perspective,
            CommandTopology topology, CommandAssignmentSnapshot assignments, int holdSite) {
        return new ThunderRunCommandFrame(ConquestCommandFrame.disclose(view, perspective, topology, assignments), holdSite);
    }
    public ConquestCommandFacts facts() { return facts; }
    public int holdSite() { return holdSite; }
}
