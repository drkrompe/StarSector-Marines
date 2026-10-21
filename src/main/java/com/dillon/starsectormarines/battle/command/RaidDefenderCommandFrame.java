package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;

/** Typed defender frame that deliberately excludes attacker egress geometry. */
public final class RaidDefenderCommandFrame extends CommandFrame {
    private final RaidDefenderCommandFacts facts;
    private final GarrisonableRegistry garrisonables;

    private RaidDefenderCommandFrame(CommandFrame common,
                                     RaidDefenderCommandFacts facts,
            GarrisonableRegistry garrisonables) {
        super(common.tick(), common.perspective(), common.squads(),
                common.influence(), common.topology(), common.assignments());
        this.facts = facts;
        this.garrisonables = garrisonables;
    }

    static RaidDefenderCommandFrame disclose(BattleView sim, Faction perspective,
                                             CommandTopology topology,
                                             CommandAssignmentSnapshot assignments) {
        return new RaidDefenderCommandFrame(CommandFrame.freeze(sim, perspective,
                topology, assignments), RaidDefenderCommandFacts.freeze(sim),
                MissionGarrisons.enabled() ? sim.garrisonables() : null);
    }

    public RaidDefenderCommandFacts facts() { return facts; }

    /** The map's garrisonables, for reactive manning; {@code null} with it switched off. */
    GarrisonableRegistry garrisonables() { return garrisonables; }
}
