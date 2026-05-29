package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
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
    public ActionStatus execute(Unit member, Squad squad, BattleSimulation sim) {
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
        Unit target = sim.targetOf(member);
        boolean targetOutOfZone = target != null
                && sim.getZoneGraph().zoneIdAt(target.getCellX(), target.getCellY()) != targetZoneId;
        if (target == null
                || targetOutOfZone
                || !sim.getTacticalScoring().shouldKeepPursuing(member, target)) {
            Unit inZone = pickInZoneTarget(member, sim);
            if (inZone == null) inZone = pickNearestInZoneEnemy(member, sim);
            target = inZone != null ? inZone : sim.getTacticalScoring().findBestTarget(member);
            member.setTarget(target);
        }
        if (target == null) return ActionStatus.RUNNING;

        float dist = TacticalScoring.cellDistance(member.getCellX(), member.getCellY(),
                target.getCellX(), target.getCellY());
        boolean inRange = dist <= member.getAttackRange();
        boolean visible = sim.getGrid().hasLineOfSight(member.getCellX(), member.getCellY(),
                target.getCellX(), target.getCellY());
        if (inRange && visible && member.getCooldownTimer() <= 0f) {
            sim.fireShot(member, target);
            member.setCooldownTimer(member.attackCooldown);
            member.beginBurst(target);
            return ActionStatus.RUNNING;
        }

        // Out of range / no LOS — close on the target IFF the target is in
        // the zone we're clearing. Out-of-zone targets we don't pursue —
        // that's Story K's "doesn't push into rooms it's not clearing" rule.
        if (sim.getZoneGraph().zoneIdAt(target.getCellX(), target.getCellY()) != targetZoneId) {
            return ActionStatus.RUNNING;
        }
        if (member.getMoveProgress() == 0f) {
            int[] dest = sim.getTacticalScoring().findFiringPosition(member, target);
            if (dest == null) {
                // No reachable firing or vantage cell for this in-zone target.
                // Drop the target — pickInZoneTarget will get a fresh shot next
                // tick (Story K stays satisfied: we only ever clear within zone,
                // and an unreachable in-zone target shouldn't pin the unit).
                member.setTargetId(0L);
                return ActionStatus.RUNNING;
            }
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    member.getCellX(), member.getCellY(), dest[0], dest[1], sim.getOccupancyMap()));
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
    private Unit pickInZoneTarget(Unit self, BattleView sim) {
        Faction enemy = enemyOf(self.faction);
        Unit best = null;
        float bestDist = Float.MAX_VALUE;
        for (Unit other : sim.getUnits()) {
            if (!other.isAlive()) continue;
            if (other.faction != enemy) continue;
            if (!other.type.combatant) continue;
            if (sim.getZoneGraph().zoneIdAt(other.getCellX(), other.getCellY()) != targetZoneId) continue;
            if (!sim.getGrid().hasLineOfSight(self.getCellX(), self.getCellY(), other.getCellX(), other.getCellY())) continue;
            float d = TacticalScoring.cellDistance(self.getCellX(), self.getCellY(), other.getCellX(), other.getCellY());
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
    private Unit pickNearestInZoneEnemy(Unit self, BattleView sim) {
        Faction enemy = enemyOf(self.faction);
        Unit best = null;
        float bestDist = Float.MAX_VALUE;
        for (Unit other : sim.getUnits()) {
            if (!other.isAlive()) continue;
            if (other.faction != enemy) continue;
            if (!other.type.combatant) continue;
            if (sim.getZoneGraph().zoneIdAt(other.getCellX(), other.getCellY()) != targetZoneId) continue;
            float d = TacticalScoring.cellDistance(self.getCellX(), self.getCellY(), other.getCellX(), other.getCellY());
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
