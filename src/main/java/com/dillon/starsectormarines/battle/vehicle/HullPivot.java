package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.turret.TurretFireSystem;

/**
 * What a hull's turn does to its gun ({@code convoy-nouns.md}, what presents the front). A turret rides on the hull,
 * so while the heading turns the turret has to traverse against it just to stay on its target, and the gun shakes
 * the way a moving shooter's does. The penalty is the share of the turret's traverse rate the hull's yaw takes up,
 * up to the moving shooter's full penalty ({@link FireStance#MOVING}) once the hull turns as fast as the turret can.
 * A slow-turning turret on a hull swinging round at full lock fires at a moving shooter's accuracy; a tank backing
 * round at a crawl loses a little.
 *
 * <p>The yaw is the body's own ({@link GroundBody#yawRateDegPerSec}), whatever turns it: a turn onto a threat, a
 * corner on a route, a driver at the wheel. A hull driving straight is not shaken by this.
 * {@code -Dbattle.vehicle.pivotAccuracy=false} is the control.
 */
public final class HullPivot implements TurretFireSystem.ShooterAccuracy {

    public static final String PROPERTY = "battle.vehicle.pivotAccuracy";

    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    private final ConvoyService convoy;

    public HullPivot(ConvoyService convoy) {
        this.convoy = convoy;
    }

    @Override
    public float multiplier(long shooterId) {
        if (!ENABLED || !convoy.isVehicle(shooterId)) return 1f;
        VehicleType type = convoy.vehicleType(shooterId);
        if (type == null || !type.hasTurretWeapon()) return 1f;
        return accuracyMultiplier(convoy.body(shooterId).yawRateDegPerSec(),
                convoy.turretStructure(shooterId).mount.turnRateDegPerSec);
    }

    /**
     * Accuracy multiplier for a gun on a hull turning at {@code yawDegPerSec} under a turret that traverses at
     * {@code traverseDegPerSec}: one standing still, the moving shooter's at or past the traverse rate, linear between.
     */
    static float accuracyMultiplier(float yawDegPerSec, float traverseDegPerSec) {
        if (!(traverseDegPerSec > 0f)) return FireStance.MOVING.accuracyMult;
        float share = Math.min(1f, Math.abs(yawDegPerSec) / traverseDegPerSec);
        return 1f - (1f - FireStance.MOVING.accuracyMult) * share;
    }
}
