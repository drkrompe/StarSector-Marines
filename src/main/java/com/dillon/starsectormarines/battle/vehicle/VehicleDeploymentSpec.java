package com.dillon.starsectormarines.battle.vehicle;

/**
 * One company vehicle frozen for a battle: the campaign identity a loss writes back to,
 * the callsign, and the hull. No campaign object crosses into the simulation.
 *
 * <p>{@code campaignId} is null for a debug or mission-supplied vehicle the company does
 * not own; such a vehicle has nothing to write back.
 */
public record VehicleDeploymentSpec(String campaignId, String callsign, VehicleType type) {

    public VehicleDeploymentSpec {
        if (type == null) throw new IllegalArgumentException("Vehicle type is required");
    }

    /** An unowned vehicle of {@code type}: debug grants and missions without a bay. */
    public static VehicleDeploymentSpec standard(VehicleType type) {
        return new VehicleDeploymentSpec(null, null, type);
    }
}
