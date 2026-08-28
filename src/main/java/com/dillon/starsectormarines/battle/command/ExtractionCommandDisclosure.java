package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Trusted Marine disclosure for the generic Extraction corridor. */
public final class ExtractionCommandDisclosure
        implements CommandFrameDisclosure<ExtractionCommandFrame> {
    public static final ExtractionCommandDisclosure INSTANCE =
            new ExtractionCommandDisclosure();

    private ExtractionCommandDisclosure() { }

    @Override
    public ExtractionCommandFrame freeze(
            BattleView sim, Faction perspective, CommandTopology topology,
            CommandAssignmentSnapshot assignments) {
        if (perspective != Faction.MARINE) {
            throw new IllegalArgumentException(
                    "Extraction corridor command requires MARINE perspective");
        }
        return ExtractionCommandFrame.disclose(sim, perspective, topology,
                assignments);
    }
}
