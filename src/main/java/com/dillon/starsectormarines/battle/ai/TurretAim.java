package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.MapTurret;

/**
 * Shared turret aim/fire loop — used by both static {@link MapTurret}s
 * (via {@link TurretBehavior}) and shuttle-mounted turrets (via
 * {@link com.dillon.starsectormarines.battle.air.AirSystem}). Caller fills in
 * a {@link State}, calls {@link #tick}, and on return reads back the updated
 * facing/cooldown/target plus {@link State#fireThisTick} to decide whether
 * to fire this tick.
 *
 * <p>Acquisition + LOS + range checks all use the integer cell coordinates on
 * {@code State} ({@code originCellX/Y}), so a moving mount that's not exactly
 * on a cell boundary just floors its world position before each tick. Bearing
 * and facing slew use the float coordinates ({@code originX/Y}) so a shuttle
 * hovering between cells still aims precisely.
 */
public final class TurretAim {

    /** Tolerance in degrees between current facing and bearing-to-target before the turret will fire. Tight enough that the barrel visibly lines up; loose enough that a slow-turning turret isn't perpetually a couple degrees off. */
    public static final float FIRE_ARC_DEG = 12f;

    /**
     * Mutable per-tick state for one turret. Reuse across ticks — the same
     * instance carries facing/cooldown/target forward. Caller is responsible
     * for refreshing position fields each tick when the mount moves.
     */
    public static final class State {
        /** Origin cell — used for LOS and range checks (int domain). */
        public int originCellX, originCellY;
        /** Origin float position — used for bearing (cell-center precision for static mounts; body.x/y for shuttle mounts). */
        public float originX, originY;
        /** Shooter faction — drives the enemy filter in target acquisition. */
        public Faction faction;
        /** Shooter squad id — feeds crowding logic. {@link Unit#NO_SQUAD} for turrets, which don't squad up. */
        public int squadId = Unit.NO_SQUAD;
        /** If the shooter is itself a {@link Unit} on the units list, pass it here so target acquisition's crowding pass doesn't count self. {@code null} for non-Unit shooters. */
        public Unit excludeFromCrowding;

        /** Current barrel facing, degrees. Mutated by the slew each tick. */
        public float facingDegrees;
        public float turnRateDegPerSec;
        public float attackRange;
        /** Minimum engagement range in cells. Targets closer than this are dropped without firing — keeps lobbed-AoE weapons from dropping rounds on top of friendlies. {@code 0} = no minimum. */
        public float minRange;
        /** Sim-seconds until the turret can fire again. Decremented each tick; reset to {@link #attackCooldown} on a fire. */
        public float cooldownTimer;
        public float attackCooldown;
        /** Active target; null when no enemy is locked. Mutated by the acquisition pass and dropped when out of range / LOS. */
        public Unit target;

        /** Output: true when the caller should fire this tick. Reset every {@link #tick} call. */
        public boolean fireThisTick;
        /**
         * Output: line-of-sight state at the moment {@link #fireThisTick}
         * latched. The caller (turret behavior / air system) passes this into
         * the fire path so indirect-fire kinds can apply
         * {@link com.dillon.starsectormarines.battle.turret.TurretKind#noLosAccuracyMult}
         * to shots taken blind. Meaningful only when {@code fireThisTick} is
         * {@code true}.
         */
        public boolean lastFireHadLos;

        /**
         * If true, walls within {@link #closeWallRadius} cells of the origin
         * are treated as transparent for both target acquisition and target
         * validation. Used by shuttle-mounted turrets: a flying mount that's
         * positioned over a building should be able to fire OUT of the
         * building it's inside, but still needs real LOS to hit anything past
         * the next building's walls.
         */
        public boolean ignoreCloseWalls;
        /** Radius (cells) over which walls are treated as transparent when {@link #ignoreCloseWalls} is true. */
        public float closeWallRadius;
        /**
         * If true, the aim loop keeps the target locked when LoS breaks —
         * artillery-style indirect fire. Acquisition also considers non-visible
         * candidates (scored via the {@code allowNoLos} pass in
         * {@link TacticalScoring}). The actual LoS-at-fire state is published
         * back to the caller via {@link #lastFireHadLos}.
         */
        public boolean indirectFire;
    }

    private TurretAim() {}

