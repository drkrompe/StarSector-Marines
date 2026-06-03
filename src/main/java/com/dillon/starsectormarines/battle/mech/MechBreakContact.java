package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

/**
 * <b>Mech squad posture: pull back to cover, keep firing all three weapon
 * tracks.</b> The mech sibling of {@link BreakContact} — same fallback-cell
 * picker and pathing flow, but opportunistic fire routes through
 * {@link MechCombatantBehavior#tryFireMechWeapons} so the chaingun / SRM /
 * LRM tracks all stay live during the retreat. A mech withdrawing under fire
 * still has a long-range artillery card to play even when the chassis is
 * past LRM-range-only.
 *
 * <p>Runs perpetually ({@link ActionStatus#RUNNING}); the squad-level
 * 2-second replan re-evaluates whether MORALE_BROKEN still holds. Once
 * mech-side morale recovers past
 * {@link com.dillon.starsectormarines.battle.squad.SquadMoraleSystem#MECH_MORALE_CLEAR_THRESHOLD} the per-mech flags
 * clear, the squad aggregator drops {@link Squad#moraleBroken}, and
 * {@code MechSurviveContact} goes inactive — the squad falls back to its
 * role goal (or {@code MechEliminateEnemies} as the engagement floor).
 *
 * <p>Empty preconditions/effects — only emitted by
 * {@code MechSurviveContact}'s customPlan.
 */
public final class MechBreakContact implements Action {

    public static final MechBreakContact INSTANCE = new MechBreakContact();

    private MechBreakContact() {}

    @Override public String name() { return "MechBreakContact"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Entity member, Squad squad, BattleControl sim) {
        if (sim.getTacticalScoring().fallbackDestinationNeedsRefresh(member)) {
            int[] dest = sim.getTacticalScoring().findFallbackPosition(member);
            sim.world().setFallbackCell(member.entityId, dest[0], dest[1]);
        }

        boolean atDest = sim.world().cellX(member.entityId) == sim.world().fallbackCellX(member.entityId)
                      && sim.world().cellY(member.entityId) == sim.world().fallbackCellY(member.entityId);
        if (!atDest) {
            opportunisticMechFire(member, sim);
            if (sim.world().moveProgress(member.entityId) == 0f) {
                sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                        sim.world().cellX(member.entityId), sim.world().cellY(member.entityId),
                        sim.world().fallbackCellX(member.entityId), sim.world().fallbackCellY(member.entityId),
                        sim.getOccupancyMap()));
            }
            sim.advanceMovement(member);
        } else {
            if (!member.pathEmpty()) sim.clearPath(member);
            sim.world().setMoveProgress(member.entityId, 0f);
            member.setRenderPos(sim.world().cellX(member.entityId), sim.world().cellY(member.entityId));
            opportunisticMechFire(member, sim);
        }
        return ActionStatus.RUNNING;
    }

    /**
     * Pick a target (or keep the current one), then run the same three-track
     * mech fire pass {@link MechCombatantBehavior#tryFireMechWeapons} uses
     * for parity engagement. LRMs may fire without LOS at the indirect-fire
     * accuracy penalty — preserves the "arc artillery over cover while
     * pulling back" read.
     */
    private static void opportunisticMechFire(Entity u, BattleControl sim) {
        Entity target = sim.targetOf(u);
        if (target == null
                || !sim.getTacticalScoring().shouldKeepPursuing(u, target)) {
            target = sim.getTacticalScoring().findBestTarget(u);
            sim.world().setTargetId(u.entityId, Entity.idOf(target));
        }
        if (target == null) return;
        float dist = TacticalScoring.cellDistance(sim.world().cellX(u.entityId), sim.world().cellY(u.entityId),
                sim.world().cellX(target.entityId), sim.world().cellY(target.entityId));
        if (dist > sim.world().attackRange(u.entityId)) return;
        boolean visible = sim.getGrid().hasLineOfSight(sim.world().cellX(u.entityId), sim.world().cellY(u.entityId),
                sim.world().cellX(target.entityId), sim.world().cellY(target.entityId));
        MechLoadoutComponent m = sim.world().component(u.entityId, MechLoadoutComponent.class);
        MechCombatantBehavior.tryFireMechWeapons(u, m, target, dist, sim, visible);
    }
}
