package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.decision.UnitBehavior;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;

/** Direct pressure behavior for swarm runners; no squad or infantry GOAP. */
public final class SwarmPressureBehavior implements UnitBehavior {

    private static final float CURRENT_TARGET_LEEWAY_SQUARED = 1.25f * 1.25f;
    private static final int ROAM_MIN_DISTANCE = 3;
    private static final int ROAM_RADIUS = 7;
    private static final int ROAM_SAMPLE_ATTEMPTS = 16;
    /** Conservative bridge from continuous-position buckets to cell-distance sensing. */
    private static final float SENSE_GATHER_PADDING = 1.414214f;
    /** UPDATE_UNITS is parallel, so every worker owns its candidate buffer. */
    private static final ThreadLocal<LongBucket> TARGET_CANDIDATES =
            ThreadLocal.withInitial(LongBucket::new);

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
            updateRoaming(runner, previousTarget, sim);
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
                sim.applyDamage(target, runner,
                        sim.combat().attackDamage(runner), 0f,
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
     * Keeps an undiscovered swarm visibly roving before the first ground force
     * arrives. Destinations are deterministic per runner/tick and paths may not
     * enter the protected shelter or pickup footprints.
     */
    private static void updateRoaming(long runner, long previousTarget,
                                      BattleSimulation sim) {
        if (previousTarget != 0L) sim.clearPath(runner);
        int[] currentPath = sim.movement().path(runner);
        if (sim.movement().pathIdx(runner) < Paths.cellCount(currentPath)) {
            sim.advanceMovement(runner);
            return;
        }
        if (!sim.movement().mayRepath(runner)) return;

        int originX = sim.world().cellX(runner);
        int originY = sim.world().cellY(runner);
        int span = ROAM_RADIUS * 2 + 1;
        long seed = runner * 0x9E3779B97F4A7C15L
                ^ (long) sim.getSimTickIndex() * 0xBF58476D1CE4E5B9L;
        int destinationX = Integer.MIN_VALUE;
        int destinationY = Integer.MIN_VALUE;
        for (int attempt = 0; attempt < ROAM_SAMPLE_ATTEMPTS; attempt++) {
            long sample = mix(seed + attempt * 0x94D049BB133111EBL);
            int dx = Math.floorMod((int) sample, span) - ROAM_RADIUS;
            int dy = Math.floorMod((int) (sample >>> 32), span) - ROAM_RADIUS;
            if (Math.abs(dx) + Math.abs(dy) < ROAM_MIN_DISTANCE) continue;
            int candidateX = originX + dx;
            int candidateY = originY + dy;
            if (!sim.getGrid().inBounds(candidateX, candidateY)
                    || !sim.getGrid().isWalkable(candidateX, candidateY)
                    || sim.isInsideRescueOpeningProtectedZone(
                            candidateX, candidateY)) {
                continue;
            }
            int destinationCell = sim.getGrid().index(candidateX, candidateY);
            if ((sim.getOccupancyMap()[destinationCell] & 0xFF) != 0) continue;
            destinationX = candidateX;
            destinationY = candidateY;
            break;
        }
        if (destinationX == Integer.MIN_VALUE) {
            sim.clearPath(runner);
            return;
        }

        // Candidate sampling is deliberately cheap. Pay for at most one A*
        // per roam decision; a blocked result simply waits for the next
        // decorrelated repath window instead of multiplying path searches.
        int[] path = GridPathfinder.findPath(sim.getGrid(), originX, originY,
                destinationX, destinationY, sim.getOccupancyMap());
        if (Paths.isEmpty(path) || crossesProtectedZone(path, sim)) {
            sim.clearPath(runner);
            return;
        }
        sim.setPath(runner, path);
        sim.advanceMovement(runner);
    }

    private static boolean crossesProtectedZone(int[] path,
                                                BattleSimulation sim) {
        for (int i = 0, n = Paths.cellCount(path); i < n; i++) {
            if (sim.isInsideRescueOpeningProtectedZone(
                    Paths.cellX(path, i), Paths.cellY(path, i))) return true;
        }
        return false;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
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

        LongBucket nearby = TARGET_CANDIDATES.get();
        float senseRange = sim.vision().visionRange(runner);
        sim.getUnitIndex().gatherFaction(sim.world().x(runner), sim.world().y(runner),
                senseRange + SENSE_GATHER_PADDING, Faction.MARINE, nearby);
        for (int i = 0, n = nearby.size; i < n; i++) {
            long candidate = nearby.ids[i];
            if (!eligibleMarine(candidate, sim)) continue;
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
        if (sim.isCivilianShelterProtected()) {
            return sim.getUnitIndex().nearestFaction(
                    sim.world().x(runner), sim.world().y(runner),
                    Faction.MARINE, candidate -> eligibleMarine(candidate, sim));
        }
        return sim.getUnitIndex().nearestFaction(
                sim.world().x(runner), sim.world().y(runner), Faction.MARINE);
    }

    private static boolean isEligibleRememberedTarget(
            long candidate, CivilianEvacuationTracker tracker,
            BattleSimulation sim) {
        if (candidate == 0L || sim.resolveUnit(candidate) == 0L) return false;
        if (sim.identity().faction(candidate) == Faction.MARINE) {
            return eligibleMarine(candidate, sim);
        }
        return !sim.isCivilianShelterProtected()
                && tracker.state(candidate) == CivilianEvacuationTracker.State.ACTIVE;
    }

    private static boolean eligibleMarine(long candidate,
                                           BattleSimulation sim) {
        if (sim.identity().faction(candidate) != Faction.MARINE) return false;
        Squad squad = sim.squadOf(candidate);
        return !sim.isCivilianShelterProtected()
                || squad == null || !squad.rescueShelterGuard;
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
