package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A machine's gaps are one rule on every machine: required parts first and red, then what it goes without. */
class FittingGapsTest {

    @Test
    void aBareHullLacksItsEngineAndBankFirstThenItsPlateAndGun() {
        CampaignVehicle hull = CampaignVehicle.hull("a", "Mule 01", VehicleType.HEAVY_APC);
        List<FittingGap> gaps = hull.gaps();
        assertEquals(List.of(FittingGap.missing(MechInternal.Role.ENGINE),
                FittingGap.missing(MechInternal.Role.CAPACITOR_BANK),
                FittingGap.without(FittingGap.Lack.ARMOR),
                FittingGap.without(FittingGap.Lack.WEAPON)), gaps);
        assertFalse(hull.complete());
    }

    @Test
    void aHullBuiltWithNoGunCardGoesWithoutNoWeapon() {
        CampaignVehicle truck = CampaignVehicle.hull("t", "Truck 01", VehicleType.SUPPLY_TRUCK);
        assertFalse(Equipped.lacks(truck.gaps(), FittingGap.Lack.WEAPON));
    }

    @Test
    void aShuttleReadsHerGapsByTheSameRule() {
        CampaignBoat boat = new CampaignBoat("b", "Gig", ShuttleType.HERMES, null, null);
        boat.clear(BoatFittingSlot.DRIVE);
        boat.clear(BoatFittingSlot.PLATING);
        boat.clear(BoatFittingSlot.EMITTER);
        for (int index = 0; index < ShuttleType.HERMES.hardpoints(); index++) {
            boat.installGuns(index, new BoatGun[BoatHardpoint.POSITIONS]);
        }
        List<FittingGap> gaps = boat.gaps();
        assertEquals(FittingGap.missing(BoatFittingSlot.DRIVE), gaps.get(0));
        assertTrue(gaps.containsAll(List.of(FittingGap.without(FittingGap.Lack.ARMOR),
                FittingGap.without(FittingGap.Lack.SHIELD))));
        assertEquals(ShuttleType.HERMES.hardpoints() > 0, Equipped.lacks(gaps, FittingGap.Lack.WEAPON));
        assertFalse(boat.complete());
        assertTrue(Equipped.missing(gaps, BoatFittingSlot.DRIVE));
        assertFalse(Equipped.missing(gaps, BoatFittingSlot.CABIN));
    }
}
