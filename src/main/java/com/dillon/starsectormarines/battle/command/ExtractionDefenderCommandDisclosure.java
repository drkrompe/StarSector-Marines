package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Defender-only disclosure of the public source and legal alarm picture. */
public final class ExtractionDefenderCommandDisclosure
        implements CommandFrameDisclosure<ExtractionDefenderCommandFrame> {
    public static final ExtractionDefenderCommandDisclosure INSTANCE =
            new ExtractionDefenderCommandDisclosure();

    private ExtractionDefenderCommandDisclosure() { }

    @Override
    public ExtractionDefenderCommandFrame freeze(
            BattleView sim, Faction perspective, CommandTopology topology,
            CommandAssignmentSnapshot assignments) {
        if (perspective != Faction.DEFENDER) {
            throw new IllegalArgumentException(
                    "Extraction defense requires DEFENDER perspective");
        }
        return ExtractionDefenderCommandFrame.disclose(sim, perspective,
                topology, assignments);
    }
}
