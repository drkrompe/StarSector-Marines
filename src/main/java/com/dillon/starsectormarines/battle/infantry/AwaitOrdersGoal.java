package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;

import java.util.List;

/**
 * The bottom of the ladder: what a squad does when nothing above it can be
 * acted on and it holds no evidence worth closing on either.
 *
 * <p>{@link AmbientEngagementGoal} sits above this in the same bucket and takes
 * the case where the squad does hold a cue. This one takes the remainder, and
 * the remainder turned out to be most of it: instrumented counters over a
 * Conquest matrix put roughly twenty-nine thousand order-holding squad replans
 * per run at a yielded mission goal with no belief and no bearing — a squad
 * that has been told to clear a room, found it already clear, and correctly
 * declined its own order. Before this goal the ladder ended there, and
 * declining meant standing plan-less until the commander's next pulse.
 *
 * <p><b>Always relevant, and that is the point of a floor.</b> Every other goal
 * in the library answers "is there something in particular to do"; this one
 * answers "there is not", which is never false. It cannot displace anything:
 * {@link Priority#IDLE} is the last bucket consulted, so a squad reaches this
 * only when every mission, survival and engagement goal has either scored zero
 * or proved unplannable.
 *
 * <p><b>No morale guard, deliberately.</b> {@link AmbientEngagementGoal} refuses
 * to fire for a broken squad because advancing while broken is wrong, and
 * survival owns that squad. Standing still while broken is not wrong, and this
 * goal is only ever reached when survival has already declined — so guarding it
 * would hand the squad back to the null plan this exists to remove, in exactly
 * the state where being unable to act is most expensive.
 */
public final class AwaitOrdersGoal implements Goal {

    public static final AwaitOrdersGoal INSTANCE = new AwaitOrdersGoal();

    /**
     * Below {@link AmbientEngagementGoal}'s, so a squad holding a cue closes on
     * it rather than standing about. Within one bucket relevance is the only
     * ordering, and these two are the bucket.
     */
    private static final float RELEVANCE = 0.5f;

    private AwaitOrdersGoal() {}

    @Override public String name() { return "AwaitOrders"; }

    @Override public Priority priority() { return Priority.IDLE; }

    @Override
    public float relevance(WorldState state, Squad squad, BattleView sim) {
        return RELEVANCE;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleView sim) {
        return WorldState.EMPTY;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleView sim) {
        // Kept across replans rather than re-synthesised. The step never
        // completes, so a running one is always still the right plan, and
        // handing back the same instance keeps the role assignment and the
        // step's own state instead of rebuilding both every couple of seconds.
        SquadPlan current = squad.currentPlan;
        if (current != null && !current.isComplete()) {
            SquadPlan.Step step = current.currentStep();
            if (step != null && step.action instanceof AwaitOrders) return current;
        }
        return new SquadPlan(List.of(new SquadPlan.Step(AwaitOrders.INSTANCE)));
    }
}
