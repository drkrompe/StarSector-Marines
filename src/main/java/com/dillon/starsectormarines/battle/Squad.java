package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.ai.PatrolBehavior;
import com.dillon.starsectormarines.battle.ai.SquadAlertLevel;
import com.dillon.starsectormarines.battle.ai.goap.Goal;
import com.dillon.starsectormarines.battle.ai.goap.SquadPlan;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
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
    /**
     * Squad leader. Initially the first marine to deboard (or the first
     * defender minted into the squad at setup). On leader death,
     * {@code BattleSimulation.applyDamage} promotes the closest still-alive
     * squad member to take over — preserves direction of travel through
     * the badge change. The leader's cell is the cohesion anchor that
     * {@link com.dillon.starsectormarines.battle.ai.InfantryCohesion#cohesionOverride}
     * pulls drifting members toward; a fully-wiped squad has a null leader
     * and the cohesion helper falls back to the others-centroid.
     */
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
     * Strategic task handed down by this faction's
     * {@link com.dillon.starsectormarines.battle.command.MissionCommand}, or
     * {@code null} when no commander has written one yet. MISSION-priority
     * GOAP goals ({@code ClearAssignedZoneGoal}, {@code HoldAssignedNodeGoal},
     * {@code RushAssignedObjectiveGoal}) report {@code relevance() = 0} when
     * this is null, so squads fall through to their ambient ENGAGEMENT
     * goals — keeping the commander layer opt-in. Replaced wholesale on
     * each re-assignment (the record is immutable) so any goal that
     * snapshots it at relevance-eval time sees a consistent state.
     *
     * <p>Distinct from {@link #assignedNode}: {@code assignedNode} is the
     * <em>spawn-time</em> tactical anchor (GARRISON home / PATROL seed),
     * stable for the squad's life. {@link #assignedObjective} is the
     * <em>strategic</em> task, updated by the commander each slow-tick.
     */
    public ObjectiveAssignment assignedObjective;

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
    /**
     * Cell-radius around {@link #assignedNode} the squad samples patrol waypoints
     * from. Default matches {@link PatrolBehavior#PATROL_DISTRICT_RADIUS} (wide
     * district sweep); guardpost squads tighten this to their tier's
     * {@link DefensePostKind#patrolRadius} so they orbit the post until release.
     * Reverts to the default when {@link #defensePost} releases.
     */
    public int patrolRadius = PatrolBehavior.PATROL_DISTRICT_RADIUS;
    /**
     * Defense post this squad is garrisoning. Null for regular patrols and for
     * marine squads. Set by {@link BattleSetup} post-{@code allocateDefenders}
     * for squads whose {@link #assignedNode} is a {@link TacticalNode.Kind#GUARDPOST};
     * cleared by {@link BattleSimulation#demolishDeadTurrets} once every turret on
     * the post is destroyed, releasing the squad into normal wide-radius patrol.
     */
    public DefensePost defensePost;

    // ---- Per-tick cached aggregates ----
    // Refreshed once per sim tick by BattleSimulation.updateSquadAlertLevels, so
    // behaviors can read them in O(1) instead of re-walking the unit list.
    // Stale outside that pass; treat as read-only from inside behaviors.

    /** Alive-member count from the most recent tick. 0 when the squad has been wiped. */
    public int aliveMembers = 0;

    /**
     * Soft cohesion variable in [0, 1]. Drains on incoming hits and member
     * deaths, recovers passively while out of contact, and is capped by
     * {@code aliveMembers / originalSize} — so a mauled squad can shake off
     * a bad engagement <em>once</em> but can never fully reset.
     *
     * <p>Drives {@link com.dillon.starsectormarines.battle.ai.goap.Predicate#MORALE_BROKEN}
     * with hysteresis on {@link #moraleBroken}: trips below
     * {@link com.dillon.starsectormarines.battle.BattleSimulation#MORALE_BROKEN_THRESHOLD},
     * clears above {@link com.dillon.starsectormarines.battle.BattleSimulation#MORALE_CLEAR_THRESHOLD}.
     * Updated each tick by {@code BattleSimulation.updateSquadMorale}.
     */
    public float morale = 1.0f;
    /**
     * Hysteresis flag set by {@code updateSquadMorale}. Once {@link #morale}
     * crosses below the broken threshold, this stays true until morale climbs
     * above the (higher) clear threshold — prevents flickering at the boundary
     * if a squad keeps oscillating just under the line. SurviveContact reads
     * this, not the raw morale value, so the planner sees a stable signal.
     */
    public boolean moraleBroken = false;
    /**
     * Sim-seconds remaining on the morale-drain cooldown. Each drain event
     * (hit or near-miss) sets this to {@link com.dillon.starsectormarines.battle.BattleSimulation#MORALE_DRAIN_COOLDOWN};
     * subsequent drains within the window are silently dropped. Prevents a
     * burst of bullets in one tick from insta-breaking a full squad — caps
     * effective drain rate at ~5 events per second.
     */
    public float moraleDrainCooldown = 0f;
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
    /**
     * Per-tick transient set by {@code BattleSimulation.updateSquadAlertLevels}:
     * true if any squadmate sighted a close (within {@link com.dillon.starsectormarines.battle.BattleSimulation#KILL_ZONE_RANGE_CELLS}
     * cells) hostile combatant this tick. Drives the {@link #killZoneLosTicks}
     * hysteresis counter — read once at the end of the alert-update pass and
     * cleared at the top of the next.
     */
    public boolean _killZoneSightedThisTick = false;

    // ---- Story A: garrison ambush gating ----

    /**
     * When true, the squad refuses to open fire from {@link com.dillon.starsectormarines.battle.ai.goap.actions.EngagePosture}
     * until {@link com.dillon.starsectormarines.battle.ai.goap.Predicate#ENEMY_IN_KILL_ZONE}
     * flips true (an enemy entered the kill zone <em>and</em> LOS to that enemy
     * has been stable for {@link com.dillon.starsectormarines.battle.BattleSimulation#KILL_ZONE_LOS_TICKS_THRESHOLD}
     * ticks). Set at construction by {@code BattleSetup} for GARRISON-routed
     * defender squads. Marines and patrol squads leave this false — the
     * evaluator short-circuits the predicate to true for them so the existing
     * Engage flow is unchanged.
     */
    public boolean holdsFireUntilKillZone = false;

    /**
     * Tick counter for kill-zone LOS hysteresis: incremented in
     * {@link com.dillon.starsectormarines.battle.BattleSimulation#updateSquadAlertLevels}
     * when this garrison squad has LOS to a close enemy this tick, reset to 0
     * when LOS is lost. Only updated for squads with {@link #holdsFireUntilKillZone};
     * other squads leave this at 0 (the predicate evaluator never reads it
     * for them).
     */
    public int killZoneLosTicks = 0;

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

    /**
     * Sim-seconds since the current {@link com.dillon.starsectormarines.battle.ai.goap.actions.BreachAndAdvance}
     * step entered its stack-up phase. Used to enforce the per-step stack-up
     * timeout — once it exceeds the threshold, the breach commits regardless
     * of how many members made it to the doorway. Reset to 0 when the breach
     * step completes successfully. No-op when no breach action is active.
     */
    public float breachStackupTimer = 0f;

    /**
     * Portal id the squad's current
     * {@link com.dillon.starsectormarines.battle.ai.goap.actions.ChokePointHold}
     * action is watching, or {@code -1} when no choke-point hold is active.
     * Set by {@code ChokePointHold} on its first execute tick (idempotent —
     * stamps the same id on every tick for the lifetime of the action) and
     * consumed by the {@link com.dillon.starsectormarines.battle.ai.goap.Predicate#ENEMY_IN_PORTAL_CELL}
     * evaluator to scope "which portal is this squad guarding."
     *
     * <p>Story L's choke-point ambush trigger: an enemy combatant standing on
     * the cell of {@code chokePointPortalId}'s doorway flips the predicate
     * true, which the action consults to fire its concentrated burst.
     */
    public int chokePointPortalId = -1;

    public Squad(int id, Faction faction) {
        this.id = id;
        this.faction = faction;
    }

    /**
     * True when this squad's combatants are mechs (carry a
     * {@link MechLoadoutState}). Read by the per-tick replan dispatch in
     * {@code BattleSimulation.tick} to route mech squads to
     * {@code GoapMechBehavior} instead of {@code GoapInfantryBehavior}.
     *
     * <p>Relies on {@code BattleSetup} producing homogeneous squads — mech
     * members and infantry members never share a squad. Probes the leader
     * (set on the first member at mint time) rather than scanning the unit
     * list, so a leaderless squad — leader dead, promotion logic not yet
     * landed — returns {@code false} and the squad falls through to the
     * infantry path harmlessly until the leader pointer is restored.
     */
    public boolean isMechSquad() {
        return leader != null && leader.mech != null;
    }
}
