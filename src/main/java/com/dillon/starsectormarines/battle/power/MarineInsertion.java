package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.InfantryPayload;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.setup.InfantryLoadoutRolls;

import java.util.Objects;
import java.util.Random;

/** Player-called Valkyrie insertion of one full marine squad. */
public final class MarineInsertion extends AirDeliveryPower {

    public static final String ID = "marine_insertion";

    private final Random loadoutRng;

    public MarineInsertion() {
        this(new Random());
    }

    MarineInsertion(Random loadoutRng) {
        super(ID, "Marine Drop", 3f, 1, 2, 2,
                ShuttleType.VALKYRIE, InfantryPayload.INSTANCE, 4);
        this.loadoutRng = Objects.requireNonNull(loadoutRng, "loadoutRng");
    }

    @Override
    protected void configureMission(ShuttleMission mission, ShuttleType carrier) {
        mission.marineLoadout = InfantryLoadoutRolls.playerSquad(carrier.capacity, loadoutRng);
    }
}
