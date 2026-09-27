package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.vehicle.components.VehicleControlComponent;

import java.util.Arrays;

/**
 * Immutable identity of one completed, unsuccessful bounded rescue search.
 * This is not an unreachability proof: it only reuses the same algorithm's null
 * answer for identical frozen inputs. Owned by the vehicle control component.
 */
public final class FailedVehicleRecovery {
    /** Malformed geometry must never acquire a reusable failed answer. */
    static boolean cacheable(float facing, float radius) {
        return Float.isFinite(facing) && Float.isFinite(radius) && radius >= 0f;
    }

    private final ProgressiveVehicleField fields;
    private final long topologyRevision;
    private final VehicleType type;
    private final float[] routeXs, routeYs;
    private final int startX, startY, goalX, goalY, facingBits, radiusBits, triedMask;
    private final int[] avoidX, avoidY;

    FailedVehicleRecovery(VehicleControlComponent state, ProgressiveVehicleField fields,
                          VehicleType type, int startX, int startY, int goalX, int goalY,
                          float facing, float radius) {
        this.fields = fields;
        topologyRevision = fields.grid().topologyRevision();
        this.type = type;
        routeXs = state.routeXs;
        routeYs = state.routeYs;
        this.startX = startX;
        this.startY = startY;
        this.goalX = goalX;
        this.goalY = goalY;
        facingBits = Float.floatToRawIntBits(facing);
        radiusBits = Float.floatToRawIntBits(radius);
        triedMask = state.rescueFirstStepTriedMask;
        avoidX = Arrays.copyOf(state.rerouteAvoidX, state.rerouteAvoidCount);
        avoidY = Arrays.copyOf(state.rerouteAvoidY, state.rerouteAvoidCount);
    }

    /** Allocation-free comparison; avoidance storage is mutable, so compare its copied contents. */
    boolean matches(VehicleControlComponent state, ProgressiveVehicleField fields,
                    VehicleType type, int startX, int startY, int goalX, int goalY,
                    float facing, float radius) {
        if (this.fields != fields || topologyRevision != fields.grid().topologyRevision()
                || this.type != type || routeXs != state.routeXs || routeYs != state.routeYs
                || this.startX != startX || this.startY != startY
                || this.goalX != goalX || this.goalY != goalY
                || facingBits != Float.floatToRawIntBits(facing)
                || radiusBits != Float.floatToRawIntBits(radius)
                || triedMask != state.rescueFirstStepTriedMask
                || avoidX.length != state.rerouteAvoidCount) return false;
        for (int i = 0; i < avoidX.length; i++) {
            if (avoidX[i] != state.rerouteAvoidX[i] || avoidY[i] != state.rerouteAvoidY[i]) return false;
        }
        return true;
    }
}
