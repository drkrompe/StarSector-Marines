package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * Per-faction strategic planner ("Tier C") sitting above the per-squad GOAP
 * planner. Decides <em>which squad goes where, doing what</em> by writing
 * {@code Squad.assignedObjective} on its faction's squads. Per-squad GOAP
 * ({@code GoapInfantryBehavior} / {@code GoapMechBehavior}) reads that
 * assignment and picks MISSION-priority goals accordingly.
 *
 * <p>Cadence — slower than the per-squad replan loop. Driven by
 * {@link BattleSimulation#COMMANDER_TICK_PERIOD} (2.5s today; tunable as
 * playtest exposes pressure points). The sim batches all commanders'
 * {@link #tick(BattleSimulation)} calls into the same slow-tick boundary
 * before the per-squad replan pass, so a squad that replans this tick sees
 * the freshest assignment.
 *
 * <p>One instance per faction. The default for any unregistered faction is
 * {@link #NOOP} — does nothing, leaves {@code assignedObjective = null},
 * squads fall through to ambient goals (e.g. {@code EliminateEnemiesGoal}).
 * That keeps the layer opt-in: existing missions that don't wire a commander
 * keep the Stage 1 behavior.
 *
 * <p>Per the design doc ({@code roadmap/ai/12-squad-of-squads.md}), event
 * triggers (zone flip, squad wipe, objective explosion) eventually augment
 * the slow tick. Stage 1 ships the slow tick only — the implementation can
 * add event hooks later without changing the interface.
 */
public interface MissionCommand {

    /**
     * The faction this commander owns. Used by the sim to look up which
     * faction's squads this commander writes assignments to, and by debug
     * UI to label the commander's state.
     */
    Faction faction();

    /**
     * Run the commander's slow-tick decision pass. Implementations refresh
     * any internal state (zone status, objective registry), score the
     * (squad, objective) pairs they care about, and write the chosen
     * {@link ObjectiveAssignment} onto each squad of {@link #faction()}.
     *
     * <p>Called by {@code BattleSimulation.tick} at the commander cadence
     * — typically every {@link BattleSimulation#COMMANDER_TICK_PERIOD}
     * sim-seconds — before the per-squad GOAP replan pass.
     */
    void tick(BattleSimulation sim);

    /**
     * Default no-op commander used when a faction has no commander wired.
     * Leaves {@code Squad.assignedObjective} alone so MISSION-priority
     * goals report {@code relevance() = 0} and squads fall through to
     * their ambient ENGAGEMENT-priority goals.
     */
    MissionCommand NOOP = new MissionCommand() {
        @Override public Faction faction() { return null; }
        @Override public void tick(BattleSimulation sim) { /* deliberate no-op */ }
    };
}
