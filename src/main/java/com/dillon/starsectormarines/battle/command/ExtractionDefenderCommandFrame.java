package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;

/** Frozen defender frame that contains no hidden Extraction corridor truth. */
public final class ExtractionDefenderCommandFrame extends CommandFrame {
    private final ExtractionDefenderCommandFacts facts;
    private final GarrisonableRegistry garrisonables;

    private ExtractionDefenderCommandFrame(CommandFrame common,
                                            ExtractionDefenderCommandFacts facts,
            GarrisonableRegistry garrisonables) {
        super(common.tick(), common.perspective(), common.squads(),
                common.influence(), common.topology(), common.assignments());
        this.facts = facts;
        this.garrisonables = garrisonables;
    }

    static ExtractionDefenderCommandFrame disclose(
            BattleView sim, Faction perspective, CommandTopology topology,
            CommandAssignmentSnapshot assignments) {
        return new ExtractionDefenderCommandFrame(CommandFrame.freeze(sim,
                perspective, topology, assignments),
                ExtractionDefenderCommandFacts.freeze(sim),
                MissionGarrisons.enabled() ? sim.garrisonables() : null);
    }

    public ExtractionDefenderCommandFacts facts() { return facts; }

    /** The map's garrisonables, for reactive manning; {@code null} with it switched off. */
    GarrisonableRegistry garrisonables() { return garrisonables; }
}
