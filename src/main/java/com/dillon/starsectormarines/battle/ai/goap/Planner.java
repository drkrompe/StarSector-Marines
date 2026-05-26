package com.dillon.starsectormarines.battle.ai.goap;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Squad;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Backward-chaining A* planner over the GOAP action space. F.E.A.R.-style:
 * start at the goal's {@link Goal#desiredState(Squad, BattleSimulation)},
 * regress through actions whose {@link Action#effects()} satisfy unsatisfied
 * predicates, terminate when the regressed state is satisfied by the
 * current world.
 *
 * <p><b>Pure-functional and thread-safe.</b> No static mutable state. All
 * working data is per-call. The squad-level replan pass calls this in
 * parallel across squads; the planner itself does not branch on thread
 * identity.
 *
 * <p><b>Returns a {@link SquadPlan} with empty member assignments.</b> The
 * role assigner (a separate stage) fills in {@code assignedMembers} on
 * each step.
 *
 * <p>Cost: each search node copies a {@link WorldState} (two longs in a
 * fresh object) and a parent pointer; expansions are bounded by
 * {@code nodeLimit}. For Stage 1's action library this typically terminates
 * in &lt; 20 node expansions.
 */
public final class Planner {

    /** Sentinel cost used by the priority queue's f-cost ordering when h = 0 and g = 0 — exists so the comparator stays straightforward. */
    private static final float EPS = 1e-6f;

    private Planner() {}

    /**
     * Plans a sequence of actions whose chained effects, starting from
     * {@code current}, satisfy {@code goal}. Returns {@code null} when no
     * plan is reachable within {@code nodeLimit} expansions.
     *
     * @param current   snapshot of the world right now
     * @param goal      desired state the plan must satisfy
     * @param available actions the planner may use; conventionally the full
     *                  action library — the planner ignores actions whose
     *                  effects don't help any unsatisfied predicate at each
     *                  search step
     * @param squad     planning squad (passed through to {@link Action#cost})
     * @param sim       sim context (passed through to {@link Action#cost} —
     *                  read-only during the replan window)
     * @param nodeLimit safety cap on node expansions. 256 is plenty for the
     *                  Stage 1 library; raise as the action surface grows.
     */
    public static SquadPlan plan(
            WorldState current,
            WorldState goal,
            List<Action> available,
            Squad squad,
            BattleSimulation sim,
            int nodeLimit) {

        // Trivial case: goal already satisfied. Return an empty plan rather
        // than null — null is "no plan reachable", which is a different
        // signal for replan triggers.
        if (current.satisfies(goal)) {
            return new SquadPlan(new ArrayList<>());
        }

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fCost));
        Set<WorldState> closed = new HashSet<>();

        Node root = new Node(goal, null, null, 0f, goal.countUnsatisfiedAgainst(current));
        open.add(root);

        int expanded = 0;
        while (!open.isEmpty() && expanded < nodeLimit) {
            Node n = open.poll();
            if (!closed.add(n.regressed)) continue;   // already expanded a cheaper path here
            expanded++;

            if (current.satisfies(n.regressed)) {
                return reconstructPlan(n);
            }

            for (Action action : available) {
                if (!n.regressed.helpsSatisfy(action.effects())) continue;
                WorldState next = n.regressed.regress(action.effects(), action.preconditions());
                if (closed.contains(next)) continue;

                float gNext = n.gCost + Math.max(EPS, action.cost(current, squad, sim));
                float hNext = next.countUnsatisfiedAgainst(current);
                open.add(new Node(next, n, action, gNext, gNext + hNext));
            }
        }
        return null;
    }

    /**
     * Walks parent pointers from {@code leaf} back to the root, collecting
     * the action attached to each edge. Because backward search expands the
     * <i>last</i> action first, walking from leaf to root naturally yields
     * actions in execution order — no reversal needed.
     */
    private static SquadPlan reconstructPlan(Node leaf) {
        List<SquadPlan.Step> steps = new ArrayList<>();
        for (Node n = leaf; n.parent != null; n = n.parent) {
            steps.add(new SquadPlan.Step(n.actionTaken));
        }
        return new SquadPlan(steps);
    }

    /** Search-tree node. Stored in the open queue + closed set during planning. */
    private static final class Node {
        final WorldState regressed;
        final Node parent;          // null for root
        final Action actionTaken;   // edge label from parent to this; null for root
        final float gCost;
        final float fCost;

        Node(WorldState regressed, Node parent, Action actionTaken, float gCost, float fCost) {
            this.regressed = regressed;
            this.parent = parent;
            this.actionTaken = actionTaken;
            this.gCost = gCost;
            this.fCost = fCost;
        }
    }
}
