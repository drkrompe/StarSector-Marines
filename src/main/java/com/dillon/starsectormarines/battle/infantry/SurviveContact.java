package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.action.BreakContact;

import java.util.List;

/**
 * The whole-squad tail of the morale model: every one of the squad's fire
 * teams has broken, so there is no composed element left to plan around and
 * the squad as a unit pulls back to cover, reconstitutes, and re-enters the
 * fight if morale recovers.
 *
 * <p><b>Not the ordinary break path.</b> Cohesion breaks per fire team, and a
 * broken team peels on its own through the morale override in
 * {@code GoapInfantryBehavior} while its siblings keep fighting. This goal
 * only becomes relevant once {@link Predicate#MORALE_BROKEN} trips, which now
 * requires <em>all</em> live teams broken — see {@link Squad#moraleBroken}.
 * By then the override is already walking every member back to cover; what
 * this goal adds is releasing the squad's mission goal, so a garrison that
 * is entirely finished stops holding a post it has abandoned.
 *
 * <p>Lives in the {@link Priority#SURVIVAL} bucket so it outranks
 * {@link EliminateEnemiesGoal} (ENGAGEMENT) but loses to
 * ordinary {@link Priority#MISSION} goals like {@link SecureObjectiveZone}
 * and {@link CordonForPlant}, which keeps the planter from breaking the plant
 * just because the rest of the squad's been mauled. An active player tactical
 * context is the exception: its matching mission goal yields to this survival
 * bucket while cohesion is broken, then resumes without letting another
 * mission goal railroad the squad.
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
    public float relevance(WorldState state, Squad squad, BattleView sim) {
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
    public WorldState desiredState(Squad squad, BattleView sim) {
        return WorldState.EMPTY.with(Predicate.MORALE_BROKEN, false);
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        return new SquadPlan(List.of(new SquadPlan.Step(BreakContact.INSTANCE)));
    }
}
