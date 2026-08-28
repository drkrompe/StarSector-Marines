package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationPlacement;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.Objects;

/** Trusted owning-side disclosure for the blind Silent Colony expedition. */
public final class SilentColonyCommandDisclosure
        implements CommandFrameDisclosure<SilentColonyCommandFrame> {

    private final int shelterApproachCellX;
    private final int shelterApproachCellY;

    public SilentColonyCommandDisclosure(
            CivilianEvacuationPlacement placement) {
        Objects.requireNonNull(placement, "placement");
        shelterApproachCellX = placement.shelterApproachX;
        shelterApproachCellY = placement.shelterApproachY;
    }

    @Override
    public SilentColonyCommandFrame freeze(
            BattleView sim, Faction perspective, CommandTopology topology,
            CommandAssignmentSnapshot assignments) {
        if (perspective != Faction.MARINE) {
            throw new IllegalArgumentException(
                    "Silent Colony expedition requires MARINE perspective");
        }
        return SilentColonyCommandFrame.disclose(sim, perspective, topology,
                assignments, shelterApproachCellX, shelterApproachCellY);
    }
}
