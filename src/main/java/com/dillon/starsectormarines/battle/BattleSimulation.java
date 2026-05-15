package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

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
    private final List<Shuttle> shuttles = new ArrayList<>();
    private final List<ShotEvent> activeShots = new ArrayList<>();
    /** Shots fired during the last {@link #advance(float)} call. Cleared on each advance, populated per tick. Drives one-shot audio in the renderer. */
    private final List<ShotEvent> shotsThisFrame = new ArrayList<>();
    /** Units that transitioned from alive to dead during the last {@link #advance(float)} call. Same lifecycle as {@link #shotsThisFrame}. */
    private final List<Unit> deathsThisFrame = new ArrayList<>();
    private final Random rng = new Random();

    /** Counter for IDs of marines deboarded from shuttles. Bumped per spawn — "m0", "m1", ... like the pre-shuttle setup. */
    private int deboardedMarineCount = 0;
    /** Max BFS radius from the LZ when looking for a free deboard cell. Past this we drop the deboard for this tick. */
    private static final int DEBOARD_SCAN_RADIUS = 5;

    /** Visual scale of a shuttle at cruising altitude (sells "I am up high"). Lerped down to 1.0 at touchdown. */
    private static final float SHUTTLE_CRUISE_SCALE = 1.5f;
    /** Per-leg max bow as a fraction of the leg's chord length. Capped by {@link #SHUTTLE_CURVE_ABS_MAX} to keep long legs from arcing across the map. */
    private static final float SHUTTLE_CURVE_REL_MAX = 0.15f;
    /** Absolute cell-cap on the perpendicular bow. */
    private static final float SHUTTLE_CURVE_ABS_MAX = 8f;
    /** Floor so even short legs get a little wobble — straight-line shuttles read as cardboard. */
    private static final float SHUTTLE_CURVE_MIN = 1.5f;
    /** Frequency (Hz) of the in-flight scale wobble. Slower than a heartbeat — reads as atmospheric drift, not a flicker. */
    private static final float SHUTTLE_WOBBLE_HZ = 0.7f;
    /** Peak amplitude of the wobble, in scale units. ±0.04 on top of a 1.5 cruise = ~2.7%; well inside the 5% target. */
    private static final float SHUTTLE_WOBBLE_AMPLITUDE = 0.04f;
    /** Curve-strength multiplier for the DEPARTING leg — wider bow than INCOMING so takeoff reads as a banking loop, not a straight climb. */
    private static final float SHUTTLE_DEPART_CURVE_MULT = 2.5f;
    /** Fraction of the DEPARTING leg over which facing eases from landed direction into the leg tangent. */
    private static final float SHUTTLE_DEPART_FACING_EASE = 0.4f;

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
    public List<Shuttle> getShuttles()     { return shuttles; }
    public List<ShotEvent> getActiveShots(){ return activeShots; }
    public List<ShotEvent> getShotsThisFrame() { return shotsThisFrame; }
    public List<Unit> getDeathsThisFrame()     { return deathsThisFrame; }
    public boolean isComplete()            { return complete; }
    public Faction getWinner()             { return winner; }

    public void addUnit(Unit u) {
        units.add(u);
    }

    public void addShuttle(Shuttle s) {
        shuttles.add(s);
    }

    /**
     * Drives the simulation forward. Accepts any real-time delta; internally
     * runs zero or more fixed 30Hz ticks until the accumulator is drained.
     * Returns immediately once the battle is complete.
     */
    public void advance(float dt) {
        // Clear unconditionally so a paused caller doesn't keep replaying the previous frame's events.
        shotsThisFrame.clear();
        deathsThisFrame.clear();
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
        // Shuttles tick AFTER units so new deboarded marines aren't iterated
        // mid-loop. They'll be picked up by next tick's occupancy + target pass.
        advanceShuttles();
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
            boolean wasAlive = target.isAlive();
            target.hp -= shooter.attackDamage;
            if (wasAlive && !target.isAlive()) {
                deathsThisFrame.add(target);
            }
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
        ShotEvent evt = new ShotEvent(fromX, fromY, toX, toY, hit, shooter.faction, SHOT_LIFETIME);
        activeShots.add(evt);
        shotsThisFrame.add(evt);
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
        }
        // Pending / inbound / deboarding shuttles count as marines-in-play —
        // otherwise the first tick would insta-complete as defender win before
        // any marine has touched the ground.
        if (!marineAlive) {
            for (Shuttle s : shuttles) {
                if (s.marinesRemaining > 0
                        && s.state != Shuttle.State.DEPARTING
                        && s.state != Shuttle.State.GONE) {
                    marineAlive = true;
                    break;
                }
            }
        }
        if (marineAlive && defenderAlive) return;
        complete = true;
        winner = marineAlive ? Faction.MARINE
                : (defenderAlive ? Faction.DEFENDER : null);
    }

    private static float cellDistance(int x0, int y0, int x1, int y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Advances each shuttle's state machine by one tick. PENDING burns down the
     * stagger delay; INCOMING/DEPARTING fly along their entry→LZ / LZ→exit
     * vectors at {@link ShuttleType#flightSpeed}; LANDED ticks a deboard timer
     * and spawns a marine on each fire.
     */
    private void advanceShuttles() {
        for (Shuttle s : shuttles) {
            switch (s.state) {
                case PENDING:
                    s.pendingDelay -= TICK_DT;
                    if (s.pendingDelay <= 0f) {
                        setupShuttleLeg(s, s.entryX, s.entryY, s.lzX, s.lzY, 1f);
                        s.state = Shuttle.State.INCOMING;
                    }
                    break;

                case INCOMING:
                    if (stepShuttleAlongLeg(s, s.entryX, s.entryY, s.lzX, s.lzY)) {
                        // Snap to LZ on touchdown — sin² envelope is zero at endpoint
                        // so this matches the curve's natural value, but be explicit.
                        s.worldX = s.lzX;
                        s.worldY = s.lzY;
                        s.scaleMult = 1f;
                        s.landedFacing = s.facingDegrees;
                        s.state = Shuttle.State.LANDED;
                        s.deboardCountdown = s.type.deboardInterval;
                    }
                    break;

                case LANDED:
                    s.deboardCountdown -= TICK_DT;
                    if (s.deboardCountdown <= 0f && s.marinesRemaining > 0) {
                        if (tryDeboardMarine(s)) {
                            s.marinesRemaining--;
                        }
                        s.deboardCountdown = s.type.deboardInterval;
                    }
                    if (s.marinesRemaining == 0) {
                        setupShuttleLeg(s, s.lzX, s.lzY, s.exitX, s.exitY, SHUTTLE_DEPART_CURVE_MULT);
                        s.state = Shuttle.State.DEPARTING;
                    }
                    break;

                case DEPARTING:
                    if (stepShuttleAlongLeg(s, s.lzX, s.lzY, s.exitX, s.exitY)) {
                        s.state = Shuttle.State.GONE;
                    }
                    break;

                case GONE:
                default:
                    break;
            }
        }
    }

    /**
     * Initializes a shuttle's progress, chord length, and randomized curve
     * params for a new leg. {@code strengthMult} scales both the floor and
     * cap of the random curve strength — INCOMING passes 1.0 for a gentle
     * approach; DEPARTING passes a larger value so takeoff bows wide enough
     * to read as a banking loop.
     *
     * <p>Does NOT overwrite {@code facingDegrees} — the first tick of
     * {@link #stepShuttleAlongLeg} computes facing from the tangent (or, for
     * DEPARTING, eases from the preserved {@code landedFacing}).
     */
    private void setupShuttleLeg(Shuttle s, float fromX, float fromY, float toX, float toY, float strengthMult) {
        s.legProgress = 0f;
        float dx = toX - fromX;
        float dy = toY - fromY;
        s.legChordLength = Math.max(0.001f, (float) Math.sqrt(dx * dx + dy * dy));
        float cap = Math.min(s.legChordLength * SHUTTLE_CURVE_REL_MAX, SHUTTLE_CURVE_ABS_MAX) * strengthMult;
        float floor = Math.min(SHUTTLE_CURVE_MIN * strengthMult, cap);
        s.curveStrength = floor + rng.nextFloat() * Math.max(0f, cap - floor);
        s.curveSide = rng.nextBoolean() ? 1 : -1;
        s.flightPhase = rng.nextFloat() * (float) (2 * Math.PI);
        s.worldX = fromX;
        s.worldY = fromY;
    }

    /**
     * Advances the shuttle one tick along the current leg's curved path and
     * updates its world position, facing tangent, and scale multiplier. Returns
     * {@code true} the tick the shuttle reaches the leg's endpoint (caller
     * transitions to the next state).
     *
     * <p>Path is a straight-line interpolation plus a perpendicular bow with
     * a sin² envelope:
     * <pre>{@code
     *   pos(t) = lerp(from, to, t) + perp * sin²(πt) * strength * side
     * }</pre>
     * The sin² envelope is zero AND has zero derivative at both endpoints, so
     * facing exactly matches the straight-line direction at entry and
     * touchdown — no banked-landing artifact — while the midflight tangent
     * rotates smoothly through the curve.
     */
    private boolean stepShuttleAlongLeg(Shuttle s, float fromX, float fromY, float toX, float toY) {
        s.legProgress += (s.type.flightSpeed * TICK_DT) / s.legChordLength;
        boolean done = s.legProgress >= 1f;
        if (done) s.legProgress = 1f;
        float t = s.legProgress;

        float legDx = toX - fromX;
        float legDy = toY - fromY;
        float perpX = -legDy / s.legChordLength;
        float perpY =  legDx / s.legChordLength;

        // sin²(πt) envelope. Peaks at t=0.5; zero (with zero slope) at t=0 and t=1.
        float sinPiT = (float) Math.sin(t * Math.PI);
        float envelope = sinPiT * sinPiT;
        float bow = envelope * s.curveStrength * s.curveSide;

        float linearX = fromX + legDx * t;
        float linearY = fromY + legDy * t;
        s.worldX = linearX + perpX * bow;
        s.worldY = linearY + perpY * bow;

        // Tangent for facing: d/dt of position. d(sin²(πt))/dt = π·sin(2πt).
        float dEnvelopeDt = (float) (Math.PI * Math.sin(2.0 * Math.PI * t));
        float dBowDt = dEnvelopeDt * s.curveStrength * s.curveSide;
        float tangentX = legDx + perpX * dBowDt;
        float tangentY = legDy + perpY * dBowDt;
        float tangentFacing = Shuttle.facingTowards(0f, 0f, tangentX, tangentY);

        // Departure pivot — for the first SHUTTLE_DEPART_FACING_EASE of progress,
        // smoothly rotate from the held landed facing into the leg tangent.
        // Smoothstep (3t²-2t³) gives an ease-in-out so the rotation accelerates
        // away from the landed pose and decelerates into the cruise heading.
        if (s.state == Shuttle.State.DEPARTING && t < SHUTTLE_DEPART_FACING_EASE) {
            float u = t / SHUTTLE_DEPART_FACING_EASE;
            float ease = u * u * (3f - 2f * u);
            s.facingDegrees = lerpAngleDeg(s.landedFacing, tangentFacing, ease);
        } else {
            s.facingDegrees = tangentFacing;
        }

        // Altitude scale — cruise on entry, ground at touchdown. Mirrored on takeoff.
        // INCOMING: t=0 → cruise, t=1 → 1.0. DEPARTING: t=0 → 1.0, t=1 → cruise.
        float altitudeT = (s.state == Shuttle.State.DEPARTING) ? t : (1f - t);
        float baseScale = 1f + (SHUTTLE_CRUISE_SCALE - 1f) * altitudeT;
        // In-flight wobble. Tapered with the same sin² envelope as the bow so
        // it's zero (and zero-slope) at the endpoints — touchdown snaps cleanly
        // to the cruise→1.0 baseline with no wobble residue.
        s.flightPhase += TICK_DT * 2f * (float) Math.PI * SHUTTLE_WOBBLE_HZ;
        float wobble = (float) Math.sin(s.flightPhase) * SHUTTLE_WOBBLE_AMPLITUDE * envelope;
        s.scaleMult = baseScale + wobble;
        return done;
    }

    /**
     * Finds a free cell adjacent to the LZ and spawns a marine there as a fresh
     * {@link Unit}. Returns {@code false} when no nearby cell is available this
     * tick (rare — only happens if the area around the LZ is fully clogged with
     * units or walls); caller leaves {@code marinesRemaining} unchanged and the
     * shuttle re-tries next interval.
     */
    private boolean tryDeboardMarine(Shuttle s) {
        int lzCellX = (int) Math.floor(s.lzX);
        int lzCellY = (int) Math.floor(s.lzY);
        int[] cell = findDeboardCell(lzCellX, lzCellY);
        if (cell == null) return false;
        Unit marine = new Unit("m" + deboardedMarineCount++, s.faction, cell[0], cell[1]);
        addUnit(marine);
        return true;
    }

    /**
     * BFS outward from the LZ cell for the first walkable, unoccupied cell at
     * distance >= 1. Distance 0 (the LZ itself) is skipped so the marine
     * sprite doesn't draw directly under the parked shuttle. Returns
     * {@code null} if no eligible cell is found within {@link #DEBOARD_SCAN_RADIUS}.
     */
    private int[] findDeboardCell(int lzX, int lzY) {
        Set<Long> seen = new HashSet<>();
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{lzX, lzY, 0});
        seen.add(((long) lzX << 32) | (lzY & 0xFFFFFFFFL));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (p[2] > DEBOARD_SCAN_RADIUS) continue;
            if (p[2] > 0
                    && grid.inBounds(p[0], p[1])
                    && grid.isWalkable(p[0], p[1])
                    && !cellHasLiveUnit(p[0], p[1])) {
                return new int[]{p[0], p[1]};
            }
            for (int[] d : dirs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny)) continue;
                long k = ((long) nx << 32) | (ny & 0xFFFFFFFFL);
                if (!seen.add(k)) continue;
                q.add(new int[]{nx, ny, p[2] + 1});
            }
        }
        return null;
    }

    private boolean cellHasLiveUnit(int x, int y) {
        for (Unit u : units) {
            if (!u.isAlive()) continue;
            if (u.cellX == x && u.cellY == y) return true;
        }
        return false;
    }

    /**
     * Linearly interpolates between two angles in degrees, taking the shortest
     * arc through the ±180° wrap. Standard ({@code (b-a+540) mod 360 - 180})
     * trick to fold the delta into the [-180, 180] range before scaling.
     */
    private static float lerpAngleDeg(float a, float b, float t) {
        float delta = ((b - a + 540f) % 360f) - 180f;
        return a + t * delta;
    }
}
