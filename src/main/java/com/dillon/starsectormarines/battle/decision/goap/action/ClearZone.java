package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.decision.goap.SquadRouteGoalProvider;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.command.compound.ObjectiveArea;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;

/**
 * <b>Squad posture: clear a zone.</b> Stays inside {@link #targetZoneId} and
 * engages enemies until the zone reads clear via
 * {@link ZoneQueries#zoneClear}. First member to observe the zone-clear
 * condition reports {@link ActionStatus#SUCCESS}, advancing the squad to
 * the next plan step (either {@link EnterZone} for the next zone in the
 * sweep, or the plan completes if this was the last).
 *
 * <p>Engagement piggybacks on {@link TacticalScoring#findBestTarget} for the
 * default Stage 1 picker behavior (crowding, threat-density, weapon affinity)
 * with one Story K twist: if the picker chooses a target outside
 * {@code targetZoneId}, the action prefers an in-zone target as the
 * engagement focus instead. The squad doesn't strictly stop firing at the
 * out-of-zone target — Story K's "doesn't pursue enemies across portals"
 * intent is enforced by the success condition (zone-clear), not by
 * filtering shots: if the squad already has LOS+range on an enemy through
 * a portal, taking the shot costs nothing and adds to suppression. What we
 * stop is <em>chasing</em> across portals, which falls out of the per-member
 * pathing — members don't path-advance toward an out-of-zone target.
 *
 * <p>Per-zone parameterized like {@link EnterZone}; emitted only by
 * {@link com.dillon.starsectormarines.battle.infantry.SecureObjectiveZone}'s
 * custom plan. Empty preconditions/effects: not used by the backward-chaining
 * planner.
 */
public final class ClearZone extends AbstractZoneAction implements SquadRouteGoalProvider {

    private final ClearZoneDecisions decisions = new ClearZoneDecisions(64);
    /** Bound within the zone, or null for the whole zone. */
    private final ObjectiveArea area;
    /** Where a member outside {@link #area} goes in, inside it and in the zone; unused without an area. */
    private final int areaCellX, areaCellY;

    @Override
    public Goal squadRouteGoal(Squad squad, BattleView sim) {
        int[] interior = entryCell(sim, squad);
        return interior == null ? null : new Goal(interior[0], interior[1]);
    }

    public ClearZone(int targetZoneId) {
        this(targetZoneId, null, -1, -1);
    }

    /**
     * Clears only the part of {@code targetZoneId} inside {@code area}, entering at {@code (cellX, cellY)}, which must
     * lie in both. A compound's clear: its room may have merged into the street.
     */
    public ClearZone(int targetZoneId, ObjectiveArea area, int cellX, int cellY) {
        super(targetZoneId);
        this.area = area;
        this.areaCellX = cellX;
        this.areaCellY = cellY;
    }

    @Override protected ObjectiveArea area() { return area; }

    private int[] entryCell(BattleView sim, Squad squad) {
        if (area != null) return new int[]{areaCellX, areaCellY};
        return interiorCellOf(targetZoneId, sim, squad);
    }

    @Override public String name() { return "ClearZone[" + targetZoneId + "]"; }

