package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.drone.DroneHub;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;

import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * A fireteam of marines that deboarded from one shuttle, or a defender squad
 * pegged to a tactical node at battle start. Squads are the unit of cohesion,
 * field-of-fire spreading, and shared awareness — members stay within radius
 * of squadmates, target selection penalizes squadmates already engaging the
 * same enemy, and a squad's {@link SquadAlertLevel} drives the idle vs.
 * engaged branch in {@link com.dillon.starsectormarines.battle.infantry.HoldPost}
 * and {@link com.dillon.starsectormarines.battle.infantry.PatrolRoute}.
 *
 * <p>Squad identity is just an integer key on {@code squadId}. The
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
     * Sentinel for "not part of a squad" — a solo unit (defender / civilian /
     * unsquadded turret) carries no {@code SQUAD} component, so presence IS
     * membership. Used as the default {@code squadId} and the "no squad" value in
     * membership lookups; never a stored column value.
     */
    public static final int NO_SQUAD = -1;

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
     * Squad leader, by entity id ({@code 0L} = none / fully-wiped squad).
     * Initially the first marine to deboard (or the first defender minted into
     * the squad at setup). On leader death,
     * {@code DamageResolver.resolve} promotes the closest still-alive squad
     * member to take over — preserves direction of travel through the badge
     * change. The leader's cell is the cohesion anchor that
     * {@link com.dillon.starsectormarines.battle.infantry.InfantryCohesion#cohesionOverride}
     * pulls drifting members toward; a fully-wiped squad has {@code leaderId == 0L}
     * and the cohesion helper falls back to the others-centroid.
     *
     * <p>Held as an id, not an object handle: the leader can die and be
     * released from the registry while the squad lives on, so a held ref
     * would dangle (the {@code isAlive()}-on-a-corpse hazard). Gate liveness
     * on demand via {@code sim.resolveUnit(leaderId)} (returns {@code 0L} when
     * dead-or-none) or {@code registry.isLive(leaderId)}. Compare membership by
     * id ({@code memberId == leaderId}).
     */
    public long leaderId;

    /** Current awareness state. Bumped by {@code SquadAlertSystem}; behaviors only read. */
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
     * Member count at the moment {@link com.dillon.starsectormarines.battle.setup.BattleSetup}
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
     * {@link com.dillon.starsectormarines.battle.infantry.HoldPost} routes
     * members to their freshly-assigned home cells regardless of alert level
     * while this flag is set, and the sim clears it once every surviving
     * member is within the home-arrival radius (see
     * {@link com.dillon.starsectormarines.battle.squad.SquadFallbackSystem})
     * of their home cell.
     */
    public boolean fallbackInProgress = false;

    /**
     * Current patrol waypoint cell. -1 sentinel = not assigned yet, the
     * behavior picks one on next tick. {@link com.dillon.starsectormarines.battle.infantry.PatrolRoute}
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
     * from. Default matches {@link com.dillon.starsectormarines.battle.infantry.PatrolRoute#DEFAULT_DISTRICT_RADIUS}
     * (wide district sweep); guardpost squads tighten this to their tier's
     * {@link DefensePostKind#patrolRadius} so they orbit the post until release.
     * Reverts to the default when {@link #defensePost} releases.
     */
    public int patrolRadius = com.dillon.starsectormarines.battle.infantry.PatrolRoute.DEFAULT_DISTRICT_RADIUS;
    /**
     * Defense post this squad is garrisoning. Null for regular patrols and for
     * marine squads. Set by {@link BattleSetup} post-{@code allocateDefenders}
     * for squads whose {@link #assignedNode} is a {@link TacticalNode.Kind#GUARDPOST};
     * cleared by {@code TurretDemolitionSystem} once every turret on
     * the post is destroyed, releasing the squad into normal wide-radius patrol.
     */
    public DefensePost defensePost;

    // ---- Per-tick cached aggregates ----
    // Refreshed once per sim tick by SquadAlertSystem, so
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
     * <p>Drives {@link com.dillon.starsectormarines.battle.decision.goap.Predicate#MORALE_BROKEN}
     * with hysteresis on {@link #moraleBroken}: trips below
     * {@link com.dillon.starsectormarines.battle.squad.SquadMoraleSystem#MORALE_BROKEN_THRESHOLD},
     * clears above {@link com.dillon.starsectormarines.battle.squad.SquadMoraleSystem#MORALE_CLEAR_THRESHOLD}.
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
     * (hit or near-miss) sets this to {@link com.dillon.starsectormarines.battle.squad.SquadMoraleSystem#MORALE_DRAIN_COOLDOWN};
     * subsequent drains within the window are silently dropped. Prevents a
     * burst of bullets in one tick from insta-breaking a full squad — caps
     * effective drain rate at ~5 events per second.
     */
    public float moraleDrainCooldown = 0f;
    /**
     * Sim seconds since the last hit or near-miss on a squadmate. Gates morale
     * recovery — see {@link com.dillon.starsectormarines.battle.squad.SquadMoraleSystem#MORALE_RECOVER_AFTER_FIRE_SECONDS}.
     * Initialized to a large value so fresh squads can recover immediately if
     * broken without first being shot at (degenerate case but possible).
     */
    public float timeSinceUnderFire = Float.MAX_VALUE / 2f;
    /** Centroid X over alive members. Undefined when {@link #aliveMembers} is 0. */
    public float centroidX = 0f;
    /** Centroid Y over alive members. Undefined when {@link #aliveMembers} is 0. */
    public float centroidY = 0f;
    /**
     * Internal flags filled mid-pass by {@code SquadAlertSystem}
     * to track "did any squadmate's LoS hit this tick" / "did anyone trip a
     * suspicious condition." Driven entirely by the sim — behaviors should
     * read {@link #alertLevel}, not these. Public only because they're
     * mutated across a same-package boundary.
     */
    public boolean _engagedThisTick = false;
    public boolean _suspiciousThisTick = false;
    /**
     * Per-tick transient set by {@code SquadAlertSystem}:
     * true if any squadmate sighted a close (within {@link com.dillon.starsectormarines.battle.squad.SquadAlertSystem#KILL_ZONE_RANGE_CELLS}
     * cells) hostile combatant this tick. Drives the {@link #killZoneLosTicks}
     * hysteresis counter — read once at the end of the alert-update pass and
     * cleared at the top of the next.
     */
    public boolean _killZoneSightedThisTick = false;

    // ---- Story A: garrison ambush gating ----

    /**
     * When true, the squad refuses to open fire from {@link com.dillon.starsectormarines.battle.infantry.EngagePosture}
     * until {@link com.dillon.starsectormarines.battle.decision.goap.Predicate#ENEMY_IN_KILL_ZONE}
     * flips true (an enemy entered the kill zone <em>and</em> LOS to that enemy
     * has been stable for {@link com.dillon.starsectormarines.battle.squad.SquadAlertSystem#KILL_ZONE_LOS_TICKS_THRESHOLD}
     * ticks). Set at construction by {@code BattleSetup} for GARRISON-routed
     * defender squads. Marines and patrol squads leave this false — the
     * evaluator short-circuits the predicate to true for them so the existing
     * Engage flow is unchanged.
     */
    public boolean holdsFireUntilKillZone = false;

    /**
     * Tick counter for kill-zone LOS hysteresis: incremented in
     * {@link com.dillon.starsectormarines.battle.squad.SquadAlertSystem}
     * when this garrison squad has LOS to a close enemy this tick, reset to 0
     * when LOS is lost. Only updated for squads with {@link #holdsFireUntilKillZone};
     * other squads leave this at 0 (the predicate evaluator never reads it
     * for them).
     */
    public int killZoneLosTicks = 0;

    /**
     * Cumulative sim-seconds this garrison squad has been taking incoming
     * fire with LoS back to the shooter — accumulates monotonically once
     * the first qualifying shot lands, never decays. Drives the
     * "ambush-is-blown" override in
     * {@link com.dillon.starsectormarines.battle.decision.goap.world.WorldStateBuilder}'s
     * {@code evalEnemyInKillZone}: once this passes
     * {@link com.dillon.starsectormarines.battle.squad.SquadAlertSystem#KILL_ZONE_AMBUSH_BLOWN_SECONDS}
     * the gate opens regardless of whether an enemy is in the 8-cell kill
     * zone, so the squad can return fire at long-range attackers that probed
     * them from beyond the ambush radius.
     *
     * <p>SQ-17 motivator: a garrison being chipped by a long-LOS mech had no
     * tactical answer — kill-zone gate closed, BreakLOS couldn't relocate
     * out of the firing lane fast enough, squad just absorbed fire.
     * Permanent so the surprise element is treated as a one-way door:
     * once you've been discovered, you don't get to re-hide.
     */
    public float timeUnderSustainedFire = 0f;

    /**
     * Per-tick transient set by {@code SquadAlertSystem} for every squad:
     * true if any squadmate took a shot from an enemy with LoS to that shot's
     * origin this tick. Infantry consumes it as an immediate GOAP replan
     * interrupt; garrisons also use it to drive the
     * {@link #timeUnderSustainedFire} accumulator. Cleared at the top of the
     * next alert pass.
     */
    public boolean _underFireAtLosThisTick = false;

    /**
     * Previous alert pass's under-fire value. The infantry replan gate compares
     * this with {@link #_underFireAtLosThisTick} so the first incoming shot
     * interrupts immediately without rebuilding the same BreakLOS plan on
     * every tick of a tracer's visual lifetime.
     */
    public boolean _underFireAtLosLastTick = false;

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
     * Sim-seconds since the current {@link com.dillon.starsectormarines.battle.infantry.BreachAndAdvance}
     * step entered its stack-up phase. Used to enforce the per-step stack-up
     * timeout — once it exceeds the threshold, the breach commits regardless
     * of how many members made it to the doorway. Reset to 0 when the breach
     * step completes successfully. No-op when no breach action is active.
     */
    public volatile float breachStackupTimer = 0f;

    /**
     * Portal id the squad's current
     * {@link com.dillon.starsectormarines.battle.infantry.ChokePointHold}
     * action is watching, or {@code -1} when no choke-point hold is active.
     * Set by {@code ChokePointHold} on its first execute tick (idempotent —
     * stamps the same id on every tick for the lifetime of the action) and
     * consumed by the {@link com.dillon.starsectormarines.battle.decision.goap.Predicate#ENEMY_IN_PORTAL_CELL}
     * evaluator to scope "which portal is this squad guarding."
     *
     * <p>Story L's choke-point ambush trigger: an enemy combatant standing on
     * the cell of {@code chokePointPortalId}'s doorway flips the predicate
     * true, which the action consults to fire its concentrated burst.
     */
    public volatile int chokePointPortalId = -1;

    // ---- Story 19: threat-scored objective advance ----

    /** Raw commit-vs-press weight in [0,1] from the most recent EnterZone advance tick. Diagnostic plus hysteresis input. */
    public volatile float advanceEngageWeight = 0f;
    /** True while the weight has crossed the commit threshold and not yet fallen below the lower release threshold. */
    public volatile boolean advanceEngageCommitted = false;
    /** Maximum cells the squad may step off the advance axis to prosecute the current contact. Zero while pressing. */
    public volatile float advanceEngageLeash = 0f;
    /** Highest-contributing contact behind the current advance score, or {@code 0L} when no route threat exists. */
    public volatile long advanceThreatId = 0L;
    /** Raw hostile/friendly headcounts behind the weighted score; surfaced in squad dumps for tuning. */
    public volatile int advanceThreatFoes = 0;
    public volatile int advanceThreatFriends = 0;
    /** Closest point on the advance axis to {@link #advanceThreatId}; center of the off-axis firing-position leash. */
    public volatile int advanceThreatAnchorX = -1;
    public volatile int advanceThreatAnchorY = -1;
    /** True when the primary contact's active path ends materially farther from the squad centroid. */
    public volatile boolean advanceThreatRetreating = false;
    /** Sim tick at which the cached score was computed. Prevents N members from repeating one squad-level tally in the same tick. */
    public volatile int advanceThreatTick = -1;

    // ---- Story 20: bounding overwatch during a committed advance ----

    /** True while EnterZone is executing a two-team bound against the committed route threat. */
    public volatile boolean boundingActive = false;
    /** Monotonic bound number. Even: team A overwatches; odd: team B overwatches. */
    public volatile int boundingPhase = 0;
    /** EnterZone identity carried as data so stale state cannot leak into the next zone step. */
    public volatile int boundingTargetZoneId = -1;
    public volatile int boundingDestX = -1;
    public volatile int boundingDestY = -1;
    /** Threat this phase is covering, or {@code 0L} while inactive. */
    public volatile long boundingThreatId = 0L;
    /** Forward-axis anchor used to ensure each phase leapfrogs beyond the previous one. */
    public volatile int boundingStrideX = -1;
    public volatile int boundingStrideY = -1;
    /** Frozen member-to-cell assignment for the currently moving half. Arrays are replaced atomically under {@link #lock}. */
    public volatile long[] boundingMemberIds = new long[0];
    public volatile int[] boundingTargetXs = new int[0];
    public volatile int[] boundingTargetYs = new int[0];
    /** Last tick that tried to start or flip a bound; prevents every sibling retrying the same failed search. */
    public volatile int boundingAttemptTick = -1;

    /**
     * Entity id of the hub this squad's drones launched from, or {@code 0L} for
     * marine / defender squads. Set when
     * {@link com.dillon.starsectormarines.battle.drone.DroneSpawner} mints the
     * squad on the hub's first launch. Held as an id, not a hub {@code Entity}
     * ref: the hub can be destroyed (and registry-released) while the squad is
     * torn down, so resolve on demand via {@code sim.resolveUnit(droneHubId)} —
     * {@code null} means the hub is gone.
     *
     * <p>Drives squad-level dispatch in {@code BattleSimulation.tick}'s replan
     * loop: {@link #isDroneSquad()} routes to {@code GoapDroneBehavior} instead
     * of the mech / infantry paths. ({@code isDroneSquad} reads the id presence,
     * so it stays true for the squad's life; {@code DefendHubGoal} resolves the
     * id to gate relevance on the hub still being alive.)
     */
    public long droneHubId;

    /**
     * Per-squad monitor for guarding squad-shared mutable state from concurrent
     * mutation when {@code BattleSimulation.tick} dispatches UPDATE_UNITS in
     * parallel. GOAP actions and behaviors that mutate squad fields
     * (waypoint pick, breach stack-up timer, plan advance/clear, current-plan
     * null-out on FAILURE, choke-point portal stamp) wrap their critical
     * section in {@code synchronized (squad.lock) { ... }}. Per-squad locks
     * (not per-sim) so different squads don't contend on the same monitor.
     *
     * <p>Idempotent or commutative per-tick writes that are gated to the squad
     * leader ({@code if (member.entityId == squad.leaderId)}) bypass the lock as a cheaper
     * alternative — see {@link com.dillon.starsectormarines.battle.infantry.PatrolRoute}'s
     * dwell-timer decrement for the canonical example.
     *
     * <p>Never hold this lock while acquiring another squad's lock — actions
     * that touch multiple squads must sort by {@link #id} before locking.
     */
    public final Object lock = new Object();

    /**
     * Clears every Story 20 phase field atomically. Called when EnterZone
     * releases its route threat, reaches the zone, or a replan switches to a
     * different action. Reentrant on {@link #lock}, so a phase-transition
     * failure may call it from an already-guarded section.
     */
    public void clearBoundingOverwatch() {
        synchronized (lock) {
            boundingActive = false;
            boundingPhase = 0;
            boundingTargetZoneId = -1;
            boundingDestX = -1;
            boundingDestY = -1;
            boundingThreatId = 0L;
            boundingStrideX = -1;
            boundingStrideY = -1;
            boundingMemberIds = new long[0];
            boundingTargetXs = new int[0];
            boundingTargetYs = new int[0];
            boundingAttemptTick = -1;
        }
    }

    public Squad(int id, Faction faction) {
        this.id = id;
        this.faction = faction;
    }

    /**
     * True when this squad's combatants are mechs (carry a
     * {@link MechLoadoutComponent}). Set once at mint from the first member's
     * {@code mech} (squads are homogeneous — {@code BattleSetup} never mixes
     * mech and infantry members), so it's stable for the squad's life and
     * <b>survives leader death</b> — unlike the old leader-probe, a mech squad
     * stays a mech squad while leaderless. Read by the per-tick replan dispatch
     * in {@code BattleSimulation.tick} to route mech squads to
     * {@code GoapMechBehavior} instead of {@code GoapInfantryBehavior}.
     */
    public boolean mechSquad;

    public boolean isMechSquad() {
        return mechSquad;
    }

    /**
     * True when this squad belongs to a {@link DroneHub}-created hub. Routed to
     * {@code GoapDroneBehavior} by the replan dispatch — drone squads have
     * their own action library (encircle-on-engage, sector-on-patrol) and
     * skip the infantry/mech action sets entirely.
     */
    public boolean isDroneSquad() {
        return droneHubId != 0L;
    }

    /**
     * Assign this squad a {@code HOLD_NODE} objective for {@code node} — used by
     * compound garrison drops so the deboarded squad is born holding (runs
     * {@code GarrisonCompound} from its first tick). Keeps the command-layer
     * {@link ObjectiveAssignment} construction off the air/deboard path.
     */
    public void assignHoldNode(TacticalNode node) {
        this.assignedObjective = ObjectiveAssignment.holdNode(this.id, node);
    }
}
