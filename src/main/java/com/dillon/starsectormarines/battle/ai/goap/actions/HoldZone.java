package com.dillon.starsectormarines.battle.ai.goap.actions;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.decision.TacticalNode;

/**
 * <b>Squad posture: hold a zone until a compound capture completes.</b>
 * Terminal step in a {@link com.dillon.starsectormarines.battle.ai.goap.goals.SecureCompoundGoal}
 * plan. Marines stay in the zone, engage enemies that enter, and report
 * {@link ActionStatus#SUCCESS} once the compound's state reaches
 * {@link CompoundService.CompoundState#MARINE_HELD}.
 *
 * <p>Capture progress is driven by {@link com.dillon.starsectormarines.battle.command.compound.CompoundCaptureSystem}
 * at 1 Hz — this action's job is simply to keep marines present in the zone
 * so the capture timer accumulates. Engagement behavior mirrors
 * {@link ClearZone}: in-zone enemies are preferred, out-of-zone enemies are
 * shot opportunistically but not pursued.
 *
 * <p>Parameterized per-zone like {@link EnterZone} and {@link ClearZone};
 * not a singleton, not in {@code INFANTRY_ACTIONS}. Emitted only by
 * {@link com.dillon.starsectormarines.battle.ai.goap.goals.SecureCompoundGoal}'s
 * custom plan.
 */
public final class HoldZone implements Action {

    private final int targetZoneId;
    private final TacticalNode compoundNode;

    public HoldZone(int targetZoneId, TacticalNode compoundNode) {
        this.targetZoneId = targetZoneId;
        this.compoundNode = compoundNode;
    }

    public int targetZoneId() { return targetZoneId; }

    @Override public String name() { return "HoldZone[" + targetZoneId + "]"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleSimulation sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Unit member, Squad squad, BattleSimulation sim) {
        CompoundService.Record record = sim.getCompoundService().getRecord(compoundNode);
        if (record != null && record.state == CompoundService.CompoundState.MARINE_HELD) {
            return ActionStatus.SUCCESS;
        }

        Faction enemy = squad.faction == Faction.MARINE ? Faction.DEFENDER : Faction.MARINE;
        boolean enemiesInZone = !ZoneQueries.zoneClear(targetZoneId, enemy, sim);

        if (enemiesInZone) {
            return engageInZone(member, squad, sim, enemy);
        }

        hold(member, sim);
        return ActionStatus.RUNNING;
    }

    private ActionStatus engageInZone(Unit member, Squad squad, BattleSimulation sim, Faction enemy) {
        Unit target = sim.targetOf(member);
        boolean targetOutOfZone = target != null
                && sim.getZoneGraph().zoneIdAt(target.getCellX(), target.getCellY()) != targetZoneId;
        if (target == null
                || targetOutOfZone
                || !sim.getTacticalScoring().shouldKeepPursuing(member, target)) {
            target = pickInZoneTarget(member, sim, enemy);
            if (target == null) target = sim.getTacticalScoring().findBestTarget(member);
            member.setTarget(target);
        }
        if (target == null) {
            hold(member, sim);
            return ActionStatus.RUNNING;
        }

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

        if (sim.getZoneGraph().zoneIdAt(target.getCellX(), target.getCellY()) != targetZoneId) {
            hold(member, sim);
            return ActionStatus.RUNNING;
        }
        if (member.getMoveProgress() == 0f) {
            int[] dest = sim.getTacticalScoring().findFiringPosition(member, target);
            if (dest == null) {
                member.setTargetId(0L);
                hold(member, sim);
                return ActionStatus.RUNNING;
            }
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    member.getCellX(), member.getCellY(), dest[0], dest[1], sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);
        return ActionStatus.RUNNING;
    }

    private Unit pickInZoneTarget(Unit self, BattleSimulation sim, Faction enemy) {
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

    private static void hold(Unit member, BattleSimulation sim) {
        if (!member.pathEmpty()) sim.clearPath(member);
        member.setMoveProgress(0f);
        member.setRenderPos(member.getCellX(), member.getCellY());
    }
}
