package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.ExtractionPayloadObjective;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;

/** Public source and identity-free alarm available to generic Extraction defense. */
public record ExtractionDefenderCommandFacts(ExtractionObjectiveFacts payload) {

    static ExtractionDefenderCommandFacts freeze(BattleView sim) {
        List<ExtractionObjectiveFacts> packages = ExtractionObjectiveDisclosure
                .freeze(sim, Faction.DEFENDER).stream()
                .filter(payload -> payload.kind()
                        == ExtractionPayloadObjective.Kind.PACKAGE
                        || payload.kind() == ExtractionPayloadObjective.Kind.COHORT)
                .toList();
        if (packages.size() != 1) {
            throw new IllegalStateException(
                    "Extraction defense requires one payload objective");
        }
        return new ExtractionDefenderCommandFacts(packages.get(0));
    }
}
