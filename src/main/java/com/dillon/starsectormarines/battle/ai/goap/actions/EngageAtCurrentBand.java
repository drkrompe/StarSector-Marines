package com.dillon.starsectormarines.battle.ai.goap.actions;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.ai.MechCombatantBehavior;
import com.dillon.starsectormarines.battle.ai.TacticalScoring;
import com.dillon.starsectormarines.battle.ai.goap.Action;
import com.dillon.starsectormarines.battle.ai.goap.ActionStatus;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;

/**
 * Mech parity action — the GOAP-side equivalent of the legacy
 * {@link MechCombatantBehavior#update(Unit, BattleSimulation)} loop. Picks
 * a target, fires all three weapon tracks at whichever bands their targets
 * sit in, and advances toward a firing position when not already in close
 * engagement. No role gating — every mech runs this regardless of doctrine.
 *
 * <p>This is the floor: the {@code MechEliminateEnemies} ambient goal plans
 * a single-step plan of this action, and {@code GoapMechBehavior} executes
 * it for every member each tick. Role-anchored actions (overwatch /
 * backstop) layer on top in subsequent slices and override this for
 * mechs whose {@link com.dillon.starsectormarines.battle.weapons.MechRole}-keyed
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
    @Override public float cost(WorldState s, Squad squad, BattleSimulation sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(Unit u, Squad squad, BattleSimulation sim) {
        Unit target = sim.getTacticalScoring().refreshTargetIfNotShootable(u);
        u.setTarget(target);
        if (target == null) return ActionStatus.RUNNING;

        float dist = TacticalScoring.cellDistance(u.getCellX(), u.getCellY(), target.getCellX(), target.getCellY());
        boolean inRange = dist <= u.attackRange;
        boolean visible = sim.getGrid().hasLineOfSight(u.getCellX(), u.getCellY(), target.getCellX(), target.getCellY());

        // The fire pass runs outside the inRange-and-visible gate because LRMs
        // are indirect-fire capable — a mech with line of sight blocked by a
        // building still lobs artillery over it (with an accuracy penalty).
        // Chaingun + SRM still need LOS — gated inside tryFireMechWeapons.
        if (inRange) {
            MechCombatantBehavior.tryFireMechWeapons(u, target, dist, sim, visible);
        }

        // Close engagement = in chaingun range with LOS. Outside that, the
        // mech advances toward a firing position so it can re-acquire LOS for
        // its short-range weapons (LRMs already fire from here via the
        // indirect path above).
        boolean closeEngagement = inRange && visible && dist <= u.mech.srmPod.range;
        if (!closeEngagement && u.moveProgress == 0f) {
            int[] dest = sim.getTacticalScoring().findFiringPosition(u, target);
            if (dest == null) {
                // No reachable firing or vantage cell for the current target.
                // Drop and let the mech's per-tick target acquisition re-pick.
                // LRM indirect fire above already ran for this tick — chaingun /
                // SRM stay quiet until a reachable target is acquired.
                u.targetId = 0L;
            } else {
                sim.setPath(u, GridPathfinder.findPath(sim.getGrid(),
                        u.getCellX(), u.getCellY(), dest[0], dest[1], sim.getOccupancyMap()));
            }
        }
        if (u.pathIdx < u.pathCellCount()) {
            sim.advanceMovement(u);
        } else {
            u.moveProgress = 0f;
            u.renderX = u.getCellX();
            u.renderY = u.getCellY();
        }
        return ActionStatus.RUNNING;
    }
}
