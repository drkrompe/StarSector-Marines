package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A vehicle's gun card: the weapons it takes, and how they are kept in stores. */
class VehicleGunsTest {

    @Test
    void theGunCardTakesOnlyAWeaponOfItsTypeAndSize() {
        assertTrue(CampaignVehicle.takesGun(VehicleType.BREAKTHROUGH_TANK, MechWeaponComponent.HC_20));
        assertFalse(CampaignVehicle.takesGun(VehicleType.BREAKTHROUGH_TANK, MechWeaponComponent.PL_2_AP));
        assertFalse(CampaignVehicle.takesGun(VehicleType.SUPPLY_TRUCK, MechWeaponComponent.CHAINGUN), "no card, no gun");

        CampaignVehicle tank = new CampaignVehicle("v1", "Breaker", VehicleType.BREAKTHROUGH_TANK);
        assertNull(tank.gun());
        assertNull(tank.freezeForDeployment().gun(), "the hull fires its own gun");
    }

    @Test
    void aGunFitsFromStoresReplacesToStoresAndGoesDownWithTheHullButNotWithAScrap() {
        MechBay bay = new MechBay();
        CampaignVehicle tank = new CampaignVehicle("v1", "Breaker", VehicleType.BREAKTHROUGH_TANK);
        assertTrue(bay.addVehicle(MechBay.STARTER_SQUAD_ID, tank));

        assertFalse(bay.fitGun("v1", MechWeaponComponent.HC_20), "nothing in stores");
        bay.addWeapon(MechWeaponComponent.HC_20.id, 1);
        bay.addWeapon(MechWeaponComponent.HC_10.id, 1);
        bay.addWeapon(MechWeaponComponent.PL_2_AP.id, 1);
        assertFalse(bay.fitGun("v1", MechWeaponComponent.PL_2_AP), "an energy weapon is not a ballistic one");
        assertTrue(bay.fitGun("v1", MechWeaponComponent.HC_20));
        assertEquals(0, bay.availableWeapon(MechWeaponComponent.HC_20.id));
        assertSame(MechWeaponComponent.HC_20, tank.freezeForDeployment().gun(), "the drop carries the gun");

        assertTrue(bay.fitGun("v1", MechWeaponComponent.HC_10));
        assertEquals(1, bay.availableWeapon(MechWeaponComponent.HC_20.id), "the outgoing gun is a spare");

        assertTrue(bay.stripGun("v1"));
        assertNull(tank.gun());
        assertFalse(bay.stripGun("v1"), "the hull fires its own gun already");
        assertEquals(1, bay.availableWeapon(MechWeaponComponent.HC_10.id));

        assertTrue(bay.fitGun("v1", MechWeaponComponent.HC_10));
        assertTrue(bay.scrapVehicle("v1"));
        assertEquals(1, bay.availableWeapon(MechWeaponComponent.HC_10.id), "a scrap spares the gun");

        CampaignVehicle lost = new CampaignVehicle("v2", "Lost", VehicleType.BREAKTHROUGH_TANK);
        assertTrue(bay.addVehicle(MechBay.STARTER_SQUAD_ID, lost));
        assertTrue(bay.fitGun("v2", MechWeaponComponent.HC_20));
        assertEquals(1, bay.loseVehicles(List.of("v2")));
        assertEquals(0, bay.ownedWeapon(MechWeaponComponent.HC_20.id), "a wrecked hull takes its gun with it");
    }

    @Test
    void aFittedGunCountsAsInstalledSoItIsNeverAlsoASpare() {
        MechBay bay = new MechBay();
        CampaignVehicle tank = new CampaignVehicle("v1", "Breaker", VehicleType.BREAKTHROUGH_TANK);
        assertTrue(bay.addVehicle(MechBay.STARTER_SQUAD_ID, tank));
        bay.addWeapon(MechWeaponComponent.HC_10.id, 1);
        assertTrue(bay.fitGun("v1", MechWeaponComponent.HC_10));
        assertEquals(1, bay.installedWeapon(MechWeaponComponent.HC_10.id));
        assertEquals(0, bay.availableWeapon(MechWeaponComponent.HC_10.id));
    }
}
