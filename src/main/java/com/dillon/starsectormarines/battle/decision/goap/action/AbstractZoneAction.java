package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Planner;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;

/**
 * Shared base for the per-zone squad-push actions
 * ({@link EnterZone}, {@link ClearZone}, {@link HoldZone}). All three carry a
 * {@link #targetZoneId}, take no part in the backward-chaining planner (empty
 * preconditions/effects, flat cost), and obey one common rule: <b>a member
 * isn't performing the action until it is actually inside the target zone.</b>
 *
 * <p>That rule is the zone-entry precondition shared across the family —
 * {@link #memberInZone}. A member standing outside {@code targetZoneId} must
 * first consolidate <em>into</em> it via {@link #advanceIntoZone} rather than
 * engaging from an adjacent room: without this, a member with a firing
 * solution across a portal sits at the threshold trading fire forever, and a
 * compound capture stays permanently contested while the squad fights the
 * room from the doorway instead of pushing in.
 *
 * <p>The one tactical knob between members of the family is whether the
 * advance <em>halts</em> at contact. {@link EnterZone} is the approach step:
 * it halts so an engagement-tier goal (a morale break routing to BreakContact)
 * can preempt before the squad commits. {@link ClearZone}/{@link HoldZone} are
 * commitment steps: the squad is taking the room, so they push through contact
 * while firing suppressively instead of freezing at the threshold. That's the
 * {@code haltOnContact} flag, not duplicated movement code.
 */
abstract class AbstractZoneAction implements Action {

    /**
     * Minimum sim-seconds since the last squad replan before a contact-halt is
     * allowed to force another. Without this throttle a pinned squad would
     * replan every tick — the planner would re-pick the same approach action
     * (no morale break, no other relevant goal), the marine would halt again,
     * replan would fire again, ad infinitum. 1.0s gives a clean
     * one-replan-per-contact-event under the 2.0s base period. Only consulted
     * on the {@code haltOnContact} path.
     */
    protected static final float CONTACT_HALT_REPLAN_THROTTLE = 1.0f;

    protected final int targetZoneId;

    protected AbstractZoneAction(int targetZoneId) {
        this.targetZoneId = targetZoneId;
    }

    public final int targetZoneId() { return targetZoneId; }

    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    /** True iff {@code member}'s logical cell lies inside {@link #targetZoneId}. */
    protected final boolean memberInZone(Entity member, BattleView sim) {
        return sim.getZoneGraph().zoneIdAt(sim.world().cellX(member.entityId), sim.world().cellY(member.entityId)) == targetZoneId;
    }

    /**
     * Advance a member that is standing <em>outside</em> the target zone toward
     * {@code (destX, destY)} — an interior cell — taking opportunistic shots at
     * a visible in-range enemy while it moves. Caller invokes this only when
     * {@link #memberInZone} is false and then returns {@link ActionStatus#RUNNING}.
     *
     * <p>Target handling mirrors the in-zone engage paths: a stale-but-alive
     * target that's no longer worth pursuing is dropped and re-picked via
     * {@link TacticalScoring#findBestTarget}, so a member that fixated on the
     * approach doesn't walk past fresh shooters.
     *
     * @param haltOnContact when true (approach semantics), a visible in-range
     *        enemy stops the advance so the member fights in place and the
     *        squad replan is accelerated (throttled) to let an engagement-tier
     *        goal take over; when false (commitment semantics), the member
     *        keeps pushing into the zone while firing.
     */
    protected final void advanceIntoZone(Entity member, Squad squad, BattleControl sim,
                                         int destX, int destY, boolean haltOnContact) {
        Entity target = sim.targetOf(member);
        if (target == null || !sim.getTacticalScoring().shouldKeepPursuing(member, target)) {
            target = sim.getTacticalScoring().findBestTarget(member);
            sim.world().setTargetId(member.entityId, Entity.idOf(target));
        }

        boolean inContact = false;
        if (target != null) {
            float d = TacticalScoring.cellDistance(sim.world().cellX(member.entityId), sim.world().cellY(member.entityId),
                    sim.world().cellX(target.entityId), sim.world().cellY(target.entityId));
            boolean visible = sim.getGrid().hasLineOfSight(sim.world().cellX(member.entityId), sim.world().cellY(member.entityId),
                    sim.world().cellX(target.entityId), sim.world().cellY(target.entityId));
            inContact = d <= sim.world().attackRange(member.entityId) && visible;
        }

        if (sim.world().cooldownTimer(member.entityId) <= 0f) {
            if (inContact) {
                sim.fireShot(member, target, haltOnContact ? FireStance.STANCED : FireStance.MOVING);
                sim.world().setCooldownTimer(member.entityId, member.attackCooldown);
                member.beginBurst(sim.world(), target);
            } else {
                // Opportunistic return fire while advancing. The pursuit target
                // is out of range/LoS (or absent) — across the open approach
                // that left members marching past enemies they could hit,
                // eating shots without returning any. Fire on the nearest enemy
                // actually in range and LoS, MOVING stance, without halting or
                // touching the pursuit target: the squad still commits to the
                // zone push, the trigger just stops it being a sitting duck.
                // beginBurst keys off a separate burst target, so the follow-up
                // burst tracks the enemy we shot, not the pursuit target.
                Entity opportune = sim.getTacticalScoring().closestEnemyInAttackRange(member);
                if (opportune != null) {
                    sim.fireShot(member, opportune, FireStance.MOVING);
                    sim.world().setCooldownTimer(member.entityId, member.attackCooldown);
                    member.beginBurst(sim.world(), opportune);
                }
            }
        }

        if (haltOnContact && inContact) {
            if (!Paths.isEmpty(sim.world().path(member.entityId))) sim.clearPath(member);
            sim.world().setMoveProgress(member.entityId, 0f);
            sim.world().setRenderPos(member.entityId, sim.world().cellX(member.entityId), sim.world().cellY(member.entityId));
            if (squad.timeSinceReplan >= CONTACT_HALT_REPLAN_THROTTLE) {
                squad.timeSinceReplan = Planner.REPLAN_PERIOD;
            }
            return;
        }

        if (sim.world().moveProgress(member.entityId) == 0f) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    sim.world().cellX(member.entityId), sim.world().cellY(member.entityId), destX, destY, sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);
    }

    /**
     * Representative interior cell of {@code zone} — the middle entry in its
     * flat cell-index array. {@code cellIndices} is in detection order (roughly
     * scan-line), so the middle usually lands deep in the zone rather than at a
     * portal edge. Returns {@code null} for a missing or empty zone.
     */
    protected static int[] interiorCell(NavigationZone zone, NavigationGrid grid) {
        if (zone == null) return null;
        int[] cells = zone.getCellIndices();
        if (cells.length == 0) return null;
        int pick = cells[cells.length / 2];
        return new int[]{ pick % grid.getWidth(), pick / grid.getWidth() };
    }

    /** {@link #interiorCell} resolved from a zone id against the live graph/grid. */
    protected static int[] interiorCellOf(int zoneId, BattleView sim) {
        return interiorCell(sim.getZoneGraph().zoneById(zoneId), sim.getGrid());
    }
}
