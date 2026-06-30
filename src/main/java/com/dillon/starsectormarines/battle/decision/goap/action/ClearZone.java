package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

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
public final class ClearZone extends AbstractZoneAction {

    public ClearZone(int targetZoneId) {
        super(targetZoneId);
    }

    @Override public String name() { return "ClearZone[" + targetZoneId + "]"; }

    @Override
    public ActionStatus execute(Entity member, Squad squad, BattleControl sim) {
        // Quick exit when the zone reads clear for this squad's enemy faction.
        // Checked from anywhere (global predicate) so an all-outside squad —
        // e.g. the lone in-zone member died — still advances the plan rather
        // than deadlocking behind the zone-entry gate below.
        Faction enemy = enemyOf(squad.faction);
        if (ZoneQueries.zoneClear(targetZoneId, enemy, sim)) {
            return ActionStatus.SUCCESS;
        }

        // Zone-entry rule (AbstractZoneAction): a member standing outside the
        // zone consolidates in — firing suppressively while it moves — before
        // it engages. Don't clear the room from the doorway.
        if (!memberInZone(member, sim)) {
            int[] interior = interiorCellOf(targetZoneId, sim);
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
        // alternative appears). pickInZoneTarget tries LOS first; when no
        // in-zone enemy is visible (squad in zone but blocked from the
        // surviving enemy by a wall), pickNearestInZoneEnemy returns the
        // closest in-zone enemy unconditionally so we close through the
        // wall via the pathfinder rather than freezing. Falls back to the
        // squad-aware best-target only when zone has no live enemies (rare —
        // zoneClear normally short-circuits first).
        Entity target = sim.targetOf(member);
        boolean targetOutOfZone = target != null
                && sim.getZoneGraph().zoneIdAt(sim.world().cellX(target.entityId), sim.world().cellY(target.entityId)) != targetZoneId;
        if (target == null
                || targetOutOfZone
                || !sim.getTacticalScoring().shouldKeepPursuing(member, target)) {
            Entity inZone = pickInZoneTarget(member, sim);
            if (inZone == null) inZone = pickNearestInZoneEnemy(member, sim);
            target = inZone != null ? inZone : sim.getTacticalScoring().findBestTarget(member);
            sim.world().setTargetId(member.entityId, Entity.idOf(target));
        }
        if (target == null) return ActionStatus.RUNNING;

        float dist = TacticalScoring.cellDistance(sim.world().cellX(member.entityId), sim.world().cellY(member.entityId),
                sim.world().cellX(target.entityId), sim.world().cellY(target.entityId));
        boolean inRange = dist <= sim.world().attackRange(member.entityId);
        boolean visible = sim.getGrid().hasLineOfSight(sim.world().cellX(member.entityId), sim.world().cellY(member.entityId),
                sim.world().cellX(target.entityId), sim.world().cellY(target.entityId));
        if (inRange && visible && sim.world().cooldownTimer(member.entityId) <= 0f) {
            sim.fireShot(member, target);
            sim.world().setCooldownTimer(member.entityId, sim.world().attackCooldown(member.entityId));
            member.beginBurst(sim.world(), target);
            return ActionStatus.RUNNING;
        }

        // Out of range / no LOS — close on the target IFF the target is in
        // the zone we're clearing. Out-of-zone targets we don't pursue —
        // that's Story K's "doesn't push into rooms it's not clearing" rule.
        if (sim.getZoneGraph().zoneIdAt(sim.world().cellX(target.entityId), sim.world().cellY(target.entityId)) != targetZoneId) {
            return ActionStatus.RUNNING;
        }
        if (sim.world().moveProgress(member.entityId) == 0f) {
            int[] dest = sim.getTacticalScoring().findFiringPosition(member, target);
            int[] path = dest == null ? GridPathfinder.EMPTY_PATH
                    : GridPathfinder.findPath(sim.getGrid(),
                            sim.world().cellX(member.entityId), sim.world().cellY(member.entityId),
                            dest[0], dest[1], sim.getOccupancyMap());
            if (path.length == 0) {
                // No reachable firing cell for this in-zone target: either
                // findFiringPosition found nothing, OR it returned a LOS+range
                // cell the pathfinder can't route to. The latter is the trap —
                // its stage-1 search doesn't verify reachability, so a target
                // walled off within the flood-zone (zones ignore edges, the
                // pathfinder honors them — [[zone_graph_ignores_edges]]) yields
                // a cell on the wrong side of a wall. Setting that empty path
                // would pin the unit in place forever (the SQ-96 garrison
                // freeze, here on the assault path). Drop the target instead —
                // pickInZoneTarget re-acquires next tick (Story K stays
                // satisfied: we only ever clear within zone). A zone whose every
                // survivor is unreachable idles here pending a make-passage /
                // breach action — the documented limitation, surfaced via
                // SquadStateDumper.clearZoneReachability.
                sim.world().setTargetId(member.entityId, 0L);
                return ActionStatus.RUNNING;
            }
            sim.setPath(member, path);
        }
        sim.advanceMovement(member);
        return ActionStatus.RUNNING;
    }

    /**
     * Scans enemies of the squad's faction whose cell sits in the target zone
     * and returns the closest visible one. Linear over the unit list — fine
     * given typical squad-tick budgets. Returns null when no in-zone enemy
     * exists (caller falls back to {@link #pickNearestInZoneEnemy}, then
     * to the normal squad-aware picker).
     */
    private Entity pickInZoneTarget(Entity self, BattleView sim) {
        Faction enemy = enemyOf(self.faction);
        Entity best = null;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            Entity other = sim.liveUnitAt(i);
            if (other.faction != enemy) continue;
            if (!other.type.combatant) continue;
            if (sim.getZoneGraph().zoneIdAt(sim.world().cellX(other.entityId), sim.world().cellY(other.entityId)) != targetZoneId) continue;
            if (!sim.getGrid().hasLineOfSight(sim.world().cellX(self.entityId), sim.world().cellY(self.entityId), sim.world().cellX(other.entityId), sim.world().cellY(other.entityId))) continue;
            float d = TacticalScoring.cellDistance(sim.world().cellX(self.entityId), sim.world().cellY(self.entityId), sim.world().cellX(other.entityId), sim.world().cellY(other.entityId));
            if (d < bestDist) {
                bestDist = d;
                best = other;
            }
        }
        return best;
    }

