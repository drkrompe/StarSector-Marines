package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * <b>Squad posture: patrol a turret emplacement's bounding box.</b> Custom-plan
 * action emitted by {@link GuardPost} for a defender squad linked to a live
 * {@link com.dillon.starsectormarines.battle.turret.DefensePost} (turrets still
 * standing). The open-terrain counterpart to {@link GarrisonPatrol}: where the
 * compound garrison rotates through indoor rooms, a turret emplacement sits on
 * a beach / port / embankment with no rooms to rotate through, so this wanders
 * an axis-aligned bounding box centred on the post anchor with half-extent
 * {@link Squad#patrolRadius} (the per-tier
 * {@link com.dillon.starsectormarines.battle.turret.DefensePostKind#patrolRadius}).
 * The radius encodes the tier's flavour: a tight LIGHT/ARTILLERY box reads as
 * "sit on the post", a wide LARGE box as a loose perimeter sweep.
 *
 * <p>Replaces {@link HoldPost}'s static 6-cell leash for posted turret squads —
 * they now drift across the emplacement rather than standing on one cell.
 * {@link com.dillon.starsectormarines.battle.turret.TurretDemolitionSystem}
 * clears {@link Squad#defensePost} once every turret on the post is down, at
 * which point {@link GuardPost} drops back to {@link HoldPost}.
 *
 * <p>Perpetual (always {@link ActionStatus#RUNNING}); the squad-level replan
 * swaps goals when reality changes (morale break → {@code SurviveContact};
 * chokepoint geometry → {@code GarrisonAmbush}). Branches each tick:
 *
 * <ul>
 *   <li><b>FALLBACK</b> — the squad is retreating to a new post: every member
 *       walks to its home cell regardless of alert (mirrors {@link HoldPost}).</li>
 *   <li><b>ENGAGE</b> — a target is acquired: engage it, repositioning to a
 *       firing cell clamped to the patrol box (so the squad masses on the
 *       emplacement and never chases an intruder out across the map). The full
 *       box is the engagement leash — same extent as the quiet wander.</li>
 *   <li><b>INVESTIGATE</b> — no target, SUSPICIOUS with a last-seen cell: lean
 *       toward the noise, clamped to the box.</li>
 *   <li><b>QUIET</b> — no target, unaware: round a squad-scoped waypoint sampled
 *       inside the box via {@link PatrolMotion} (this action supplies the
 *       box-sample {@link PatrolMotion.WaypointSource}, including the
 *       out-of-box staleness check), firing opportunistically at anything
 *       visible.</li>
 * </ul>
 *
 * <p>Not a singleton: each plan carries the box (anchor + radius) resolved from
 * the squad's post at replan time. The rotation cursor lives on the
 * {@link Squad}, surviving the plan rebuild.
 */
public final class GuardPostPatrol implements Action {

    /** Max attempts to roll a random walkable cell inside the box before giving up this tick and dwelling on the current waypoint. */
    private static final int WAYPOINT_SAMPLE_ATTEMPTS = 16;

    private final int anchorX;
    private final int anchorY;
    /** Half-extent of the patrol box, in cells, around the post anchor. */
    private final int radius;

    /** Box-sample waypoint strategy, widened to re-roll a waypoint that has drifted outside the box (shared {@code patrolWaypointX/Y} isn't reset on a posture switch). */
    private final PatrolMotion.WaypointSource waypointSource;

    public GuardPostPatrol(int anchorX, int anchorY, int radius) {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.radius = Math.max(1, radius);
        this.waypointSource = new PatrolMotion.WaypointSource() {
            @Override public int[] next(Unit member, Squad squad, BattleView sim) {
                return nextWaypoint(member, squad, sim);
            }
            @Override public boolean needsNew(Squad squad) {
                return needsNewWaypoint(squad);
            }
        };
    }

    @Override public String name() { return "GuardPostPatrol"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleControl sim) {
        // Retreating to a new post — every member walks home regardless of alert.
        // updateSquadFallback drops the flag once everyone arrives.
        if (squad.fallbackInProgress) {
            int homeX = member.homeCellX >= 0 ? member.homeCellX : member.getCellX();
            int homeY = member.homeCellY >= 0 ? member.homeCellY : member.getCellY();
            return returnTo(member, sim, homeX, homeY);
        }

        Unit target = sim.targetOf(member);
        if (target == null || !sim.getTacticalScoring().shouldKeepPursuing(member, target)) {
            target = sim.getTacticalScoring().findBestTarget(member);
            member.setTarget(target);
        }
        if (target != null) {
            return engage(member, target, sim);
        }

        if (squad.alertLevel == SquadAlertLevel.SUSPICIOUS
                && squad.lastSeenEnemyX >= 0 && squad.lastSeenEnemyY >= 0) {
            return investigateClamped(member, sim, squad);
        }

        return PatrolMotion.advance(member, squad, sim, waypointSource, /*fireWhilePatrolling*/ true);
    }

    /**
     * Engage {@code target}, leashed to the patrol box. Fires in place when in
     * range + LOS; otherwise repositions to a firing cell within {@link #radius}
     * of the post anchor, switching to any other box-engageable enemy if the
     * current target can't be reached from inside the box. Mirrors
     * {@link HoldPost}'s engagement, anchored to the post box rather than the
     * member's tight home ring.
     */
    private ActionStatus engage(Unit member, Unit target, BattleControl sim) {
        if (member.getCooldownTimer() > 0f) {
            member.setCooldownTimer(member.getCooldownTimer() - BattleSimulation.TICK_DT);
        }

        float dist = TacticalScoring.cellDistance(member.getCellX(), member.getCellY(),
                target.getCellX(), target.getCellY());
        boolean inRange = dist <= member.getAttackRange();
        boolean visible = sim.getGrid().hasLineOfSight(member.getCellX(), member.getCellY(),
                target.getCellX(), target.getCellY());

        if (inRange && visible) {
            if (member.getCooldownTimer() <= 0f) {
                sim.fireShot(member, target);
                member.setCooldownTimer(member.attackCooldown);
                member.beginBurst(target);
            }
            PatrolMotion.hold(member, sim);
            return ActionStatus.RUNNING;
        }

        int[] firingPos = sim.getTacticalScoring().findFiringPositionWithin(
                member, target, anchorX, anchorY, radius);
        if (firingPos == null) {
            Unit alt = sim.getTacticalScoring().findEngageableEnemyWithin(
                    member, anchorX, anchorY, radius);
            if (alt != null) {
                member.setTarget(alt);
                target = alt;
                firingPos = sim.getTacticalScoring().findFiringPositionWithin(
                        member, target, anchorX, anchorY, radius);
            }
        }
        if (firingPos == null || (firingPos[0] == member.getCellX() && firingPos[1] == member.getCellY())) {
            PatrolMotion.hold(member, sim);
            return ActionStatus.RUNNING;
        }
        PatrolMotion.moveToward(member, sim, firingPos[0], firingPos[1]);
        return ActionStatus.RUNNING;
    }

    private ActionStatus investigateClamped(Unit member, BattleControl sim, Squad squad) {
        int tx = squad.lastSeenEnemyX;
        int ty = squad.lastSeenEnemyY;
        float distFromAnchor = TacticalScoring.cellDistance(anchorX, anchorY, tx, ty);
        if (distFromAnchor > radius) {
            float scale = radius / distFromAnchor;
            tx = anchorX + Math.round((tx - anchorX) * scale);
            ty = anchorY + Math.round((ty - anchorY) * scale);
        }
        PatrolMotion.moveToward(member, sim, tx, ty);
        return ActionStatus.RUNNING;
    }

    /**
     * A fresh random walkable cell inside the box, distinct from the current
     * waypoint. Null when no roll lands a new cell this tick — the caller keeps
     * the current waypoint and dwells, re-rolling when the dwell next expires.
     */
    private int[] nextWaypoint(Unit member, Squad squad, BattleView sim) {
        NavigationGrid grid = sim.getGrid();
        int span = radius * 2 + 1;
        for (int i = 0; i < WAYPOINT_SAMPLE_ATTEMPTS; i++) {
            int cx = anchorX + member.rng.nextInt(span) - radius;
            int cy = anchorY + member.rng.nextInt(span) - radius;
            if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) continue;
            if (cx == squad.patrolWaypointX && cy == squad.patrolWaypointY) continue;
            return new int[]{cx, cy};
        }
        return null;
    }

    /**
     * Needs a fresh waypoint roll: none set, arrived at the current one, or —
     * crucially — the current waypoint is <em>outside the box</em>.
     * {@code patrolWaypointX/Y} is shared squad state written by every patrol
     * posture and isn't reset on a posture switch, so a squad entering this
     * action could inherit a far waypoint. Re-rolling (or holding when the roll
     * fails) instead of walking to it keeps the squad inside its box.
     */
    private boolean needsNewWaypoint(Squad squad) {
        if (!PatrolMotion.hasValidWaypoint(squad)) return true;
        if (PatrolMotion.squadHasArrived(squad)) return true;
        return Math.abs(squad.patrolWaypointX - anchorX) > radius
                || Math.abs(squad.patrolWaypointY - anchorY) > radius;
    }

    private static ActionStatus returnTo(Unit member, BattleControl sim, int tx, int ty) {
        if (member.getCellX() == tx && member.getCellY() == ty) {
            PatrolMotion.hold(member, sim);
            return ActionStatus.RUNNING;
        }
        PatrolMotion.moveToward(member, sim, tx, ty);
        return ActionStatus.RUNNING;
    }
}
