package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.perception.NoiseDetection;
import com.dillon.starsectormarines.battle.perception.NoiseEvent;
import com.dillon.starsectormarines.battle.perception.NoiseEventBus;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.sim.CombatService;
import com.dillon.starsectormarines.battle.sim.IdentityService;
import com.dillon.starsectormarines.battle.sim.VisionService;
import com.dillon.starsectormarines.battle.sim.World;

import java.util.List;
import java.util.function.LongPredicate;

/**
 * Serial tick consumer that refreshes {@link SquadAlertLevel} on every
 * registered squad. Promotion rules:
 * <ul>
 *   <li><b>ENGAGED</b> — any living squadmate has LOS to an alive enemy
 *       combatant. {@code timeSinceContact} resets to zero and every visible
 *       hostile refreshes the squad's identified contact belief.</li>
 *   <li><b>SUSPICIOUS</b> — no current LOS, but a squadmate is in a
 *       fall-back (recently hit) or the squad detected hostile noise. The
 *       squad converges on the last known or localized enemy cell so a patrol
 *       does not keep walking its route obliviously.</li>
 *   <li><b>UNAWARE</b> — neither of the above. After
 *       {@link Squad#ENGAGED_DECAY_SECONDS} of no contact an ENGAGED squad
 *       drops to SUSPICIOUS, and after another
 *       {@link Squad#SUSPICIOUS_DECAY_SECONDS} a SUSPICIOUS squad drops to
 *       UNAWARE. The decay lets garrisons hold their state across brief
 *       duck-behind-cover moments and patrols commit to investigation
 *       before giving up.</li>
 * </ul>
 *
 * <p>Empty squads (all members dead) are left in their last state — the GC
 * cleans them up on save; the next tick's behaviors won't dispatch because
 * no member is alive.
 *
 * <p>Single per-tick pass that fills in every squad's derived aggregates
 * (alive member count, centroid, alert level) and drives the
 * ENGAGED/SUSPICIOUS/UNAWARE state machine. Structured as a units-outer
 * pass that posts each alive unit's contribution to its squad in one walk:
 * increments the alive count, accumulates centroid, notes if any member is
 * in fall-back, and records every hostile combatant visible to any member.
 * Candidate discovery uses the tick-start unit spatial index and each
 * observer's vision range; only nearby candidates pay faction, range, and
 * line-of-sight checks.
 *
 * <p>Hostile noises are heard through a one-tick mailbox, independent of
 * line of sight. They create imperfect localized bearings and may refresh an
 * identified contact only when the producer safely exposes a direct source.
 *
 * <p>Sibling to other {@code *System} tick consumers — single {@link #tick}
 * entry point, all dependencies constructor-injected.
 */
public final class SquadAlertSystem {

    /**
     * Story A: cell range within which an enemy is considered "in the kill
     * zone" for a garrison squad's ambush trigger. The kill zone is intentionally
     * close — garrisons want the first shot to land, not to give away their
     * post at extreme range. {@code 8} cells matches typical rifle effective
     * range while staying short of the renderer's visibility horizon.
     */
    public static final int KILL_ZONE_RANGE_CELLS = 8;

    /**
     * Story A: consecutive sim ticks of LOS to a close enemy required before
     * the kill-zone gate trips. At {@code TICK_DT = 1/30 sec}, 6 ticks ≈ 0.2s
     * of stable sight — short enough to feel reflexive, long enough that a
     * single transient LoS frame (target dancing through a doorway) doesn't
     * spring the ambush prematurely.
     */
    public static final int KILL_ZONE_LOS_TICKS_THRESHOLD = 6;

    /**
     * Story A backstop: cumulative sim-seconds a garrison squad must take
     * LoS-confirmed incoming fire before the kill-zone gate is forced open,
     * regardless of whether the shooter ever entered the 8-cell kill zone.
     * Prevents a long-LOS attacker (mech with 30-40-cell range, distant
     * sniper) from chipping a holdsFireUntilKillZone garrison that's
     * forbidden to fire back. {@code 3s} is the user-feel sweet spot:
     * long enough that a one-off pot shot doesn't blow a planned ambush,
     * short enough that the squad isn't a free target for the rest of the
     * engagement.
     */
    public static final float KILL_ZONE_AMBUSH_BLOWN_SECONDS = 3.0f;

