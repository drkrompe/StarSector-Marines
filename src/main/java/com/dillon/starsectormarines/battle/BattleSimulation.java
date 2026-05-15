package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Headless auto-battler simulation. Owns the {@link NavigationGrid}, the unit
 * roster, and the per-tick logic that drives target acquisition, pathfinding,
 * movement interpolation, and combat resolution.
 *
 * <p>The caller drives time via {@link #advance(float)}, which accumulates real
 * time into fixed 30Hz ticks. That keeps simulation determinism independent of
 * the render framerate and lets speed/pause controls work by scaling the input
 * dt (or feeding zero) — the sim itself doesn't care.
 *
 * <p>v1 behavior, each tick:
 * <ul>
 *   <li>Each alive unit refreshes its target to the nearest alive enemy.</li>
 *   <li>If a target is in {@link Unit#attackRange}, the unit stops moving and
 *       fires on {@link Unit#attackCooldown}, dealing {@link Unit#attackDamage}.</li>
 *   <li>Otherwise the unit re-pathfinds (only when between cells, to avoid a
 *       visual jump mid-step) and advances {@code moveProgress} along the path
 *       at {@link Unit#moveSpeed} cells/sec.</li>
 *   <li>When one faction runs out of alive units, the sim flags
 *       {@link #isComplete()} and records the winner.</li>
 * </ul>
 *
 * <p>Pathfinding runs every tick a unit is moving — at 16 units × 30Hz on a
 * 24×16 grid it's a few thousand cell expansions per second, well inside the
 * budget. Spatial indexing for target search can come later if we scale up.
 */
public class BattleSimulation {

    /** Fixed simulation timestep — 30Hz. */
    public static final float TICK_DT = 1f / 30f;

    /** Per-engaging-ally penalty added to target selection — pushes the squad to spread fire instead of dogpiling the closest enemy. */
    private static final float TARGET_CROWDING_COST = 6f;
    /** Per-ally-on-cell penalty added when picking a firing position around a target — pushes units into a spread ring. */
    private static final float FIRING_OCCUPANCY_COST = 4f;
    /** Minimum cell-distance from target when picking a firing position. Avoids picking the target's own cell. */
    private static final float FIRING_MIN_DISTANCE = 0.7f;
    /** Per-adjacent-wall bonus subtracted from firing-position score. Pushes units to peek from corners and wall edges instead of standing in the open. */
    private static final float FIRING_COVER_BONUS = 3f;

    /** Sim seconds a tracer stays visible after being fired. */
    private static final float SHOT_LIFETIME = 0.15f;
    /** Min/max near-miss offset (cells) from target cell-center on a missed shot. */
    private static final float MISS_OFFSET_MIN = 0.5f;
    private static final float MISS_OFFSET_MAX = 2.0f;

    /** Probability that, after firing, a unit picks a different firing position. Models routine sidestepping between shots. */
    private static final float REPOSITION_CHANCE = 0.30f;
    /** Probability a hit puts the target into fall-back. Rolled once per hit; ignored if already falling back. */
    private static final float FALLBACK_CHANCE   = 0.25f;
    /** Sim seconds a unit stays in fall-back state once entered. After this, normal engagement resumes. */
    private static final float FALLBACK_DURATION = 3.5f;
    /** Cell radius searched for a fall-back position around the hit unit. */
    private static final int   FALLBACK_SCAN_RANGE = 8;
    /** Per-ally-on-cell penalty in fall-back scoring. */
    private static final float FALLBACK_OCCUPANCY_COST = 4f;
    /** Per-adjacent-wall bonus in fall-back scoring (cover preference). */
    private static final float FALLBACK_COVER_BONUS    = 2f;

    private final NavigationGrid grid;
    private final List<Unit> units = new ArrayList<>();
    private final List<ShotEvent> activeShots = new ArrayList<>();
    private final Random rng = new Random();

    /** Per-cell unit count, rebuilt at the start of each tick. Passed to the pathfinder so units route around ally-held cells. */
    private final byte[] occupancyMap;

    private float tickAccumulator = 0f;
    private boolean complete = false;
    private Faction winner;

    public BattleSimulation(NavigationGrid grid) {
        this.grid = grid;
        this.occupancyMap = new byte[grid.getWidth() * grid.getHeight()];
    }

    public NavigationGrid getGrid()        { return grid; }
    public List<Unit> getUnits()           { return units; }
    public List<ShotEvent> getActiveShots(){ return activeShots; }
    public boolean isComplete()            { return complete; }
    public Faction getWinner()             { return winner; }

    public void addUnit(Unit u) {
        units.add(u);
    }

    /**
     * Drives the simulation forward. Accepts any real-time delta; internally
     * runs zero or more fixed 30Hz ticks until the accumulator is drained.
     * Returns immediately once the battle is complete.
     */
    public void advance(float dt) {
        if (complete) return;
        tickAccumulator += dt;
        while (tickAccumulator >= TICK_DT) {
            tick();
            tickAccumulator -= TICK_DT;
            if (complete) break;
        }
    }

    private void tick() {
        rebuildOccupancyMap();
        for (Unit u : units) {
            if (!u.isAlive()) continue;
            updateUnit(u);
        }
        advanceShots();
        checkWinCondition();
    }

    /**
     * Counts alive units per cell into {@link #occupancyMap}, including each
     * unit's path destination cell (if different from its current cell). This
     * makes destination cells visible to firing-position and fall-back scoring,
     * so units don't all converge on the same goal. Saturates at 255.
     *
     * <p>The map is also incrementally updated within a tick — when a unit
     * re-paths in {@link #updateUnit}, the old destination is decremented and
     * the new one incremented — so units picking positions later in the same
     * tick see the freshest information.
     */
    private void rebuildOccupancyMap() {
        Arrays.fill(occupancyMap, (byte) 0);
        for (Unit u : units) {
            if (!u.isAlive()) continue;
            incrementOccupancy(u.cellX, u.cellY);
            int[] dest = pathDestination(u);
            if (dest != null && (dest[0] != u.cellX || dest[1] != u.cellY)) {
                incrementOccupancy(dest[0], dest[1]);
            }
        }
    }

    private void incrementOccupancy(int x, int y) {
        if (!grid.inBounds(x, y)) return;
        int idx = y * grid.getWidth() + x;
        int cur = occupancyMap[idx] & 0xFF;
        if (cur < 255) occupancyMap[idx] = (byte) (cur + 1);
    }

    private void decrementOccupancy(int x, int y) {
        if (!grid.inBounds(x, y)) return;
        int idx = y * grid.getWidth() + x;
        int cur = occupancyMap[idx] & 0xFF;
        if (cur > 0) occupancyMap[idx] = (byte) (cur - 1);
    }

    /** Returns the unit's final path cell, or null if the path is empty. */
    private static int[] pathDestination(Unit u) {
        return u.path.isEmpty() ? null : u.path.get(u.path.size() - 1);
    }

    /**
     * Occupancy count at cell (cx, cy), excluding self's own contributions
     * (current cell + path destination). Used so a unit doesn't penalize
     * itself when scoring its own current/intended position.
     */
    private int occupantsExcludingSelf(Unit self, int cx, int cy) {
        if (!grid.inBounds(cx, cy)) return 0;
        int n = occupancyMap[cy * grid.getWidth() + cx] & 0xFF;
        if (cx == self.cellX && cy == self.cellY) n--;
        int[] selfDest = pathDestination(self);
        if (selfDest != null && selfDest[0] == cx && selfDest[1] == cy
                && (selfDest[0] != self.cellX || selfDest[1] != self.cellY)) {
            n--;
        }
        return Math.max(0, n);
    }

    /**
     * Replaces a unit's path and keeps {@link #occupancyMap} in sync — the old
     * destination loses its occupancy contribution and the new destination
     * gains one (subject to start-cell guards).
     */
    private void setPath(Unit u, List<int[]> newPath) {
        int[] oldDest = pathDestination(u);
        if (oldDest != null && (oldDest[0] != u.cellX || oldDest[1] != u.cellY)) {
            decrementOccupancy(oldDest[0], oldDest[1]);
        }
        u.path = newPath;
        u.pathIdx = newPath.isEmpty() ? 0 : 1;
        int[] newDest = pathDestination(u);
        if (newDest != null && (newDest[0] != u.cellX || newDest[1] != u.cellY)) {
            incrementOccupancy(newDest[0], newDest[1]);
        }
    }

    /** Ages every active shot by one tick and drops expired ones. Reverse iteration for in-place removal. */
    private void advanceShots() {
        for (int i = activeShots.size() - 1; i >= 0; i--) {
            ShotEvent s = activeShots.get(i);
            s.lifetime -= TICK_DT;
            if (s.lifetime <= 0f) activeShots.remove(i);
        }
    }

    private void updateUnit(Unit u) {
        // Fall-back state — unit was recently hit and is breaking contact.
        // Path toward an out-of-LOS cell, then hold until the timer expires.
        if (u.fallbackTimer > 0f) {
            u.fallbackTimer -= TICK_DT;
            int fx = u.fallbackCellX;
            int fy = u.fallbackCellY;
            if (u.cellX == fx && u.cellY == fy) {
                setPath(u, Collections.emptyList());
                u.moveProgress = 0f;
                u.renderX = u.cellX;
                u.renderY = u.cellY;
                return;
            }
            if (u.moveProgress == 0f) {
                setPath(u, GridPathfinder.findPath(grid, u.cellX, u.cellY, fx, fy, occupancyMap));
            }
            advanceMovement(u);
            return;
        }

        if (u.target == null || !u.target.isAlive()) {
            u.target = findBestTarget(u);
        }
        if (u.target == null) return; // nothing to do — usually a win-condition frame

        if (u.cooldownTimer > 0f) u.cooldownTimer -= TICK_DT;

        float dist = cellDistance(u.cellX, u.cellY, u.target.cellX, u.target.cellY);
        boolean inRange = dist <= u.attackRange;
        boolean visible = grid.hasLineOfSight(u.cellX, u.cellY, u.target.cellX, u.target.cellY);
        if (inRange && visible) {
            if (u.cooldownTimer <= 0f) {
                fireShot(u, u.target);
                u.cooldownTimer = u.attackCooldown;
                // Reposition roll — set a path to a different firing position.
                // Path runs while cooldown counts down; the unit fires from the
                // new spot once cooldown clears, producing visible "shoot, sidestep,
                // shoot" cadence instead of static turrets.
                if (rng.nextFloat() < REPOSITION_CHANCE) {
                    int[] firingPos = findFiringPosition(u, u.target, u.cellX, u.cellY);
                    if (firingPos[0] != u.cellX || firingPos[1] != u.cellY) {
                        setPath(u, GridPathfinder.findPath(grid, u.cellX, u.cellY, firingPos[0], firingPos[1], occupancyMap));
                    }
                }
            }
            // Continue any path in progress (from a recent reposition). If no
            // path, hold position and let renderX/Y snap to the logical cell.
            if (!u.path.isEmpty() && u.pathIdx < u.path.size()) {
                advanceMovement(u);
            } else {
                u.moveProgress = 0f;
                u.renderX = u.cellX;
                u.renderY = u.cellY;
            }
        } else {
            // Out of range or out of sight — path to a firing position near target.
            // Re-path only between cells so mid-step movement doesn't snap.
            if (u.moveProgress == 0f) {
                int[] firingPos = findFiringPosition(u, u.target);
                setPath(u, GridPathfinder.findPath(grid, u.cellX, u.cellY, firingPos[0], firingPos[1], occupancyMap));
            }
            advanceMovement(u);
        }
    }

    private void advanceMovement(Unit u) {
        if (u.path.isEmpty() || u.pathIdx >= u.path.size()) return;

        int[] nextCell = u.path.get(u.pathIdx);
        float dx = nextCell[0] - u.cellX;
        float dy = nextCell[1] - u.cellY;
        float cellDist = (float) Math.sqrt(dx * dx + dy * dy);
        if (cellDist < 0.0001f) {
            u.pathIdx++;
            return;
        }

        float stepLength = u.moveSpeed * TICK_DT; // cell-units this tick
        u.moveProgress += stepLength / cellDist;

        if (u.moveProgress >= 1f) {
            u.cellX = nextCell[0];
            u.cellY = nextCell[1];
            u.renderX = u.cellX;
            u.renderY = u.cellY;
            u.moveProgress = 0f;
            u.pathIdx++;
        } else {
            u.renderX = u.cellX + dx * u.moveProgress;
            u.renderY = u.cellY + dy * u.moveProgress;
        }
    }

    /**
     * Picks the lowest-scored enemy where score = cell-distance + a per-engager
     * crowding penalty. The squad spreads fire across the defender line instead
     * of dogpiling.
     *
     * <p>Prefers targets the unit has line-of-sight to. If none of the enemies
     * are visible from this cell, falls back to the nearest enemy (any LOS) so
     * the unit pathfinders toward them and visibility eventually opens.
     */
    private Unit findBestTarget(Unit self) {
        Unit bestVisible = null;
        float bestVisibleScore = Float.MAX_VALUE;
        Unit bestAny = null;
        float bestAnyDist = Float.MAX_VALUE;

        for (Unit other : units) {
            if (!other.isAlive()) continue;
            if (other.faction == self.faction) continue;
            float d = cellDistance(self.cellX, self.cellY, other.cellX, other.cellY);
            if (d < bestAnyDist) {
                bestAnyDist = d;
                bestAny = other;
            }
            if (!grid.hasLineOfSight(self.cellX, self.cellY, other.cellX, other.cellY)) continue;
            int engagers = countAlliesTargeting(self, other);
            float score = d + TARGET_CROWDING_COST * engagers;
            if (score < bestVisibleScore) {
                bestVisibleScore = score;
                bestVisible = other;
            }
        }
        return bestVisible != null ? bestVisible : bestAny;
    }

    private int countAlliesTargeting(Unit self, Unit target) {
        int n = 0;
        for (Unit u : units) {
            if (u == self || !u.isAlive()) continue;
            if (u.faction != self.faction) continue;
            if (u.target == target) n++;
        }
        return n;
    }

    /**
     * Picks a walkable cell at attack range from the target, minimizing
     * {@code distFromSelf + occupancy_penalty - cover_bonus}. Candidates must
     * have line of sight to the target — a cell on the far side of a wall is
     * useless even at range.
     *
     * <p>The cover bonus rewards cells with adjacent walls. With the bonus,
     * units gravitate to building corners and wall edges and "peek" around
     * them at the target rather than standing in open lanes.
     *
     * <p>Returns the target's own cell as a fallback if no candidate is found
     * (e.g., target boxed in by walls, no visible spots in range); the
     * in-range + LOS check in updateUnit then keeps the unit pathing closer
     * until something opens.
     */
    private int[] findFiringPosition(Unit self, Unit target) {
        return findFiringPosition(self, target, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    private int[] findFiringPosition(Unit self, Unit target, int rejectX, int rejectY) {
        int range = Math.max(1, (int) Math.floor(self.attackRange));
        int tx = target.cellX;
        int ty = target.cellY;

        int[] best = null;
        float bestScore = Float.MAX_VALUE;
        for (int dy = -range; dy <= range; dy++) {
            for (int dx = -range; dx <= range; dx++) {
                int cx = tx + dx;
                int cy = ty + dy;
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) continue;
                if (cx == rejectX && cy == rejectY) continue;

                float distFromTarget = (float) Math.sqrt(dx * dx + dy * dy);
                if (distFromTarget > self.attackRange) continue;
                if (distFromTarget < FIRING_MIN_DISTANCE) continue;
                if (!grid.hasLineOfSight(cx, cy, tx, ty)) continue;

                int occupants = occupantsExcludingSelf(self, cx, cy);
                int coverWalls = countAdjacentWalls(cx, cy);
                float distFromSelf = cellDistance(self.cellX, self.cellY, cx, cy);
                float score = distFromSelf
                        + FIRING_OCCUPANCY_COST * occupants
                        - FIRING_COVER_BONUS * coverWalls;
                if (score < bestScore) {
                    bestScore = score;
                    best = new int[]{cx, cy};
                }
            }
        }
        return best != null ? best : new int[]{tx, ty};
    }

    /** Count of cardinal-neighbor cells that are walls or out of bounds — used as a cover heuristic. */
    private int countAdjacentWalls(int cx, int cy) {
        int n = 0;
        if (!grid.inBounds(cx + 1, cy) || !grid.isWalkable(cx + 1, cy)) n++;
        if (!grid.inBounds(cx - 1, cy) || !grid.isWalkable(cx - 1, cy)) n++;
        if (!grid.inBounds(cx, cy + 1) || !grid.isWalkable(cx, cy + 1)) n++;
        if (!grid.inBounds(cx, cy - 1) || !grid.isWalkable(cx, cy - 1)) n++;
        return n;
    }

    /**
     * Rolls accuracy, applies damage on a hit, and emits a {@link ShotEvent}
     * either way so the renderer can draw a tracer. The miss endpoint is a
     * random angle + 0.5..2.0 cell offset from target cell-center — it reads
     * as a stray round whizzing past the target rather than a deleted dud.
     */
    private void fireShot(Unit shooter, Unit target) {
        boolean hit = rng.nextFloat() < shooter.accuracy;
        if (hit) {
            target.hp -= shooter.attackDamage;
            // Roll fall-back on hit. Skip if target is dead or already breaking contact.
            if (target.isAlive() && target.fallbackTimer <= 0f
                    && rng.nextFloat() < FALLBACK_CHANCE) {
                int[] fallback = findFallbackPosition(target);
                if (fallback[0] != target.cellX || fallback[1] != target.cellY) {
                    target.fallbackCellX = fallback[0];
                    target.fallbackCellY = fallback[1];
                    target.fallbackTimer = FALLBACK_DURATION;
                    // Stale path no longer applies — target will re-path to the
                    // fall-back cell on its next updateUnit pass.
                    setPath(target, Collections.emptyList());
                }
            }
        }

        float fromX = shooter.cellX + 0.5f;
        float fromY = shooter.cellY + 0.5f;
        float toX, toY;
        if (hit) {
            toX = target.cellX + 0.5f;
            toY = target.cellY + 0.5f;
        } else {
            float angle = rng.nextFloat() * (float) (Math.PI * 2);
            float spread = MISS_OFFSET_MIN + rng.nextFloat() * (MISS_OFFSET_MAX - MISS_OFFSET_MIN);
            toX = target.cellX + 0.5f + (float) Math.cos(angle) * spread;
            toY = target.cellY + 0.5f + (float) Math.sin(angle) * spread;
        }
        activeShots.add(new ShotEvent(fromX, fromY, toX, toY, hit, shooter.faction, SHOT_LIFETIME));
    }

    /**
     * Scans cells within {@link #FALLBACK_SCAN_RANGE} of {@code self} for a
     * walkable spot with no LOS to any alive enemy. Scores candidates by
     * {@code distFromSelf + occupancyPenalty - coverBonus} — cells close to
     * self, off everyone else's path, with cover-adjacent walls.
     *
     * <p>Falls through to {@code self}'s current cell if nothing qualifies —
     * the unit just holds in place for the fall-back duration and re-engages
     * from where they were hit.
     */
    private int[] findFallbackPosition(Unit self) {
        int sx = self.cellX;
        int sy = self.cellY;
        int[] best = null;
        float bestScore = Float.MAX_VALUE;
        for (int dy = -FALLBACK_SCAN_RANGE; dy <= FALLBACK_SCAN_RANGE; dy++) {
            for (int dx = -FALLBACK_SCAN_RANGE; dx <= FALLBACK_SCAN_RANGE; dx++) {
                int cx = sx + dx;
                int cy = sy + dy;
                if (!grid.inBounds(cx, cy) || !grid.isWalkable(cx, cy)) continue;
                if (!isHiddenFromAllEnemies(self, cx, cy)) continue;

                int occupants = occupantsExcludingSelf(self, cx, cy);
                int coverWalls = countAdjacentWalls(cx, cy);
                float distFromSelf = cellDistance(sx, sy, cx, cy);
                float score = distFromSelf
                        + FALLBACK_OCCUPANCY_COST * occupants
                        - FALLBACK_COVER_BONUS * coverWalls;
                if (score < bestScore) {
                    bestScore = score;
                    best = new int[]{cx, cy};
                }
            }
        }
        return best != null ? best : new int[]{sx, sy};
    }

    private boolean isHiddenFromAllEnemies(Unit self, int cx, int cy) {
        for (Unit other : units) {
            if (!other.isAlive()) continue;
            if (other.faction == self.faction) continue;
            if (grid.hasLineOfSight(cx, cy, other.cellX, other.cellY)) return false;
        }
        return true;
    }

    private void checkWinCondition() {
        boolean marineAlive = false;
        boolean defenderAlive = false;
        for (Unit u : units) {
            if (!u.isAlive()) continue;
            if (u.faction == Faction.MARINE)        marineAlive = true;
            else if (u.faction == Faction.DEFENDER) defenderAlive = true;
            if (marineAlive && defenderAlive) return; // both sides still in
        }
        complete = true;
        winner = marineAlive ? Faction.MARINE
                : (defenderAlive ? Faction.DEFENDER : null);
    }

    private static float cellDistance(int x0, int y0, int x1, int y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
