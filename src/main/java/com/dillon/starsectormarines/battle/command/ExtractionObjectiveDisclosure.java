package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.objective.ExtractionPayloadObjective;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Trusted perspective boundary for Extraction-family objective facts. */
public final class ExtractionObjectiveDisclosure {

    private ExtractionObjectiveDisclosure() { }

    public static List<ExtractionObjectiveFacts> freeze(
            BattleView sim, Faction perspective) {
        if (perspective != Faction.MARINE && perspective != Faction.DEFENDER) {
            throw new IllegalArgumentException(
                    "Extraction disclosure requires a combat perspective");
        }
        List<ExtractionPayloadObjective> objectives = objectives(sim);
        List<ExtractionObjectiveFacts> facts = new ArrayList<>(objectives.size());
        for (ExtractionPayloadObjective objective : objectives) {
            facts.add(perspective == Faction.MARINE
                    ? full(objective) : defender(objective));
        }
        return List.copyOf(facts);
    }

    /** Full neutral truth for evidence only; never a commander input. */
    public static List<ExtractionObjectiveFacts> freezeNeutral(BattleView sim) {
        List<ExtractionPayloadObjective> objectives = objectives(sim);
        List<ExtractionObjectiveFacts> facts = new ArrayList<>(objectives.size());
        for (ExtractionPayloadObjective objective : objectives) {
            facts.add(full(objective));
        }
        return List.copyOf(facts);
    }

    private static List<ExtractionPayloadObjective> objectives(BattleView sim) {
        List<ExtractionPayloadObjective> objectives = new ArrayList<>();
        for (Objective objective : sim.getObjectives()) {
            if (objective instanceof ExtractionPayloadObjective payload) {
                objectives.add(payload);
            }
        }
        objectives.sort(Comparator.comparing(
                ExtractionPayloadObjective::payloadId));
        return objectives;
    }

    private static ExtractionObjectiveFacts full(
            ExtractionPayloadObjective objective) {
        return new ExtractionObjectiveFacts(objective.payloadId(),
                objective.payloadName(), objective.payloadKind(),
                objective.extractionPhase().name(), objective.sourceCellX(),
                objective.sourceCellY(), objective.egressCellX(),
                objective.egressCellY(), objective.payloadCellX(),
                objective.payloadCellY(), objective.initialElements(),
                objective.activeElements(), objective.boardedElements(),
                objective.lostElements(), objective.normalizedProgress(),
                objective.alarmActive(), objective.alarmRaisedTick(),
                objective.controllingSquadId(), objective.escortPresent(),
                objective.isComplete(), objective.isFailed(),
                objective.failureReason());
    }

    private static ExtractionObjectiveFacts defender(
            ExtractionPayloadObjective objective) {
        String phase = objective.isComplete() ? "COMPLETE"
                : objective.isFailed() ? "FAILED"
                : objective.alarmActive() ? "ALARM" : "QUIET";
        return new ExtractionObjectiveFacts(objective.payloadId(),
                objective.payloadName(), objective.payloadKind(), phase,
                objective.sourceCellX(), objective.sourceCellY(),
                -1, -1, -1, -1, -1, -1, -1, -1, 0f,
                objective.alarmActive(), objective.alarmRaisedTick(),
                -1, false, objective.isComplete(), objective.isFailed(),
                objective.isFailed() ? objective.failureReason()
                        : ExtractionPayloadObjective.Failure.NONE);
    }
}
