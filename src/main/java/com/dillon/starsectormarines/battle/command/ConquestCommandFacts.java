package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.goap.world.GarrisonArea;
import com.dillon.starsectormarines.battle.sim.BattleView;

import java.util.ArrayList;
import java.util.List;

/** Objective-law facts disclosed to either Conquest perspective. */
public final class ConquestCommandFacts {

    public record Compound(
            CompoundService.CompoundState state,
            TacticalNode node,
            int anchorZoneId,
            int[] garrisonZoneIds) {

        public Compound {
            garrisonZoneIds = garrisonZoneIds.clone();
        }

        @Override public int[] garrisonZoneIds() {
            return garrisonZoneIds.clone();
        }
    }

    private final List<Compound> compounds;

    private ConquestCommandFacts(List<Compound> compounds) {
        this.compounds = List.copyOf(compounds);
    }

    /** Sole live objective-state projection used by migrated Conquest strategies. */
    static ConquestCommandFacts freeze(BattleView sim) {
        List<Compound> facts = new ArrayList<>();
        for (CompoundService.Record record : sim.getCompoundService().getRecords()) {
            TacticalNode node = CommandFrameCopies.node(record.node);
            int anchorZone = sim.getZoneGraph().zoneIdAt(
                    record.node.anchorX, record.node.anchorY);
            List<Integer> garrison = GarrisonArea.garrisonZones(record.node,
                    ConquestCommand.GARRISON_MARGIN, sim);
            facts.add(new Compound(record.state, node, anchorZone,
                    garrison.stream().mapToInt(Integer::intValue).toArray()));
        }
        return new ConquestCommandFacts(facts);
    }

    public List<Compound> compounds() {
        return compounds;
    }
}
