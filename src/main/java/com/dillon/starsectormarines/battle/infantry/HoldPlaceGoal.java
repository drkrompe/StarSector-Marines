package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;

/**
 * The player's order to hold a place: the one goal that must win while a player's
 * {@link AssignmentKind#HOLD_NODE} stands.
 *
 * <p>A commander's held node is served by two goals in competition —
 * {@link FrontageDefense} standing to at the apertures while hostiles are believed close,
 * {@link GarrisonCompound} walking the rooms while they are not — and a player order has to
 * name one goal that wins outright. This is that goal, and it is nothing of its own: it asks
 * the same two in the same order, so a building held on the player's word is held exactly the
 * way a garrison holds it.
 *
 * <p>Inert unless the hold is the player's, so a commander's garrison keeps its ordinary
 * competition, where {@link GarrisonAmbush} may still preempt.
 */
public final class HoldPlaceGoal implements Goal {

    public static final HoldPlaceGoal INSTANCE = new HoldPlaceGoal();

    /** {@link GarrisonCompound}'s own relevance, for the quiet half of the hold. */
    private static final float QUIET_RELEVANCE = 0.95f;

    private HoldPlaceGoal() {}

    @Override public String name() { return "HoldPlace"; }

    @Override public Priority priority() { return Priority.MISSION; }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        if (!applies(state, squad)) return 0f;
        if (FrontageDefense.INSTANCE.relevance(state, squad, sim) > 0f) {
            return FrontageDefense.RELEVANCE;
        }
        return GarrisonCompound.INSTANCE.relevance(state, squad, sim) > 0f ? QUIET_RELEVANCE : 0f;
    }

    @Override
    public Evaluation evaluate(WorldState state, Squad squad, BattleView sim,
                               EvaluationContext context) {
        if (!applies(state, squad)) return new Evaluation(0f, null);
        Evaluation frontage = context.evaluate(FrontageDefense.INSTANCE);
        if (frontage.relevance() > 0f) return frontage;
        SquadPlan garrison = GarrisonCompound.INSTANCE.customPlan(squad, sim);
        return garrison == null ? new Evaluation(0f, null) : new Evaluation(QUIET_RELEVANCE, garrison);
    }

    @Override
    public WorldState desiredState(Squad squad, BattleView sim) {
        return WorldState.EMPTY;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        SquadPlan frontage = FrontageDefense.INSTANCE.customPlan(squad, sim);
        return frontage != null ? frontage : GarrisonCompound.INSTANCE.customPlan(squad, sim);
    }

    private static boolean applies(WorldState state, Squad squad) {
        return !state.get(Predicate.MORALE_BROKEN) && squad.hasPlayerOrder(AssignmentKind.HOLD_NODE);
    }
}
