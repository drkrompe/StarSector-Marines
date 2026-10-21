package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.decision.UnitBehavior;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.List;

/** Direct pressure behavior for swarm runners; no squad or infantry GOAP. */
public final class SwarmPressureBehavior implements UnitBehavior {

    private static final float CURRENT_TARGET_LEEWAY_SQUARED = 1.25f * 1.25f;
    private static final int ROAM_MIN_DISTANCE = 3;
    private static final int ROAM_RADIUS = 7;
    private static final int ROAM_SAMPLE_ATTEMPTS = 16;
    /** Conservative bridge from continuous-position buckets to cell-distance sensing. */
    private static final float SENSE_GATHER_PADDING = 1.414214f;

    /**
     * Combatant sides the swarm hunts, derived from the hostility relation
     * rather than listed. The rush is defender-side, so it presses everyone
     * the defender fights: a MARINE-only scan left a whole allied militia
     * invisible to it and unkillable by it. Civilians are deliberately absent
     * — the cohort is reached through the evacuation tracker, which knows
     * which of them the swarm has actually discovered.
     */
    static final Faction[] PREY;

    static {
        List<Faction> prey = new ArrayList<>();
        for (Faction faction : Faction.values()) {
            if (Faction.DEFENDER.hostileTo(faction)) prey.add(faction);
        }
        PREY = prey.toArray(new Faction[0]);
    }
    /** UPDATE_UNITS is parallel, so every worker owns its candidate buffer. */
    private static final ThreadLocal<LongBucket> TARGET_CANDIDATES =
            ThreadLocal.withInitial(LongBucket::new);

    public static final SwarmPressureBehavior INSTANCE =
            new SwarmPressureBehavior();

    private SwarmPressureBehavior() {}

    @Override
    public void update(long runner, BattleSimulation sim) {
        // Every strain keeps the SWARM_PRESSURE role, so the director, hit response and
        // evidence see one swarm; what differs is what a body does with a target.
        switch (sim.identity().type(runner)) {
            case SWARM_BRUTE: SwarmBruteBehavior.update(runner, sim); return;
            case SWARM_SPITTER: SwarmSpitterBehavior.update(runner, sim); return;
            default: break;
        }
        tickCooldown(runner, sim);
        if (sim.swarmHarvest().isCarrier(runner)) {
            carryHome(runner, sim);
            return;
        }
        long previousTarget = sim.combat().targetId(runner);
        long target = selectTarget(runner, sim);
        sim.combat().setTargetId(runner, target);
        if (target == 0L) {
            updateRoaming(runner, previousTarget, sim);
            return;
        }
        if (target != previousTarget) sim.swarmCues().post(SwarmCues.Kind.ALERT, runner, sim);

        float dx = sim.world().x(target) - sim.world().x(runner);
        float dy = sim.world().y(target) - sim.world().y(runner);
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance <= sim.combat().attackRange(runner)) {
            sim.clearPath(runner);
            if (sim.combat().cooldownTimer(runner) <= 0f) {
                sim.applyDamage(target, runner,
                        sim.combat().attackDamage(runner), 0f,
                        sim.identity().type(runner).moraleImpact);
                sim.swarmHarvest().struck(target, runner);
                sim.swarmCues().post(SwarmCues.Kind.STRIKE, runner, sim);
                sim.combat().setCooldownTimer(runner,
                        sim.combat().attackCooldown(runner));
            }
            return;
        }

