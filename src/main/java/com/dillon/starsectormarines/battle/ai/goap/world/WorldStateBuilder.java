package com.dillon.starsectormarines.battle.ai.goap.world;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.ai.InfantryCohesion;
import com.dillon.starsectormarines.battle.ai.TacticalScoring;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link WorldState} snapshot for one squad from current
 * {@link BattleSimulation} state. Called by the squad-level replan pass
 * — runs in parallel across squads, read-only against the sim.
 *
 * <p>The registry-of-evaluators approach (one {@link PredicateEvaluator}
 * per {@link Predicate}) keeps "add a fact" a two-line change: enum entry
 * + evaluator registration here. Actions and goals reference predicates
 * by enum identity and never need to know how they're backed.
 *
 * <p><b>Stage 1 simplification:</b> the LOS / range predicates evaluate
 * over "any squadmate × any alive enemy combatant" pair, not against a
 * sticky squad-primary-target. Matches the per-unit independent target
 * selection from before the planner landed. Stage 2+ may add a
 * {@code Squad.primaryTarget} field for coordinated maneuver scoring.
 */
public final class WorldStateBuilder {

    private static final Map<Predicate, PredicateEvaluator> EVALUATORS = new EnumMap<>(Predicate.class);

    /** Stub evaluator used for predicates pre-declared but not yet implemented (Stage 2 fanout placeholders). Always reads false so {@code build} produces a fully-specified state and Stage 1 actions / goals are unaffected. */
    private static final PredicateEvaluator STUB_FALSE = (s, sim) -> false;

    static {
        EVALUATORS.put(Predicate.HAS_TARGET,              WorldStateBuilder::evalHasTarget);
        EVALUATORS.put(Predicate.HAS_LOS_TO_TARGET,       WorldStateBuilder::evalHasLosToTarget);
        EVALUATORS.put(Predicate.IN_RANGE_OF_TARGET,      WorldStateBuilder::evalInRangeOfTarget);
        EVALUATORS.put(Predicate.WITHIN_COHESION_RADIUS,  WorldStateBuilder::evalWithinCohesionRadius);
        // ENEMY_DAMAGED is a goal-side marker, never observed in a snapshot.
        // EngagePosture.effects() sets it; the planner regresses through it.
        EVALUATORS.put(Predicate.ENEMY_DAMAGED,           STUB_FALSE);

        // Stage 2 surface — predicates reserved for stories in
        // roadmap/ai/10-tactical-stories.md. Each story's subagent will
        // replace its STUB_FALSE entry with a real evaluator alongside the
        // story's action/goal implementation.
        EVALUATORS.put(Predicate.SQUAD_BELOW_HALF_STRENGTH,         STUB_FALSE);
        EVALUATORS.put(Predicate.ENEMY_IN_KILL_ZONE,                STUB_FALSE);
        EVALUATORS.put(Predicate.UNDER_FIRE_AT_LOS,                 STUB_FALSE);
        EVALUATORS.put(Predicate.ENEMY_SUPPRESSED,                  STUB_FALSE);
        EVALUATORS.put(Predicate.BEHIND_FRIENDLY_RELATIVE_TO_THREAT, STUB_FALSE);
        EVALUATORS.put(Predicate.CAN_REPOSITION,                    STUB_FALSE);
        EVALUATORS.put(Predicate.ZONE_CLEAR,                        STUB_FALSE);
        EVALUATORS.put(Predicate.ENEMY_IN_PORTAL_CELL,              STUB_FALSE);
        EVALUATORS.put(Predicate.NODE_IS_MUST_HOLD,                 STUB_FALSE);
        EVALUATORS.put(Predicate.THREAT_DENSITY_HIGH_AT_TARGET,     STUB_FALSE);
    }

    private WorldStateBuilder() {}

    /**
     * Snapshots {@code squad}'s world view into a fresh {@link WorldState}.
     * Every registered predicate is explicitly specified (true or false) so
     * downstream {@code satisfies} / heuristic math doesn't conflate "false"
     * with "unconstrained."
     */
    public static WorldState build(Squad squad, BattleSimulation sim) {
        WorldState state = WorldState.EMPTY;
        for (Map.Entry<Predicate, PredicateEvaluator> e : EVALUATORS.entrySet()) {
            state = state.with(e.getKey(), e.getValue().evaluate(squad, sim));
        }
        return state;
    }

    // --- Stage 1 evaluators ---------------------------------------------

    private static boolean evalHasTarget(Squad squad, BattleSimulation sim) {
        for (Unit u : sim.getUnits()) {
            if (!u.isAlive()) continue;
            if (!u.type.combatant) continue;
            if (u.faction == squad.faction) continue;
            return true;
        }
        return false;
    }

    private static boolean evalHasLosToTarget(Squad squad, BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        List<Unit> units = sim.getUnits();
        for (Unit member : units) {
            if (!member.isAlive() || member.squadId != squad.id) continue;
            for (Unit enemy : units) {
                if (!enemy.isAlive() || !enemy.type.combatant) continue;
                if (enemy.faction == squad.faction) continue;
                if (grid.hasLineOfSight(member.cellX, member.cellY, enemy.cellX, enemy.cellY)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean evalInRangeOfTarget(Squad squad, BattleSimulation sim) {
        List<Unit> units = sim.getUnits();
        for (Unit member : units) {
            if (!member.isAlive() || member.squadId != squad.id) continue;
            for (Unit enemy : units) {
                if (!enemy.isAlive() || !enemy.type.combatant) continue;
                if (enemy.faction == squad.faction) continue;
                float d = TacticalScoring.cellDistance(member.cellX, member.cellY, enemy.cellX, enemy.cellY);
                if (d <= member.attackRange) return true;
            }
        }
        return false;
    }

    /**
     * True iff every alive squadmate is within {@link InfantryCohesion#COHESION_RADIUS}
     * of the squad centroid. A scattered squad — one member out beyond the
     * radius — reads false, prompting the planner to insert a
     * {@link com.dillon.starsectormarines.battle.ai.goap.actions.RegroupPosture}
     * step before advancing.
     *
     * <p>A solo or wiped squad reads true (no scattering possible). The
     * cached {@link Squad#centroidX}/{@link Squad#centroidY} are stale outside
     * the alert-update pass; the replan call site is supposed to run inside
     * (or just after) that pass — matches how
     * {@link InfantryCohesion#cohesionOverride} reads them today.
     */
    private static boolean evalWithinCohesionRadius(Squad squad, BattleSimulation sim) {
        if (squad.aliveMembers <= 1) return true;
        float r2 = InfantryCohesion.COHESION_RADIUS * InfantryCohesion.COHESION_RADIUS;
        for (Unit u : sim.getUnits()) {
            if (!u.isAlive() || u.squadId != squad.id) continue;
            float dx = u.cellX - squad.centroidX;
            float dy = u.cellY - squad.centroidY;
            if (dx * dx + dy * dy > r2) return false;
        }
        return true;
    }
}
