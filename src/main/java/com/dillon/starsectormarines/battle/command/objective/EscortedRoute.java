package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.objective.ExtractionPayloadObjective.Failure;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.Arrays;

/**
 * The escorted-package rule ({@code extraction-command.md}, Generic recovery law): a released package moves along its
 * route only while a settled Marine combatant is within its leash and no defender combatant interdicts its cell.
 * Unattended under sustained defender control it is lost; unattended and uncontrolled for long it is abandoned.
 * Ordinary pauses and contact stall it without failing it.
 */
final class EscortedRoute {

    private final int[] route;
    private final float secondsPerCell;
    private final float lossDuration;
    private final float abandonmentDuration;
    private final int escortRadius;
    private final int interdictionRadius;

    private int index;
    private float travelProgress;
    private float lossProgress;
    private float abandonmentProgress;
    private boolean escortPresent;
    private boolean contested;
    private int controllingSquadId = -1;

    EscortedRoute(int[] route, float secondsPerCell, float lossDuration, float abandonmentDuration,
                  int escortRadius, int interdictionRadius) {
        if (route == null || route.length < 4 || (route.length & 1) != 0 || secondsPerCell <= 0f
                || lossDuration <= 0f || abandonmentDuration <= 0f || escortRadius < 0 || interdictionRadius < 0) {
            throw new IllegalArgumentException("valid escorted route and durations required");
        }
        this.route = Arrays.copyOf(route, route.length);
        this.secondsPerCell = secondsPerCell;
        this.lossDuration = lossDuration;
        this.abandonmentDuration = abandonmentDuration;
        this.escortRadius = escortRadius;
        this.interdictionRadius = interdictionRadius;
    }

    /** One tick of transit; the failure it ended in, or {@link Failure#NONE}. */
    Failure tick(BattleView sim) {
        int x = cellX();
        int y = cellY();
        EscortPresence.Escort escort = EscortPresence.nearestMarine(sim, x, y, escortRadius, -1);
        contested = EscortPresence.combatantPresent(sim, Faction.DEFENDER, x, y, interdictionRadius, -1);
        escortPresent = escort.present();
        controllingSquadId = escortPresent ? escort.squadId() : -1;

        if (!escortPresent) {
            travelProgress = 0f;
            if (contested) {
                lossProgress += BattleSimulation.TICK_DT;
                abandonmentProgress = 0f;
                return lossProgress >= lossDuration ? Failure.LOST : Failure.NONE;
            }
            abandonmentProgress += BattleSimulation.TICK_DT;
            lossProgress = 0f;
            return abandonmentProgress >= abandonmentDuration ? Failure.ABANDONED : Failure.NONE;
        }
        lossProgress = 0f;
        abandonmentProgress = 0f;
        if (contested) {
            travelProgress = 0f;
            return Failure.NONE;
        }
        travelProgress += BattleSimulation.TICK_DT;
        while (travelProgress >= secondsPerCell && index < cellCount() - 1) {
            travelProgress -= secondsPerCell;
            index++;
        }
        return Failure.NONE;
    }

    /** Clears who escorts the package once it no longer moves (it boarded, or the mission ended). */
    void release() {
        escortPresent = false;
        controllingSquadId = -1;
    }

    boolean arrived() { return index >= cellCount() - 1; }
    int index() { return index; }
    int cellCount() { return Paths.cellCount(route); }
    int cellX() { return Paths.cellX(route, index); }
    int cellY() { return Paths.cellY(route, index); }
    int cellX(int at) { return Paths.cellX(route, at); }
    int cellY(int at) { return Paths.cellY(route, at); }
    int endX() { return Paths.cellX(route, cellCount() - 1); }
    int endY() { return Paths.cellY(route, cellCount() - 1); }
    float lossProgress() { return lossProgress; }
    float abandonmentProgress() { return abandonmentProgress; }
    float lossDuration() { return lossDuration; }
    float abandonmentDuration() { return abandonmentDuration; }
    boolean escortPresent() { return escortPresent; }
    boolean contested() { return contested; }
    int controllingSquadId() { return controllingSquadId; }
}
