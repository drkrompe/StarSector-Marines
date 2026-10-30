package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VehicleGunArtTest {

    @Test
    void theLightMachineGunHasARoofDrawingOfItsOwn() {
        assertEquals(VehicleGunArt.PINTLE_LMG, VehicleGunArt.vehicleDrawingPath(MechWeaponComponent.LIGHT_MACHINE_GUN));
    }

    @Test
    void otherWeaponsAndAnEmptyCardFallBackToTheMechDrawing() {
        assertNull(VehicleGunArt.vehicleDrawingPath(MechWeaponComponent.BL_1));
        assertNull(VehicleGunArt.vehicleDrawingPath(null));
    }
}
