package com.dillon.starsectormarines.battle.ai.goap;

import com.dillon.starsectormarines.battle.Unit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ordered sequence of actions a squad is currently executing, with per-step
 * role-slot assignments. Produced by the planner (with empty assignments),
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
     * it, partitioned by <b>role slot</b>. A step's {@code action} declares
     * its role slots via {@link Action#roles}; the role assigner then fills
     * each slot's candidate list with squad members ranked by that slot's
     * scorer.
     *
     * <p>The simple "every member does the same thing" case
     * ({@link Action}'s default role behavior) populates a single
     * {@code "any"} slot with all alive members — identical to the Stage 1
     * "add everyone to every step" wiring but expressed through the same
     * slot machinery the multi-role Stage 2 actions use.
     *
     * <p>The {@link #assignments} map is mutable so the role assigner can
     * fill it after the planner returns — but once execution starts, callers
     * should treat it as read-only.
     */
    public static final class Step {
        public final Action action;
        /**
         * Slot name → ordered list of members assigned to that slot. Iteration
         * order matches the {@code Action.roles} declaration so action
         * implementations can index by slot index when natural (e.g. portal[0],
         * portal[1] in a cordon).
         */
        public final Map<String, List<Unit>> assignments;

        public Step(Action action) {
            this.action = action;
            this.assignments = new LinkedHashMap<>();
        }

        /**
         * Looks up which slot {@code unit} fills in this step, or {@code null}
         * if it's not assigned. Linear scan over slot lists; squads cap around
         * 8 members and slot counts cap around 4, so this is O(squad size) in
         * the worst case — fine for the per-member dispatch path.
         */
        public String slotOf(Unit unit) {
            for (Map.Entry<String, List<Unit>> e : assignments.entrySet()) {
                if (e.getValue().contains(unit)) return e.getKey();
            }
            return null;
        }

        /** Flattened view of every assigned member across all slots. Order is slot-major, member-order-within-slot inside that. */
        public List<Unit> allAssignedMembers() {
            List<Unit> out = new ArrayList<>();
            for (List<Unit> v : assignments.values()) out.addAll(v);
            return out;
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
