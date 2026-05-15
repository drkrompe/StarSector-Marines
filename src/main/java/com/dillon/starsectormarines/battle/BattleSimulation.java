package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
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

    /** Sim seconds a tracer stays visible after being fired. */
    private static final float SHOT_LIFETIME = 0.15f;
    /** Min/max near-miss offset (cells) from target cell-center on a missed shot. */
    private static final float MISS_OFFSET_MIN = 0.5f;
    private static final float MISS_OFFSET_MAX = 2.0f;

    private final NavigationGrid grid;
    private final List<Unit> units = new ArrayList<>();
    private final List<ShotEvent> activeShots = new ArrayList<>();
    private final Random rng = new Random();

    private float tickAccumulator = 0f;
    private boolean complete = false;
    private Faction winner;

    public BattleSimulation(NavigationGrid grid) {
        this.grid = grid;
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
        for (Unit u : units) {
            if (!u.isAlive()) continue;
            updateUnit(u);
        }
        advanceShots();
        checkWinCondition();
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
        if (u.target == null || !u.target.isAlive()) {
            u.target = findBestTarget(u);
        }
        if (u.target == null) return; // nothing to do — usually a win-condition frame

        if (u.cooldownTimer > 0f) u.cooldownTimer -= TICK_DT;

        float dist = cellDistance(u.cellX, u.cellY, u.target.cellX, u.target.cellY);
        if (dist <= u.attackRange) {
            // In range — snap to current cell, stop, fire when off cooldown.
            u.path = Collections.emptyList();
            u.pathIdx = 0;
            u.moveProgress = 0f;
            u.renderX = u.cellX;
            u.renderY = u.cellY;

            if (u.cooldownTimer <= 0f) {
                fireShot(u, u.target);
                u.cooldownTimer = u.attackCooldown;
            }
        } else {
            // Out of range — path to a free cell at attackRange around the target,
            // not the target's own cell. With 12v12 piling on a single defender,
            // pathing straight at the target bunches everyone on one tile;
            // firing positions naturally spread the squad into a ring.
            //
            // Re-path only between cells so mid-step movement doesn't snap. The
            // unit always commits to its current step before reacting to a moved
            // target or a shifted firing position.
            if (u.moveProgress == 0f) {
                int[] firingPos = findFiringPosition(u, u.target);
                u.path = GridPathfinder.findPath(grid, u.cellX, u.cellY, firingPos[0], firingPos[1]);
                u.pathIdx = 1; // path[0] is the cell we're already in
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
     * crowding penalty. Without the penalty all squadmates target the nearest
     * defender and dogpile; the penalty makes a defender 6 cells "further" for
     * every ally already engaging it, so the squad spreads fire across the
     * defender line.
     */
    private Unit findBestTarget(Unit self) {
        Unit best = null;
        float bestScore = Float.MAX_VALUE;
        for (Unit other : units) {
            if (!other.isAlive()) continue;
            if (other.faction == self.faction) continue;
            float d = cellDistance(self.cellX, self.cellY, other.cellX, other.cellY);
            int engagers = countAlliesTargeting(self, other);
            float score = d + TARGET_CROWDING_COST * engagers;
            if (score < bestScore) {
                bestScore = score;
                best = other;
            }
        }
        return best;
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
     * Picks a free walkable cell at attack range from the target, minimizing
     * (distance from self) + (occupancy penalty). With 12 attackers and a
     * range-4 ring, ~40 candidate cells exist around one target — plenty for
     * the squad to ring up without stacking.
     *
     * <p>Returns the target's own cell as a fallback if no candidate is found
     * (e.g., target boxed in by walls); the in-range check in updateUnit then
     * still triggers fire once we close to melee.
     */
    private int[] findFiringPosition(Unit self, Unit target) {
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

                float distFromTarget = (float) Math.sqrt(dx * dx + dy * dy);
                if (distFromTarget > self.attackRange) continue;
                if (distFromTarget < FIRING_MIN_DISTANCE) continue;

                int occupants = countUnitsAt(self, cx, cy);
                float distFromSelf = cellDistance(self.cellX, self.cellY, cx, cy);
                float score = distFromSelf + FIRING_OCCUPANCY_COST * occupants;
                if (score < bestScore) {
                    bestScore = score;
                    best = new int[]{cx, cy};
                }
            }
        }
        return best != null ? best : new int[]{tx, ty};
    }

    /**
     * Rolls accuracy, applies damage on a hit, and emits a {@link ShotEvent}
     * either way so the renderer can draw a tracer. The miss endpoint is a
     * random angle + 0.5..2.0 cell offset from target cell-center — it reads
     * as a stray round whizzing past the target rather than a deleted dud.
     */
    private void fireShot(Unit shooter, Unit target) {
        boolean hit = rng.nextFloat() < shooter.accuracy;
        if (hit) target.hp -= shooter.attackDamage;

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

    /** Count of other (non-self) alive units occupying or moving toward cell (cx, cy). */
    private int countUnitsAt(Unit self, int cx, int cy) {
        int n = 0;
        for (Unit u : units) {
            if (u == self || !u.isAlive()) continue;
            if (u.cellX == cx && u.cellY == cy) n++;
        }
        return n;
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
