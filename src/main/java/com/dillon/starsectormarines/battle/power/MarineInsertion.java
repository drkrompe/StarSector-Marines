package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.InfantryPayload;
import com.dillon.starsectormarines.battle.air.ShuttleType;

/** Player-called Valkyrie insertion of one full marine squad. */
public final class MarineInsertion extends AirDeliveryPower {

    public static final String ID = "marine_insertion";

    public MarineInsertion() {
        super(ID, "Marine Drop", 3f, 1, 2,
                ShuttleType.VALKYRIE, InfantryPayload.INSTANCE, 4);
    }
}
