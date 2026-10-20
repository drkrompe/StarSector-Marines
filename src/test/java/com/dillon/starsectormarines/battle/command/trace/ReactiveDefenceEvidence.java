package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.command.CommandStrategy;
import com.dillon.starsectormarines.battle.command.ReactiveDefenceSource;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.Map;

/**
 * One line per mission run for the mission defenders' reactive manning (`garrisonables.md`, Beyond Conquest): whether
 * it was on, the defender commander's manning figures, losses on both sides from the run's own trace, and the result.
 * Printed by the mission evidence harnesses so a run and its control run ({@code -Dbattle.mission.reactiveDefence=false})
 * can be read side by side.
 */
final class ReactiveDefenceEvidence {

    private ReactiveDefenceEvidence() {}

    static String line(String runId, BattleSimulation sim, String trace) {
        StringBuilder out = new StringBuilder("[reactive-defence] ").append(runId)
                .append(" enabled=").append(!"false".equalsIgnoreCase(
                        System.getProperty("battle.mission.reactiveDefence", "true")));
        CommandStrategy defender = sim.getCommander(Faction.DEFENDER);
        if (defender instanceof ReactiveDefenceSource source) {
            for (Map.Entry<String, Integer> entry : source.reactiveDefenceFigures().entrySet()) {
                out.append(' ').append(entry.getKey()).append('=').append(entry.getValue());
            }
        } else {
            out.append(" figures=none");
        }
        try {
            CommandTraceAnalyzer.RunMetrics run = CommandTraceAnalyzer.analyze(trace).run();
            out.append(" marineLosses=").append(run.combatantCasualties().getOrDefault(Faction.MARINE, 0))
                    .append(" defenderLosses=").append(run.combatantCasualties().getOrDefault(Faction.DEFENDER, 0))
                    .append(" termination=").append(run.termination())
                    .append(" winner=").append(run.winner())
                    .append(" ticks=").append(run.durationTicks());
        } catch (Exception e) {
            out.append(" analysis=").append(e.getClass().getSimpleName());
        }
        return out.toString();
    }
}
