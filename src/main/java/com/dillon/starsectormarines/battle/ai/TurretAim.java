package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Unit;

/**
 * Shared turret aim/fire loop — used by both static {@link com.dillon.starsectormarines.battle.MapTurret}s
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
        /** Sim-seconds until the turret can fire again. Decremented each tick; reset to {@link #attackCooldown} on a fire. */
        public float cooldownTimer;
        public float attackCooldown;
        /** Active target; null when no enemy is locked. Mutated by the acquisition pass and dropped when out of range / LOS. */
        public Unit target;

        /** Output: true when the caller should fire this tick. Reset every {@link #tick} call. */
        public boolean fireThisTick;
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

        if (s.target == null || !s.target.isAlive()) {
            s.target = TacticalScoring.findBestTarget(
                    s.originCellX, s.originCellY, s.faction, s.squadId, s.excludeFromCrowding, sim);
        }
        if (s.cooldownTimer > 0f) s.cooldownTimer -= dt;
        if (s.target == null) return;

        float dist = TacticalScoring.cellDistance(
                s.originCellX, s.originCellY, s.target.cellX, s.target.cellY);
        boolean inRange = dist <= s.attackRange;
        boolean visible = sim.getGrid().hasLineOfSight(
                s.originCellX, s.originCellY, s.target.cellX, s.target.cellY);
        if (!inRange || !visible) {
            // Drop the target so a fresh acquisition happens next tick. By the
            // time LOS or range is broken, there's probably a better candidate.
            s.target = null;
            return;
        }

        float bearing = bearingTo(s.originX, s.originY, s.target.cellX + 0.5f, s.target.cellY + 0.5f);
        float maxStep = s.turnRateDegPerSec * dt;
        s.facingDegrees = slewToward(s.facingDegrees, bearing, maxStep);

        if (Math.abs(shortestAngleDelta(s.facingDegrees, bearing)) <= FIRE_ARC_DEG
                && s.cooldownTimer <= 0f) {
            s.fireThisTick = true;
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
}
