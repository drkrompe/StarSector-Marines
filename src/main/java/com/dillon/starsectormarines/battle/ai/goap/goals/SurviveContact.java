package com.dillon.starsectormarines.battle.ai.goap.goals;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Squad;
import com.dillon.starsectormarines.battle.ai.goap.Goal;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.SquadPlan;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.ai.goap.actions.BreakContact;

import java.util.List;

/**
 * Story B — a squad whose morale has broken pulls back to cover, reconstitutes,
 * and re-enters the fight if morale recovers. Triggers on
 * {@link Predicate#MORALE_BROKEN}, lives in the {@link Priority#SURVIVAL}
 * bucket so it outranks {@link EliminateEnemiesGoal} (ENGAGEMENT) but loses
 * to {@link Priority#MISSION} goals like {@link SecureObjectiveZone} and
 * {@link CordonForPlant}, which keeps the planter from breaking the plant
 * just because the rest of the squad's been mauled.
 *
 * <p>Custom-plan: synthesizes a single-step plan of {@link BreakContact}.
 * The action runs perpetually (never returns SUCCESS); the squad-level
 * 2-second periodic replan is what re-evaluates whether MORALE_BROKEN still
 * holds — once morale recovers past
 * {@link com.dillon.starsectormarines.battle.squad.SquadMoraleSystem#MORALE_CLEAR_THRESHOLD}
 * the hysteresis flag clears, this goal goes inactive, and the squad falls
 * back to whichever ENGAGEMENT-tier goal is most relevant. A heavily-mauled
 * squad's morale cap (alive/original ratio) keeps them locked in
 * SurviveContact, which is the intended "they're done for this fight" outcome.
 *
 * <p>The legacy per-unit fall-back ({@code rollFallbackOnHit} →
 * {@code FallbackBehavior}) skips GOAP-driven targets (squad members without
 * a mech loadout), so morale-driven BreakContact is the sole retreat path
 * for infantry squad members. Civilians and mechs still get the legacy roll
 * until their own substitutes land.
 */
public final class SurviveContact implements Goal {

    public static final SurviveContact INSTANCE = new SurviveContact();

    private SurviveContact() {}

    @Override public String name() { return "SurviveContact"; }

    @Override
    public Priority priority() {
        return Priority.SURVIVAL;
    }

    /**
     * Returns positive relevance only when the snapshot reports
     * {@link Predicate#MORALE_BROKEN}. Within the SURVIVAL bucket the goal
     * stands alone today; the constant {@code 1.0f} is a placeholder for
     * future bucketmate tie-breaking.
     */
    @Override
    public float relevance(WorldState state, Squad squad, BattleSimulation sim) {
        return state.get(Predicate.MORALE_BROKEN) ? 1.0f : 0f;
    }

    /**
     * Diagnostic only — the custom-plan path means {@link Goal#desiredState}
     * isn't consulted by the planner. Wanting MORALE_BROKEN = false reads
     * sensibly on the HUD; recovery is driven by
     * {@code BattleSimulation.updateSquadMorale}, not by a planner-driven
     * action.
     */
    @Override
    public WorldState desiredState(Squad squad, BattleSimulation sim) {
        return WorldState.EMPTY.with(Predicate.MORALE_BROKEN, false);
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleSimulation sim) {
        return new SquadPlan(List.of(new SquadPlan.Step(BreakContact.INSTANCE)));
    }
}
