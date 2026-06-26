package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;

/**
 * Mech parity action — the GOAP-side equivalent of the legacy
 * {@link MechCombatantBehavior#update(Entity, BattleSimulation)} loop. Picks
 * a target, fires all three weapon tracks at whichever bands their targets
 * sit in, and advances toward a firing position when not already in close
 * engagement. No role gating — every mech runs this regardless of doctrine.
 *
 * <p>This is the floor: the {@code MechEliminateEnemies} ambient goal plans
 * a single-step plan of this action, and {@code GoapMechBehavior} executes
 * it for every member each tick. Role-anchored actions (overwatch /
 * backstop) layer on top in subsequent slices and override this for
 * mechs whose {@link com.dillon.starsectormarines.battle.mech.MechRole}-keyed
 * goal has higher relevance.
 *
 * <p>Always returns {@link ActionStatus#RUNNING} — there's no terminal
 * "engagement complete" state. The plan re-runs every tick; replan
 * triggers (alert level change, member death, 2s timer) build a fresh
 * plan that may pick a different goal.
 */
public final class EngageAtCurrentBand implements Action {

    public static final EngageAtCurrentBand INSTANCE = new EngageAtCurrentBand();

    private static final WorldState PRE = WorldState.EMPTY;
    private static final WorldState EFF = WorldState.EMPTY
            .with(Predicate.ENEMY_DAMAGED, true);

    private EngageAtCurrentBand() {}

    @Override public String name() { return "EngageAtCurrentBand"; }
    @Override public WorldState preconditions() { return PRE; }
    @Override public WorldState effects() { return EFF; }
    @Override public float cost(WorldState s, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Entity u, Squad squad, BattleControl sim) {
        Entity target = sim.getTacticalScoring().refreshTargetIfNotShootable(u);
        sim.world().setTargetId(u.entityId, Entity.idOf(target));
        if (target == null) return ActionStatus.RUNNING;

        // Loadout component reached by id (zero-alloc direct lookup).
        MechLoadoutComponent m = sim.world().mechLoadout(u.entityId);

        float dist = TacticalScoring.cellDistance(sim.world().cellX(u.entityId), sim.world().cellY(u.entityId), sim.world().cellX(target.entityId), sim.world().cellY(target.entityId));
        boolean inRange = dist <= sim.world().attackRange(u.entityId);
        boolean visible = sim.getGrid().hasLineOfSight(sim.world().cellX(u.entityId), sim.world().cellY(u.entityId), sim.world().cellX(target.entityId), sim.world().cellY(target.entityId));

        // The fire pass runs outside the inRange-and-visible gate because LRMs
        // are indirect-fire capable — a mech with line of sight blocked by a
        // building still lobs artillery over it (with an accuracy penalty).
        // Chaingun + SRM still need LOS — gated inside tryFireMechWeapons.
        if (inRange) {
            MechCombatantBehavior.tryFireMechWeapons(u, m, target, dist, sim, visible);
        }

        // Close engagement = in chaingun range with LOS. Outside that, the
        // mech advances toward a firing position so it can re-acquire LOS for
        // its short-range weapons (LRMs already fire from here via the
        // indirect path above).
        boolean closeEngagement = inRange && visible && dist <= m.srmPod.range;
        if (!closeEngagement && sim.world().moveProgress(u.entityId) == 0f) {
            int[] dest = sim.getTacticalScoring().findFiringPosition(u, target);
            if (dest == null) {
                // No reachable firing or vantage cell for the current target.
                // Drop and let the mech's per-tick target acquisition re-pick.
                // LRM indirect fire above already ran for this tick — chaingun /
                // SRM stay quiet until a reachable target is acquired.
                sim.world().setTargetId(u.entityId, 0L);
            } else {
                sim.setPath(u, GridPathfinder.findPath(sim.getGrid(),
                        sim.world().cellX(u.entityId), sim.world().cellY(u.entityId), dest[0], dest[1], sim.getOccupancyMap()));
            }
        }
        if (sim.world().pathIdx(u.entityId) < Paths.cellCount(sim.world().path(u.entityId))) {
            sim.advanceMovement(u);
        } else {
            sim.world().setMoveProgress(u.entityId, 0f);
            sim.world().setRenderPos(u.entityId, sim.world().cellX(u.entityId), sim.world().cellY(u.entityId));
        }
        return ActionStatus.RUNNING;
    }
}
