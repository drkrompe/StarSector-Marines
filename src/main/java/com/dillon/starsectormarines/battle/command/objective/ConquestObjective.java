package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

/**
 * Marine-side Conquest win condition: complete when every defender
 * compound ({@code COMMAND_POST} / {@code BARRACKS} / {@code ARMORY}) has
 * flipped to {@link CompoundService.CompoundState#MARINE_HELD} and at
 * least one marine is in play. A generated {@code COMMAND_POST} is required,
 * so capturing the central keep is always part of the aggregate objective.
 * Replaces the marine-side
 * {@link EliminateFactionObjective} on Conquest missions; the defender's
 * side keeps {@code EliminateFactionObjective(DEFENDER, MARINE)} so the
 * "every marine died" loss path still terminates.
 *
 * <p>Why an alive-marine precondition: without it, the tick that flips
 * the last compound to MARINE_HELD <em>also</em> often coincides with
 * the last marine dying (final assault on the keep). Both objectives
 * would complete the same tick and {@link WinCheckSystem} returns a
 * mutual-victory draw. The conquest noun model (see
 * {@code conquest-nouns.md}) requires the
 * marine to <em>survive</em> the capture, so the alive check defers
 * completion by one tick — the defender's elimination objective
 * latches first and wins.
 *
 * <p>Current inbound payloads and committed future shuttle sorties count as
 * marines-in-play, matching {@link EliminateFactionObjective}'s semantics so
 * neither opening deployment nor the rearm gap between authored cycles is
 * mistaken for absence. A fresh Conquest where every defender compound
 * somehow starts marine-held (degenerate; never expected in production) still
 * resolves cleanly.
 */
public final class ConquestObjective implements Objective {

    private static final Logger LOG = Global.getLogger(ConquestObjective.class);

    private final CompoundService compounds;
    private boolean complete = false;
    private boolean failed = false;

    public ConquestObjective(CompoundService compounds) {
        this.compounds = compounds;
    }

    @Override
    public Faction owningFaction() { return Faction.MARINE; }

    @Override
    public void tick(BattleView sim) {
        if (complete || failed) return;

        // No compound layer → marine cannot win, ever. Conquest missions
        // are expected to install compounds; the only path to an empty
        // service is a map-gen bug (e.g. the BSP failed to allocate a
        // MILITARY_BASE block, so MilitaryBaseFiller emits no nodes).
        // Stalling forever is worse than failing — the player just sits
        // through a battle with no win condition. Fail-closed: defender
        // wins via their own elimination objective, the player sees the
        // loss screen, and the bug gets reported instead of silently
        // soft-locking the run.
        if (compounds.getRecords().isEmpty()) {
            LOG.warn("ConquestObjective: no compounds registered — Conquest map-gen produced none. "
                    + "Marking objective failed so the battle terminates.");
            failed = true;
            return;
        }

        boolean keepRegistered = false;
        for (CompoundService.Record r : compounds.getRecords()) {
            if (r.node.kind == TacticalNode.Kind.COMMAND_POST) keepRegistered = true;
        }
        if (!keepRegistered) {
            LOG.warn("ConquestObjective: no COMMAND_POST registered — central keep is missing. "
                    + "Marking objective failed rather than allowing a keep-less victory.");
            failed = true;
            return;
        }
        for (CompoundService.Record r : compounds.getRecords()) {
            if (r.state != CompoundService.CompoundState.MARINE_HELD) return;
        }

        // Marine-survival precondition — see class doc. Mirrors the
        // alive-marine scan in EliminateFactionObjective so the two
        // objectives use the same "is the marine side in play" semantic.
        if (!ObjectiveFactionPresence.anyInPlay(sim, Faction.MARINE)) return;

        complete = true;
    }

    @Override
    public boolean isComplete() { return complete; }

    @Override
    public boolean isFailed() { return failed; }

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
