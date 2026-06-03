package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.unit.Entity;

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
 * <p>One plan per {@link com.dillon.starsectormarines.battle.squad.Squad}; under the
 * per-unit parallel dispatch in {@code BattleSimulation.tick} multiple members
 * of the same squad can call into the plan concurrently. {@link #currentIndex}
 * is therefore {@code volatile} for cheap reads, and the mutating call sites
 * (the behavior's SUCCESS → {@link #advance()} transition, the squad's
 * {@code currentPlan = null} on FAILURE) wrap their step-check + advance under
 * {@code synchronized (squad.lock)} so two members both observing the same
 * step's SUCCESS can't double-advance and skip the next step.
 * {@link #currentStep()} is null-safe past the end so a sibling worker that
 * raced past {@code size()} still gets a clean {@code null} instead of an
 * AIOOBE. The plan is invalidated (set to {@code null} on the squad) on any
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
        public final Map<String, List<Entity>> assignments;

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
        public String slotOf(Entity unit) {
            for (Map.Entry<String, List<Entity>> e : assignments.entrySet()) {
                if (e.getValue().contains(unit)) return e.getKey();
            }
            return null;
        }

        /** Flattened view of every assigned member across all slots. Order is slot-major, member-order-within-slot inside that. */
        public List<Entity> allAssignedMembers() {
            List<Entity> out = new ArrayList<>();
            for (List<Entity> v : assignments.values()) out.addAll(v);
            return out;
        }
    }

    private final List<Step> steps;
    /**
     * Volatile so the parallel UPDATE_UNITS dispatch sees a coherent
     * monotonically-advancing index. Writes still need {@code synchronized
     * (squad.lock)} at the call site to make "observed SUCCESS → advance"
     * atomic — volatile alone doesn't stop two members both observing the
     * same step then both calling {@link #advance()} (skipping the next
     * step). See class-doc.
     */
    private volatile int currentIndex = 0;

    public SquadPlan(List<Step> steps) {
        this.steps = steps;
    }

    /**
     * Step the squad is currently executing, or {@code null} if the plan has
     * completed. Null-safe (returns null instead of throwing AIOOBE) because
     * a sibling worker can advance the plan past the end between our load of
     * {@code currentIndex} and the {@code steps.get} call. Callers should
     * null-check before dereferencing.
     */
    public Step currentStep() {
        int idx = currentIndex;
        if (idx < 0 || idx >= steps.size()) return null;
        return steps.get(idx);
    }

    /**
     * Moves to the next step. After this returns, {@link #isComplete()} may
     * report true if there are no more steps. Must be called under
     * {@code synchronized (squad.lock)} with a prior check that
     * {@link #currentStep()} still matches the step the caller observed
     * succeeding — otherwise two members both observing the same step's
     * SUCCESS will double-advance and skip the next step.
     */
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
