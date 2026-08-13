package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.MechSupportPayload;
import com.dillon.starsectormarines.battle.air.ShuttleType;

/** Calls a shootable heavy transport that physically unloads one marine mech. */
public final class MechSupport extends AirDeliveryPower {

    public static final String ID = "mech_support";

    public MechSupport() {
        super(ID, "Mech Support", 4f, 1, ShuttleType.VALKYRIE,
                MechSupportPayload.INSTANCE, 5);
    }
}
