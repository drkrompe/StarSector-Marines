package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Frozen Marine view of the archive and survivor expedition branches. */
public final class SilentColonyCommandFrame extends CommandFrame {

    private final SilentColonyCommandFacts facts;
    private final int archiveZoneId;

    private SilentColonyCommandFrame(
            CommandFrame common, SilentColonyCommandFacts facts,
            int archiveZoneId) {
        super(common.tick(), common.perspective(), common.squads(),
                common.influence(), common.topology(), common.assignments());
        this.facts = facts;
        this.archiveZoneId = archiveZoneId;
    }

    static SilentColonyCommandFrame disclose(
            BattleView sim, Faction perspective, CommandTopology topology,
            CommandAssignmentSnapshot assignments,
            int shelterApproachCellX, int shelterApproachCellY) {
        SilentColonyCommandFacts facts = SilentColonyCommandFacts.freeze(
                sim, shelterApproachCellX, shelterApproachCellY);
        CommandFrame common = CommandFrame.freeze(sim, perspective, topology,
                assignments);
        int archiveZoneId = topology.zoneIdAt(
                facts.archive().sourceCellX(),
                facts.archive().sourceCellY());
        return new SilentColonyCommandFrame(common, facts, archiveZoneId);
    }

    public SilentColonyCommandFacts facts() { return facts; }
    public int archiveZoneId() { return archiveZoneId; }
}