    @Override
    public ActionStatus execute(long member, Squad squad, BattleControl sim) {
        long target = sim.targetOf(member);
        ClearZoneDecisions.Entry decision = retainedDecision(member, target, squad, sim);
        // A live hostile in this zone is an exact NOT-clear witness. No cached
        // absence can finish the action: losing the witness immediately requires
        // the original current-roster predicate, including non-combatants.
        boolean hostileWitness = liveHostileInZone(target, squad, sim) || decision != null;
        // Quick exit when the zone reads clear of everything this squad fights.
        // Checked from anywhere (global predicate) so an all-outside squad —
        // e.g. the lone in-zone member died — still advances the plan rather
        // than deadlocking behind the zone-entry gate below.
        if (!hostileWitness && ZoneQueries.areaClearOfHostiles(targetZoneId, area, squad.faction, sim)) {
            return ActionStatus.SUCCESS;
        }

        // Zone-entry rule (AbstractZoneAction): a member standing outside the
        // zone consolidates in — firing suppressively while it moves — before
        // it engages. Don't clear the room from the doorway.
        if (!memberInZone(member, sim)) {
            int[] interior = entryCell(sim, squad);
            if (interior != null) {
                advanceIntoZone(member, squad, sim, interior[0], interior[1], false);
            }
            return ActionStatus.RUNNING;
        }

        // Refresh target: prefer an in-zone enemy. An out-of-zone fixation
        // counts as "drop and re-pick" — without this, members that lose LOS
        // to the in-zone enemy and grab a visible adjacent-zone target via
        // findBestTarget get stuck (line 90 short-circuits movement, and
        // shouldKeepPursuing keeps voting yes because no closer visible
        // alternative appears). pickZoneTarget prefers the closest visible
        // in-zone enemy; when none is visible (squad in zone but blocked from
        // the surviving enemy by a wall), it returns the closest in-zone
        // enemy unconditionally so we close through the
        // wall via the pathfinder rather than freezing. Falls back to the
        // squad-aware best-target only when zone has no live enemies (rare —
        // zoneClear normally short-circuits first).
        if (decision != null) {
            target = decision.target();
            TickInnerProfile profile = TickInnerProfile.currentIfBound();
            if (profile != null) profile.recordCount(decision.negative()
                    ? TickInnerProfile.Bucket.CLEAR_ZONE_NEGATIVE_REUSE
                    : TickInnerProfile.Bucket.CLEAR_ZONE_DECISION_REUSE, 1);
        } else if (!liveHostileInZone(target, squad, sim)
                || !sim.getTacticalScoring().shouldKeepPursuing(member, target)) {
            long inZone = pickZoneTarget(member, sim);
            target = inZone != 0L ? inZone : sim.getTacticalScoring().findBestTarget(member);
            sim.world().setTargetId(member, target);
        }
        if (target == 0L || !sim.world().isAlive(target)) return ActionStatus.RUNNING;
        if (decision == null && liveHostileInZone(target, squad, sim)) {
            rememberDecision(member, target, squad, sim, false);
        }

        float dist = TacticalScoring.cellDistance(sim.world().x(member), sim.world().y(member),
                sim.world().x(target), sim.world().y(target));
        boolean inRange = dist <= sim.world().attackRange(member);
        boolean clearShot = sim.getTacticalScoring().hasClearShot(member, target);
        if (decision != null && decision.negative()) {
            if (!inRange || !clearShot) {
                // Failed searches retain only a retry deadline and enemy identity,
                // not the combat target or a negative shot verdict. Preserve the
                // failed-search hold: an old path may belong to another purpose.
                return ActionStatus.RUNNING;
            }
            // A newly legal shot is useful immediately, even during backoff.
            // lookup rejected any different externally assigned nonzero target.
            sim.world().setTargetId(member, target);
            rememberDecision(member, target, squad, sim, false);
        }
        if (inRange && clearShot) {
            sim.combat().setFireIntent(member, target, FireStance.STANCED, false);
            // Movement gate, not a fire gate — FiringSystem owns the cooldown
            // check for the shot itself. This read only preserves the old
            // control flow: on the ready tick the member stands to shoot;
            // between shots it keeps creeping toward a better firing position
            // (the block below).
            if (sim.combat().cooldownTimer(member) <= 0f) {
                return ActionStatus.RUNNING;
            }
        }

        // Out of range / no LOS — close on the target IFF the target is in
        // the zone we're clearing. Out-of-zone targets we don't pursue —
        // that's Story K's "doesn't push into rooms it's not clearing" rule.
        if (!inTarget(sim.world().cellX(target), sim.world().cellY(target), sim)) {
            return ActionStatus.RUNNING;
        }
        if (sim.movement().mayRepath(member)) {
            int[] dest = sim.getTacticalScoring().selectFiringPosition(
                    member, target, squad, sim.getSimTickIndex(), false);
            // Settled members can already occupy the retained firing cell. Do
            // not construct a trivial route every cooldown tick. An active old
            // route must still be replaced, and moving routes keep their normal
            // periodic topology repair.
            if (dest != null && sim.movement().settled(member)
                    && sim.world().x(member) == dest[0] + 0.5f
                    && sim.world().y(member) == dest[1] + 0.5f) {
                return ActionStatus.RUNNING;
            }
            int[] path = dest == null ? GridPathfinder.EMPTY_PATH
                    : GridPathfinder.findPath(sim.getGrid(),
                            sim.world().cellX(member), sim.world().cellY(member),
                            dest[0], dest[1], sim.getOccupancyMap());
            if (path.length == 0) {
                sim.getTacticalScoring().forgetFiringPosition(member);
                // No reachable firing cell for this in-zone target: either
                // findFiringPosition found nothing, OR it returned a LOS+range
                // cell the pathfinder can't route to. The latter is the trap —
                // its stage-1 search doesn't verify reachability, so a target
                // walled off within the flood-zone (zones ignore edges, the
                // pathfinder honors them — [[zone_graph_ignores_edges]]) yields
                // a cell on the wrong side of a wall. Setting that empty path
                // would pin the unit in place forever (the SQ-96 garrison
                // freeze, here on the assault path). Drop the target instead —
                // pickZoneTarget re-acquires after a bounded retry (Story K stays
                // satisfied: we only ever clear within zone). A zone whose every
                // survivor is unreachable idles here pending a make-passage /
                // breach action — the documented limitation, surfaced via
                // SquadStateDumper.clearZoneReachability.
                sim.world().setTargetId(member, 0L);
                rememberDecision(member, target, squad, sim, true);
                return ActionStatus.RUNNING;
            }
            sim.setPath(member, path);
        }
        sim.advanceMovement(member);
        return ActionStatus.RUNNING;
    }

