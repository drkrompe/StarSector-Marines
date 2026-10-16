package com.dillon.starsectormarines.battle.air;

/**
 * The machines a player picked from the bay manifest, mechs and vehicles alike, set down
 * in the order they were loaded ({@link ShuttleMission#armorDrop}).
 *
 * <p>Mechs off one sortie form one squad, as a lance always has; a vehicle comes off the
 * ramp as its own commandable hull. A cradled boat flies one machine per trip, so the trip
 * number counts in the index exactly as it does for {@link MechSupportPayload}.
 */
public enum ArmorDropPayload implements AirDeliveryPayload {
    INSTANCE;

    @Override
    public int unitsPerSortie(ShuttleType carrier) {
        return 1;
    }

    @Override
    public boolean tryDeploy(AirDeliveryContext context) {
        ArmorLoad[] loads = context.mission.armorDrop;
        if (loads == null || loads.length == 0) return true;
        ArmorLoad load = loads[Math.min(MechSupportPayload.machineIndex(context.mission),
                loads.length - 1)];
        return load.isVehicle()
                ? VehicleSupportPayload.deploy(context, load.vehicle())
                : MechSupportPayload.deploy(context, load.mech());
    }
}