    /**
     * Conservative padding for a cell-distance vision query over an index of
     * true positions. Two points in cells whose coordinates are in range can
     * differ by up to sqrt(2) more than the cell-coordinate distance.
     */
    private static final float VISION_GATHER_PADDING = 1.414214f;

    private final NavigationService navigation;
    private final UnitRosterService roster;
    private final ShotService shots;
    private final NoiseEventBus noiseEvents;
    /** Serial-pass scratch reused by every member query; grows only to the largest local crowd. */
    private final LongBucket awarenessCandidates = new LongBucket();
    /** Serial-pass scratch reused by hostile-shot endpoint queries. */
    private final LongBucket underFireCandidates = new LongBucket();
    /**
     * Belief expiry test handed to every squad at tick start. Held as a field
     * so the per-squad loop does not mint a capture each tick.
     */
    private final LongPredicate beliefIdentityResolves = this::identityResolves;

    public SquadAlertSystem(NavigationService navigation,
                            UnitRosterService roster,
                            ShotService shots,
                            NoiseEventBus noiseEvents) {
        this.navigation = navigation;
        this.roster = roster;
        this.shots = shots;
        this.noiseEvents = noiseEvents;
    }

    /**
     * Whether a remembered contact identity still names something a squad can
     * act on — a live roster unit, or a body its carrier says is still
     * reachable.
     *
     * <p>This used to spell that out as "a live unit, or a targetable convoy
     * vehicle", which is the same sentence {@code BattleSimulation.resolveUnit}
     * spelled out, and both of them silently dropped an aircraft the moment one
     * became a body. The list is now one question asked of the carrier
     * registry, so the next carrier is remembered without anybody editing this.
     */
    boolean identityResolves(long unitId) {
        return roster.bodies().isTargetable(unitId);
    }

