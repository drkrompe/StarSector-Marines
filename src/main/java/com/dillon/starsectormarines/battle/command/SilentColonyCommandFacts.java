package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.ExtractionPayloadObjective;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;

/** Mission-owned objective facts disclosed to the Silent Colony expedition. */
public record SilentColonyCommandFacts(
        ExtractionObjectiveFacts survivors,
        ExtractionObjectiveFacts archive,
        int shelterApproachCellX,
        int shelterApproachCellY) {

    static SilentColonyCommandFacts freeze(
            BattleView sim, int shelterApproachCellX,
            int shelterApproachCellY) {
        List<ExtractionObjectiveFacts> objectives =
                ExtractionObjectiveDisclosure.freeze(sim, Faction.MARINE);
        ExtractionObjectiveFacts survivors = one(objectives,
                ExtractionPayloadObjective.Kind.COHORT);
        ExtractionObjectiveFacts archive = one(objectives,
                ExtractionPayloadObjective.Kind.ARCHIVE);
        return new SilentColonyCommandFacts(survivors, archive,
                shelterApproachCellX, shelterApproachCellY);
    }

    private static ExtractionObjectiveFacts one(
            List<ExtractionObjectiveFacts> objectives,
            ExtractionPayloadObjective.Kind kind) {
        List<ExtractionObjectiveFacts> matches = objectives.stream()
                .filter(objective -> objective.kind() == kind)
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException(
                    "Silent Colony command requires one " + kind
                            + " objective");
        }
        return matches.get(0);
    }
}
