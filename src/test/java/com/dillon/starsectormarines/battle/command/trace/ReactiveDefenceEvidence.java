package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.command.CommandStrategy;
import com.dillon.starsectormarines.battle.command.ReactiveDefenceSource;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

/**
 * One line per mission run for the mission defenders' reactive manning (`garrisonables.md`, mission defenders): whether
 * it was on, the defender commander's manning figures, combatant losses on both sides from the run's own trace, and the result.
 * Printed by the mission evidence harnesses so a run and its control run ({@code -Dbattle.mission.reactiveDefence=false})
 * can be read side by side.
 */
public final class ReactiveDefenceEvidence {

    private ReactiveDefenceEvidence() {}

    /** The defender commander's manning figures, or an empty object when it mans nothing reactively. */
    public static JSONObject figures(BattleSimulation sim) throws JSONException {
        JSONObject out = new JSONObject();
        if (sim.getCommander(Faction.DEFENDER) instanceof ReactiveDefenceSource source) {
            for (Map.Entry<String, Integer> entry : source.reactiveDefenceFigures().entrySet()) {
                out.put(entry.getKey(), entry.getValue().intValue());
            }
        }
        return out;
    }

    static String line(String runId, BattleSimulation sim, String trace) throws Exception {
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
        int marines = 0;
        int defenders = 0;
        for (String row : trace.lines().toList()) {
            if (row.isBlank() || !row.contains("\"casualty\"")) continue;
            JSONObject event = new JSONObject(row);
            if (!"casualty".equals(event.optString("event")) || !event.optBoolean("combatant")) continue;
            if ("MARINE".equals(event.optString("faction"))) marines++;
            else if ("DEFENDER".equals(event.optString("faction"))) defenders++;
        }
        out.append(" marineLosses=").append(marines)
                .append(" defenderLosses=").append(defenders)
                .append(" termination=").append(sim.isComplete() ? "TERMINAL" : "TIMEOUT")
                .append(" winner=").append(sim.getWinner())
                .append(" ticks=").append(sim.getSimTickIndex());
        return out.toString();
    }
}
