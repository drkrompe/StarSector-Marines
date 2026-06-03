package com.dillon.starsectormarines.battle.decision;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.Random;

/**
 * Non-combatant ambient behavior. Two modes, gated by whether an armed unit is
 * within {@link #PERCEPTION_RADIUS} cells of the civilian:
 *
 * <ul>
 *   <li><b>Threatened</b> — picks a flee target on the opposite side of the map
 *       from the nearest threat and paths there. Re-picks after
 *       {@link #REPATH_CELL_THRESHOLD} cells of progress so the destination
 *       tracks the threat's motion.</li>
 *   <li><b>Idle</b> — wanders. The civilian walks to a random walkable cell
 *       within {@link #WANDER_MAX_RADIUS} cells, dwells there
 *       {@link #DWELL_MIN_SECONDS}..{@link #DWELL_MAX_SECONDS} seconds, then
 *       picks a new destination. Produces foot traffic on the map without any
 *       authored waypoint data.</li>
 * </ul>
 *
 * <p>Civilians never fire and don't pick combat targets; they're invisible to
 * {@link TacticalScoring#findBestTarget} (which filters by combatant flag) so
 * marines and militia ignore them unless someone explicitly orders a war crime.
 */
public final class FleeBehavior implements UnitBehavior {

    public static final FleeBehavior INSTANCE = new FleeBehavior();

    /** Cell radius a civilian senses combatants from. Smaller than weapon range — civilians don't react until shots are practically next to them. */
    public static final float PERCEPTION_RADIUS = 14f;
    /** Once a civilian has a flee path, they only re-pick a destination after they've moved this many cells along it. Stops every tick from rebuilding paths. */
    private static final int   REPATH_CELL_THRESHOLD = 4;
    /** Minimum cell-distance the flee destination must be from the threat. Anything closer doesn't count as "away" and is rejected in favor of staying put. */
    private static final float MIN_DISTANCE_FROM_THREAT = 8f;

    /** Minimum cell radius of a wander leg. Below this it reads as fidgeting instead of going somewhere. */
    private static final int   WANDER_MIN_RADIUS = 4;
    /** Maximum cell radius of a wander leg. Capped so civilians don't cross the whole map per trip — they look like they have local errands. */
    private static final int   WANDER_MAX_RADIUS = 14;
    /** Random destination sampling attempts before giving up this tick. A failure rolls a short dwell and we try again later. */
    private static final int   WANDER_SAMPLE_ATTEMPTS = 8;
    /** Dwell range when a civilian arrives at a wander destination. Long enough that 8 ambient civilians don't all look like they're sprint-pacing. */
    private static final float DWELL_MIN_SECONDS = 3f;
    private static final float DWELL_MAX_SECONDS = 8f;
    /** Short dwell when we couldn't find a wander cell this tick. Avoids re-rolling the (expensive) sample loop every single tick. */
    private static final float FAILED_SAMPLE_DWELL = 1f;

    private FleeBehavior() {}

    @Override
    public void update(Entity u, BattleSimulation sim) {
        Entity threat = findNearestThreat(u, sim);
        if (threat != null) {
            updateFleeing(u, threat, sim);
            return;
        }
        updateIdle(u, sim);
    }

    /**
     * Threat present — rebuild the flee path periodically and run. Cancels any
     * in-flight wander dwell so the civilian doesn't stand around mid-panic.
     */
    private static void updateFleeing(Entity u, Entity threat, BattleSimulation sim) {
        sim.world().setWanderDwellTimer(u.entityId, 0f);
        boolean needsRepath = u.pathIdx >= u.pathCellCount()
                || cellsTraveled(u) >= REPATH_CELL_THRESHOLD;
        if (needsRepath && sim.world().moveProgress(u.entityId) == 0f) {
            int[] dest = pickFleeDestination(u, threat, sim);
            if (dest != null) {
                sim.setPath(u, GridPathfinder.findPath(sim.getGrid(), sim.world().cellX(u.entityId), sim.world().cellY(u.entityId), dest[0], dest[1], sim.getOccupancyMap()));
            }
        }
        sim.advanceMovement(u);
    }

    /**
     * No threat in range — wander. Advances any active wander path; on arrival
     * starts a dwell, and when dwell expires picks a new destination.
     */
    private static void updateIdle(Entity u, BattleSimulation sim) {
        if (u.pathIdx < u.pathCellCount()) {
            sim.advanceMovement(u);
            if (u.pathIdx >= u.pathCellCount()) {
                // Arrived this tick — clear the path and start dwelling.
                sim.clearPath(u);
                sim.world().setWanderDwellTimer(u.entityId, randomDwellSeconds(u.rng));
            }
            return;
        }

        if (sim.world().wanderDwellTimer(u.entityId) > 0f) {
            sim.world().setWanderDwellTimer(u.entityId, sim.world().wanderDwellTimer(u.entityId) - BattleSimulation.TICK_DT);
            u.setRenderPos(sim.world().cellX(u.entityId), sim.world().cellY(u.entityId));
            sim.world().setMoveProgress(u.entityId, 0f);
            return;
        }

        if (sim.world().moveProgress(u.entityId) != 0f) return;
        int[] dest = pickWanderDestination(u, sim);
        if (dest == null) {
            sim.world().setWanderDwellTimer(u.entityId, FAILED_SAMPLE_DWELL);
            return;
        }
        sim.setPath(u, GridPathfinder.findPath(sim.getGrid(), sim.world().cellX(u.entityId), sim.world().cellY(u.entityId), dest[0], dest[1], sim.getOccupancyMap()));
        if (u.pathEmpty()) {
            // Pathfinder found no route (isolated room, blocked by walls). Dwell briefly and try elsewhere.
            sim.world().setWanderDwellTimer(u.entityId, FAILED_SAMPLE_DWELL);
            return;
        }
        sim.advanceMovement(u);
    }

