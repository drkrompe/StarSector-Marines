package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

/**
 * An armoured chassis set down on the field for the player to command.
 *
 * <p>Unlike every other payload here, what comes off the ramp is not a unit
 * that joins a squad and takes objectives. It is a vehicle with no errand: it
 * holds where it was put until it is told to go somewhere, which is the whole
 * point of it — see {@code vehicle-as-commandable-unit.md}.
 *
 * <p>What it sets down is the sortie's frozen company vehicle from the mech bay
 * ({@code mechs-nouns.md}, gantries). A sortie with none — a debug grant, or a mission
 * flown without a bay — sets down the {@link #FALLBACK} hull, which belongs to nobody.
 */
public enum VehicleSupportPayload implements AirDeliveryPayload {
    INSTANCE;

    /** The hull a sortie with no frozen company vehicle sets down. */
    public static final VehicleType FALLBACK = VehicleType.HEAVY_APC;

    @Override
    public int unitsPerSortie(ShuttleType carrier) {
        return 1;
    }

    @Override
    public boolean tryDeploy(AirDeliveryContext context) {
        return deploy(context, context.mission.vehicleDeployment != null
                ? context.mission.vehicleDeployment : VehicleDeploymentSpec.standard(FALLBACK));
    }

    /** Sets {@code vehicle} down; shared with {@link ArmorDropPayload}'s mixed drop. */
    static boolean deploy(AirDeliveryContext context, VehicleDeploymentSpec vehicle) {
        int[] cell = context.findOpenDeboardCell();
        if (cell == null) return false;
        // Pointed the way the carrier flew in. Better than an authored
        // constant because it is at least consistent with what the player
        // watched happen, and the first order turns it anyway.
        float facing = AirBody.facingToward(
                context.mission.lzX - context.mission.entryX,
                context.mission.lzY - context.mission.entryY);
        context.deployVehicle(vehicle, cell[0] + 0.5f, cell[1] + 0.5f, facing);
        return true;
    }
}
