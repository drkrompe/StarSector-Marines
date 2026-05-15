package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    private final NavigationGrid grid;
    private final List<Unit> units = new ArrayList<>();

    private float tickAccumulator = 0f;
    private boolean complete = false;
    private Faction winner;

    public BattleSimulation(NavigationGrid grid) {
        this.grid = grid;
    }

    public NavigationGrid getGrid() { return grid; }
    public List<Unit> getUnits()    { return units; }
    public boolean isComplete()     { return complete; }
    public Faction getWinner()      { return winner; }

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
        checkWinCondition();
    }

    private void updateUnit(Unit u) {
        if (u.target == null || !u.target.isAlive()) {
            u.target = findNearestEnemy(u);
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
                u.target.hp -= u.attackDamage;
                u.cooldownTimer = u.attackCooldown;
            }
        } else {
            // Out of range — recompute path only between cells so mid-step
            // movement doesn't snap. The unit always commits to the current
            // step before reacting to a target that moved.
            if (u.moveProgress == 0f) {
                u.path = GridPathfinder.findPath(grid, u.cellX, u.cellY, u.target.cellX, u.target.cellY);
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

    private Unit findNearestEnemy(Unit self) {
        Unit best = null;
        float bestDist = Float.MAX_VALUE;
        for (Unit other : units) {
            if (!other.isAlive()) continue;
            if (other.faction == self.faction) continue;
            float d = cellDistance(self.cellX, self.cellY, other.cellX, other.cellY);
            if (d < bestDist) {
                bestDist = d;
                best = other;
            }
        }
        return best;
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
