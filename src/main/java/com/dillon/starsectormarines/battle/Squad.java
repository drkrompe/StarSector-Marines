package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.ai.SquadAlertLevel;
import com.dillon.starsectormarines.battle.ai.goap.Goal;
import com.dillon.starsectormarines.battle.ai.goap.SquadPlan;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;

/**
 * A fireteam of marines that deboarded from one shuttle, or a defender squad
 * pegged to a tactical node at battle start. Squads are the unit of cohesion,
 * field-of-fire spreading, and shared awareness — members stay within radius
 * of squadmates, target selection penalizes squadmates already engaging the
 * same enemy, and a squad's {@link SquadAlertLevel} drives the idle vs.
 * engaged branch in {@link com.dillon.starsectormarines.battle.ai.GarrisonBehavior}
 * and {@link com.dillon.starsectormarines.battle.ai.PatrolBehavior}.
 *
 * <p>Squad identity is just an integer key on {@link Unit#squadId}. The
 * {@link Squad} object holds metadata the AI consults — leader pointer,
 * alert state, the assigned tactical node for garrison/patrol squads, and
 * the last cell an enemy was seen at (for SUSPICIOUS-state convergence).
 *
 * <p>Created in two paths:
 * <ul>
 *   <li>Marine deboard: {@code AirSystem} mints a squad on the first marine
 *       out of each shuttle and chains the rest to that id. {@link #assignedNode}
 *       stays null — marines navigate by objective, not by tactical-node anchor.</li>
 *   <li>Defender setup: {@code BattleSetup} mints one squad per occupied
 *       tactical node, sets {@link #assignedNode}, and stamps the role on
 *       each member (GARRISON for held nodes, PATROL for roving squads with
 *       a district-seed node).</li>
 * </ul>
 */
public final class Squad {

    /**
     * Sim-seconds the squad stays ENGAGED after the last LOS to an enemy
     * drops. Generous so a brief duck-behind-cover doesn't yank a garrison
     * back into idle posture, and so SUSPICIOUS still has time to converge.
     */
    public static final float ENGAGED_DECAY_SECONDS = 6.0f;
    /**
     * After ENGAGED decays, sim-seconds the squad stays SUSPICIOUS before
     * dropping to UNAWARE and resuming idle routines. Long enough that a
     * patrol commits to investigating a last-seen cell before giving up.
     */
    public static final float SUSPICIOUS_DECAY_SECONDS = 8.0f;

    public final int id;
    public final Faction faction;
    /** First marine to deboard. May die — leader-promotion logic isn't in yet, so a leaderless squad just has a null leader and falls back to "follow the centroid." */
    public Unit leader;

    /** Current awareness state. Bumped by {@code BattleSimulation.updateSquadAlertLevels}; behaviors only read. */
    public SquadAlertLevel alertLevel = SquadAlertLevel.UNAWARE;
    /** Sim-seconds since the most recent contact event (LOS or fall-back trigger). Drives the ENGAGED → SUSPICIOUS → UNAWARE decay. */
    public float timeSinceContact = 0f;
    /** Last cell an enemy was seen at by any squadmate. -1 sentinel = never. SUSPICIOUS uses this as the convergence target. */
    public int lastSeenEnemyX = -1;
    public int lastSeenEnemyY = -1;

    /**
     * Tactical node this squad is anchored to. For GARRISON it's the position
     * to hold; for PATROL it's the seed point of the patrol district (members
     * pick random nearby nodes as waypoints). Null for marine squads.
     */
    public TacticalNode assignedNode;

    /**
     * Member count at the moment {@link com.dillon.starsectormarines.battle.BattleSetup}
     * finished spawning the squad. The fallback trigger compares
     * {@link #aliveMembers} against this peak: when casualties bring the squad
     * to half or fewer of its original strength, it reassigns to the first
     * {@link TacticalNode.LinkKind#FALLBACK_TO} target. 0 for squads that
     * weren't sized at creation (marine deboards grow incrementally).
     */
    public int originalSize = 0;
    /** True once the squad has already executed its one-shot fallback this battle. Suppresses re-trigger so a squad doesn't cascade through every node in its FALLBACK_TO chain in one tick. */
    public boolean fallbackTriggered = false;
    /**
     * True while the squad is still walking from the old post to the new one.
     * {@link com.dillon.starsectormarines.battle.ai.GarrisonBehavior} routes
     * members to their freshly-assigned home cells regardless of alert level
     * while this flag is set, and the sim clears it once every surviving
     * member is within {@link com.dillon.starsectormarines.battle.BattleSimulation#HOME_ARRIVAL_RADIUS}
     * of their home cell.
     */
    public boolean fallbackInProgress = false;

    /**
     * Current patrol waypoint cell. -1 sentinel = not assigned yet, the
     * behavior picks one on next tick. {@link com.dillon.starsectormarines.battle.ai.PatrolBehavior}
     * picks a new waypoint when the squad's centroid arrives at the current
     * one, then dwells {@link #patrolDwellTimer} sim-seconds before moving on.
     * Squad-scoped so all members converge on the same target rather than
     * wandering independently.
     */
    public int patrolWaypointX = -1;
    public int patrolWaypointY = -1;
    /** Sim-seconds the squad rests at the current waypoint before picking a new one. */
    public float patrolDwellTimer = 0f;

    // ---- Per-tick cached aggregates ----
    // Refreshed once per sim tick by BattleSimulation.updateSquadAlertLevels, so
    // behaviors can read them in O(1) instead of re-walking the unit list.
    // Stale outside that pass; treat as read-only from inside behaviors.

    /** Alive-member count from the most recent tick. 0 when the squad has been wiped. */
    public int aliveMembers = 0;
    /** Centroid X over alive members. Undefined when {@link #aliveMembers} is 0. */
    public float centroidX = 0f;
    /** Centroid Y over alive members. Undefined when {@link #aliveMembers} is 0. */
    public float centroidY = 0f;
    /**
     * Internal flags filled mid-pass by {@code BattleSimulation.updateSquadAlertLevels}
     * to track "did any squadmate's LoS hit this tick" / "did anyone trip a
     * suspicious condition." Driven entirely by the sim — behaviors should
     * read {@link #alertLevel}, not these. Public only because they're
     * mutated across a same-package boundary.
     */
    public boolean _engagedThisTick = false;
    public boolean _suspiciousThisTick = false;

    // ---- GOAP plan state ----
    // Populated by GoapInfantryBehavior.replanIfNeeded; mutated by per-unit
    // GoapInfantryBehavior.update as members execute the current step's action.

    /** Squad's currently-executing plan, or null when the planner has nothing to do (no relevant goal / no reachable plan). */
    public SquadPlan currentPlan = null;
    /** Goal the planner chose at the last replan. Null when the squad has no relevant goal. Diagnostic — consumed by the GOAP debug HUD; not load-bearing for execution. */
    public Goal currentGoal = null;
    /** Sim-seconds since the last replan. Drives the periodic-replan trigger; resets to zero on every replan. */
    public float timeSinceReplan = 0f;
    /** {@link #aliveMembers} value at the moment the current plan was built. Diff vs. live {@link #aliveMembers} drives death-triggered replan: any change forces a refresh next tick. */
    public int aliveMembersAtLastPlan = 0;

    public Squad(int id, Faction faction) {
        this.id = id;
        this.faction = faction;
    }
}
