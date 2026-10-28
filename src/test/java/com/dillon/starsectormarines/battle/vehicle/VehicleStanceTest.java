package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.vehicle.VehicleType.Stance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A hull's stance follows the gun it fires: its own gun keeps its type's, a fitted weapon asks for its own. */
class VehicleStanceTest {

    @Test
    void everyStockHullsGunAsksForTheStanceItsTypeStates() {
        for (VehicleType type : VehicleType.values()) {
            if (!type.hasTurretWeapon()) continue;
            assertEquals(type.engagementStance(), VehicleStance.of(type.turretStructure().mount), type.name());
        }
    }

    @Test
    void aFittedWeaponDecidesHowTheHullFights() {
        StructureDef stock = VehicleType.BREAKTHROUGH_TANK.turretStructure();
        assertEquals(Stance.STANDOFF, VehicleStance.of(VehicleGun.fit(stock, MechWeaponComponent.LRM_5).mount),
                "an arcing launcher fights from beyond reach");
        assertEquals(Stance.DUEL, VehicleStance.of(VehicleGun.fit(stock, MechWeaponComponent.SINGLE_HEAVY_CANNON).mount));
        assertEquals(MechWeaponComponent.SRM_5.antiArmorOnly() ? Stance.AMBUSH : Stance.DUEL,
                VehicleStance.of(VehicleGun.fit(stock, MechWeaponComponent.SRM_5).mount),
                "a direct anti-armor rack waits for armor");
    }
}
