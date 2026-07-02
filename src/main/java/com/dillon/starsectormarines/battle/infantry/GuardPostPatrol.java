package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Entity;
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
 *       firing cell clamped to an <em>odds-scaled</em> leash around the post.
 *       At even-or-better odds the leash is the full box, so the squad fights
 *       forward to the perimeter against a lone attacker; as it gets
 *       outnumbered the leash collapses toward a tight defensive ring on the
 *       post's cover and turret, giving ground to the strongpoint rather than
 *       trading shots out on the edge where it gets walked off the line. The
 *       post's own live turret(s) count toward the defenders, so the guard
 *       holds forward while the turret stands and falls back once a second
 *       attacker push tips the ratio.</li>
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

    /** Cells beyond the box, ≈ one rifle range, that the odds tally reaches — so the squad senses a build-up massing just outside its box, not only the foes already inside it. */
    private static final float SENSE_MARGIN = 24f;
    /** Floor the engage leash collapses to when heavily outnumbered — a tight ring on the post's cover + turret, the squad's last-ditch defensive footing. */
    private static final float DEFENSIVE_RING = 6f;

    private final int anchorX;
    private final int anchorY;
    /** Half-extent of the patrol box, in cells, around the post anchor. */
    private final int radius;

    /**
     * Engage leash for this tick, shrunk from {@link #radius} toward
     * {@link #DEFENSIVE_RING} as the squad gets outnumbered (see
     * {@link #computeLeash}). Volatile + leader-gated: the leader recomputes
     * the local force tally once per tick and the squad's other members read
     * its value (they share the same anchor-centred counts, so one gather per
     * squad-tick suffices). {@code -1} until first computed.
     */
    private volatile float cachedLeashRadius = -1f;

    /** Box-sample waypoint strategy, widened to re-roll a waypoint that has drifted outside the box (shared {@code patrolWaypointX/Y} isn't reset on a posture switch). */
    private final PatrolMotion.WaypointSource waypointSource;

    public GuardPostPatrol(int anchorX, int anchorY, int radius) {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.radius = Math.max(1, radius);
        this.waypointSource = new PatrolMotion.WaypointSource() {
            @Override public int[] next(Entity member, Squad squad, BattleView sim) {
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
    public ActionStatus execute(Entity member, Squad squad, BattleControl sim) {
        // Retreating to a new post — every member walks home regardless of alert.
        // updateSquadFallback drops the flag once everyone arrives.
        if (squad.fallbackInProgress) {
            boolean hasHome = sim.home().hasHome(member.entityId);
            int homeX = hasHome ? sim.home().homeCellX(member.entityId) : sim.world().cellX(member.entityId);
            int homeY = hasHome ? sim.home().homeCellY(member.entityId) : sim.world().cellY(member.entityId);
            return returnTo(member, sim, homeX, homeY);
        }

        Entity target = sim.targetOf(member);
        if (target == null || !sim.getTacticalScoring().shouldKeepPursuing(member, target)) {
            target = sim.getTacticalScoring().findBestTarget(member);
            sim.world().setTargetId(member.entityId, Entity.idOf(target));
        }
        if (target != null) {
            return engage(member, target, squad, sim);
        }

        if (squad.alertLevel == SquadAlertLevel.SUSPICIOUS
                && squad.lastSeenEnemyX >= 0 && squad.lastSeenEnemyY >= 0) {
            return investigateClamped(member, sim, squad);
        }

        return PatrolMotion.advance(member, squad, sim, waypointSource, /*fireWhilePatrolling*/ true);
    }

    /**
     * Engage {@code target}, leashed to the odds-scaled ring around the post
     * (see {@link #effectiveLeash}). Fires in place when in range + LOS
     * <em>and</em> still inside the leash; otherwise repositions to a firing
     * cell within the leash of the post anchor, switching to any other
     * leash-engageable enemy if the current target can't be reached from
     * inside it. A member that has drifted beyond a now-shrunk leash gives
     * ground — it stops trading shots from the perimeter and paths back toward
     * the strongpoint even if it could still fire from where it stands.
     */
    private ActionStatus engage(Entity member, Entity target, Squad squad, BattleControl sim) {
        float leash = effectiveLeash(member, squad, sim);

        float dist = TacticalScoring.cellDistance(sim.world().cellX(member.entityId), sim.world().cellY(member.entityId),
                sim.world().cellX(target.entityId), sim.world().cellY(target.entityId));
        boolean inRange = dist <= sim.world().attackRange(member.entityId);
        boolean visible = sim.getGrid().hasLineOfSight(sim.world().cellX(member.entityId), sim.world().cellY(member.entityId),
                sim.world().cellX(target.entityId), sim.world().cellY(target.entityId));
        boolean withinLeash = TacticalScoring.cellDistance(anchorX, anchorY,
                sim.world().cellX(member.entityId), sim.world().cellY(member.entityId)) <= leash;

        if (inRange && visible && withinLeash) {
            sim.combat().setFireIntent(member.entityId, Entity.idOf(target), FireStance.STANCED, false);
            PatrolMotion.hold(member, sim);
            return ActionStatus.RUNNING;
        }

        int[] firingPos = sim.getTacticalScoring().findFiringPositionWithin(
                member, target, anchorX, anchorY, leash);
        if (firingPos == null) {
            Entity alt = sim.getTacticalScoring().findEngageableEnemyWithin(
                    member, anchorX, anchorY, leash);
            if (alt != null) {
                sim.world().setTargetId(member.entityId, Entity.idOf(alt));
                target = alt;
                firingPos = sim.getTacticalScoring().findFiringPositionWithin(
                        member, target, anchorX, anchorY, leash);
            }
        }
        if (firingPos == null || (firingPos[0] == sim.world().cellX(member.entityId) && firingPos[1] == sim.world().cellY(member.entityId))) {
            PatrolMotion.hold(member, sim);
            return ActionStatus.RUNNING;
        }
        PatrolMotion.moveToward(member, sim, firingPos[0], firingPos[1]);
        return ActionStatus.RUNNING;
    }

    /**
     * This tick's engage leash. The leader recomputes the odds-scaled value
     * once per tick (one force tally per squad); other members read its cached
     * result, and the first call before the leader has run computes inline.
     * Leaderless squads fall back to the last cached value (or a one-off
     * compute), which is harmless — a stale leash for a few ticks just delays
     * the give-ground response.
     */
    private float effectiveLeash(Entity member, Squad squad, BattleControl sim) {
        if (member.entityId == squad.leaderId || cachedLeashRadius < 0f) {
            cachedLeashRadius = computeLeash(squad, sim);
        }
        return cachedLeashRadius;
    }

    /**
     * Maps the local enemy:defender ratio around the post to a leash between
     * {@link #DEFENSIVE_RING} (heavily outnumbered → collapse onto the post)
     * and the full box {@link #radius} (even-or-better odds → fight forward to
     * the perimeter). Defenders include the post's live turret(s) and any
     * nearby friendly squads, so a lone attacker faces squad + turret and the
     * guard holds forward; a second attacker push tips the ratio and pulls it
     * back. No contesting enemy in sensing range → the full box.
     *
     * <p><b>Perception debt (story 15).</b> The {@code foes} tally is an
     * <em>omniscient</em> ground-truth read — the same "squads react to enemies
     * they have no business knowing about" class as
     * {@link com.dillon.starsectormarines.battle.decision.goap.world.WorldStateBuilder}'s
     * {@code HAS_LOS_TO_TARGET}. Acceptable interim while the perception layer is
     * parked; swap to {@code squad.believedEnemies} (Tier B) when it lands so a
     * post can't collapse against an unseen flank. The {@code friends} tally
     * does <em>not</em> carry this debt — a faction legitimately knows its own
     * positions (the {@code friendly_influence} channel).
     */
    private float computeLeash(Squad squad, BattleControl sim) {
        Faction enemy = squad.faction == Faction.MARINE ? Faction.DEFENDER : Faction.MARINE;
        float sense = radius + SENSE_MARGIN;
        TacticalScoring scoring = sim.getTacticalScoring();
        // PERCEPTION-DEBT (story 15): omniscient enemy read; swap to
        // squad.believedEnemies when Tier B belief ships.
        int foes = scoring.countCombatantsWithin(enemy, anchorX, anchorY, sense);
        if (foes == 0) return radius;
        int friends = scoring.countCombatantsWithin(squad.faction, anchorX, anchorY, sense);
        float factor = Math.min(1f, friends / (float) foes);
        float inner = Math.min(DEFENSIVE_RING, radius);
        return inner + (radius - inner) * factor;
    }

    private ActionStatus investigateClamped(Entity member, BattleControl sim, Squad squad) {
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
    private int[] nextWaypoint(Entity member, Squad squad, BattleView sim) {
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

    private static ActionStatus returnTo(Entity member, BattleControl sim, int tx, int ty) {
        if (sim.world().cellX(member.entityId) == tx && sim.world().cellY(member.entityId) == ty) {
            PatrolMotion.hold(member, sim);
            return ActionStatus.RUNNING;
        }
        PatrolMotion.moveToward(member, sim, tx, ty);
        return ActionStatus.RUNNING;
    }
}
