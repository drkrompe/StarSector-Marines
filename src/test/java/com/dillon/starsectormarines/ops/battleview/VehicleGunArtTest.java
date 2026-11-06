package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.vehicle.VehicleType;
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
    void anEnergyWeaponUnderTheOnagersClosedHeadIsTheHegemonyEmitter() {
        assertEquals(VehicleGunArt.HEGEMONY_EMITTER,
                VehicleGunArt.vehicleDrawingPath(VehicleType.BREAKTHROUGH_TANK, MechWeaponComponent.PL_3));
        assertEquals(VehicleGunArt.HEGEMONY_EMITTER,
                VehicleGunArt.vehicleDrawingPath(VehicleType.BREAKTHROUGH_TANK, MechWeaponComponent.LL_3));
        assertEquals(VehicleGunArt.HEGEMONY_EMITTER,
                VehicleGunArt.vehicleDrawingPath(VehicleType.MEDIUM_TANK, MechWeaponComponent.PL_2));
        assertNull(VehicleGunArt.vehicleDrawingPath(VehicleType.LASER_TANK, MechWeaponComponent.PL_3),
                "Tri-Tachyon's tank keeps the white emitter");
        assertNull(VehicleGunArt.vehicleDrawingPath(VehicleType.BREAKTHROUGH_TANK, MechWeaponComponent.HC_20));
    }

    @Test
    void otherWeaponsAndAnEmptyCardFallBackToTheMechDrawing() {
        assertNull(VehicleGunArt.vehicleDrawingPath(MechWeaponComponent.BL_1));
        assertNull(VehicleGunArt.vehicleDrawingPath(null));
    }
}
