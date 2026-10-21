package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;

/** Frozen defender-only Assault command frame. */
public final class AssaultDefenderCommandFrame extends CommandFrame {
    private final AssaultDefenderCommandFacts facts;
    private final GarrisonableRegistry garrisonables;

    private AssaultDefenderCommandFrame(CommandFrame common,
                                        AssaultDefenderCommandFacts facts,
            GarrisonableRegistry garrisonables) {
        super(common.tick(), common.perspective(), common.squads(),
                common.influence(), common.topology(), common.assignments());
        this.facts = facts;
        this.garrisonables = garrisonables;
    }

    static AssaultDefenderCommandFrame disclose(
            BattleView sim, Faction perspective, CommandTopology topology,
            CommandAssignmentSnapshot assignments) {
        return new AssaultDefenderCommandFrame(CommandFrame.freeze(sim,
                perspective, topology, assignments),
                AssaultDefenderCommandFacts.freeze(sim, topology),
                MissionGarrisons.enabled() ? sim.garrisonables() : null);
    }

    public AssaultDefenderCommandFacts facts() { return facts; }

    /** The map's garrisonables, for reactive manning; {@code null} with it switched off. */
    GarrisonableRegistry garrisonables() { return garrisonables; }
}
