package com.dillon.starsectormarines.battle.ai.goap;

import com.dillon.starsectormarines.battle.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered sequence of actions a squad is currently executing, with per-step
 * member assignments. Produced by the planner (with empty assignments),
 * filled in by the {@code RoleAssigner}, then advanced one step at a time
 * by {@code GoapInfantryBehavior} as each step's {@code execute} returns
 * {@link ActionStatus#SUCCESS}.
 *
 * <p>One plan per {@link com.dillon.starsectormarines.battle.Squad}; mutation
 * happens only in the serial unit-update pass so no synchronization is needed.
 * The plan is invalidated (set to {@code null} on the squad) on any
 * {@link ActionStatus#FAILURE} or when a replan trigger fires; a fresh plan
 * is built next replan.
 */
public final class SquadPlan {

    /**
     * One slot in the plan: an action plus the squadmates assigned to execute
     * it. The {@code assignedMembers} list is mutable so the role assigner
     * can fill it after the planner returns — but once execution starts,
     * callers should treat it as read-only.
     */
    public static final class Step {
        public final Action action;
        public final List<Unit> assignedMembers;

        public Step(Action action, List<Unit> assignedMembers) {
            this.action = action;
            this.assignedMembers = assignedMembers;
        }

        public Step(Action action) {
            this(action, new ArrayList<>(action.requiredMembers()));
        }
    }

    private final List<Step> steps;
    private int currentIndex = 0;

    public SquadPlan(List<Step> steps) {
        this.steps = steps;
    }

    /** Step the squad is currently executing. Throws if the plan has completed — guard with {@link #isComplete()}. */
    public Step currentStep() {
        return steps.get(currentIndex);
    }

    /** Moves to the next step. After this returns, {@link #isComplete()} may report true if there are no more steps. */
    public void advance() {
        currentIndex++;
    }

    public boolean isComplete() {
        return currentIndex >= steps.size();
    }

    public int currentIndex() {
        return currentIndex;
    }

    public int stepCount() {
        return steps.size();
    }

    public List<Step> steps() {
        return steps;
    }
}