    /**
     * Advances one aim tick. If the state has no target (or its target died),
     * acquires the best visible enemy via {@link TacticalScoring}. Slews
     * {@link State#facingDegrees} toward the bearing-to-target at the turret's
     * turn rate, and sets {@link State#fireThisTick} when aligned and off
     * cooldown. The caller fires the actual shot using the appropriate
     * sim path (sim.fireShot for Units; sim.fireShotFrom for mounts).
     */
    public static void tick(State s, BattleSimulation sim, float dt) {
        s.fireThisTick = false;
        NavigationGrid grid = sim.getGrid();
        TacticalScoring.LosTest los = s.ignoreCloseWalls
                ? (x0, y0, x1, y1) -> airLosVisible(grid, x0, y0, x1, y1, s.closeWallRadius)
                : grid::hasLineOfSight;

        if (s.target == null || !s.target.isAlive()) {
            s.target = TacticalScoring.findBestTarget(
                    s.originCellX, s.originCellY, s.faction, s.squadId, s.excludeFromCrowding,
                    los, sim, /*allowNoLos*/ s.indirectFire);
        }
        if (s.cooldownTimer > 0f) s.cooldownTimer -= dt;
        if (s.target == null) return;

        float dist = TacticalScoring.cellDistance(
                s.originCellX, s.originCellY, s.target.cellX, s.target.cellY);
        boolean inRange = dist <= s.attackRange && dist >= s.minRange;
        boolean visible = los.visible(s.originCellX, s.originCellY, s.target.cellX, s.target.cellY);
        // Direct-fire kinds drop on either out-of-range OR LoS loss; indirect-
        // fire kinds keep the lock when LoS breaks (the kremlin wall doesn't
        // hide attackers from artillery that's been ranged in) and only drop
        // on out-of-range. The actual LoS state at fire time still rides out
        // via {@link State#lastFireHadLos} so the fire path can apply the
        // no-LoS accuracy multiplier.
        boolean dropOnNoLos = !s.indirectFire;
        if (!inRange || (dropOnNoLos && !visible)) {
            s.target = null;
            return;
        }

        float bearing = bearingTo(s.originX, s.originY, s.target.cellX + 0.5f, s.target.cellY + 0.5f);
        float maxStep = s.turnRateDegPerSec * dt;
        s.facingDegrees = slewToward(s.facingDegrees, bearing, maxStep);

        if (Math.abs(shortestAngleDelta(s.facingDegrees, bearing)) <= FIRE_ARC_DEG
                && s.cooldownTimer <= 0f) {
            s.fireThisTick = true;
            s.lastFireHadLos = visible;
            s.cooldownTimer = s.attackCooldown;
        }
    }

    /**
     * Bearing in the Starsector sprite-angle convention: 0° = +Y (north),
     * positive clockwise.
     */
    public static float bearingTo(float fromX, float fromY, float toX, float toY) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        if (dx == 0f && dy == 0f) return 0f;
        return (float) Math.toDegrees(Math.atan2(dy, dx)) - 90f;
    }

    /** Wraps the delta to [-180, 180] and clamps its magnitude by {@code maxStep}. */
    public static float slewToward(float current, float target, float maxStep) {
        float delta = shortestAngleDelta(current, target);
        if (Math.abs(delta) <= maxStep) return target;
        return current + Math.signum(delta) * maxStep;
    }

    /** Signed shortest-arc delta from {@code a} to {@code b}, in [-180, 180]. */
    public static float shortestAngleDelta(float a, float b) {
        return ((b - a + 540f) % 360f) - 180f;
    }

    /**
     * Air LoS — like {@link NavigationGrid#hasLineOfSight} but treats walls
     * within {@code closeWallRadius} cells of the origin as transparent.
     * Models "the shuttle is high enough to fire over the walls of the
     * building it's directly above, but still has to see through real
     * intervening cover past that."
     *
     * <p>Bresenham-stepped along the line; per-step Euclidean (squared)
     * distance from origin gates the close-wall pass.
     */
    public static boolean airLosVisible(NavigationGrid grid, int x0, int y0, int x1, int y1,
                                        float closeWallRadius) {
        float r2 = closeWallRadius * closeWallRadius;
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            boolean endpoint = (x == x0 && y == y0) || (x == x1 && y == y1);
            if (!endpoint && grid.blocksLineOfSight(x, y)) {
                float distSq = (float) ((x - x0) * (x - x0) + (y - y0) * (y - y0));
                if (distSq > r2) return false;
                // else: wall is part of the "shuttle's building"; ignore.
            }
            if (x == x1 && y == y1) return true;
            int e2 = err << 1;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 <  dx) { err += dx; y += sy; }
        }
    }
}
