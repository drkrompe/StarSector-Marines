package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.ExtractionPayloadObjective;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;

/** Full Marine-owned objective projection for generic Extraction command. */
public record ExtractionCommandFacts(ExtractionObjectiveFacts payload) {

    static ExtractionCommandFacts freeze(BattleView sim) {
        List<ExtractionObjectiveFacts> payloads =
                ExtractionObjectiveDisclosure.freeze(sim, Faction.MARINE);
        List<ExtractionObjectiveFacts> packages = payloads.stream()
                .filter(payload -> payload.kind()
                        == ExtractionPayloadObjective.Kind.PACKAGE)
                .toList();
        if (packages.size() != 1) {
            throw new IllegalStateException(
                    "generic Extraction command requires one package objective");
        }
        return new ExtractionCommandFacts(packages.get(0));
    }
}
