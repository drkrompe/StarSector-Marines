package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.ai.SquadAlertLevel;
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

    public Squad(int id, Faction faction) {
        this.id = id;
        this.faction = faction;
    }
}
