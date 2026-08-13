package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.ResupplyPayload;
import com.dillon.starsectormarines.battle.air.ShuttleType;

/** A vulnerable utility shuttle that leaves a finite, persistent supply cache. */
public final class EmergencyResupply extends AirDeliveryPower {

    public static final String ID = "emergency_resupply";

    public EmergencyResupply() {
        super(ID, "Resupply", 3f, 2, ShuttleType.TARSUS,
                ResupplyPayload.INSTANCE, 4);
    }
}
