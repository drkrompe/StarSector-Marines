package com.dillon.starsectormarines.battle.ai.goap.actions;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.ai.TacticalScoring;
import com.dillon.starsectormarines.battle.ai.goap.Action;
import com.dillon.starsectormarines.battle.ai.goap.ActionStatus;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.ai.goap.world.ZoneQueries;
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
 * {@link com.dillon.starsectormarines.battle.ai.goap.goals.SecureObjectiveZone}'s
 * custom plan. Empty preconditions/effects: not used by the backward-chaining
 * planner.
 */
public final class ClearZone implements Action {

    private final int targetZoneId;

    public ClearZone(int targetZoneId) {
        this.targetZoneId = targetZoneId;
    }

    public int targetZoneId() { return targetZoneId; }

    @Override public String name() { return "ClearZone[" + targetZoneId + "]"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleSimulation sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleSimulation sim) {
        // Quick exit when the zone reads clear for this squad's enemy faction.
        Faction enemy = enemyOf(squad.faction);
        if (ZoneQueries.zoneClear(targetZoneId, enemy, sim)) {
            return ActionStatus.SUCCESS;
        }

        // Refresh target: prefer an in-zone enemy. Falls back to the
        // squad-aware best-target if no in-zone target is reachable, so the
        // squad still fires on threats from adjacent zones rather than
        // standing in a doorway taking hits.
        if (member.target == null
                || !member.target.isAlive()
                || !TacticalScoring.shouldKeepPursuing(member, member.target, sim)) {
            Unit inZone = pickInZoneTarget(member, sim);
            member.target = inZone != null ? inZone : TacticalScoring.findBestTarget(member, sim);
        }
        if (member.target == null) return ActionStatus.RUNNING;

        float dist = TacticalScoring.cellDistance(member.cellX, member.cellY,
                member.target.cellX, member.target.cellY);
        boolean inRange = dist <= member.attackRange;
        boolean visible = sim.getGrid().hasLineOfSight(member.cellX, member.cellY,
                member.target.cellX, member.target.cellY);
        if (inRange && visible && member.cooldownTimer <= 0f) {
            sim.fireShot(member, member.target);
            member.cooldownTimer = member.attackCooldown;
            if (member.primaryWeapon != null && member.primaryWeapon.burstCount > 1) {
                member.burstRemaining = member.primaryWeapon.burstCount - 1;
                member.burstTimer = member.primaryWeapon.burstSpacing;
                member.burstTarget = member.target;
            }
            return ActionStatus.RUNNING;
        }

        // Out of range / no LOS — close on the target IFF the target is in
        // the zone we're clearing. Out-of-zone targets we don't pursue —
        // that's Story K's "doesn't push into rooms it's not clearing" rule.
        if (sim.getZoneGraph().zoneIdAt(member.target.cellX, member.target.cellY) != targetZoneId) {
            return ActionStatus.RUNNING;
        }
        if (member.moveProgress == 0f) {
            int[] dest = TacticalScoring.findFiringPosition(member, member.target, sim);
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    member.cellX, member.cellY, dest[0], dest[1], sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);
        return ActionStatus.RUNNING;
    }

    /**
     * Scans enemies of the squad's faction whose cell sits in the target zone
     * and returns the closest visible one. Linear over the unit list — fine
     * given typical squad-tick budgets. Returns null when no in-zone enemy
     * exists (caller falls back to the normal picker).
     */
    private Unit pickInZoneTarget(Unit self, BattleSimulation sim) {
        Faction enemy = enemyOf(self.faction);
        Unit best = null;
        float bestDist = Float.MAX_VALUE;
        for (Unit other : sim.getUnits()) {
            if (!other.isAlive()) continue;
            if (other.faction != enemy) continue;
            if (!other.type.combatant) continue;
            if (sim.getZoneGraph().zoneIdAt(other.cellX, other.cellY) != targetZoneId) continue;
            if (!sim.getGrid().hasLineOfSight(self.cellX, self.cellY, other.cellX, other.cellY)) continue;
            float d = TacticalScoring.cellDistance(self.cellX, self.cellY, other.cellX, other.cellY);
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
