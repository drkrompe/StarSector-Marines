package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.decision.UnitBehavior;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;

/** Direct pressure behavior for swarm runners; no squad or infantry GOAP. */
public final class SwarmPressureBehavior implements UnitBehavior {

    private static final float CURRENT_TARGET_LEEWAY_SQUARED = 1.25f * 1.25f;

    public static final SwarmPressureBehavior INSTANCE =
            new SwarmPressureBehavior();

    private SwarmPressureBehavior() {}

    @Override
    public void update(long runner, BattleSimulation sim) {
        tickCooldown(runner, sim);
        long previousTarget = sim.combat().targetId(runner);
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

        boolean targetChanged = target != previousTarget;
        if ((targetChanged || sim.movement().mayRepath(runner))
                && needsPath(runner, targetX, targetY, sim)) {
            sim.setPath(runner, GridPathfinder.findPath(sim.getGrid(),
                    runnerX, runnerY, targetX, targetY,
                    sim.getOccupancyMap()));
        }
        sim.advanceMovement(runner);
    }

    /**
     * Chooses the nearest sensed marine or active evacuee, with modest
     * stickiness for the current target. This lets nearby marines peel runners
     * away from civilians without making the swarm oscillate between nearly
     * equidistant victims. When no local target is sensed, a runner continues
     * toward remembered prey or falls back to the nearest marine.
     */
    public static long selectTarget(long runner, BattleSimulation sim) {
        CivilianEvacuationTracker tracker =
                sim.getCivilianEvacuationTracker();
        long current = sim.combat().targetId(runner);
        boolean currentValid = isEligibleRememberedTarget(current, tracker, sim);
        long best = 0L;
        float bestDistance = Float.MAX_VALUE;
        if (!sim.isCivilianShelterProtected()) {
            for (int i = 0, n = tracker.registeredCount(); i < n; i++) {
                long candidate = tracker.entityIdAt(i);
                if (tracker.state(candidate)
                        != CivilianEvacuationTracker.State.ACTIVE
                        || sim.resolveUnit(candidate) == 0L) {
                    continue;
                }
                if (!canSense(runner, candidate, sim)) continue;
                float distance = distanceSquared(runner, candidate, sim);
                if (isBetter(candidate, distance, best, bestDistance)) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }

        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long candidate = sim.liveUnitAt(i);
            if (sim.identity().faction(candidate) != Faction.MARINE) continue;
            if (!canSense(runner, candidate, sim)) continue;
            float distance = distanceSquared(runner, candidate, sim);
            if (isBetter(candidate, distance, best, bestDistance)) {
                best = candidate;
                bestDistance = distance;
            }
        }

        if (best != 0L) {
            if (currentValid) {
                float currentDistance = distanceSquared(runner, current, sim);
                if (current == best
                        || currentDistance <= bestDistance * CURRENT_TARGET_LEEWAY_SQUARED) {
                    return current;
                }
            }
            return best;
        }

        if (currentValid) return current;

        // Strategic pressure fallback: the swarm still advances when all
        // marines are beyond local sensing range, but civilians remain unknown
        // until first contact reveals them.
        bestDistance = Float.MAX_VALUE;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long candidate = sim.liveUnitAt(i);
            if (sim.identity().faction(candidate) != Faction.MARINE) continue;
            float distance = distanceSquared(runner, candidate, sim);
            if (isBetter(candidate, distance, best, bestDistance)) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean isEligibleRememberedTarget(
            long candidate, CivilianEvacuationTracker tracker,
            BattleSimulation sim) {
        if (candidate == 0L || sim.resolveUnit(candidate) == 0L) return false;
        if (sim.identity().faction(candidate) == Faction.MARINE) return true;
        return !sim.isCivilianShelterProtected()
                && tracker.state(candidate) == CivilianEvacuationTracker.State.ACTIVE;
    }

    private static boolean isBetter(long candidate, float distance,
                                    long best, float bestDistance) {
        return distance < bestDistance
                || (distance == bestDistance && (best == 0L || candidate < best));
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
