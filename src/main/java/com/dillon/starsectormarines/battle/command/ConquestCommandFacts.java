package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.goap.world.GarrisonArea;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;

/** Objective-law facts disclosed to either Conquest perspective. */
public final class ConquestCommandFacts {

    public record Compound(
            CompoundService.CompoundState state,
            TacticalNode node,
            int captureCellX,
            int captureCellY,
            int captureZoneId,
            int[] garrisonZoneIds) {

        public Compound {
            garrisonZoneIds = garrisonZoneIds.clone();
        }

        @Override public int[] garrisonZoneIds() {
            return garrisonZoneIds.clone();
        }
    }

    private final List<Compound> compounds;
    private final List<DefenceStructureFact> structures;

    private ConquestCommandFacts(List<Compound> compounds) {
        this(compounds, List.of());
    }

    private ConquestCommandFacts(List<Compound> compounds, List<DefenceStructureFact> structures) {
        this.compounds = List.copyOf(compounds);
        this.structures = List.copyOf(structures);
    }

    /**
     * As {@link #freeze(BattleView)}, plus the ground-defence structures {@code perspective} is allowed to know of: its
     * own side's (built for it or held by it, wherever they now stand) and any it has seen. A structure no friendly
     * ever held and no friendly has revealed is not disclosed, so a commander is never sent to ground it has no
     * reason to know exists.
     */
    static ConquestCommandFacts freeze(BattleView sim, Faction perspective) {
        ConquestCommandFacts base = freeze(sim);
        List<DefenceStructureFact> known = new ArrayList<>();
        for (DefenceStructureFact structure : sim.defenceStructures()) {
            boolean ours = perspective.friendlyTo(structure.holder())
                    || perspective.friendlyTo(structure.originalHolder());
            if (ours || seesCell(sim, perspective, structure.seenCellX(), structure.seenCellY())) {
                known.add(structure);
            }
        }
        return new ConquestCommandFacts(base.compounds, known);
    }

    /** Only the company's fog is modelled, so another perspective sees nothing it was not told. */
    private static boolean seesCell(BattleView sim, Faction perspective, int x, int y) {
        return Faction.MARINE.friendlyTo(perspective) && sim.playerSeesCell(x, y);
    }

    /** Sole live objective-state projection used by migrated Conquest strategies. */
    static ConquestCommandFacts freeze(BattleView sim) {
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        long started = profile != null ? System.nanoTime() : 0L;
        try {
            return freezeFacts(sim);
        } finally {
            if (profile != null) profile.record(TickInnerProfile.Bucket.COMMANDER_FRAME_FACTS,
                    System.nanoTime() - started);
        }
    }

    private static ConquestCommandFacts freezeFacts(BattleView sim) {
        List<Compound> facts = new ArrayList<>();
        for (CompoundService.Record record : sim.getCompoundService().getRecords()) {
            TacticalNode node = CommandFrameCopies.node(record.node);
            int captureZone = sim.getCompoundService()
                    .captureZoneId(record, sim);
            List<Integer> garrison = GarrisonArea.garrisonZones(record.node,
                    ConquestCommand.GARRISON_MARGIN, sim);
            facts.add(new Compound(record.state, node,
                    record.captureCellX, record.captureCellY, captureZone,
                    garrison.stream().mapToInt(Integer::intValue).toArray()));
        }
        return new ConquestCommandFacts(facts, List.of());
    }

    public List<Compound> compounds() {
        return compounds;
    }

    /** The ground-defence structures this perspective may know of; empty for the other freeze. */
    public List<DefenceStructureFact> structures() {
        return structures;
    }
}
