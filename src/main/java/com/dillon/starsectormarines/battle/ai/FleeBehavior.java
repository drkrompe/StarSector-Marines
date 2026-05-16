package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.Collections;

/**
 * Non-combatant panic behavior. The unit wanders idle until any armed unit
 * comes within {@link #PERCEPTION_RADIUS} cells — then it picks a flee target
 * on the opposite side of the map from the nearest threat and paths there.
 * On arrival (or when the threat clears) it idles again.
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

    private FleeBehavior() {}

    @Override
    public void update(Unit u, BattleSimulation sim) {
        Unit threat = findNearestThreat(u, sim);
        if (threat == null) {
            // Nothing scary nearby — stop where we are. Civilians don't wander
            // procedurally; standing around looking at phones is in-character.
            sim.setPath(u, Collections.emptyList());
            u.moveProgress = 0f;
            u.renderX = u.cellX;
            u.renderY = u.cellY;
            return;
        }

        boolean needsRepath = u.path.isEmpty()
                || u.pathIdx >= u.path.size()
                || cellsTraveled(u) >= REPATH_CELL_THRESHOLD;
        if (needsRepath && u.moveProgress == 0f) {
            int[] dest = pickFleeDestination(u, threat, sim);
            if (dest != null) {
                sim.setPath(u, GridPathfinder.findPath(sim.getGrid(), u.cellX, u.cellY, dest[0], dest[1], sim.getOccupancyMap()));
            }
        }
        sim.advanceMovement(u);
    }

    /**
     * Nearest combatant of any faction within {@link #PERCEPTION_RADIUS}. Both
     * sides spook civilians — they don't know which marines are friendly and
     * gunfire is gunfire regardless of who's behind the trigger.
     */
    private static Unit findNearestThreat(Unit self, BattleSimulation sim) {
        Unit best = null;
        float bestDist = PERCEPTION_RADIUS;
        for (Unit u : sim.getUnits()) {
            if (!u.isAlive() || u == self) continue;
            if (!u.type.combatant) continue;
            float d = TacticalScoring.cellDistance(self.cellX, self.cellY, u.cellX, u.cellY);
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
    private static int[] pickFleeDestination(Unit self, Unit threat, BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        float dx = self.cellX - threat.cellX;
        float dy = self.cellY - threat.cellY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) {
            // Threat is on the same cell (rare). Pick a random cardinal away.
            dx = sim.getRng().nextFloat() * 2f - 1f;
            dy = sim.getRng().nextFloat() * 2f - 1f;
            len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 0.001f) { dx = 1f; dy = 0f; len = 1f; }
        }
        float nx = dx / len;
        float ny = dy / len;
        int[] best = null;
        int maxSteps = Math.max(grid.getWidth(), grid.getHeight());
        for (int step = 1; step <= maxSteps; step++) {
            int cx = self.cellX + Math.round(nx * step);
            int cy = self.cellY + Math.round(ny * step);
            if (!grid.inBounds(cx, cy)) break;
            if (!grid.isWalkable(cx, cy)) break;
            float distFromThreat = TacticalScoring.cellDistance(cx, cy, threat.cellX, threat.cellY);
            if (distFromThreat >= MIN_DISTANCE_FROM_THREAT) {
                best = new int[]{cx, cy};
            }
        }
        return best;
    }

    /**
     * Approximates how far we've travelled along the current path by counting
     * the path indices already consumed. Cheap stand-in for true arc length —
     * the path is grid-aligned with 1-cell steps so {@code pathIdx} ≈ cells
     * moved, which is good enough to gate re-pathing.
     */
    private static int cellsTraveled(Unit u) {
        return u.pathIdx;
    }
}
