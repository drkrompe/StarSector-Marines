package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.sim.VisionService;
import com.dillon.starsectormarines.battle.sim.World;

import java.util.List;

/**
 * Stateless tick consumer that refreshes {@link SquadAlertLevel} on every
 * registered squad. Promotion rules:
 * <ul>
 *   <li><b>ENGAGED</b> — any living squadmate has LOS to an alive enemy
 *       combatant. {@code timeSinceContact} resets to zero and
 *       {@code lastSeenEnemy} captures that enemy's cell.</li>
 *   <li><b>SUSPICIOUS</b> — no current LOS, but a squadmate is in a
 *       fall-back (recently hit). The squad converges on the last known
 *       enemy cell so a patrol that gets sniped doesn't keep walking its
 *       route obliviously.</li>
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
 * in fall-back, and (only if the squad isn't already tagged ENGAGED this
 * tick) runs a LoS scan for visible enemies. Engaged squads short-circuit
 * subsequent LoS scans for the same squad within this tick.
 *
 * <p>The audible-gunfire promotion only runs for squads that finished the
 * first pass still un-engaged. Final state transitions are applied once
 * per squad at the end.
 *
 * <p>Sibling to other {@code *System} tick consumers — single {@link #tick}
 * entry point, all dependencies constructor-injected.
 */
public final class SquadAlertSystem {

    /** Cell radius around a squadmate inside which an enemy shot's origin counts as "audible gunfire" and promotes the squad to SUSPICIOUS. Bigger than weapon ranges so a distant firefight pulls patrols in to investigate — that's the whole point. */
    public static final float GUNFIRE_ALERT_RADIUS = 18f;

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

    private final NavigationService navigation;
    private final UnitRosterService roster;
    private final ShotService shots;

    public SquadAlertSystem(NavigationService navigation,
                            UnitRosterService roster,
                            ShotService shots) {
        this.navigation = navigation;
        this.roster = roster;
        this.shots = shots;
    }