    private boolean liveHostileInZone(long target, Squad squad, BattleView sim) {
        // Validate identity before accessing its position: a previous target may
        // have died or been released between unit-update phases.
        return target != 0L && sim.liveUnitIndexOf(target) >= 0 && sim.world().isAlive(target)
                && squad.faction.hostileTo(sim.identity().faction(target))
                && inTarget(sim.world().cellX(target), sim.world().cellY(target), sim);
    }

    private ClearZoneDecisions.Entry retainedDecision(long member, long target, Squad squad, BattleView sim) {
        ClearZoneDecisions.Entry entry = decisions.lookup(member, squad, squad.assignmentForExecution(),
                squad.currentGoal, sim.getGrid().topologyRevision(), sim.getSimTickIndex(),
                sim.world().cellX(member), sim.world().cellY(member), target, sim.world().attackRange(member));
        if (entry != null && (!liveHostileInZone(entry.target(), squad, sim)
                || entry.targetMoved(sim.world().cellX(entry.target()), sim.world().cellY(entry.target())))) {
            decisions.forget(entry);
            return null;
        }
        return entry;
    }

    private void rememberDecision(long member, long target, Squad squad, BattleView sim, boolean negative) {
        decisions.remember(member, squad, squad.assignmentForExecution(), squad.currentGoal,
                sim.getGrid().topologyRevision(), sim.getSimTickIndex(),
                sim.world().cellX(member), sim.world().cellY(member), target,
                sim.world().cellX(target), sim.world().cellY(target), sim.world().attackRange(member), negative);
    }

    /**
     * Closest visible in-zone enemy, or closest in-zone enemy when none is
     * visible. A farther candidate cannot beat an existing visible winner,
     * so distance rules it out before paying for its LOS ray. Both winners
     * are maintained in one dense-roster pass, retaining the first exact tie.
     * No sight-radius cap or snapshot-position assumption narrows the zone.
     */
    long pickZoneTarget(long self, BattleView sim) {
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        long started = profile == null ? 0L : System.nanoTime();
        TargetScanWork work = profile == null ? null : new TargetScanWork();
        try {
            return pickZoneTarget(self, sim, work);
        } finally {
            if (profile != null) {
                profile.record(TickInnerProfile.Bucket.CLEAR_ZONE_TARGET_SELECT,
                        System.nanoTime() - started);
                profile.recordCount(TickInnerProfile.Bucket.CLEAR_ZONE_TARGET_VISIT, work.visits);
                profile.recordCount(TickInnerProfile.Bucket.CLEAR_ZONE_TARGET_RAY, work.rays);
            }
        }
    }

    private static final class TargetScanWork {
        int visits;
        int rays;
    }

    private long pickZoneTarget(long self, BattleView sim, TargetScanWork work) {
        Faction selfFaction = sim.identity().faction(self);
        float selfX = sim.world().x(self), selfY = sim.world().y(self);
        int selfCellX = sim.world().cellX(self), selfCellY = sim.world().cellY(self);
        long nearest = 0L, visible = 0L;
        float nearestDistance = Float.MAX_VALUE, visibleDistance = Float.MAX_VALUE;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            if (work != null) work.visits++;
            long other = sim.liveUnitAt(i);
            if (sim.isRiding(other)) continue;
            if (!selfFaction.hostileTo(sim.identity().faction(other))) continue;
            if (!sim.identity().type(other).combatant) continue;
            float distance = TacticalScoring.cellDistance(selfX, selfY,
                    sim.world().x(other), sim.world().y(other));
            if (!(distance < visibleDistance)) continue;
            int otherX = sim.world().cellX(other), otherY = sim.world().cellY(other);
            if (!inTarget(otherX, otherY, sim)) continue;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = other;
            }
            if (work != null) work.rays++;
            if (sim.getGrid().hasLineOfSight(selfCellX, selfCellY, otherX, otherY)) {
                visibleDistance = distance;
                visible = other;
            }
        }
        return visible != 0L ? visible : nearest;
    }
}