    /**
     * Closest alive enemy combatant in the target zone, ignoring LOS. The
     * fallback for {@link #pickInZoneTarget} when a wall blocks LOS to every
     * survivor in the zone — without this, the squad picks an out-of-zone
     * target via findBestTarget and freezes (the action refuses to chase
     * out-of-zone targets, see Story K). Linear scan; one zone-clearing
     * squad pays it once per posture tick.
     *
     * <p>The squad ↔ zone-clear flood share the same walkability rules but
     * the zone graph ignores edges ([[zone_graph_ignores_edges]]) — so a
     * non-LOS in-zone enemy may still be unreachable. The pathfinder either
     * routes around (members close, eventually gain LOS) or returns an
     * empty path (next tick re-evaluates; if persistently empty the squad
     * is geometrically stuck — surfaced via clearZoneReachability in
     * {@link com.dillon.starsectormarines.battle.ui.debug.SquadStateDumper}).
     */
    private Entity pickNearestInZoneEnemy(Entity self, BattleView sim) {
        Faction enemy = enemyOf(self.faction);
        Entity best = null;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            Entity other = sim.liveUnitAt(i);
            if (other.faction != enemy) continue;
            if (!other.type.combatant) continue;
            if (sim.getZoneGraph().zoneIdAt(sim.world().cellX(other.entityId), sim.world().cellY(other.entityId)) != targetZoneId) continue;
            float d = TacticalScoring.cellDistance(sim.world().cellX(self.entityId), sim.world().cellY(self.entityId), sim.world().cellX(other.entityId), sim.world().cellY(other.entityId));
            if (d < bestDist) {
                bestDist = d;
                best = other;
            }
        }
        return best;
    }

    /**
     * Marine ↔ defender flip used to scope the zone-clear predicate. Civilians
     * never count; turrets carry whichever faction owns the emplacement and
     * fall under the normal enemy bucket via faction equality.
     */
    private static Faction enemyOf(Faction f) {
        return f == Faction.MARINE ? Faction.DEFENDER : Faction.MARINE;
    }
}
