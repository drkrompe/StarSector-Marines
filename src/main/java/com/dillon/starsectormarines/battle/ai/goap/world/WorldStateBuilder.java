package com.dillon.starsectormarines.battle.ai.goap.world;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.ShotEvent;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.ai.InfantryCohesion;
import com.dillon.starsectormarines.battle.ai.TacticalScoring;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.Portal;

import java.util.ArrayList;
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
        // SQUAD_BELOW_HALF_STRENGTH is deprecated — superseded by MORALE_BROKEN,
        // which recovers over time. Stub kept here so any stragglers reading
        // the predicate see a stable false until they're swept.
        EVALUATORS.put(Predicate.SQUAD_BELOW_HALF_STRENGTH,         STUB_FALSE);
        EVALUATORS.put(Predicate.MORALE_BROKEN,                     (s, sim) -> s.moraleBroken);
        EVALUATORS.put(Predicate.ENEMY_IN_KILL_ZONE,                WorldStateBuilder::evalEnemyInKillZone);
        EVALUATORS.put(Predicate.UNDER_FIRE_AT_LOS,                 WorldStateBuilder::evalUnderFireAtLos);
        EVALUATORS.put(Predicate.ENEMY_SUPPRESSED,                  STUB_FALSE);
        EVALUATORS.put(Predicate.BEHIND_FRIENDLY_RELATIVE_TO_THREAT, STUB_FALSE);
        EVALUATORS.put(Predicate.CAN_REPOSITION,                    WorldStateBuilder::evalCanReposition);
        EVALUATORS.put(Predicate.ZONE_CLEAR,                        STUB_FALSE);
        EVALUATORS.put(Predicate.ENEMY_IN_PORTAL_CELL,              WorldStateBuilder::evalEnemyInPortalCell);
        EVALUATORS.put(Predicate.NODE_IS_MUST_HOLD,                 STUB_FALSE);
        EVALUATORS.put(Predicate.THREAT_DENSITY_HIGH_AT_TARGET,     STUB_FALSE);

        // Mech GOAP Stage 1 — goal-side markers; both role-anchored goals
        // use customPlan so these predicates are never observed at search time.
        EVALUATORS.put(Predicate.KILL_ZONE_COVERED,                 STUB_FALSE);
        EVALUATORS.put(Predicate.SQUAD_BACKED,                      STUB_FALSE);
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

    /**
     * Cell radius around {@link Squad#lastSeenEnemyX}/{@code Y} within which
     * enemies count as threat-set members for HAS_LOS_TO_TARGET. Sized to
     * comfortably absorb post-observation drift during the ENGAGED alert
     * window (~6s × ~2 cells/sec = ~12 cells) plus some slack for path
     * detours. Squads with no known threat ({@code lastSeenEnemy = -1}) read
     * HAS_LOS_TO_TARGET as false outright — they don't get pulled to enemies
     * they've never observed.
     *
     * <p>Interim stand-in for the per-squad {@code BelievedContact} map
     * documented in roadmap/ai/15-perception-and-influence.md. The single
     * stamped cell is the minimum-viable representation of "what this squad
     * knows about enemies"; the full belief map replaces it when the
     * perception layer ships.
     *
     * <p>Indirect fire (mech LRM at 40-cell launch range) does not poison the
     * stamp today because the audible-gunfire path
     * ({@link BattleSimulation#GUNFIRE_ALERT_RADIUS} = 18) excludes far
     * launchers before {@code lastSeenEnemy} would be updated.
     */
    private static final float HAS_LOS_THREAT_SET_RADIUS = 20f;

    private static boolean evalHasLosToTarget(Squad squad, BattleSimulation sim) {
        if (squad.lastSeenEnemyX < 0 || squad.lastSeenEnemyY < 0) return false;

        NavigationGrid grid = sim.getGrid();
        List<Unit> units = sim.getUnits();

        // Pre-collect squad members once so the inner loop is O(threat-set × squad-size)
        // instead of O(threat-set × total-units).
        List<Unit> members = new ArrayList<>(4);
        for (Unit u : units) {
            if (u.isAlive() && u.squadId == squad.id) members.add(u);
        }
        if (members.isEmpty()) return false;

        // Gather only enemies inside the threat-set window via the per-tick
        // spatial index — eliminates the previous O(total-units) outer scan
        // when the squad's lastSeenEnemy point sits far from most of the map.
        ArrayList<Unit> threats = new ArrayList<>();
        sim.getUnitIndex().gather(squad.lastSeenEnemyX, squad.lastSeenEnemyY,
                HAS_LOS_THREAT_SET_RADIUS, threats);
        for (int i = 0, n = threats.size(); i < n; i++) {
            Unit enemy = threats.get(i);
            if (!enemy.type.combatant) continue;
            if (enemy.faction == squad.faction) continue;
            for (Unit member : members) {
                if (grid.hasLineOfSight(member.cellX, member.cellY, enemy.cellX, enemy.cellY)) return true;
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
    /**
     * Story G predicate — true when any alive squadmate's reposition cooldown
     * has expired. Aggregated at squad scope to match the other "any
     * squadmate" predicates (HAS_LOS, IN_RANGE); the per-member decision to
     * actually reposition is made inline inside
     * {@link com.dillon.starsectormarines.battle.ai.goap.actions.EngagePosture}'s
     * call to {@link com.dillon.starsectormarines.battle.ai.goap.actions.RepositionToCover#tryReposition}.
     * Predicate exists for goals that want to require reposition-readiness
     * (Story C bounding overwatch is the next consumer); the basic engage
     * loop doesn't gate on it — the cooldown gate happens inside the action.
     */
    private static boolean evalCanReposition(Squad squad, BattleSimulation sim) {
        for (Unit u : sim.getUnits()) {
            if (!u.isAlive() || u.squadId != squad.id) continue;
            if (u.repositionCooldown <= 0f) return true;
        }
        return false;
    }

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

    // --- Story L evaluators ---------------------------------------------

    /**
     * Story L trigger: true iff an enemy combatant is standing on the cell of
     * the portal the squad's choke-point action is watching
     * ({@link Squad#chokePointPortalId}). The portal id is stamped onto the
     * squad by
     * {@link com.dillon.starsectormarines.battle.ai.goap.actions.ChokePointHold}
     * on its first execute tick.
     *
     * <p>Reads false when no portal is being watched ({@code chokePointPortalId
     * == -1}), the portal id no longer resolves (graph rebuild during the
     * action's lifetime — unlikely but defensive), or no enemy combatant
     * happens to occupy the doorway cell this tick. The "enemy of the squad"
     * means alive, combatant, opposite faction — same rules every other
     * predicate uses.
     */
    private static boolean evalEnemyInPortalCell(Squad squad, BattleSimulation sim) {
        int portalId = squad.chokePointPortalId;
        if (portalId < 0) return false;
        Portal p = sim.getZoneGraph().portalById(portalId);
        if (p == null) return false;
        NavigationGrid grid = sim.getGrid();
        int w = grid.getWidth();
        int dwIdx = p.getDoorwayCellIdx();
        int dwX = dwIdx % w;
        int dwY = dwIdx / w;
        for (Unit u : sim.getUnits()) {
            if (!u.isAlive() || !u.type.combatant) continue;
            if (u.faction == squad.faction) continue;
            if (u.cellX == dwX && u.cellY == dwY) return true;
        }
        return false;
    }

    // --- Story A evaluators ---------------------------------------------

    /**
     * <b>Story A trigger.</b> True when the squad's ambush gate is ready to
     * fire. Non-garrison squads always read true — they have no "wait" state,
     * so the predicate stays a no-op in {@link com.dillon.starsectormarines.battle.ai.goap.actions.EngagePosture}'s
     * preconditions for marines and patrol squads.
     *
     * <p>Garrison squads ({@link Squad#holdsFireUntilKillZone}) read true iff:
     * <ul>
     *   <li>{@link Squad#killZoneLosTicks} has reached
     *       {@link BattleSimulation#KILL_ZONE_LOS_TICKS_THRESHOLD} — LOS to a
     *       close enemy has been stable for ~0.2s, suppressing flicker on
     *       transient sightings; AND</li>
     *   <li>At least one squadmate currently has LOS to an enemy combatant
     *       within {@link BattleSimulation#KILL_ZONE_RANGE_CELLS} cells —
     *       the trigger doesn't latch; once the enemy retreats out of the
     *       kill zone the gate closes again.</li>
     * </ul>
     */
    private static boolean evalEnemyInKillZone(Squad squad, BattleSimulation sim) {
        if (!squad.holdsFireUntilKillZone) return true;
        if (squad.killZoneLosTicks < BattleSimulation.KILL_ZONE_LOS_TICKS_THRESHOLD) return false;
        NavigationGrid grid = sim.getGrid();
        int range2 = BattleSimulation.KILL_ZONE_RANGE_CELLS * BattleSimulation.KILL_ZONE_RANGE_CELLS;
        List<Unit> units = sim.getUnits();
        for (Unit member : units) {
            if (!member.isAlive() || member.squadId != squad.id) continue;
            for (Unit enemy : units) {
                if (!enemy.isAlive() || !enemy.type.combatant) continue;
                if (enemy.faction == squad.faction) continue;
                int dx = enemy.cellX - member.cellX;
                int dy = enemy.cellY - member.cellY;
                if (dx * dx + dy * dy > range2) continue;
                if (grid.hasLineOfSight(member.cellX, member.cellY, enemy.cellX, enemy.cellY)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * <b>Story A re-trigger.</b> True when at least one squadmate is taking
     * incoming fire at a cell with LOS back to the firing enemy — i.e. the
     * member is exposed in the shot's lane. Garrison ambush flips back into
     * BreakLOS posture when this trips, so a squad caught in return fire
     * ducks for cover instead of trading blows at parity.
     *
     * <p>Scans {@link BattleSimulation#getActiveShots} for hostile shots whose
     * target endpoint is within 2 cells of any squadmate. A squadmate at the
     * shot's target area with LOS back to {@code (fromX, fromY)} qualifies —
     * the LOS test is what distinguishes "shot through a wall (impossible,
     * but the shot grazed past a corner)" from "we're standing in the firing
     * lane."
     */
    private static boolean evalUnderFireAtLos(Squad squad, BattleSimulation sim) {
        List<ShotEvent> shots = sim.getActiveShots();
        if (shots.isEmpty()) return false;
        NavigationGrid grid = sim.getGrid();
        List<Unit> units = sim.getUnits();
        for (ShotEvent shot : shots) {
            if (shot.shooterFaction == squad.faction) continue;
            for (Unit member : units) {
                if (!member.isAlive() || member.squadId != squad.id) continue;
                float dx = shot.toX - (member.cellX + 0.5f);
                float dy = shot.toY - (member.cellY + 0.5f);
                if (dx * dx + dy * dy > 4f) continue; // 2 cells squared
                // Shot fromX/fromY are cell-centers (shooter.cellX + 0.5);
                // floor recovers the integer cell.
                int fromCellX = (int) Math.floor(shot.fromX);
                int fromCellY = (int) Math.floor(shot.fromY);
                if (grid.hasLineOfSight(member.cellX, member.cellY, fromCellX, fromCellY)) {
                    return true;
                }
            }
        }
        return false;
    }
}