    /**
     * Nearest combatant of any faction within {@link #PERCEPTION_RADIUS}. Both
     * sides spook civilians — they don't know which marines are friendly and
     * gunfire is gunfire regardless of who's behind the trigger.
     */
    private static Entity findNearestThreat(Entity self, BattleSimulation sim) {
        Entity best = null;
        float bestDist = PERCEPTION_RADIUS;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            Entity u = sim.liveUnitAt(i);
            if (u == self) continue;
            if (!u.type.combatant) continue;
            float d = TacticalScoring.cellDistance(sim.world().cellX(self.entityId), sim.world().cellY(self.entityId), sim.world().cellX(u.entityId), sim.world().cellY(u.entityId));
            if (d <= bestDist) {
                bestDist = d;
                best = u;
            }
        }
        return best;
    }

    /**
     * Picks a walkable cell along the vector pointing away from the threat. We
     * shoot a ray from the civilian's cell in the (self - threat) direction
     * and step out until we either leave the grid or hit a wall, taking the
     * furthest walkable cell along the ray as the destination. Falls back to
     * the map edge in that direction if the ray is short.
     */
    private static int[] pickFleeDestination(Entity self, Entity threat, BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        float dx = sim.world().cellX(self.entityId) - sim.world().cellX(threat.entityId);
        float dy = sim.world().cellY(self.entityId) - sim.world().cellY(threat.entityId);
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) {
            // Threat is on the same cell (rare). Pick a random cardinal away.
            dx = self.rng.nextFloat() * 2f - 1f;
            dy = self.rng.nextFloat() * 2f - 1f;
            len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 0.001f) { dx = 1f; dy = 0f; len = 1f; }
        }
        float nx = dx / len;
        float ny = dy / len;
        int[] best = null;
        int maxSteps = Math.max(grid.getWidth(), grid.getHeight());
        for (int step = 1; step <= maxSteps; step++) {
            int cx = sim.world().cellX(self.entityId) + Math.round(nx * step);
            int cy = sim.world().cellY(self.entityId) + Math.round(ny * step);
            if (!grid.inBounds(cx, cy)) break;
            if (!grid.isWalkable(cx, cy)) break;
            float distFromThreat = TacticalScoring.cellDistance(cx, cy, sim.world().cellX(threat.entityId), sim.world().cellY(threat.entityId));
            if (distFromThreat >= MIN_DISTANCE_FROM_THREAT) {
                best = new int[]{cx, cy};
            }
        }
        return best;
    }

    /**
     * Samples random cells inside a square ring around the civilian and returns
     * the first walkable one at least {@link #WANDER_MIN_RADIUS} cells away.
     * Square-ring sampling is biased toward the corners but is cheap and the
     * bias doesn't read in motion — the destinations still look local.
     */
    private static int[] pickWanderDestination(Entity u, BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        Random rng = u.rng;
        int span = WANDER_MAX_RADIUS * 2 + 1;
        for (int i = 0; i < WANDER_SAMPLE_ATTEMPTS; i++) {
            int dx = rng.nextInt(span) - WANDER_MAX_RADIUS;
            int dy = rng.nextInt(span) - WANDER_MAX_RADIUS;
            if (Math.abs(dx) + Math.abs(dy) < WANDER_MIN_RADIUS) continue;
            int cx = sim.world().cellX(u.entityId) + dx;
            int cy = sim.world().cellY(u.entityId) + dy;
            if (!grid.inBounds(cx, cy)) continue;
            if (!grid.isWalkable(cx, cy)) continue;
            if (cx == sim.world().cellX(u.entityId) && cy == sim.world().cellY(u.entityId)) continue;
            return new int[]{cx, cy};
        }
        return null;
    }

    private static float randomDwellSeconds(Random rng) {
        return DWELL_MIN_SECONDS + rng.nextFloat() * (DWELL_MAX_SECONDS - DWELL_MIN_SECONDS);
    }

    /**
     * Approximates how far we've travelled along the current path by counting
     * the path indices already consumed. Cheap stand-in for true arc length —
     * the path is grid-aligned with 1-cell steps so {@code pathIdx} ≈ cells
     * moved, which is good enough to gate re-pathing.
     */
    private static int cellsTraveled(Entity u) {
        return u.pathIdx;
    }
}
