package com.dillon.starsectormarines.battle.decision.goap.world;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.BelievedContact;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.infantry.InfantryCohesion;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.Portal;
import com.dillon.starsectormarines.battle.squad.SquadAlertSystem;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link WorldState} snapshot for one squad from current
 * {@link BattleView} state. Called by the squad-level replan pass
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

        // Reserved extension predicates follow ai-nouns.md. A contracted
        // story replaces its STUB_FALSE entry with a real evaluator alongside
        // the story's action/goal implementation.
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
        EVALUATORS.put(Predicate.NODE_IS_MUST_HOLD,                 (s, sim) -> TacticalNodeQueries.isMustHold(s));
        EVALUATORS.put(Predicate.THREAT_DENSITY_HIGH_AT_TARGET,
                (s, sim) -> s.engagementDisciplineHold);

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
    public static WorldState build(Squad squad, BattleView sim) {
        WorldState state = WorldState.EMPTY;
        for (Map.Entry<Predicate, PredicateEvaluator> e : EVALUATORS.entrySet()) {
            state = state.with(e.getKey(), e.getValue().evaluate(squad, sim));
        }
        return state;
    }

    // --- Stage 1 evaluators ---------------------------------------------

    private static boolean evalHasTarget(Squad squad, BattleView sim) {
        return squad.hasBelievedContacts();
    }

    /**
     * Direct observation is resolved once in the serial alert pass. A contact
     * refreshed on this sim tick therefore means at least one member had LOS;
     * the parallel planner does not rediscover enemies from global live state.
     */
    private static boolean evalHasLosToTarget(Squad squad, BattleView sim) {
        return squad.hasDirectContactThisTick();
    }

    private static boolean evalInRangeOfTarget(Squad squad, BattleView sim) {
        List<BelievedContact> contacts = squad.believedContacts();
        if (contacts.isEmpty()) return false;
        for (int mi = 0, n = sim.squadMemberCount(squad.id); mi < n; mi++) {
            long member = sim.squadMemberAt(squad.id, mi);
            for (BelievedContact contact : contacts) {
                float d = TacticalScoring.cellDistance(sim.world().x(member),
                        sim.world().y(member), contact.lastSeenCellX() + 0.5f,
                        contact.lastSeenCellY() + 0.5f);
                if (d <= sim.world().attackRange(member)) return true;
            }
        }
        return false;
    }

    /**
     * True iff every alive squadmate is within {@link InfantryCohesion#COHESION_RADIUS}
     * of the squad centroid. A scattered squad — one member out beyond the
     * radius — reads false, prompting the planner to insert a
     * {@link com.dillon.starsectormarines.battle.infantry.RegroupPosture}
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
     * {@link com.dillon.starsectormarines.battle.infantry.EngagePosture}'s
     * call to {@link com.dillon.starsectormarines.battle.infantry.RepositionToCover#tryReposition}.
     * Predicate exists for goals that want to require reposition-readiness
     * (Story C bounding overwatch is the next consumer); the basic engage
     * loop doesn't gate on it — the cooldown gate happens inside the action.
     */
    private static boolean evalCanReposition(Squad squad, BattleView sim) {
        for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
            long u = sim.squadMemberAt(squad.id, i);
            if (sim.world().repositionCooldown(u) <= 0f) return true;
        }
        return false;
    }

    private static boolean evalWithinCohesionRadius(Squad squad, BattleView sim) {
        if (squad.aliveMembers <= 1) return true;
        float r2 = InfantryCohesion.COHESION_RADIUS * InfantryCohesion.COHESION_RADIUS;
        for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
            long u = sim.squadMemberAt(squad.id, i);
            float dx = sim.world().x(u) - squad.centroidX;
            float dy = sim.world().y(u) - squad.centroidY;
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
     * {@link com.dillon.starsectormarines.battle.infantry.ChokePointHold}
     * on its first execute tick.
     *
     * <p>Reads false when no portal is being watched ({@code chokePointPortalId
     * == -1}), the portal id no longer resolves (graph rebuild during the
     * action's lifetime — unlikely but defensive), or no enemy combatant
     * happens to occupy the doorway cell this tick. The "enemy of the squad"
     * means alive, combatant, opposite faction — same rules every other
     * predicate uses.
     */
    private static boolean evalEnemyInPortalCell(Squad squad, BattleView sim) {
        int portalId = squad.chokePointPortalId;
        if (portalId < 0) return false;
        Portal p = sim.getZoneGraph().portalById(portalId);
        if (p == null) return false;
        NavigationGrid grid = sim.getGrid();
        int w = grid.getWidth();
        int dwIdx = p.getDoorwayCellIdx();
        int dwX = dwIdx % w;
        int dwY = dwIdx / w;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long u = sim.liveUnitAt(i);
            if (!sim.identity().type(u).combatant) continue;
            if (sim.identity().faction(u) == squad.faction) continue;
            if (sim.movement().atCell(u, dwX, dwY)) return true;
        }
        return false;
    }

    // --- Story A evaluators ---------------------------------------------

    /**
     * <b>Story A trigger.</b> True when the squad's ambush gate is ready to
     * fire. Non-garrison squads always read true — they have no "wait" state,
     * so the predicate stays a no-op in {@link com.dillon.starsectormarines.battle.infantry.EngagePosture}'s
     * preconditions for marines and patrol squads.
     *
     * <p>Garrison squads ({@link Squad#holdsFireUntilKillZone}) read true iff:
     * <ul>
     *   <li>{@link Squad#timeUnderSustainedFire} has crossed
     *       {@link SquadAlertSystem#KILL_ZONE_AMBUSH_BLOWN_SECONDS} — the
     *       squad has been taking LoS-confirmed incoming long enough that the
     *       ambush is blown, so the gate is forced open and the garrison can
     *       return fire at attackers outside the kill-zone radius; OR</li>
     *   <li>{@link Squad#killZoneLosTicks} has reached
     *       {@link SquadAlertSystem#KILL_ZONE_LOS_TICKS_THRESHOLD} — LOS to a
     *       close enemy has been stable for ~0.2s, suppressing flicker on
     *       transient sightings; AND
     *       the serial alert pass currently sees an enemy combatant
     *       within {@link SquadAlertSystem#KILL_ZONE_RANGE_CELLS} cells —
     *       the trigger doesn't latch; once the enemy retreats out of the
     *       kill zone the gate closes again (unless the ambush-blown
     *       backstop above has already fired).</li>
     * </ul>
     */
    private static boolean evalEnemyInKillZone(Squad squad, BattleView sim) {
        if (!squad.holdsFireUntilKillZone) return true;
        if (squad.timeUnderSustainedFire >= SquadAlertSystem.KILL_ZONE_AMBUSH_BLOWN_SECONDS) return true;
        if (squad.killZoneLosTicks < SquadAlertSystem.KILL_ZONE_LOS_TICKS_THRESHOLD) return false;
        return squad.hasEnemyInKillZoneThisTick();
    }

    /**
     * <b>Story A re-trigger.</b> True when at least one squadmate is taking
     * incoming fire at a cell with LOS back to the firing enemy — i.e. the
     * member is exposed in the shot's lane. Garrison ambush flips back into
     * BreakLOS posture when this trips, so a squad caught in return fire
     * ducks for cover instead of trading blows at parity.
     *
     * <p>The serial alert pass owns the active-shot, proximity, and LOS scan.
     * Planning consumes that published result so every parallel replan sees
     * the same tick snapshot without copying shots or revisiting the roster.
     */
    private static boolean evalUnderFireAtLos(Squad squad, BattleView sim) {
        return squad.isUnderFireAtLosThisTick();
    }
}
