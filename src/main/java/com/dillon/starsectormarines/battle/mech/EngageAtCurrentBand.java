package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;

/**
 * Balanced doctrine's direct-fire action. It picks a target, fires every
 * installed weapon that is currently in band, and closes only far enough to
 * establish a medium direct-fire lane. A close visible threat is handled in
 * place instead of pulling the mech away from its current mission posture.
 *
 * <p>{@link ExecuteMechDoctrine} routes Balanced members here from the shared
 * squad plan. Specialized doctrine actions may also use it as a defensive
 * fallback when their own geometry cannot be established.
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
    private static final float MEDIUM_BAND_DEPTH = 6f;

    private EngageAtCurrentBand() {}

    @Override public String name() { return "EngageAtCurrentBand"; }
    @Override public WorldState preconditions() { return PRE; }
    @Override public WorldState effects() { return EFF; }
    @Override public float cost(WorldState s, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return 1; }

    @Override
    public ActionStatus execute(long u, Squad squad, BattleControl sim) {
        long target = MechTargeting.refreshTarget(u, sim);
        sim.world().setTargetId(u, target);
        if (target == 0L) {
            MechAssignmentBoundary.advanceToMission(u, squad, sim);
            return ActionStatus.RUNNING;
        }

        // Loadout component reached by id (zero-alloc direct lookup).
        MechLoadoutComponent m = sim.world().mechLoadout(u);

        float dist = TacticalScoring.cellDistance(sim.world().x(u), sim.world().y(u), sim.world().x(target), sim.world().y(target));
        boolean inRange = dist <= sim.world().attackRange(u);
        boolean visible = sim.getTacticalScoring().hasClearShot(u, target);

        // The fire pass runs outside the inRange-and-visible gate because LRMs
        // are indirect-fire capable — a mech with line of sight blocked by a
        // building still lobs artillery over it (with an accuracy penalty).
        // Chaingun + SRM still need LOS — gated inside tryFireMechWeapons.
        if (inRange) {
            MechCombatantBehavior.tryFireMechWeapons(u, m, target, dist, sim, visible);
        }

        // Close engagement = in the preferred supplied direct band with LOS. Outside that, the
        // mech advances toward a firing position so it can re-acquire LOS for
        // its short-range weapons (LRMs already fire from here via the
        // indirect path above).
        float preferredDirectRange = m.mediumDirectRange();
        boolean targetInsideCommand = MechAssignmentBoundary.permitsCell(
                u, squad, sim.world().cellX(target), sim.world().cellY(target), sim);
        boolean closeEngagement = inRange && visible
                && dist <= preferredDirectRange && targetInsideCommand;
        MechRouteIntent route = MechRouteIntent.forMember(u, EngageAtCurrentBand.class, target, sim);
        route.refreshCandidates(MechRouteIntent.cellKey(sim.world().cellX(target), sim.world().cellY(target)));
        route.rejectSettledPerch(u, sim.world().x(target), sim.world().y(target),
                0f, preferredDirectRange, sim);
        if (!closeEngagement && route.resume(u, sim)) return ActionStatus.RUNNING;
        if (closeEngagement) {
            if (route.pending() || !Paths.isEmpty(sim.world().path(u))) sim.clearPath(u);
            route.cancel();
        } else if (sim.movement().mayRepath(u)) {
            int[] dest = findMediumDirectPosition(
                    u, target, preferredDirectRange, sim);
            dest = MechAssignmentBoundary.constrain(u, squad, dest, sim);
            if (dest == null) {
                // Candidate/proof refusal is not evidence that the hostile
                // disappeared. Keep the perceived target and hold this posture.
                if (!Paths.isEmpty(sim.world().path(u))) sim.clearPath(u);
            } else {
                route.moveToward(u, dest[0], dest[1], sim);
                return ActionStatus.RUNNING;
            }
        }
        if (sim.world().pathIdx(u) < Paths.cellCount(sim.world().path(u))) {
            sim.advanceMovement(u);
        }
        return ActionStatus.RUNNING;
    }

    static int[] findMediumDirectPosition(long member, long target,
                                          float preferredRange,
                                          BattleView sim) {
        if (preferredRange <= 0f) return null;
        NavigationGrid grid = sim.getGrid();
        int memberX = sim.world().cellX(member);
        int memberY = sim.world().cellY(member);
        int[] connected = GridPathfinder.labelConnectedComponents(grid);
        int memberComponent = grid.inBounds(memberX, memberY)
                ? connected[grid.index(memberX, memberY)] : -1;
        if (memberComponent < 0) return null;

        float targetX = sim.world().x(target);
        float targetY = sim.world().y(target);
        int centerX = sim.world().cellX(target);
        int centerY = sim.world().cellY(target);
        int radius = (int) Math.ceil(preferredRange);
        float minimumRange = Math.max(4f, preferredRange - MEDIUM_BAND_DEPTH);
        int[] best = null;
        float bestScore = Float.MAX_VALUE;
        for (int oy = -radius; oy <= radius; oy++) {
            for (int ox = -radius; ox <= radius; ox++) {
                int x = centerX + ox;
                int y = centerY + oy;
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)
                        || connected[grid.index(x, y)] != memberComponent
                        || sim.world().mechLoadout(member).routeIntent.rejected(x, y)) continue;
                float targetDx = x + 0.5f - targetX;
                float targetDy = y + 0.5f - targetY;
                float targetDistance =
                        (float) Math.sqrt(targetDx * targetDx + targetDy * targetDy);
                if (targetDistance < minimumRange || targetDistance > preferredRange) continue;
                if (!grid.hasLineOfFire(x + 0.5f, y + 0.5f, targetX, targetY)) continue;
                float walk = TacticalScoring.cellDistance(
                        sim.world().x(member), sim.world().y(member), x + 0.5f, y + 0.5f);
                float score = walk + (preferredRange - targetDistance) * 0.25f;
                if (score < bestScore && MechRouteIntent.candidate(member, x, y, sim)) {
                    bestScore = score;
                    best = new int[]{x, y};
                }
            }
        }
        return best;
    }
}
