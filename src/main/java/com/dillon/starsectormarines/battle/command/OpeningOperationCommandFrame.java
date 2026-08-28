package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Frozen own-force picture plus the public place authored by the scenario. */
public final class OpeningOperationCommandFrame extends CommandFrame {
    private final OpeningOperationCommandFacts facts;
    private final int placeZoneId;

    private OpeningOperationCommandFrame(CommandFrame common,
                                         OpeningOperationCommandFacts facts,
                                         int placeZoneId) {
        super(common.tick(), common.perspective(), common.squads(),
                common.influence(), common.topology(), common.assignments());
        this.facts = facts;
        this.placeZoneId = placeZoneId;
    }

    static OpeningOperationCommandFrame disclose(
            BattleView sim, Faction perspective, CommandTopology topology,
            CommandAssignmentSnapshot assignments,
            OpeningOperationCommandFacts facts) {
        return new OpeningOperationCommandFrame(
                CommandFrame.freeze(sim, perspective, topology, assignments),
                facts, topology.zoneIdAt(facts.cellX(), facts.cellY()));
    }

    public OpeningOperationCommandFacts facts() { return facts; }
    public int placeZoneId() { return placeZoneId; }
}
