package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechBayTest {

    @Test
    void newBayHasEmptyGantryAndNoFreeSubsystemStock() throws Exception {
        MechBay bay = new MechBay();

        assertEquals(0, bay.activeSquad().mechs().size());
        assertEquals(0, bay.ownedInternal(MechInternal.STANDARD_LOADER.id));
        assertEquals(0, bay.ownedWeapon(MechWeaponComponent.CHAINGUN.id));
        assertEquals(0, roundTrip(bay).activeSquad().mechs().size(),
                "the modern empty start must not be mistaken for a legacy save");
    }

    @Test
    void preFoundingModelSaveRetainsItsStarterEntitlement() throws Exception {
        MechBay bay = new MechBay();
        Field marker = MechBay.class.getDeclaredField("foundingModelInitialized");
        marker.setAccessible(true);
        marker.setBoolean(bay, false);

        MechBay restored = roundTrip(bay);

        assertEquals(1, restored.activeDeployment().size());
        assertEquals(MechVariant.BULWARK, restored.activeDeployment().get(0).variant());
        assertEquals(1, restored.availableInternal(MechInternal.ACCELERATED_LOADER.id));
    }

    @Test
    void aSavedRenamedCannonCarriesOverToItsSuccessor() throws Exception {
        MechBay bay = new MechBay();
        Field weapons = MechBay.class.getDeclaredField("ownedWeapons");
        weapons.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Integer> owned = (Map<String, Integer>) weapons.get(bay);
        owned.put("component.mech-linear-cannon", 2);
        owned.put("component.mech-muster-autogun", 1);
        owned.put(MechWeaponComponent.AC_6_QS.id, 1);
        Field patterns = MechBay.class.getDeclaredField("knownPatternIds");
        patterns.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> known = (Set<String>) patterns.get(bay);
        known.add(MechPattern.PREFIX + "weapon:component.mech-quarry-breaker");

        MechBay restored = roundTrip(bay);

        assertEquals(MechWeaponComponent.HC_10, MechWeaponComponent.findById("component.mech-linear-cannon"));
        assertEquals(2, restored.ownedWeapon(MechWeaponComponent.HC_10.id));
        assertEquals(2, restored.ownedWeapon(MechWeaponComponent.AC_6_QS.id), "merged with stock under the new id");
        assertTrue(restored.knowsPattern(MechPattern.of(MechWeaponComponent.DC_4_QS).id()));
        assertFalse(restored.knowsPattern(MechPattern.PREFIX + "weapon:component.mech-quarry-breaker"));
    }

    @Test
    void legacyStarterFixtureOwnsInstalledStandardAndOneUpgrade() {
        MechBay bay = MechBay.legacyStarterFixture();
        CampaignMech mech = bay.mechById(MechBay.STARTER_MECH_ID);

        assertEquals(1, bay.activeSquad().mechs().size());
        assertEquals(MissileReplenisherComponent.STANDARD.id(),
                mech.missileReplenisherId());
        assertEquals(1, bay.ownedInternal(MechInternal.STANDARD_LOADER.id));
        assertEquals(0, bay.availableInternal(MechInternal.STANDARD_LOADER.id));
        assertEquals(1, bay.availableInternal(MechInternal.ACCELERATED_LOADER.id));
        assertEquals(2, bay.ownedWeapon(MechWeaponComponent.CHAINGUN.id));
        assertEquals(0, bay.availableWeapon(MechWeaponComponent.CHAINGUN.id));
    }

    @Test
    void refitReturnsCurrentItemAndCannotOverAssignFiniteUpgrade() {
        MechBay bay = MechBay.legacyStarterFixture();
        CampaignMech second = new CampaignMech(
                "support_mech_02", "Hound 02", MechVariant.HOUND,
                MechRole.ASSAULT, MissileReplenisherComponent.STANDARD.id());
        assertTrue(bay.addMech(MechBay.STARTER_SQUAD_ID, second));

        assertTrue(bay.installInternal(MechBay.STARTER_MECH_ID, MechInternal.ACCELERATED_LOADER));
        assertEquals(1, bay.availableInternal(MechInternal.STANDARD_LOADER.id),
                "the replaced standard unit should return to stores");
        assertFalse(bay.installInternal(second.id(), MechInternal.ACCELERATED_LOADER));
        assertEquals(MissileReplenisherComponent.STANDARD.id(),
                second.missileReplenisherId(),
                "failed atomic refit must preserve the prior component");
    }

    @Test
    void activeSquadFreezesValuesAndPersistsInstalledInventory() throws Exception {
        MechBay bay = MechBay.legacyStarterFixture();
        assertTrue(bay.installInternal(MechBay.STARTER_MECH_ID, MechInternal.ACCELERATED_LOADER));
        bay.addWeapon(MechWeaponComponent.PL_2_AP.id, 1);
        assertTrue(bay.installWeapon(MechBay.STARTER_MECH_ID, MechMountSlot.LEFT_ARM,
                MechWeaponComponent.PL_2_AP.id));

        MechBay restored = roundTrip(bay);
        List<MechDeploymentSpec> deployment = restored.activeDeployment();

        assertEquals(1, deployment.size());
        assertEquals(MechVariant.BULWARK, deployment.get(0).variant());
        assertEquals(MechRole.ARMORED_SUPPORT, deployment.get(0).role());
        assertEquals(MissileReplenisherComponent.ACCELERATED_FEED,
                deployment.get(0).missileReplenisher());
        assertEquals(MechWeaponComponent.PL_2_AP, deployment.get(0).leftArm());
        assertEquals(0, restored.availableInternal(MechInternal.ACCELERATED_LOADER.id));
        assertEquals(1, restored.availableInternal(MechInternal.STANDARD_LOADER.id));
        assertEquals(1, restored.availableWeapon(MechWeaponComponent.CHAINGUN.id));
        assertEquals(0, restored.availableWeapon(MechWeaponComponent.PL_2_AP.id));
    }

    @Test
    void weaponRefitReturnsTheOutgoingAssemblyAndFreezesTheReplacement() {
        MechBay bay = MechBay.legacyStarterFixture();
        bay.addWeapon(MechWeaponComponent.PL_2_AP.id, 1);

        assertTrue(bay.installWeapon(MechBay.STARTER_MECH_ID, MechMountSlot.LEFT_ARM,
                MechWeaponComponent.PL_2_AP.id));

        CampaignMech mech = bay.mechById(MechBay.STARTER_MECH_ID);
        assertEquals(MechWeaponComponent.PL_2_AP, mech.leftArm());
        assertEquals(1, bay.availableWeapon(MechWeaponComponent.CHAINGUN.id));
        assertEquals(0, bay.availableWeapon(MechWeaponComponent.PL_2_AP.id));
        assertEquals(MechWeaponComponent.PL_2_AP,
                bay.activeDeployment().get(0).leftArm());
    }

    @Test
    void houndCenterlineMountRejectsAThreeByTwoHeavyCannon() {
        MechBay bay = MechBay.legacyStarterFixture();
        CampaignMech hound = new CampaignMech(
                "support_mech_02", "Hound 02", MechVariant.HOUND,
                MechRole.ASSAULT, MissileReplenisherComponent.STANDARD.id());
        assertTrue(bay.addMech(MechBay.STARTER_SQUAD_ID, hound));
        bay.addWeapon(MechWeaponComponent.HC_20.id, 1);

        assertFalse(bay.canInstallWeapon(hound.id(), MechMountSlot.ARMS,
                MechWeaponComponent.HC_20.id));
        assertFalse(bay.installWeapon(hound.id(), MechMountSlot.ARMS,
                MechWeaponComponent.HC_20.id));
        assertTrue(bay.canInstallWeapon(hound.id(), MechMountSlot.ARMS,
                MechWeaponComponent.CHAINGUN.id));
        assertFalse(bay.canInstallWeapon(hound.id(), MechMountSlot.RIGHT_SHOULDER,
                MechWeaponComponent.SRM_5.id));
        assertEquals(MechWeaponComponent.CHAINGUN, hound.arms());
    }

    @Test
    void legacyRosterBackfillsTheMechBay() throws Exception {
        MarineRoster roster = new MarineRoster();
        Field mechBay = MarineRoster.class.getDeclaredField("mechBay");
        mechBay.setAccessible(true);
        mechBay.set(roster, null);
        Method readResolve = MarineRoster.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);

        readResolve.invoke(roster);

        assertEquals(1, roster.mechBay().activeDeployment().size());
        assertEquals(MechVariant.BULWARK,
                roster.mechBay().activeDeployment().get(0).variant());
    }

    private static MechBay roundTrip(MechBay bay) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(bay);
        }
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (MechBay) in.readObject();
        }
    }
}
