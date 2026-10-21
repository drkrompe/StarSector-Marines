package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.command.CommandStrategy;
import com.dillon.starsectormarines.battle.command.ReactiveDefenceSource;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadMoraleSystem;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;

import org.json.JSONObject;

import java.util.Map;

/**
 * One line per mission run for the mission defenders' reactive manning (`garrisonables.md`, mission defenders): whether
 * it was on, the defender commander's manning figures, combatant losses on both sides from the run's own trace, and the result.
 * Printed by the mission evidence harnesses so a run and its control run ({@code -Dbattle.mission.reactiveDefence=false})
 * can be read side by side.
 *
 * <p>It also reports the <b>fight mix</b> (`garrisonables.md`, not every fight is into prepared positions): every
 * {@link Mix#EVERY_TICKS} ticks, each defender infantry squad in a fight (shot at within the morale system's
 * recovery window, or a member firing) is counted once: on a post when most of its living members stand under an
 * intact roof or on a fighting-position cell, in the open otherwise, and in the open while moving when most of them
 * are also moving. Read from the simulation's truth, so it is the same
 * measurement in every arm of a control, including the one with reactive manning off.
 */
final class ReactiveDefenceEvidence {

    private ReactiveDefenceEvidence() {}

    /** Samples the defender's fights: how many were fought from a post, how many in the open, how many on the move. */
    static final class Mix {
        /** One command pulse. */
        static final int EVERY_TICKS = 75;
        int onPost;
        int open;
        int openMoving;

        void sample(BattleSimulation sim) {
            if (sim.getSimTickIndex() % EVERY_TICKS != 0) return;
            FightingPositionIndex positions = sim.fightingPositions();
            for (Squad squad : sim.getSquads()) {
                if (squad.faction != Faction.DEFENDER || squad.aliveMembers <= 0) continue;
                if (squad.isMechSquad() || squad.isDroneSquad()) continue;
                int members = sim.squadMemberCount(squad.id);
                if (members <= 0 || sim.ambientHeld(sim.squadMemberAt(squad.id, 0))) continue;
                if (squad.timeSinceUnderFire >= SquadMoraleSystem.MORALE_RECOVER_AFTER_FIRE_SECONDS
                        && !firing(sim, squad, members)) continue;
                int posted = 0;
                int moving = 0;
                for (int i = 0; i < members; i++) {
                    long member = sim.squadMemberAt(squad.id, i);
                    int x = sim.world().cellX(member);
                    int y = sim.world().cellY(member);
                    if (sim.isRoofShielded(member)
                            || positions != null && positions.isFightingPositionCell(x, y)) posted++;
                    float vx = sim.movement().velX(member);
                    float vy = sim.movement().velY(member);
                    if (vx * vx + vy * vy > 0.0001f) moving++;
                }
                if (2 * posted >= members) {
                    onPost++;
                } else {
                    open++;
                    if (2 * moving > members) openMoving++;
                }
            }
        }

        private static boolean firing(BattleSimulation sim, Squad squad, int members) {
            for (int i = 0; i < members; i++) {
                long member = sim.squadMemberAt(squad.id, i);
                if (sim.combat().has(member) && sim.combat().cooldownTimer(member) > 0f) return true;
            }
            return false;
        }

        /** Percent of the samples fought in the open, or -1 with none. */
        int openShare() {
            int all = onPost + open;
            return all == 0 ? -1 : Math.round(100f * open / all);
        }
    }

    static String line(String runId, BattleSimulation sim, String trace) throws Exception {
        return line(runId, sim, trace, null);
    }

    static String line(String runId, BattleSimulation sim, String trace, Mix mix) throws Exception {
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
        out.append(" flow=").append(!"false".equalsIgnoreCase(System.getProperty("battle.mission.flow", "true")));
        if (mix != null) {
            out.append(" fightOnPost=").append(mix.onPost).append(" fightOpen=").append(mix.open)
                    .append(" fightOpenMoving=").append(mix.openMoving).append(" openSharePct=").append(mix.openShare());
        }
        out.append(" marineLosses=").append(marines)
                .append(" defenderLosses=").append(defenders)
                .append(" termination=").append(sim.isComplete() ? "TERMINAL" : "TIMEOUT")
                .append(" winner=").append(sim.getWinner())
                .append(" ticks=").append(sim.getSimTickIndex());
        return out.toString();
    }
}