    public void tick(float dt, int simTick) {
        List<NoiseEvent> pendingNoises = noiseEvents.drain();
        NavigationGrid grid = navigation.getGrid();
        World world = roster.world();
        VisionService vision = roster.vision();
        IdentityService identity = roster.identity();
        CombatService combat = roster.combat();
        UnitSpatialIndex unitIndex = navigation.getUnitIndex();
        long[] dense = roster.denseArray();
        int liveCount = roster.liveCount();

        // Per-tick transient flags. Boxed onto Squad to keep allocation out of
        // the hot path; reset at the top so a dead squad's leftover flags
        // don't leak into next tick.
        for (Squad squad : roster.getSquads()) {
            squad._directContactStartedThisTick = false;
            squad.beginBeliefTick(dt, simTick, beliefIdentityResolves);
            squad.aliveMembers = 0;
            squad.centroidMembers = 0;
            squad.centroidX = 0f;
            squad.centroidY = 0f;
            squad._engagedThisTick = false;
            squad._suspiciousThisTick = false;
            squad._contactDoctrineChangedThisTick = false;
            squad._alertLevelChangedThisTick = false;
            squad._killZoneSightedThisTick = false;
            squad._underFireAtLosLastTick = squad._underFireAtLosThisTick;
            squad._underFireAtLosThisTick = false;
        }

        // Pass 1: accumulate squad aggregates + per-squad engagement LoS.
        // For garrison squads (holdsFireUntilKillZone), also drive the kill-zone
        // LOS-stability counter — incremented when any squadmate has LOS to an
        // enemy within KILL_ZONE_RANGE_CELLS this tick, reset to 0 otherwise.
        // We track this independently of the early-exit ENGAGED flag because
        // the ENGAGED scan stops at the first sighted enemy, and we need the
        // tighter "close + visible" predicate for the kill-zone gate.
        for (int i = 0; i < liveCount; i++) {
            long u = dense[i];
            if (!roster.squad().hasSquad(u)) continue;
            Squad squad = roster.getSquad(roster.squad().squadId(u));
            if (squad == null) continue;
            if (roster.isRiding(u)) {
                // Aboard something: still one of the squad's living members, but
                // it holds no ground, sees nothing and contributes no centroid.
                squad.aliveMembers++;
                continue;
            }
            float uAir = vision.airLosRadius(u);
            float uX = world.x(u);
            float uY = world.y(u);
            float visionRange = Math.max(0f, vision.visionRange(u));
            squad.aliveMembers++;
            squad.centroidMembers++;
            squad.centroidX += uX;
            squad.centroidY += uY;
            if (world.fallbackTimer(u) > 0f) squad._suspiciousThisTick = true;

            // One spatial query feeds both direct awareness and the tighter
            // garrison kill-zone gate. Their ranges remain independent.
            boolean needsKillZone = squad.holdsFireUntilKillZone
                    && !squad._killZoneSightedThisTick;
            float awarenessGatherRange = visionRange > 0f
                    ? visionRange + VISION_GATHER_PADDING
                    : 0f;
            float gatherRange = needsKillZone
                    ? Math.max(awarenessGatherRange, KILL_ZONE_RANGE_CELLS)
                    : awarenessGatherRange;
            unitIndex.gather(uX, uY, gatherRange, awarenessCandidates);
            int uCellX = world.cellX(u);
            int uCellY = world.cellY(u);
            float visionRangeSquared = visionRange * visionRange;
            for (int j = 0; j < awarenessCandidates.size; j++) {
                long other = awarenessCandidates.ids[j];
                if (!squad.faction.hostileTo(identity.faction(other))) continue;
                if (!identity.type(other).combatant) continue;
                float dx = world.x(other) - uX;
                float dy = world.y(other) - uY;
                float distanceSquared = dx * dx + dy * dy;
                int otherCellX = world.cellX(other);
                int otherCellY = world.cellY(other);
                int cellDx = otherCellX - uCellX;
                int cellDy = otherCellY - uCellY;
                float cellDistanceSquared = (float) cellDx * cellDx
                        + (float) cellDy * cellDy;
                boolean inKillZone = needsKillZone
                        && distanceSquared <= KILL_ZONE_RANGE_CELLS * KILL_ZONE_RANGE_CELLS;
                // A convoy chassis is a body in the index but not a row in the
                // dense roster, so it has no slot to key the once-per-tick
                // dedupe on. It is observed through the slotless overload
                // instead; there are a handful of them, so the repeated put a
                // second squadmate causes is cheaper than a parallel table.
                int otherRosterSlot = UnitRosterService.INVALID_INDEX;
                boolean needsObservation = visionRange > 0f
                        && cellDistanceSquared <= visionRangeSquared;
                if (needsObservation) {
                    otherRosterSlot = roster.indexOf(other);
                    needsObservation = otherRosterSlot == UnitRosterService.INVALID_INDEX
                            || !squad.observedDirectlyOnTick(otherRosterSlot, simTick);
                }
                if (!inKillZone && !needsObservation) continue;
                if (!TacticalScoring.canSeePair(grid, uCellX, uCellY, otherCellX, otherCellY,
                        uAir, vision.targetAirLosRadius(other))) continue;
                if (inKillZone) {
                    squad._killZoneSightedThisTick = true;
                    needsKillZone = false;
                }
                if (needsObservation) {
                    squad._engagedThisTick = true;
                    if (otherRosterSlot == UnitRosterService.INVALID_INDEX) {
                        squad.observeDirectContact(other, otherCellX, otherCellY, simTick);
                    } else {
                        squad.observeDirectContact(other, otherCellX, otherCellY,
                                simTick, otherRosterSlot);
                    }
                }
            }
        }

        List<ShotEvent> activeShots = shots.getActiveShots();
        for (Squad squad : roster.getSquads()) {
            if (squad.centroidMembers <= 0) continue;
            float listenerX = squad.centroidX / squad.centroidMembers;
            float listenerY = squad.centroidY / squad.centroidMembers;
            for (NoiseEvent event : pendingNoises) {
                if (event.sourceFaction() == squad.faction) continue;
                NoiseDetection.Detection detection = NoiseDetection.detect(
                        event, squad.id, listenerX, listenerY, grid);
                if (detection == null) continue;
                squad._suspiciousThisTick = true;
                squad.observeAudibleBearing(detection.cellX(), detection.cellY(),
                        simTick, detection.confidence(), event.sourceUnitId(), event.kind());
                if (event.hasIdentifiedSource()) {
                    squad.observeAudioContact(event.sourceUnitId(), detection.cellX(),
                            detection.cellY(), simTick, detection.confidence());
                }
            }
        }

        // Per-tick under-fire-at-LoS scan. Produces two facts from one walk:
        // the squad flag consumed by WorldStateBuilder, and the individual's own
        // incoming-fire pressure and bearing on COMBAT. It runs before the GOAP
        // replan pass so infantry can treat incoming fire as an immediate plan
        // interrupt rather than waiting for the two-second cadence, and before
        // the integral-system sweep so a suit deciding whether to raise a screen
        // reads this tick's fire rather than last tick's.
        // Garrison squads additionally consume the same flag below for the
        // legacy timeUnderSustainedFire kill-zone diagnostic/override.
        // Shots are the sparse side of this relationship, so query the unit
        // index around each endpoint instead of testing every squadmate against
        // every active shot. The squad flag deduplicates squads reached by
        // multiple members or shots; the per-unit pressure deliberately does
        // not, because two rounds is twice the fire one round is.
        //
        // The 2-cell gather is what makes a near miss count. A round that kills
        // the marine beside you is fire you are under, and the individual signal
        // says so without anyone having to aggregate it back up to the fireteam.
        if (!activeShots.isEmpty()) {
            for (ShotEvent shot : activeShots) {
                unitIndex.gather(shot.toX, shot.toY, 2f, underFireCandidates);
                int fromCellX = (int) Math.floor(shot.fromX);
                int fromCellY = (int) Math.floor(shot.fromY);
                for (int i = 0; i < underFireCandidates.size; i++) {
                    long u = underFireCandidates.ids[i];
                    if (shot.shooterFaction == identity.faction(u)) continue;
                    Squad squad = roster.squad().hasSquad(u)
                            ? roster.getSquad(roster.squad().squadId(u)) : null;
                    boolean squadNeedsFlag = squad != null && !squad._underFireAtLosThisTick;
                    boolean unitTakesPressure = combat.has(u);
                    if (!squadNeedsFlag && !unitTakesPressure) continue;
                    int uCellX = world.cellX(u);
                    int uCellY = world.cellY(u);
                    if (!grid.hasLineOfSight(uCellX, uCellY, fromCellX, fromCellY)) continue;
                    if (squadNeedsFlag) squad._underFireAtLosThisTick = true;
                    if (unitTakesPressure) {
                        combat.recordIncomingFire(u, fromCellX, fromCellY, simTick);
                    }
                }
            }
        }

        // Finalize: divide centroids, apply alert-state transitions.
        for (Squad squad : roster.getSquads()) {
            SquadAlertLevel previousAlert = squad.alertLevel;
            squad.publishBeliefSnapshot();
            int formationMembers = squad.centroidMembers;
            long controlled = squad.controlledMemberId();
            if (controlled != 0L && roster.isAliveById(controlled)
                    && !roster.isRiding(controlled) && formationMembers > 1) {
                // Perception above still includes the player's physical presence. Autonomous
                // movement below follows the remainder of the squad, including its acting leader.
                squad.centroidX -= world.x(controlled);
                squad.centroidY -= world.y(controlled);
                formationMembers--;
            }
            if (formationMembers > 0) {
                squad.centroidX /= formationMembers;
                squad.centroidY /= formationMembers;
            }
            // Story A: garrison kill-zone LOS hysteresis. Increments when any
            // squadmate sighted a close enemy this tick; resets to 0 when no
            // close-LOS sighting was recorded. Only garrison squads keep this
            // counter — non-garrison squads have holdsFireUntilKillZone=false
            // and never get a sighting flagged (skipped in pass 1).
            if (squad.holdsFireUntilKillZone) {
                if (squad._killZoneSightedThisTick) {
                    if (squad.killZoneLosTicks < KILL_ZONE_LOS_TICKS_THRESHOLD) {
                        squad.killZoneLosTicks++;
                    }
                } else {
                    squad.killZoneLosTicks = 0;
                }
                // Story A backstop: accumulate sim-time under LoS-confirmed
                // incoming fire. Monotonic — never resets within a battle so
                // the ambush-blown threshold is a one-way door. Once it
                // crosses KILL_ZONE_AMBUSH_BLOWN_SECONDS, WorldStateBuilder
                // forces the kill-zone predicate true regardless of enemy
                // proximity.
                if (squad._underFireAtLosThisTick) {
                    squad.timeUnderSustainedFire += dt;
                }
            }
            if (squad._engagedThisTick) {
                squad.alertLevel = SquadAlertLevel.ENGAGED;
                squad.timeSinceContact = 0f;
            } else if (squad._suspiciousThisTick) {
                if (squad.alertLevel != SquadAlertLevel.ENGAGED) {
                    squad.alertLevel = SquadAlertLevel.SUSPICIOUS;
                }
                squad.timeSinceContact = 0f;
            } else {
                squad.timeSinceContact += dt;
                if (squad.alertLevel == SquadAlertLevel.ENGAGED
                        && squad.timeSinceContact >= Squad.ENGAGED_DECAY_SECONDS) {
                    squad.alertLevel = SquadAlertLevel.SUSPICIOUS;
                    // SQ-82 fix: at ENGAGED→SUSPICIOUS, the squad has gone
                    // ENGAGED_DECAY_SECONDS with no squadmate-LOS to anything.
                    // shouldKeepPursuing returns true for an invisible target
                    // when no closer visible enemy exists, so the stale target
                    // can outlive contact indefinitely (SQ-82: 8 marines kept
                    // walking toward a defender they hadn't seen in 13s,
                    // pinned against unreachable cells inside the target
                    // zone). Drop targets here; next behavior tick re-picks
                    // via findBestTarget — no LOS to anything = null target,
                    // which is the correct posture for a squad in SUSPICIOUS.
                    clearSquadMemberTargets(squad.id, roster, dense, liveCount);
                } else if (squad.alertLevel == SquadAlertLevel.SUSPICIOUS
                        && squad.timeSinceContact >= Squad.ENGAGED_DECAY_SECONDS + Squad.SUSPICIOUS_DECAY_SECONDS) {
                    squad.alertLevel = SquadAlertLevel.UNAWARE;
                    squad.clearAudibleBearing();
                    squad.lastSeenEnemyX = -1;
                    squad.lastSeenEnemyY = -1;
                    // Belt-and-braces: any target re-acquired during
                    // SUSPICIOUS (via a transient LOS flicker that didn't
                    // bump back to ENGAGED) shouldn't survive into UNAWARE.
                    clearSquadMemberTargets(squad.id, roster, dense, liveCount);
                }
            }
            squad._alertLevelChangedThisTick = squad.alertLevel != previousAlert;
        }
    }

    /**
     * Clears {@code u.getTargetId()} for every alive squadmate of {@code squadId}.
     * Called at the ENGAGED→SUSPICIOUS (and SUSPICIOUS→UNAWARE) transitions
     * so a stale target id — one {@link TacticalScoring#shouldKeepPursuing
     * shouldKeepPursuing} happily keeps alive past LOS — doesn't drag the
     * squad toward an enemy they last saw seconds ago. Action {@code execute}
     * paths null-check the resolved target (they already cope with the
     * reprio-on-hit clear), so the next behavior tick repicks via
     * {@link TacticalScoring#findBestTarget findBestTarget} or holds null if
     * nobody's visible.
     */
    private void clearSquadMemberTargets(int squadId, UnitRosterService roster, long[] dense, int liveCount) {
        World world = roster.world();
        for (int i = 0; i < liveCount; i++) {
            if (roster.squad().hasSquad(dense[i]) && roster.squad().squadId(dense[i]) == squadId)
                world.setTargetId(dense[i], 0L);
        }
    }
}
