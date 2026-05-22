package com.dillon.starsectormarines.battle.objective;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.air.Shuttle;
import com.dillon.starsectormarines.battle.compound.CompoundService;

/**
 * Marine-side Conquest win condition: complete when every defender
 * compound ({@code COMMAND_POST} / {@code BARRACKS} / {@code ARMORY}) has
 * flipped to {@link CompoundService.CompoundState#MARINE_HELD} and at
 * least one marine is in play. Replaces the marine-side
 * {@link EliminateFactionObjective} on Conquest missions; the defender's
 * side keeps {@code EliminateFactionObjective(DEFENDER, MARINE)} so the
 * "every marine died" loss path still terminates.
 *
 * <p>Why an alive-marine precondition: without it, the tick that flips
 * the last compound to MARINE_HELD <em>also</em> often coincides with
 * the last marine dying (final assault on the keep). Both objectives
 * would complete the same tick and {@link WinCheckSystem} returns a
 * mutual-victory draw. The doc spec (see
 * {@code roadmap/conquest/central-keep.md} "Win condition") wants the
 * marine to <em>survive</em> the capture, so the alive check defers
 * completion by one tick — the defender's elimination objective
 * latches first and wins.
 *
 * <p>Inbound / deboarding shuttles count as marines-in-play, matching
 * {@link EliminateFactionObjective}'s opening-tick semantics so a fresh
 * Conquest where every defender compound somehow starts marine-held
 * (degenerate; would never happen in production) still resolves
 * cleanly.
 */
public final class ConquestObjective implements Objective {

    private final CompoundService compounds;
    private boolean complete = false;

    public ConquestObjective(CompoundService compounds) {
        this.compounds = compounds;
    }

    @Override
    public Faction owningFaction() { return Faction.MARINE; }

    @Override
    public void tick(BattleSimulation sim) {
        if (complete) return;

        // No compound layer → never completes. Conquest missions install
        // compounds; non-Conquest paths shouldn't be using this objective.
        // A Conquest map with zero compounds is degenerate (map-gen bug);
        // failing closed is safer than insta-completing.
        if (compounds.getRecords().isEmpty()) return;

        for (CompoundService.Record r : compounds.getRecords()) {
            if (r.state != CompoundService.CompoundState.MARINE_HELD) return;
        }

        // Marine-survival precondition — see class doc. Mirrors the
        // alive-marine scan in EliminateFactionObjective so the two
        // objectives use the same "is the marine side in play" semantic.
        if (!anyMarineInPlay(sim)) return;

        complete = true;
    }

    private static boolean anyMarineInPlay(BattleSimulation sim) {
        for (Unit u : sim.getUnits()) {
            if (u.isAlive() && u.faction == Faction.MARINE) return true;
        }
        for (Shuttle s : sim.getShuttles()) {
            if (s.faction != Faction.MARINE) continue;
            if (s.marinesRemaining > 0
                    && s.state != Shuttle.State.DEPARTING
                    && s.state != Shuttle.State.GONE) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isComplete() { return complete; }

    @Override
    public boolean isFailed() { return false; }

    @Override
    public String displayName() {
        int total = compounds.getRecords().size();
        int captured = 0;
        for (CompoundService.Record r : compounds.getRecords()) {
            if (r.state == CompoundService.CompoundState.MARINE_HELD) captured++;
        }
        return "Capture supply hubs (" + captured + " / " + total + ")";
    }
}
