package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.decision.UnitBehavior;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Direct pressure behavior for swarm runners; no squad or infantry GOAP. */
public final class SwarmPressureBehavior implements UnitBehavior {

    public static final SwarmPressureBehavior INSTANCE =
            new SwarmPressureBehavior();

    private SwarmPressureBehavior() {}

    @Override
    public void update(long runner, BattleSimulation sim) {
        tickCooldown(runner, sim);
        long target = selectTarget(runner, sim);
        sim.combat().setTargetId(runner, target);
        if (target == 0L) {
            sim.clearPath(runner);
            return;
        }

        int runnerX = sim.world().cellX(runner);
        int runnerY = sim.world().cellY(runner);
        int targetX = sim.world().cellX(target);
        int targetY = sim.world().cellY(target);
        float dx = sim.world().x(target) - sim.world().x(runner);
        float dy = sim.world().y(target) - sim.world().y(runner);
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance <= sim.combat().attackRange(runner)) {
            sim.clearPath(runner);
            if (sim.combat().cooldownTimer(runner) <= 0f) {
                sim.applyDamage(target,
                        sim.combat().attackDamage(runner), 1f,
                        sim.identity().type(runner).moraleImpact);
                sim.combat().setCooldownTimer(runner,
                        sim.combat().attackCooldown(runner));
            }
            return;
        }

        if (sim.movement().mayRepath(runner)
                && needsPath(runner, targetX, targetY, sim)) {
            sim.setPath(runner, GridPathfinder.findPath(sim.getGrid(),
                    runnerX, runnerY, targetX, targetY,
                    sim.getOccupancyMap()));
        }
        sim.advanceMovement(runner);
    }

    /**
     * Previously acquired evacuees first, then newly sensed evacuees; nearest
     * marine when no civilian has been discovered. Runners can remember prey
     * that breaks line of sight, but do not know the cohort's exact position
     * before seeing it.
     */
    public static long selectTarget(long runner, BattleSimulation sim) {
        CivilianEvacuationTracker tracker =
                sim.getCivilianEvacuationTracker();
        long best = 0L;
        float bestDistance = Float.MAX_VALUE;
        if (!sim.isCivilianShelterProtected()) {
            long remembered = sim.combat().targetId(runner);
            if (tracker.state(remembered)
                    == CivilianEvacuationTracker.State.ACTIVE
                    && sim.resolveUnit(remembered) != 0L) {
                return remembered;
            }

            for (int i = 0, n = tracker.registeredCount(); i < n; i++) {
                long candidate = tracker.entityIdAt(i);
                if (tracker.state(candidate)
                        != CivilianEvacuationTracker.State.ACTIVE
                        || sim.resolveUnit(candidate) == 0L) {
                    continue;
                }
                if (!canSense(runner, candidate, sim)) continue;
                float distance = distanceSquared(runner, candidate, sim);
                if (distance < bestDistance
                        || (distance == bestDistance && candidate < best)) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
            if (best != 0L) return best;
        }

        bestDistance = Float.MAX_VALUE;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long candidate = sim.liveUnitAt(i);
            if (sim.identity().faction(candidate) != Faction.MARINE) continue;
            float distance = distanceSquared(runner, candidate, sim);
            if (distance < bestDistance
                    || (distance == bestDistance && candidate < best)) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean canSense(long runner, long candidate,
                                    BattleSimulation sim) {
        return sim.getGrid().hasLineOfSightWithin(
                sim.world().cellX(runner), sim.world().cellY(runner),
                sim.world().cellX(candidate), sim.world().cellY(candidate),
                sim.vision().visionRange(runner));
    }

    private static boolean needsPath(long runner, int targetX, int targetY,
                                     BattleSimulation sim) {
        int[] path = sim.movement().path(runner);
        return sim.movement().pathIdx(runner) >= Paths.cellCount(path)
                || Paths.destX(path) != targetX
                || Paths.destY(path) != targetY;
    }

    private static void tickCooldown(long runner, BattleSimulation sim) {
        float cooldown = sim.combat().cooldownTimer(runner);
        if (cooldown > 0f) {
            sim.combat().setCooldownTimer(runner,
                    Math.max(0f, cooldown - BattleSimulation.TICK_DT));
        }
    }

    private static float distanceSquared(long a, long b,
                                         BattleSimulation sim) {
        float dx = sim.world().x(a) - sim.world().x(b);
        float dy = sim.world().y(a) - sim.world().y(b);
        return dx * dx + dy * dy;
    }
}