    public void tick(float dt) {
        NavigationGrid grid = navigation.getGrid();
        World world = roster.world();
        VisionService vision = roster.vision();
        Entity[] dense = roster.denseArray();
        int liveCount = roster.liveCount();

        // Per-tick transient flags. Boxed onto Squad to keep allocation out of
        // the hot path; reset at the top so a dead squad's leftover flags
        // don't leak into next tick.
        for (Squad squad : roster.getSquads()) {
            squad.aliveMembers = 0;
            squad.centroidX = 0f;
            squad.centroidY = 0f;
            squad._engagedThisTick = false;
            squad._suspiciousThisTick = false;
            squad._killZoneSightedThisTick = false;
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
            Entity u = dense[i];
            if (!roster.squad().hasSquad(u.entityId)) continue;
            Squad squad = roster.getSquad(roster.squad().squadId(u.entityId));
            if (squad == null) continue;
            float uAir = vision.airLosRadius(u.entityId);
            squad.aliveMembers++;
            squad.centroidX += world.cellX(u.entityId);
            squad.centroidY += world.cellY(u.entityId);
            if (world.fallbackTimer(u.entityId) > 0f) squad._suspiciousThisTick = true;

            // Kill-zone LOS scan for garrison squads only. Looks for ANY
            // squadmate with LOS to a close enemy combatant — a single
            // qualifying sighting per tick increments the counter for the
            // squad. The scan is keyed on holdsFireUntilKillZone so non-
            // garrison squads pay nothing.
            if (squad.holdsFireUntilKillZone && !squad._killZoneSightedThisTick) {
                int uCellX = world.cellX(u.entityId);
                int uCellY = world.cellY(u.entityId);
                for (int j = 0; j < liveCount; j++) {
                    Entity other = dense[j];
                    if (other.faction == squad.faction) continue;
                    if (!other.type.combatant) continue;
                    int otherCellX = world.cellX(other.entityId);
                    int otherCellY = world.cellY(other.entityId);
                    int dx = otherCellX - uCellX;
                    int dy = otherCellY - uCellY;
                    if (dx * dx + dy * dy > KILL_ZONE_RANGE_CELLS * KILL_ZONE_RANGE_CELLS) continue;
                    if (!TacticalScoring.canSeePair(grid, uCellX, uCellY, otherCellX, otherCellY,
                            uAir, vision.airLosRadius(other.entityId))) continue;
                    squad._killZoneSightedThisTick = true;
                    break;
                }
            }

            // LoS scan only if no squadmate has tripped ENGAGED yet this tick —
            // one engaged squadmate is enough to commit the whole squad.
            if (squad._engagedThisTick) continue;
            int uCellX = world.cellX(u.entityId);
            int uCellY = world.cellY(u.entityId);
            for (int j = 0; j < liveCount; j++) {
                Entity other = dense[j];
                if (other.faction == squad.faction) continue;
                if (!other.type.combatant) continue;
                int otherCellX = world.cellX(other.entityId);
                int otherCellY = world.cellY(other.entityId);
                if (!TacticalScoring.canSeePair(grid, uCellX, uCellY, otherCellX, otherCellY,
                        uAir, vision.airLosRadius(other.entityId))) continue;
                squad._engagedThisTick = true;
                squad.lastSeenEnemyX = otherCellX;
                squad.lastSeenEnemyY = otherCellY;
                break;
            }
        }

        // Audible-gunfire promotion runs only for not-yet-engaged squads.
        // Iterate units once more, but only do the shot scan for squads that
        // still need promoting — the early-skip means engaged squads pay
        // nothing here.
        List<ShotEvent> activeShots = shots.getActiveShots();
        if (!activeShots.isEmpty()) {
            for (int i = 0; i < liveCount; i++) {
                Entity u = dense[i];
                if (!roster.squad().hasSquad(u.entityId)) continue;
                Squad squad = roster.getSquad(roster.squad().squadId(u.entityId));
                if (squad == null || squad._engagedThisTick || squad._suspiciousThisTick) continue;
                int uCellX = world.cellX(u.entityId);
                int uCellY = world.cellY(u.entityId);
                for (ShotEvent shot : activeShots) {
                    if (shot.shooterFaction == squad.faction) continue;
                    float dx = shot.fromX - (uCellX + 0.5f);
                    float dy = shot.fromY - (uCellY + 0.5f);
                    if (dx * dx + dy * dy <= GUNFIRE_ALERT_RADIUS * GUNFIRE_ALERT_RADIUS) {
                        squad._suspiciousThisTick = true;
                        squad.lastSeenEnemyX = Math.round(shot.fromX);
                        squad.lastSeenEnemyY = Math.round(shot.fromY);
                        break;
                    }
                }
            }
        }

        // Story A backstop: per-tick under-fire-at-LoS scan for garrison
        // squads with the kill-zone gate set. Mirrors WorldStateBuilder's
        // evalUnderFireAtLos predicate but runs every tick (not per replan)
        // so timeUnderSustainedFire accumulates accurately across the
        // 2-second replan window. Scoped to holdsFireUntilKillZone garrisons
        // because the field is only consumed by their gate override.
        if (!activeShots.isEmpty()) {
            for (int i = 0; i < liveCount; i++) {
                Entity u = dense[i];
                if (!roster.squad().hasSquad(u.entityId)) continue;
                Squad squad = roster.getSquad(roster.squad().squadId(u.entityId));
                if (squad == null || !squad.holdsFireUntilKillZone) continue;
                if (squad._underFireAtLosThisTick) continue;
                int uCellX = world.cellX(u.entityId);
                int uCellY = world.cellY(u.entityId);
                for (ShotEvent shot : activeShots) {
                    if (shot.shooterFaction == squad.faction) continue;
                    float dx = shot.toX - (uCellX + 0.5f);
                    float dy = shot.toY - (uCellY + 0.5f);
                    // Same 2-cell-squared "shot landed near me" gate the
                    // predicate evaluator uses — keeps the two paths in sync.
                    if (dx * dx + dy * dy > 4f) continue;
                    int fromCellX = (int) Math.floor(shot.fromX);
                    int fromCellY = (int) Math.floor(shot.fromY);
                    if (grid.hasLineOfSight(uCellX, uCellY, fromCellX, fromCellY)) {
                        squad._underFireAtLosThisTick = true;
                        break;
                    }
                }
            }
        }

        // Finalize: divide centroids, apply alert-state transitions.
        for (Squad squad : roster.getSquads()) {
            if (squad.aliveMembers > 0) {
                squad.centroidX /= squad.aliveMembers;
                squad.centroidY /= squad.aliveMembers;
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
                    squad.lastSeenEnemyX = -1;
                    squad.lastSeenEnemyY = -1;
                    // Belt-and-braces: any target re-acquired during
                    // SUSPICIOUS (via a transient LOS flicker that didn't
                    // bump back to ENGAGED) shouldn't survive into UNAWARE.
                    clearSquadMemberTargets(squad.id, roster, dense, liveCount);
                }
            }
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
    private void clearSquadMemberTargets(int squadId, UnitRosterService roster, Entity[] dense, int liveCount) {
        World world = roster.world();
        for (int i = 0; i < liveCount; i++) {
            if (roster.squad().hasSquad(dense[i].entityId) && roster.squad().squadId(dense[i].entityId) == squadId)
                world.setTargetId(dense[i].entityId, 0L);
        }
    }
}
