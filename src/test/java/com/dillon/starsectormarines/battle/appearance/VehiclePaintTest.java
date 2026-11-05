package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which paint a side's Hegemony-line vehicles drive in, and where a painted sheet sits beside its master. */
class VehiclePaintTest {

    @Test
    void independentAndPirateLiveriesHaveTheirOwnPaintAndTheRestDriveTheHegemonys() {
        assertEquals(VehiclePaint.INDEPENDENT, VehiclePaint.of(MechLivery.INDEPENDENT));
        assertEquals(VehiclePaint.PIRATES, VehiclePaint.of(MechLivery.PIRATES));
        assertEquals(VehiclePaint.HEGEMONY, VehiclePaint.of(MechLivery.HEGEMONY));
        assertEquals(VehiclePaint.HEGEMONY, VehiclePaint.of(MechLivery.TRI_TACHYON), "a faction not yet painted");
        assertEquals(VehiclePaint.HEGEMONY, VehiclePaint.of(MechLivery.BASE));
    }

    @Test
    void aPaintedSheetSitsBesideItsMasterAndTheHegemonysIsTheMaster() {
        String master = "graphics/battle/vehicles/army-apc.png";
        assertEquals(master, VehiclePaint.HEGEMONY.path(master));
        assertEquals("graphics/battle/vehicles/army-apc-pirates.png", VehiclePaint.PIRATES.path(master));
    }

    @Test
    void onlyTheHegemonyLineComesInPaints() {
        assertTrue(VehiclePaint.paints(VehicleType.BREAKTHROUGH_TANK));
        assertTrue(VehiclePaint.paints(VehicleType.LRM_CARRIER));
        assertFalse(VehiclePaint.paints(VehicleType.LASER_TANK), "Tri-Tachyon's white is its own");
        assertFalse(VehiclePaint.paints(VehicleType.MILITIA_TECHNICAL));
    }
}
