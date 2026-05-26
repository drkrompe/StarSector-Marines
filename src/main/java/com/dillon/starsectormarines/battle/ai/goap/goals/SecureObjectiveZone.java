package com.dillon.starsectormarines.battle.ai.goap.goals;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.ai.goap.Goal;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.SquadPlan;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.ai.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.objective.ChargeSiteObjective;

/**
 * Story K — Room-clear sweep. Fires for marine squads that have an
 * objective in a different zone (currently: a planter squadmate's
 * {@link ChargeSiteObjective}). Synthesizes a custom plan via
 * {@link #customPlan} that walks the zone-graph BFS path from the squad's
 * current zone to the objective zone, alternating {@link EnterZone} and
 * {@link ClearZone} per intermediate room.
 *
 * <p>Lives in the {@link Priority#MISSION} bucket so it outranks the
 * default {@link EliminateEnemiesGoal} when both are relevant. The squad
 * doesn't stop fighting — the {@link ClearZone} step <em>is</em> their
 * engagement loop for the active room — but they no longer wander off
 * chasing whichever enemy happens to be visible from spawn.
 *
 * <p>Custom-plan goal: the backward-chaining planner is skipped entirely.
 * {@link #desiredState} is set for the diagnostic HUD and a notional
 * "if customPlan ever returned null, the planner would chase ZONE_CLEAR"
 * — but in the current MVP customPlan always returns a real plan when
 * relevance is positive, so {@link Predicate#ZONE_CLEAR}'s evaluator stays
 * {@code STUB_FALSE} without breaking anything.
 */
public final class SecureObjectiveZone implements Goal {

    public static final SecureObjectiveZone INSTANCE = new SecureObjectiveZone();

    private static final WorldState DESIRED = WorldState.EMPTY
            .with(Predicate.ZONE_CLEAR, true);

    private SecureObjectiveZone() {}

    @Override public String name() { return "SecureObjectiveZone"; }

    @Override
    public Priority priority() {
        return Priority.MISSION;
    }

    @Override
    public float relevance(WorldState state, Squad squad, BattleSimulation sim) {
        int targetZone = findObjectiveZone(squad, sim);
        if (targetZone < 0) return 0f;
        int currentZone = ZoneQueries.squadCurrentZone(squad, sim);
        if (currentZone < 0 || currentZone == targetZone) return 0f;
        // Reachability check — when the objective zone is disconnected from
        // the squad's current zone (rare, but possible if a wall got blown
        // and the BFS now hits a dead-end), fall back so EliminateEnemies
        // can take over instead of leaving the squad plan-less.
        if (ZoneQueries.zonePathBfs(currentZone, targetZone, sim).size() < 2) return 0f;
        return 1.0f;
    }

    @Override
    public WorldState desiredState(Squad squad, BattleSimulation sim) {
        return DESIRED;
    }

    @Override
    public SquadPlan customPlan(Squad squad, BattleSimulation sim) {
        int to = findObjectiveZone(squad, sim);
        // Plan stickiness: same rationale as ClearAssignedZoneGoal — when
        // the squad is bifurcated across a portal mid-sweep, the BFS path
        // would otherwise flip on each replan and oscillate members in and
        // out. Plan is dropped naturally when the planter dies (relevance
        // drops to 0 and a different goal wins) or the planter switches
        // objective (terminal zone no longer matches).
        if (ZoneQueries.planEndsAtZone(squad.currentPlan, to)) {
            return squad.currentPlan;
        }
        int from = ZoneQueries.squadCurrentZone(squad, sim);
        return ZoneQueries.synthesizeZonePushPlan(from, to, sim);
    }

    /**
     * Zone id containing this squad's mission objective. Stage 2 MVP scope:
     * checks for a squadmate carrying a {@link ChargeSiteObjective} (the
     * SABOTAGE planter pattern). Returns {@code -1} when no squadmate has a
     * mission target — squad falls back to {@link EliminateEnemiesGoal}.
     *
     * <p>Future stories (E. mech-screened advance, F. objective rush under
     * fire) will add other objective types; this method is the seam they
     * extend under without touching the customPlan logic above.
     */
    private static int findObjectiveZone(Squad squad, BattleSimulation sim) {
        for (Unit u : sim.getUnits()) {
            if (!u.isAlive() || u.squadId != squad.id) continue;
            if (u.assignedObjective instanceof ChargeSiteObjective cs) {
                if (cs.isComplete()) continue;
                return sim.getZoneGraph().zoneIdAt(cs.cellX(), cs.cellY());
            }
        }
        return -1;
    }
}
