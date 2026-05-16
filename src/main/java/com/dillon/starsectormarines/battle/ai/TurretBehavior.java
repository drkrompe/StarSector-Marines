package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.MapTurret;
import com.dillon.starsectormarines.battle.Unit;

/**
 * Static-defense behavior. Acquires the best visible enemy combatant within
 * range via {@link TacticalScoring#findBestTarget}, slews the barrel toward
 * the target at {@link com.dillon.starsectormarines.battle.TurretKind#turnRateDegPerSec},
 * and fires when the facing is within {@link #FIRE_ARC_DEG} of the target
 * bearing. No movement, no fall-back — turrets fight to the last HP and the
 * sim demolishes them on death.
 *
 * <p>Pulled out of {@link com.dillon.starsectormarines.battle.ai.CombatantBehavior}
 * rather than reusing it because the cohesion / repositioning / pathfinding
 * branches don't apply, and bolting facing-tracking into that class would
 * pollute the mobile-unit path.
 */
public final class TurretBehavior implements UnitBehavior {

    public static final TurretBehavior INSTANCE = new TurretBehavior();

    /** Tolerance in degrees between current facing and bearing-to-target before the turret will fire. Tight enough that the barrel visibly lines up; loose enough that a slow-turning turret isn't perpetually a couple degrees off. */
    private static final float FIRE_ARC_DEG = 12f;

    private TurretBehavior() {}

    @Override
    public void update(Unit u, BattleSimulation sim) {
        MapTurret t = (MapTurret) u;

        if (t.target == null || !t.target.isAlive()) {
            t.target = TacticalScoring.findBestTarget(t, sim);
        }
        if (t.cooldownTimer > 0f) t.cooldownTimer -= BattleSimulation.TICK_DT;
        if (t.target == null) return;

        float dist = TacticalScoring.cellDistance(t.cellX, t.cellY, t.target.cellX, t.target.cellY);
        boolean inRange = dist <= t.attackRange;
        boolean visible = sim.getGrid().hasLineOfSight(t.cellX, t.cellY, t.target.cellX, t.target.cellY);
        if (!inRange || !visible) {
            // Drop the target so a fresh acquisition happens next tick — by the
            // time we're out of LOS, there's probably a better candidate visible
            // somewhere else.
            t.target = null;
            return;
        }

        float bearing = bearingTo(t.cellX, t.cellY, t.target.cellX, t.target.cellY);
        float maxStep = t.kind.turnRateDegPerSec * BattleSimulation.TICK_DT;
        t.facingDegrees = slewToward(t.facingDegrees, bearing, maxStep);

        if (Math.abs(shortestAngleDelta(t.facingDegrees, bearing)) <= FIRE_ARC_DEG
                && t.cooldownTimer <= 0f) {
            sim.fireShot(t, t.target);
            t.cooldownTimer = t.attackCooldown;
        }
    }

    /**
     * Bearing in the same convention as {@code Shuttle.facingTowards}:
     * 0° = +Y (north), positive clockwise. Cell coordinates use cell centers
     * (the half-cell offsets cancel since we only need the direction).
     */
    private static float bearingTo(int fromX, int fromY, int toX, int toY) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        if (dx == 0f && dy == 0f) return 0f;
        return (float) Math.toDegrees(Math.atan2(dy, dx)) - 90f;
    }

    /** Wraps the delta to [-180, 180] and clamps its magnitude by {@code maxStep}. */
    private static float slewToward(float current, float target, float maxStep) {
        float delta = shortestAngleDelta(current, target);
        if (Math.abs(delta) <= maxStep) return target;
        return current + Math.signum(delta) * maxStep;
    }

    /** Signed shortest-arc delta from {@code a} to {@code b}, in [-180, 180]. */
    private static float shortestAngleDelta(float a, float b) {
        return ((b - a + 540f) % 360f) - 180f;
    }
}
