package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.VehicleSupportPayload;

/**
 * Calls a shootable heavy transport that sets an armoured chassis down on the
 * field for the player to command.
 *
 * <p>The delivery is the ordinary one — the carrier flies in, is exposed on the
 * way, and can be shot down with the vehicle still aboard. What is different is
 * what it leaves behind: not a squad that takes objectives on its own, but a
 * vehicle holding position until it is given somewhere to go.
 *
 * <p>Clearance five, like the mech it shares a carrier with, because an APC's
 * footprint needs somewhere to stand rather than somewhere to stand up.
 */
public final class VehicleSupport extends AirDeliveryPower {

    public static final String ID = "vehicle_support";

    public VehicleSupport() {
        super(ID, "Armour Support", 3f, 2, 0, 2,
                ShuttleType.VALKYRIE, VehicleSupportPayload.INSTANCE, 5);
    }
}