        pressToward(runner, target, target != previousTarget, sim);
    }

    /**
     * A carrier drags its load home (`colony-breach-nouns.md`, Harvest): down
     * the bore field to the nearest living bore, or its own path there while
     * the field is not built. It does not strike while it carries.
     */
    private static void carryHome(long runner, BattleSimulation sim) {
        sim.combat().setTargetId(runner, 0L);
        if (SwarmMovement.moveToward(runner, SwarmGoal.BORE, sim)) return;
        SwarmBore bore = sim.swarmBores().nearestLiving(sim.world().x(runner), sim.world().y(runner));
        if (bore != null) SwarmMovement.pathDirect(runner, bore.x(), bore.y(), false, sim);
    }

    /**
     * The swarm's one strategic movement call: move {@code body} a tick toward
     * {@code target}. Every strain moves through here (the Brute toward what it
     * means to break, the Spitter toward lobbing range), so a change to how the
     * swarm travels lands on the whole swarm at once: far off, the body
     * descends the prey flow field; close in, it paths to its victim. Local
     * manoeuvres (a Spitter backing off) are the strain's own. See
     * {@link SwarmMovement#pursue}.
     */
    public static void pressToward(long body, long target, boolean targetChanged,
                                   BattleSimulation sim) {
        SwarmMovement.pursue(body, target, targetChanged, sim);
    }

    /**
     * {@link #pressToward(long, long, boolean, BattleSimulation)} toward a cell
     * rather than a body: the body's own path, since a cell picked for one body
     * (a breach's approach) is nobody else's goal.
     */
    public static void pressToward(long body, int targetX, int targetY,
                                   boolean targetChanged, BattleSimulation sim) {
        SwarmMovement.pathDirect(body, targetX, targetY, targetChanged, sim);
    }

    /**
     * Keeps an undiscovered swarm visibly roving before the first ground force
     * arrives. Destinations are deterministic per runner/tick and paths may not
     * enter the protected shelter or pickup footprints.
     */
    static void updateRoaming(long runner, long previousTarget,
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
        boolean shelterProtected = sim.isCivilianShelterProtected();
        float runnerPosX = sim.world().x(runner);
        float runnerPosY = sim.world().y(runner);
        int runnerCellX = (int) Math.floor(runnerPosX);
        int runnerCellY = (int) Math.floor(runnerPosY);
        float senseRange = sim.vision().visionRange(runner);
        long current = sim.combat().targetId(runner);
        boolean currentValid = isEligibleRememberedTarget(
                current, tracker, shelterProtected, sim);
        long best = 0L;
        float bestDistance = Float.MAX_VALUE;
        if (!shelterProtected) {
            for (int i = 0, n = tracker.registeredCount(); i < n; i++) {
                long candidate = tracker.entityIdAt(i);
                if (tracker.state(candidate)
                        != CivilianEvacuationTracker.State.ACTIVE
                        || sim.resolveUnit(candidate) == 0L) {
                    continue;
                }
                float candidateX = sim.world().x(candidate);
                float candidateY = sim.world().y(candidate);
                if (!canSense(runnerCellX, runnerCellY,
                        candidateX, candidateY, senseRange, sim)) continue;
                float distance = distanceSquared(runnerPosX, runnerPosY,
                        candidateX, candidateY);
                if (isBetter(candidate, distance, best, bestDistance)) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }

        LongBucket nearby = TARGET_CANDIDATES.get();
        // gatherFaction clears its bucket, so each prey side is scanned in
        // turn rather than accumulated.
        for (Faction prey : PREY) {
            sim.getUnitIndex().gatherFaction(runnerPosX, runnerPosY,
                    senseRange + SENSE_GATHER_PADDING, prey, nearby);
            for (int i = 0, n = nearby.size; i < n; i++) {
                long candidate = nearby.ids[i];
                if (!eligiblePreyKnownFaction(
                        candidate, shelterProtected, sim)) continue;
                float candidateX = sim.world().x(candidate);
                float candidateY = sim.world().y(candidate);
                if (!canSense(runnerCellX, runnerCellY,
                        candidateX, candidateY, senseRange, sim)) continue;
                float distance = distanceSquared(runnerPosX, runnerPosY,
                        candidateX, candidateY);
                if (isBetter(candidate, distance, best, bestDistance)) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }

        if (sim.identity().type(runner) == UnitType.SWARM_RUNNER && sim.swarmHarvest().isOpen()) {
            // Harvest: a Runner hunts the colonists it can sense while there is a bore to take them to.
            sim.getUnitIndex().gatherFaction(runnerPosX, runnerPosY,
                    senseRange + SENSE_GATHER_PADDING, Faction.CIVILIAN, nearby);
            for (int i = 0, n = nearby.size; i < n; i++) {
                long candidate = nearby.ids[i];
                if (!isHarvestPrey(candidate, tracker, sim)) continue;
                float candidateX = sim.world().x(candidate);
                float candidateY = sim.world().y(candidate);
                if (!canSense(runnerCellX, runnerCellY,
                        candidateX, candidateY, senseRange, sim)) continue;
                float distance = distanceSquared(runnerPosX, runnerPosY,
                        candidateX, candidateY);
                if (isBetter(candidate, distance, best, bestDistance)) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }

        if (best != 0L) {
            if (currentValid) {
                if (current == best) return current;
                float currentDistance = distanceSquared(
                        runnerPosX, runnerPosY,
                        sim.world().x(current), sim.world().y(current));
                if (currentDistance
                        <= bestDistance * CURRENT_TARGET_LEEWAY_SQUARED) {
                    return current;
                }
            }
            return best;
        }

        if (currentValid) return current;

        // Strategic pressure fallback: the swarm still advances when all
        // marines are beyond local sensing range, but civilians remain unknown
        // until first contact reveals them.
        long fallback = 0L;
        float fallbackDistance = Float.MAX_VALUE;
        for (Faction prey : PREY) {
            long candidate = shelterProtected
                    ? sim.getUnitIndex().nearestFaction(
                            runnerPosX, runnerPosY, prey,
                            id -> eligiblePreyKnownFaction(id, true, sim))
                    : sim.getUnitIndex().nearestFaction(
                            runnerPosX, runnerPosY, prey);
            if (candidate == 0L) continue;
            float distance = distanceSquared(runnerPosX, runnerPosY,
                    sim.world().x(candidate), sim.world().y(candidate));
            if (isBetter(candidate, distance, fallback, fallbackDistance)) {
                fallback = candidate;
                fallbackDistance = distance;
            }
        }
        return fallback;
    }

    private static boolean isEligibleRememberedTarget(
            long candidate, CivilianEvacuationTracker tracker,
            boolean shelterProtected, BattleSimulation sim) {
        if (candidate == 0L || sim.resolveUnit(candidate) == 0L) return false;
        if (Faction.DEFENDER.hostileTo(sim.identity().faction(candidate))) {
            return eligiblePreyKnownFaction(
                    candidate, shelterProtected, sim);
        }
        if (isHarvestPrey(candidate, tracker, sim)) return sim.swarmHarvest().isOpen();
        return !shelterProtected
                && tracker.state(candidate) == CivilianEvacuationTracker.State.ACTIVE;
    }

    /** An ambient colonist, outside the cohort: what harvest feeds on. */
    private static boolean isHarvestPrey(long candidate, CivilianEvacuationTracker tracker,
                                         BattleSimulation sim) {
        return sim.resolveUnit(candidate) != 0L
                && sim.identity().faction(candidate) == Faction.CIVILIAN
                && tracker.state(candidate) == null;
    }

    /** Caller has already established that the candidate is a prey faction. */
    static boolean eligiblePreyKnownFaction(
            long candidate, boolean shelterProtected, BattleSimulation sim) {
        if (!shelterProtected) return true;
        Squad squad = sim.squadOf(candidate);
        return squad == null || !sim.isShelterGuard(squad.id);
    }

    private static boolean isBetter(long candidate, float distance,
                                    long best, float bestDistance) {
        return distance < bestDistance
                || (distance == bestDistance && (best == 0L || candidate < best));
    }

    static boolean canSense(int runnerCellX, int runnerCellY,
                                    float candidateX, float candidateY,
                                    float senseRange, BattleSimulation sim) {
        return sim.getGrid().hasLineOfSightWithin(
                runnerCellX, runnerCellY,
                (int) Math.floor(candidateX), (int) Math.floor(candidateY),
                senseRange);
    }

    static void tickCooldown(long runner, BattleSimulation sim) {
        float cooldown = sim.combat().cooldownTimer(runner);
        if (cooldown > 0f) {
            sim.combat().setCooldownTimer(runner,
                    Math.max(0f, cooldown - BattleSimulation.TICK_DT));
        }
    }

    private static float distanceSquared(float ax, float ay,
                                         float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return dx * dx + dy * dy;
    }
}
