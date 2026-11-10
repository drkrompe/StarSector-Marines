package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechMountSlot;
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
        assertTrue(CampaignVehicle.takesGun(VehicleType.BREAKTHROUGH_TANK, MechWeaponComponent.PL_2_AP), "the tank's card is omni");
        assertFalse(CampaignVehicle.takesGun(VehicleType.FIRE_SUPPORT_APC, MechWeaponComponent.PL_2_AP),
                "the mortar carrier's card stays ballistic");
        assertFalse(CampaignVehicle.takesGun(VehicleType.SUPPLY_TRUCK, MechWeaponComponent.CHAINGUN), "no card, no gun");

        CampaignVehicle tank = new CampaignVehicle("v1", "Breaker", VehicleType.BREAKTHROUGH_TANK);
        assertSame(MechWeaponComponent.HC_20, tank.gun(), "a stock hull's cannon is a part in its card");
        assertSame(MechWeaponComponent.HC_20, tank.freezeForDeployment().gun());
    }

    @Test
    void aGunFitsFromStoresReplacesToStoresAndGoesDownWithTheHullButNotWithAScrap() {
        MechBay bay = new MechBay();
        CampaignVehicle tank = CampaignVehicle.hull("v1", "Breaker", VehicleType.BREAKTHROUGH_TANK);
        assertTrue(bay.addVehicle(MechBay.STARTER_SQUAD_ID, tank));

        assertFalse(bay.fitGun("v1", MechWeaponComponent.HC_20), "nothing in stores");
        bay.addWeapon(MechWeaponComponent.HC_20.id, 1);
        bay.addWeapon(MechWeaponComponent.HC_10.id, 1);
        bay.addWeapon(MechWeaponComponent.PL_2_AP.id, 1);
        assertTrue(bay.fitGun("v1", MechWeaponComponent.HC_20));
        assertEquals(0, bay.availableWeapon(MechWeaponComponent.HC_20.id));
        assertSame(MechWeaponComponent.HC_20, tank.freezeForDeployment().gun(), "the drop carries the gun");

        assertTrue(bay.fitGun("v1", MechWeaponComponent.HC_10));
        assertEquals(1, bay.availableWeapon(MechWeaponComponent.HC_20.id), "the outgoing gun is a spare");

        assertTrue(bay.stripGun("v1"));
        assertNull(tank.gun());
        assertFalse(bay.stripGun("v1"), "the card is empty already");
        assertEquals(1, bay.availableWeapon(MechWeaponComponent.HC_10.id));

        assertTrue(bay.fitGun("v1", MechWeaponComponent.HC_10));
        assertTrue(bay.scrapVehicle("v1"));
        assertEquals(1, bay.availableWeapon(MechWeaponComponent.HC_10.id), "a scrap spares the gun");

        CampaignVehicle lost = CampaignVehicle.hull("v2", "Lost", VehicleType.BREAKTHROUGH_TANK);
        assertTrue(bay.addVehicle(MechBay.STARTER_SQUAD_ID, lost));
        assertTrue(bay.fitGun("v2", MechWeaponComponent.HC_20));
        assertEquals(1, bay.loseVehicles(List.of("v2")));
        assertEquals(0, bay.ownedWeapon(MechWeaponComponent.HC_20.id), "a wrecked hull takes its gun with it");
    }

    @Test
    void aFittedGunCountsAsInstalledSoItIsNeverAlsoASpare() {
        MechBay bay = new MechBay();
        CampaignVehicle tank = CampaignVehicle.hull("v1", "Breaker", VehicleType.BREAKTHROUGH_TANK);
        assertTrue(bay.addVehicle(MechBay.STARTER_SQUAD_ID, tank));
        bay.addWeapon(MechWeaponComponent.HC_10.id, 1);
        assertTrue(bay.fitGun("v1", MechWeaponComponent.HC_10));
        assertEquals(1, bay.installedWeapon(MechWeaponComponent.HC_10.id));
        assertEquals(0, bay.availableWeapon(MechWeaponComponent.HC_10.id));
    }

    @Test
    void theOnagersCoaxCardTakesOnlyAOneRowBallisticPart() {
        assertTrue(CampaignVehicle.takesCoax(VehicleType.BREAKTHROUGH_TANK, MechWeaponComponent.LIGHT_MACHINE_GUN));
        assertFalse(CampaignVehicle.takesCoax(VehicleType.BREAKTHROUGH_TANK, MechWeaponComponent.BL_1),
                "a one-row energy part is not ballistic");
        assertFalse(CampaignVehicle.takesCoax(VehicleType.BREAKTHROUGH_TANK, MechWeaponComponent.CHAINGUN),
                "two rows do not fit a one-row card");
        assertFalse(CampaignVehicle.takesCoax(VehicleType.MEDIUM_TANK, MechWeaponComponent.LIGHT_MACHINE_GUN),
                "no coax card, no coax");
        assertTrue(CampaignVehicle.takesWeapon(VehicleType.BREAKTHROUGH_TANK, MechMountSlot.RIGHT_ARM,
                MechWeaponComponent.LIGHT_MACHINE_GUN), "the coax card is the right arm on the doll");
        assertFalse(CampaignVehicle.takesWeapon(VehicleType.BREAKTHROUGH_TANK, MechMountSlot.LEFT_ARM,
                MechWeaponComponent.LIGHT_MACHINE_GUN));
        assertNull(CampaignVehicle.hull("v1", "Breaker", VehicleType.BREAKTHROUGH_TANK).coax(), "sold empty");
    }

    @Test
    void aCoaxFitsFromStoresBesideTheGunAndIsKeptAsTheGunIs() {
        MechBay bay = new MechBay();
        CampaignVehicle tank = CampaignVehicle.hull("v1", "Breaker", VehicleType.BREAKTHROUGH_TANK);
        assertTrue(bay.addVehicle(MechBay.STARTER_SQUAD_ID, tank));
        bay.addWeapon(MechWeaponComponent.HC_20.id, 1);
        bay.addWeapon(MechWeaponComponent.LIGHT_MACHINE_GUN.id, 2);
        assertTrue(bay.fitGun("v1", MechWeaponComponent.HC_20));
        assertFalse(bay.fitVehicleWeapon("v1", MechMountSlot.RIGHT_ARM, MechWeaponComponent.HC_20), "too big");
        assertTrue(bay.fitVehicleWeapon("v1", MechMountSlot.RIGHT_ARM, MechWeaponComponent.LIGHT_MACHINE_GUN));

        assertSame(MechWeaponComponent.HC_20, tank.gun(), "the gun card is untouched");
        assertSame(MechWeaponComponent.LIGHT_MACHINE_GUN, tank.coax());
        assertSame(MechWeaponComponent.LIGHT_MACHINE_GUN, tank.freezeForDeployment().coax(), "the drop carries it");
        assertEquals(1, bay.installedWeapon(MechWeaponComponent.LIGHT_MACHINE_GUN.id));
        assertEquals(1, bay.availableWeapon(MechWeaponComponent.LIGHT_MACHINE_GUN.id));

        assertTrue(bay.stripVehicleWeapon("v1", MechMountSlot.RIGHT_ARM));
        assertNull(tank.coax());
        assertSame(MechWeaponComponent.HC_20, tank.gun(), "stripping the coax leaves the gun");
        assertEquals(2, bay.availableWeapon(MechWeaponComponent.LIGHT_MACHINE_GUN.id), "back to stores");

        assertTrue(bay.fitVehicleWeapon("v1", MechMountSlot.RIGHT_ARM, MechWeaponComponent.LIGHT_MACHINE_GUN));
        assertEquals(1, bay.loseVehicles(List.of("v1")));
        assertEquals(1, bay.ownedWeapon(MechWeaponComponent.LIGHT_MACHINE_GUN.id), "a wrecked hull takes its coax");
    }
}
