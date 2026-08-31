package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;

/**
 * <b>Squad posture: await orders.</b> Close up and stand, while the commander
 * finds the squad something to do.
 *
 * <p>This is what a yielded order leaves behind. A mission goal may decline its
 * own assignment deliberately — the zone it names turns out to be empty, or
 * unreachable — meaning <em>hand me back, I have finished or cannot start</em>.
 * Before this action there was nothing beneath that decision, so yielding and
 * having nothing to do were the same state: no goal, a null plan, and members
 * that drop the path they were walking. The squad was not resting; it was
 * unable to act, and could not recover on its own because the individual tier
 * owns no movement of its own.
 *
 * <p><b>Standing is a state, not a task, so this never succeeds.</b> It reports
 * {@link ActionStatus#RUNNING} forever and lets an ordinary replan trigger —
 * a fresh assignment, contact, a casualty, the periodic timer — take the squad
 * off it. An action that reported {@link ActionStatus#SUCCESS} once the squad
 * had closed up would complete its plan, and a complete plan is itself a replan
 * trigger: the goal would re-synthesise the same step, the fresh step would
 * report success again on its first tick, and the squad would replan every tick
 * for as long as it had nothing to do. That is the churn plan stickiness exists
 * to prevent, arrived at from the other side.
 *
 * <p><b>Closing up is the whole of the "useful" here, and it is deliberately
 * not more.</b> A squad awaiting orders draws its scattered members back
 * together, which is what the cohesion helper already answers and costs nothing
 * when the squad is already together. It does not go looking for a fight: with
 * no assignment and no contact, choosing somewhere to go would be the squad
 * inventing the mission {@code ai-nouns.md} forbids it to invent — and
 * {@link AmbientEngagementGoal} already owns the case where the squad does hold
 * evidence worth closing on, above this one in the same bucket.
 *
 * <p>Shots of opportunity remain available throughout, by the dispatcher's
 * default. A squad standing to is still allowed to shoot whatever walks in
 * front of it; that is the difference between waiting and being defenceless.
 */
public final class AwaitOrders implements Action {

    public static final AwaitOrders INSTANCE = new AwaitOrders();

    private AwaitOrders() {}

    @Override public String name() { return "AwaitOrders"; }

    @Override public WorldState preconditions() { return WorldState.EMPTY; }

    @Override public WorldState effects() { return WorldState.EMPTY; }

    @Override public float cost(WorldState s, Squad squad, BattleView sim) { return 10f; }

    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        int[] dest = InfantryCohesion.cohesionOverride(member, sim);
        if (dest == null) {
            // Within the cohesion radius, or alone. Park on the current cell
            // rather than leaving a path from whatever authored the last plan:
            // that route belonged to an order this squad no longer holds, and
            // walking the rest of it is the squad acting on a withdrawn
            // assignment.
            PatrolMotion.hold(member, sim);
            return ActionStatus.RUNNING;
        }
        if (sim.movement().mayRepath(member)) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    sim.world().cellX(member), sim.world().cellY(member),
                    dest[0], dest[1], sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);
        return ActionStatus.RUNNING;
    }
}
