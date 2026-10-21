package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;

/** Defender-only Sabotage frame; deliberately excludes attacker task facts. */
public final class SabotageDefenderCommandFrame extends CommandFrame {
    private final SabotageDefenderCommandFacts facts;
    private final GarrisonableRegistry garrisonables;

    private SabotageDefenderCommandFrame(CommandFrame common,
                                         SabotageDefenderCommandFacts facts,
            GarrisonableRegistry garrisonables) {
        super(common.tick(), common.perspective(), common.squads(),
                common.influence(), common.topology(), common.assignments());
        this.facts = facts;
        this.garrisonables = garrisonables;
    }

    static SabotageDefenderCommandFrame disclose(
            BattleView sim, Faction perspective, CommandTopology topology,
            CommandAssignmentSnapshot assignments) {
        return new SabotageDefenderCommandFrame(CommandFrame.freeze(sim,
                perspective, topology, assignments),
                SabotageDefenderCommandFacts.freeze(sim),
                MissionGarrisons.enabled() ? sim.garrisonables() : null);
    }

    public SabotageDefenderCommandFacts facts() { return facts; }

    /** The map's garrisonables, for reactive manning; {@code null} with it switched off. */
    GarrisonableRegistry garrisonables() { return garrisonables; }
}
